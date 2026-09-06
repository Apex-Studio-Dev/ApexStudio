#!/usr/bin/env bash
# ApexStudio toolchain uninstaller.
#
# Removes selected SDK components from $ANDROID_HOME. Lone
# installations are skipped. The base environment (JDK, aapt2 and the
# command-line utilities) is installed via apt by install-toolchain.sh and is
# intentionally left untouched: removing it would break the IDE's environment.
#
# Usage:
#   uninstall-toolchain.sh [--platform <api>] [--build-tools <ver>] \
#     [--ndk <ver>] [--cmake <ver>]
#   Repeat a flag to remove multiple versions.
set -eu

PLATFORMS=()
BUILD_TOOLS=()
NDKS=()
CMAKES=()

usage() { echo "usage: $0 [options]" >&2; exit 1; }

log() { echo "[uninstall-toolchain] $*"; }

while [ $# -gt 0 ]; do
  case "$1" in
    --platform) [ $# -ge 2 ] || usage; PLATFORMS+=("$2"); shift 2 ;;
    --build-tools) [ $# -ge 2 ] || usage; BUILD_TOOLS+=("$2"); shift 2 ;;
    --ndk) [ $# -ge 2 ] || usage; NDKS+=("$2"); shift 2 ;;
    --cmake) [ $# -ge 2 ] || usage; CMAKES+=("$2"); shift 2 ;;
    *) usage ;;
  esac
done

: "${HOME:?HOME must be set}"

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"

if [ ! -d "$SDK_DIR" ]; then
  log "SDK dir not found: $SDK_DIR"
  exit 0
fi

log "Using SDK dir: $SDK_DIR"

for api in "${PLATFORMS[@]}"; do
  if [ -d "$SDK_DIR/platforms/android-$api" ]; then
    rm -rf "$SDK_DIR/platforms/android-$api"
    log "Removed platform android-$api"
  else
    log "Platform android-$api not installed, skipping"
  fi
done

for bt in "${BUILD_TOOLS[@]}"; do
  if [ -d "$SDK_DIR/build-tools/$bt" ]; then
    rm -rf "$SDK_DIR/build-tools/$bt"
    log "Removed build-tools $bt"
  else
    log "Build-tools $bt not installed, skipping"
  fi
done

for ndk in "${NDKS[@]}"; do
  base="$SDK_DIR/ndk/$ndk"
  if [ -L "$base" ]; then
    # Short token (e.g. r29) symlinks to the canonical Pkg.Revision dir.
    rev="$(readlink -f "$base")"
    rm -rf "$rev" "$base"
    log "Removed NDK $ndk ($rev)"
  elif [ -e "$base" ]; then
    rm -rf "$base"
    log "Removed NDK $ndk"
  else
    log "NDK $ndk not installed, skipping"
  fi
done

for cma in "${CMAKES[@]}"; do
  if [ -d "$SDK_DIR/cmake/$cma" ]; then
    rm -rf "$SDK_DIR/cmake/$cma"
    log "Removed CMake $cma"
  else
    log "CMake $cma not installed, skipping"
  fi
done

log "Uninstall complete"