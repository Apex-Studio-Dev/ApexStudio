#!/usr/bin/env bash
# ApexStudio toolchain installer.
#
# Manifest-driven (jq). Installs, from the bundled toolchain-manifest.json:
#   - JDK (openjdk via apt) and aapt2 (apt) from the Apex apt repository
#   - Android cmdline-tools (sdkmanager) and SDK platforms / build-tools
#   - NDK and CMake (multi-version, coexisting under $ANDROID_HOME)
# Writes $PREFIX/etc/ide-environment.properties read by the app at startup.
#
# Usage:
#   install-toolchain.sh [--manifest <path>] [--jdk <17|21|25>] \
#     [--platform <api>|all] [--build-tools <ver>|all] \
#     [--ndk <ver>|all|none] [--cmake <ver>|all|none]
# Defaults: --jdk 21 --platform <none> --build-tools <none> --ndk none --cmake none
# (platforms/build-tools are only installed when explicitly requested)
#   Repeat a flag to install multiple versions, or pass one of all.
#   --env-only installs just the base environment packages (JDK, aapt2 and
#   utilities from the Apex apt repo) and writes the environment, skipping
#   the Android SDK (cmdline-tools / platforms / build-tools / NDK / CMake).
set -eu

ENV_ONLY=""
MANIFEST="$(dirname "$0")/toolchain-manifest.json"
JDK=""
PLATFORMS=()
BUILD_TOOLS=()
NDKS=("none")
CMAKES=("none")

usage() { echo "usage: $0 [options]" >&2; exit 1; }

log() { echo "[install-toolchain] $*"; }

err() { echo "[install-toolchain] ERROR: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --manifest) [ $# -ge 2 ] || usage; MANIFEST="$2"; shift 2 ;;
    --jdk) [ $# -ge 2 ] || usage; JDK="$2"; shift 2 ;;
    --platform) [ $# -ge 2 ] || usage; PLATFORMS+=("$2"); shift 2 ;;
    --build-tools) [ $# -ge 2 ] || usage; BUILD_TOOLS+=("$2"); shift 2 ;;
    --ndk) [ $# -ge 2 ] || usage; NDKS+=("$2"); shift 2 ;;
    --cmake) [ $# -ge 2 ] || usage; CMAKES+=("$2"); shift 2 ;;
    --env-only) ENV_ONLY="1"; shift ;;
    *) usage ;;
  esac
done

: "${PREFIX:?PREFIX must be set (e.g. /data/data/dev.apexstudio.ide/files/usr)}"
: "${HOME:?HOME must be set}"

if [ "$ENV_ONLY" != "1" ]; then
  [ -f "$MANIFEST" ] || err "manifest not found: $MANIFEST"
  command -v jq >/dev/null 2>&1 || err "jq is missing from the bootstrap"
fi

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
CMDTOOLS_DIR="$SDK_DIR/cmdline-tools/latest"
SDKMANAGER="$CMDTOOLS_DIR/bin/sdkmanager"
IDE_ENV_FILE="$PREFIX/etc/ide-environment.properties"
TMP="${TMPDIR:-$PREFIX/tmp}"

[ -z "$JDK" ] && JDK="21"

# Markers that prove a component is fully installed. Mirror the strong checks
# used by the Kotlin SDK manager (ToolchainStatus).
platform_ok() { [ -f "$SDK_DIR/platforms/android-$1/android.jar" ]; }
build_tools_ok() { [ -f "$SDK_DIR/build-tools/$1/aapt2" ]; }
ndk_ok() { [ -f "$SDK_DIR/ndk/$1/source.properties" ]; }
cmake_ok() { [ -x "$SDK_DIR/cmake/$1/bin/cmake" ]; }

# Expands the requested NDK/CMake tokens into concrete versions ('all' -> the
# manifest list). Used by cleanup and only defined here so it is available
# before the install functions are registered.
ndk_requested() {
  for v in "${NDKS[@]}"; do
    if [ "$v" = "all" ]; then json_array '.ndk[].version'; elif [ "$v" != "none" ]; then echo "$v"; fi
  done
}
cmake_requested() {
  for v in "${CMAKES[@]}"; do
    if [ "$v" = "all" ]; then json_array '.cmake[].version'; elif [ "$v" != "none" ]; then echo "$v"; fi
  done
}

# Runs a (potentially long) foreground command in the background so bash's
# signal traps fire immediately: `wait` is interruptible, a foreground child is
# not (bash defers the trap until the child finishes).
bg_wait() {
  "$@" &
  local pid=$!
  wait "$pid"
}

# Removes tmp/staging artifacts and any requested component that was left in a
# partial/broken state by this failed run. Components that pass their marker
# check are never touched.
cleanup_on_fail() {
  local code=$?
  trap - EXIT INT TERM
  set +e
  # Kill any children that survived a cancel/failure (curl, sdkmanager, tar,
  # unzip, ...) so they cannot keep writing into the SDK tree while we remove
  # partial components below. Only OUR children are signalled (not the process
  # group): the app spawns bash in its own process group and must survive.
  if [ "$code" -ne 0 ]; then
    pkill -TERM -P $$ 2>/dev/null
    sleep 1
    pkill -KILL -P $$ 2>/dev/null
  fi
  rm -rf "$TMP/ctools-staging" "$TMP/ndk-extract"
  rm -f "$TMP/commandlinetools-linux.zip" "$TMP"/ndk-*.tar.xz "$TMP"/cmake-* 2>/dev/null || true
  if [ "$code" -eq 0 ]; then
    return 0
  fi
  log "Install failed (exit $code); removing partial/stale artifacts"
  [ ! -x "$SDKMANAGER" ] && { [ -d "$CMDTOOLS_DIR" ] && rm -rf "$CMDTOOLS_DIR" && log "  removed partial cmdline-tools"; }
  local v
  while IFS= read -r v; do
    platform_ok "$v" || { [ -e "$SDK_DIR/platforms/android-$v" ] && { rm -rf "$SDK_DIR/platforms/android-$v" && log "  removed partial platform android-$v"; }; }
  done < <(requested platforms "${PLATFORMS[@]}")
  while IFS= read -r v; do
    build_tools_ok "$v" || { [ -e "$SDK_DIR/build-tools/$v" ] && { rm -rf "$SDK_DIR/build-tools/$v" && log "  removed partial build-tools $v"; }; }
  done < <(requested build_tools "${BUILD_TOOLS[@]}")
  local ndk
  while IFS= read -r ndk; do
    if [ -L "$SDK_DIR/ndk/$ndk" ]; then
      rm -f "$SDK_DIR/ndk/$ndk" && log "  removed partial NDK link $ndk"
    elif ndk_ok "$ndk"; then
      :
    elif [ -e "$SDK_DIR/ndk/$ndk" ]; then
      rm -rf "$SDK_DIR/ndk/$ndk" && log "  removed partial NDK $ndk"
    fi
  done < <(ndk_requested)
  local cma
  while IFS= read -r cma; do
    cmake_ok "$cma" || { [ -e "$SDK_DIR/cmake/$cma" ] && { rm -rf "$SDK_DIR/cmake/$cma" && log "  removed partial CMake $cma"; }; }
  done < <(cmake_requested)
  log "Cleanup complete"
  return 0
}
trap cleanup_on_fail EXIT

# The app sends SIGTERM to bash on Cancel. Kill our running children first so
# a partial download/install stops writing, then exit (the EXIT trap cleans up).
# Do NOT use `kill -TERM -$$` here — bash runs in the app's process group, so
# that would take the whole Android app down.
sigexit() {
  pkill -TERM -P $$ 2>/dev/null
  exit 130
}
trap sigexit INT TERM

is_all() { [ "$1" = "all" ]; }

json_array() { jq -r "$1" "$MANIFEST"; }

in_manifest() { # <array-prop> <value>
  json_array ".[\"$1\"][]" 2>/dev/null | grep -qx "$2"
}

# Catalog DB written by sdkmanager-setup.sh; lists what is actually available
# in the SDK repository. Falls back to the bundled manifest when absent.
SDK_CATALOG="$PREFIX/etc/apexstudio/sdkmanager-packages.json"

requested() { # <prop> <array...>; prints resolved versions (all -> catalog/manifest list) separated by newline
  local prop="$1"; shift
  local out=()
  local item
  for item in "$@"; do
    if is_all "$item"; then
      while IFS= read -r v; do out+=("$v"); done < <(catalog_values "$prop")
    else
      out+=("$item")
    fi
  done
  printf '%s\n' "${out[@]}"
}

catalog_values() { # <prop>; prints versions from the SDK catalog when present, else the manifest
  if [ -f "$SDK_CATALOG" ]; then
    jq -r --arg p "$1" '.[$p][]?' "$SDK_CATALOG" 2>/dev/null && return 0
  fi
  json_array ".[\"$1\"][]"
}

in_catalog() { # <prop> <value>; true when present in the catalog if it exists, else manifest
  if [ -f "$SDK_CATALOG" ]; then
    jq -re --arg p "$1" --arg v "$2" '.[$p] | contains([$v])' "$SDK_CATALOG" >/dev/null 2>&1
  else
    in_manifest "$1" "$2"
  fi
}

jdk_pkg() { # <17|21|25> -> apt package name
  case "$1" in
    17) echo "openjdk-17" ;;
    21) echo "openjdk-21" ;;
    25) echo "openjdk-25" ;;
    *) echo "openjdk-$1" ;;
  esac
}

requested() { # <prop> <array...>; prints resolved versions (all -> manifest list) separated by newline
  local prop="$1"; shift
  local out=()
  local item
  for item in "$@"; do
    if is_all "$item"; then
      while IFS= read -r v; do out+=("$v"); done < <(json_array ".[\"$prop\"][]")
    else
      out+=("$item")
    fi
  done
  printf '%s\n' "${out[@]}"
}

log "Using manifest: $MANIFEST"
log "SDK dir: $SDK_DIR"
log "Installing JDK $JDK, platforms: ${PLATFORMS[*]}, build-tools: ${BUILD_TOOLS[*]}, ndk: ${NDKS[*]}, cmake: ${CMAKES[*]}"

# ---- 0. verify requested versions exist in the manifest ----
if [ "$ENV_ONLY" != "1" ]; then
in_manifest "jdk" "$JDK" || err "JDK $JDK is not listed in the manifest"
while IFS= read -r api; do in_catalog "platforms" "$api" || err "platform android-$api is not available for installation"; done \
  < <(requested platforms "${PLATFORMS[@]}")
while IFS= read -r bt; do in_catalog "build_tools" "$bt" || err "build-tools $bt is not available for installation"; done \
  < <(requested build_tools "${BUILD_TOOLS[@]}")
while IFS= read -r ndk; do
  is_all "$ndk" && continue
  [ "$ndk" = "none" ] && continue
  jq -er --arg v "$ndk" '.ndk[] | select(.version == $v) | .url' "$MANIFEST" >/dev/null || err "ndk $ndk is not listed in the manifest"
done < <(printf '%s\n' "${NDKS[@]}")
while IFS= read -r cma; do
  is_all "$cma" && continue
  [ "$cma" = "none" ] && continue
  jq -er --arg v "$cma" '.cmake[] | select(.version == $v) | .url' "$MANIFEST" >/dev/null || err "cmake $cma is not listed in the manifest"
done < <(printf '%s\n' "${CMAKES[@]}")
fi

# ---- 1. base packages + JDK + aapt2 from the Apex apt repo ----
log "apt update"
bg_wait apt update
APKGS=()
for v in $(jdk_pkg "$JDK"); do APKGS+=("$v"); done
APKGS+=(aapt2 jq tar unzip curl)
log "apt install: ${APKGS[*]}"
bg_wait apt install -y "${APKGS[@]}" || err "apt install failed (is the Apex apt repository reachable?)"

# ---- 2. scaffold the SDK layout ----
mkdir -p "$SDK_DIR"/{cmdline-tools,platform-tools,platforms,build-tools,ndk,cmake,licenses} "$TMP"

if [ "$ENV_ONLY" = "1" ]; then
  log "Writing $IDE_ENV_FILE (--env-only)"
  {
    echo "# Generated by ApexStudio install-toolchain"
    echo "JAVA_HOME=$PREFIX/lib/jvm/java-$JDK-openjdk"
    echo "ANDROID_HOME=$SDK_DIR"
  } > "$IDE_ENV_FILE"
  [ -d "$HOME/.gradle" ] || mkdir -p "$HOME/.gradle"
  GP="$HOME/.gradle/gradle.properties"
  touch "$GP"
  if ! grep -q 'android.aapt2FromMavenOverride' "$GP"; then
    {
      echo ""
      echo "# ApexStudio: use standalone aapt2 (from apt) instead of the Gradle-bundled one"
      echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
    } >> "$GP"
  fi
  log "Environment packages installed (--env-only)"
  exit 0
fi

# ---- 3. cmdline-tools (sdkmanager) ----
if [ ! -x "$SDKMANAGER" ]; then
  CTOOLS_URL="$(json_array '.sdkmanager.url')"
  log "Downloading cmdline-tools: $CTOOLS_URL"
  CTOOLS_ZIP="$TMP/commandlinetools-linux.zip"
  bg_wait curl -L --fail --retry 3 -o "$CTOOLS_ZIP" "$CTOOLS_URL" || err "download of cmdline-tools failed"
  rm -rf "$CMDTOOLS_DIR" "$TMP/ctools-staging"
  mkdir -p "$CMDTOOLS_DIR" "$TMP/ctools-staging"
  log "Unpacking cmdline-tools"
  bg_wait unzip -qq "$CTOOLS_ZIP" -d "$TMP/ctools-staging" || err "unzip of cmdline-tools failed"
  # The zip contains a top-level cmdline-tools/ folder; flatten it into latest/.
  if [ -d "$TMP/ctools-staging/cmdline-tools" ]; then
    mv "$TMP/ctools-staging/cmdline-tools"/* "$CMDTOOLS_DIR"
  else
    mv "$TMP/ctools-staging"/* "$CMDTOOLS_DIR"
  fi
  rm -rf "$TMP/ctools-staging"
  rm -f "$CTOOLS_ZIP"
  chmod -R 755 "$CMDTOOLS_DIR/bin"
  # sdkmanager/avdmanager are env shebang scripts; point at $PREFIX/bin/env so
  # the Termux environment is used when the app launches them.
  ENV_PATH="$(command -v env)"
  for f in "$CMDTOOLS_DIR"/bin/*; do
    [ -f "$f" ] || continue
    head -1 "$f" | grep -q '^#!/usr/bin/env' || continue
    rest="$(sed -n '1s|^#!/usr/bin/env||p' "$f")"
    sed -i "1c#!$ENV_PATH$rest" "$f"
  done
else
  log "cmdline-tools already present, skipping"
fi

# ---- 4. environment for sdkmanager ----
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_USER_HOME="$HOME/.android"
export JAVA_HOME="$PREFIX/lib/jvm/java-${JDK}-openjdk"
[ -x "$JAVA_HOME/bin/java" ] || err "JDK missing at $JAVA_HOME"
export PATH="$JAVA_HOME/bin:$CMDTOOLS_DIR/bin:$SDK_DIR/platform-tools:$PATH"

# ---- 5. licenses ----
log "Accepting SDK licenses"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 &
wait $! || true

# ---- 6. platforms + build-tools ----
PLATFORM_PKGS=()
while IFS= read -r api; do PLATFORM_PKGS+=("platforms;android-$api"); done < <(requested platforms "${PLATFORMS[@]}")
BT_PKGS=()
while IFS= read -r bt; do BT_PKGS+=("build-tools;$bt"); done < <(requested build_tools "${BUILD_TOOLS[@]}")
log "sdkmanager installing: ${PLATFORM_PKGS[*]} ${BT_PKGS[*]}"
if [ ${#PLATFORM_PKGS[@]} -gt 0 ] || [ ${#BT_PKGS[@]} -gt 0 ]; then
  bg_wait "$SDKMANAGER" "${PLATFORM_PKGS[@]}" "${BT_PKGS[@]}" || err "sdkmanager install failed"
fi

# ---- 7. NDK (musl builds, need symlink fixes for the Gradle layout) ----
# The install dir name is the embedded Pkg.Revision from source.properties
# (e.g. r27d -> ndk/27.3.13750724), exactly how Gradle/AGP resolve the NDK.
install_ndk() {
  local ndk="$1"
  local url
  url="$(jq -r --arg v "$ndk" '.ndk[] | select(.version == $v) | .url' "$MANIFEST")"
  local file="$TMP/ndk-$ndk.tar.xz"
  if [ -e "$SDK_DIR/ndk/$ndk/source.properties" ]; then
    log "NDK $ndk already installed, skipping"
    return
  fi
  log "Downloading NDK $ndk: $url"
  bg_wait curl -L --fail --retry 3 -o "$file" "$url" || err "download of NDK $ndk failed"
  log "Extracting NDK $ndk"
  rm -rf "$TMP/ndk-extract"
  mkdir -p "$TMP/ndk-extract"
  bg_wait tar --no-same-owner -xf "$file" -C "$TMP/ndk-extract" || err "extract of NDK $ndk failed"
  local src
  src="$(find "$TMP/ndk-extract" -maxdepth 1 -type d -name 'android-ndk-*' | head -1)"
  [ -n "$src" ] || src="$TMP/ndk-extract/$ndk"
  [ -d "$src" ] || err "could not locate extracted NDK dir"
  # Resolve the canonical revision BEFORE moving; it names the install dir.
  local rev
  rev="$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' "$src/source.properties" 2>/dev/null | head -1)"
  [ -n "$rev" ] || err "missing Pkg.Revision in source.properties for NDK $ndk"
  rm -rf "$SDK_DIR/ndk/$ndk" "$SDK_DIR/ndk/$rev"
  mv "$src" "$SDK_DIR/ndk/$rev"
  rm -rf "$TMP/ndk-extract" "$file"
  # musl builds ship linux-arm64 prebuilts; Gradle looks for linux-aarch64.
  local d
  for d in "$SDK_DIR/ndk/$rev/toolchains/llvm/prebuilt" "$SDK_DIR/ndk/$rev/prebuilt" "$SDK_DIR/ndk/$rev/shader-tools"; do
    [ -d "$d" ] && { [ -e "$d/linux-aarch64" ] || ln -s linux-arm64 "$d/linux-aarch64"; }
  done
  # Keep the short version token (r27d/r29/...) as a symlink to the revision dir.
  if [ "$rev" != "$ndk" ]; then
    ln -sfn "$rev" "$SDK_DIR/ndk/$ndk"
  fi
  log "NDK $ndk installed as $rev"
}

install_cmake() {
  local cma="$1"
  local url kind
  url="$(jq -r --arg v "$cma" '.cmake[] | select(.version == $v) | .url' "$MANIFEST")"
  kind="$(jq -r --arg v "$cma" '.cmake[] | select(.version == $v) | .kind' "$MANIFEST")"
  if [ -x "$SDK_DIR/cmake/$cma/bin/cmake" ]; then
    log "CMake $cma already installed, skipping"
    return
  fi
  log "Downloading CMake $cma: $url"
  local file="$TMP/cmake-$cma"
  bg_wait curl -L --fail --retry 3 -o "$file" "$url" || err "download of CMake $cma failed"
  log "Extracting CMake $cma"
  rm -rf "$SDK_DIR/cmake/$cma"
  mkdir -p "$SDK_DIR/cmake/$cma"
  case "$kind" in
    zip)
      bg_wait unzip -qq "$file" -d "$SDK_DIR/cmake/$cma" || { bg_wait unzip -qq -o "$file" -d "$SDK_DIR/cmake/$cma" || err "unzip of CMake $cma failed"; }
      ;;
    *)
      bg_wait tar -xf "$file" -C "$SDK_DIR/cmake/$cma" --strip-components=1 \
        || bg_wait tar -xf "$file" -C "$SDK_DIR/cmake/$cma" \
        || err "extract of CMake $cma failed"
      ;;
  esac
  chmod -R +x "$SDK_DIR/cmake/$cma/bin" 2>/dev/null || true
  rm -f "$file"
  log "CMake $cma installed"
}

while IFS= read -r ndk; do
  [ "$ndk" = "none" ] && continue
  is_all "$ndk" && { while IFS= read -r v; do install_ndk "$v"; done < <(json_array '.ndk[].version'); continue; }
  install_ndk "$ndk"
done < <(printf '%s\n' "${NDKS[@]}")

while IFS= read -r cma; do
  [ "$cma" = "none" ] && continue
  is_all "$cma" && { while IFS= read -r v; do install_cmake "$v"; done < <(json_array '.cmake[].version'); continue; }
  install_cmake "$cma"
done < <(printf '%s\n' "${CMAKES[@]}")

# ---- 8. persist environment for the app ----
log "Writing $IDE_ENV_FILE"
{
  echo "# Generated by ApexStudio install-toolchain"
  echo "JAVA_HOME=$PREFIX/lib/jvm/java-$JDK-openjdk"
  echo "ANDROID_HOME=$SDK_DIR"
} > "$IDE_ENV_FILE"

# ---- 9. let the Gradle build use the standalone aapt2 from apt ----
[ -d "$HOME/.gradle" ] || mkdir -p "$HOME/.gradle"
GP="$HOME/.gradle/gradle.properties"
touch "$GP"
if ! grep -q 'android.aapt2FromMavenOverride' "$GP"; then
  {
    echo ""
    echo "# ApexStudio: use standalone aapt2 (from apt) instead of the Gradle-bundled one"
    echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2"
  } >> "$GP"
fi

log "Toolchain setup complete"