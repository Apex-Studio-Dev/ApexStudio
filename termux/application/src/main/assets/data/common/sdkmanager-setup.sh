#!/usr/bin/env bash
# ApexStudio SDK Manager setup.
#
# Installs Android cmdline-tools (sdkmanager), accepts the SDK licenses and
# records the packages available in the SDK repository into a JSON database the
# SDK Manager UI reads to list installable platforms/build-tools. Writes:
#   $PREFIX/etc/apexstudio/sdkmanager-packages.json
#
# Usage:
#   sdkmanager-setup.sh [--manifest <path>]
set -eu

MANIFEST="$(dirname "$0")/toolchain-manifest.json"
MIN_API=30
MIN_BUILD_TOOLS=30

: "${PREFIX:?PREFIX must be set (e.g. /data/data/dev.apexstudio.ide/files/usr)}"
: "${HOME:?HOME must be set}"

usage() { echo "usage: $0 [--manifest <path>]" >&2; exit 1; }

log() { echo "[sdkmanager-setup] $*"; }

err() { echo "[sdkmanager-setup] ERROR: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --manifest) [ $# -ge 2 ] || usage; MANIFEST="$2"; shift 2 ;;
    *) usage ;;
  esac
done

[ -f "$MANIFEST" ] || err "manifest not found: $MANIFEST"
command -v jq >/dev/null 2>&1 || err "jq is missing from the bootstrap"

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
CMDTOOLS_DIR="$SDK_DIR/cmdline-tools/latest"
SDKMANAGER="$CMDTOOLS_DIR/bin/sdkmanager"
DB_DIR="$PREFIX/etc/apexstudio"
DB_FILE="$DB_DIR/sdkmanager-packages.json"
TMP="${TMPDIR:-$PREFIX/tmp}"
APEXSTUDIO_ETC="$PREFIX/etc/apexstudio"

# ---- 0. ensure a JDK (and the tools sdkmanager wraps) are available ----
JVM_ROOT=""
for v in 21 17 25; do
  if [ -x "$PREFIX/lib/jvm/java-$v-openjdk/bin/java" ]; then
    JVM_ROOT="$PREFIX/lib/jvm/java-$v-openjdk"
    break
  fi
done
if [ -z "$JVM_ROOT" ]; then
  log "No JDK installed; installing the recommended openjdk-21"
  apt update
  apt install -y openjdk-21 jq tar unzip curl || err "apt install of the base packages failed"
  JVM_ROOT="$PREFIX/lib/jvm/java-21-openjdk"
fi
export JAVA_HOME="$JVM_ROOT"
export PATH="$JAVA_HOME/bin:$PATH"

# ---- 1. cmdline-tools (sdkmanager) ----
if [ ! -x "$SDKMANAGER" ]; then
  CTOOLS_URL="$(jq -r '.sdkmanager.url' "$MANIFEST")"
  log "Downloading cmdline-tools: $CTOOLS_URL"
  CTOOLS_ZIP="$TMP/commandlinetools-linux.zip"
  curl -L --fail --retry 3 -o "$CTOOLS_ZIP" "$CTOOLS_URL" || err "download of cmdline-tools failed"
  rm -rf "$CMDTOOLS_DIR" "$TMP/ctools-staging"
  mkdir -p "$CMDTOOLS_DIR" "$TMP/ctools-staging" "$SDK_DIR"/{platform-tools,platforms,build-tools,licenses}
  log "Unpacking cmdline-tools"
  unzip -qq "$CTOOLS_ZIP" -d "$TMP/ctools-staging" || err "unzip of cmdline-tools failed"
  if [ -d "$TMP/ctools-staging/cmdline-tools" ]; then
    mv "$TMP/ctools-staging/cmdline-tools"/* "$CMDTOOLS_DIR"
  else
    mv "$TMP/ctools-staging"/* "$CMDTOOLS_DIR"
  fi
  rm -rf "$TMP/ctools-staging"
  rm -f "$CTOOLS_ZIP"
  chmod -R 755 "$CMDTOOLS_DIR/bin"
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

# ---- 2. licenses ----
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export ANDROID_USER_HOME="$HOME/.android"
export PATH="$CMDTOOLS_DIR/bin:$SDK_DIR/platform-tools:$PATH"
log "Accepting SDK licenses"
yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true

# ---- 3. query the repository and store the available catalog ----
log "Querying sdkmanager --list"
LIST_TMP="$TMP/sdkmanager-list.txt"
"$SDKMANAGER" --list >"$LIST_TMP" 2>&1 || err "sdkmanager --list failed"

platforms="$(grep -oE 'platforms;android-[0-9]+' "$LIST_TMP" \
  | sed 's/^platforms;android-//' \
  | awk -v m="$MIN_API" '$1 >= m' \
  | sort -nr -u)"

build_tools="$(grep -oE 'build-tools;[0-9]+\.[0-9]+([.0-9]+)?' "$LIST_TMP" \
  | sed 's/^build-tools;//' \
  | awk -F. -v m="$MIN_BUILD_TOOLS" '$1 >= m' \
  | sort -t. -k1,1nr -k2,2nr -k3,3nr -u)"

rm -f "$LIST_TMP"

n_platforms="$(printf '%s\n' "$platforms" | grep -c '^[0-9]' || true)"
n_build_tools="$(printf '%s\n' "$build_tools" | grep -c '^[0-9]' || true)"
log "Found $n_platforms platforms and $n_build_tools build-tools to offer"

platform_list="$(printf '%s\n' "$platforms" | sed '/^$/d' | jq -R . | jq -s -c .)"
build_tools_list="$(printf '%s\n' "$build_tools" | sed '/^$/d' | jq -R . | jq -s -c .)"

mkdir -p "$DB_DIR" "$APEXSTUDIO_ETC"
{
  printf '{\n'
  printf '"schema": 1,\n'
  printf '"platforms": %s,\n' "$platform_list"
  printf '"build_tools": %s\n' "$build_tools_list"
  printf '}\n'
} > "$DB_FILE"

log "Catalog written to $DB_FILE"