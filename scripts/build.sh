#!/usr/bin/env bash
#
# Builds SoluPlay with the portable toolchain on this PC.
#
# The JDK, the Android SDK and the SDK location are machine specific, so they
# live here rather than in the build files. Nothing is downloaded: the build
# runs offline, which is the Phase 0 gate.
#
#   scripts/build.sh                 # both debug flavours + unit tests
#   scripts/build.sh assemblePhoneRelease
#   scripts/build.sh --online assemblePhoneDebug

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# The toolchain lives in the account's home folder. The account's name is not
# written into this file, so the repository can be public.
USER_HOME="${USERPROFILE:-$HOME}"
export JAVA_HOME="${JAVA_HOME:-$USER_HOME/android-tv-toolchain/jdk17/jdk-17.0.20.1+1}"
export ANDROID_HOME="${ANDROID_HOME:-$USER_HOME/android-tv-toolchain/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

OFFLINE="--offline"
TASKS=()

for arg in "$@"; do
  case "$arg" in
    --online) OFFLINE="" ;;
    *) TASKS+=("$arg") ;;
  esac
done

if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(assemblePhoneDebug assembleTvDebug test)
fi

./gradlew ${OFFLINE} "${TASKS[@]}" --console=plain

echo
echo "APKs:"
find app/build/outputs/apk -name "*.apk" -newer settings.gradle.kts 2>/dev/null || \
  find app/build/outputs/apk -name "*.apk" 2>/dev/null
