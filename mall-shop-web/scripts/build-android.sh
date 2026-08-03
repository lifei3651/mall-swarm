#!/usr/bin/env bash
set -euo pipefail

LINGQIMALL_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LINGQIMALL_WEB_DIR="$(cd "${LINGQIMALL_SCRIPT_DIR}/.." && pwd)"
LINGQIMALL_LOCAL_JDK="/Users/minmatemp/.local/share/lingqimall-android/jdk-21.0.11+10/Contents/Home"
LINGQIMALL_LOCAL_SDK="/Users/minmatemp/.local/share/lingqimall-android/sdk"

if [[ -x "${LINGQIMALL_LOCAL_JDK}/bin/java" ]]; then
  export JAVA_HOME="${LINGQIMALL_LOCAL_JDK}"
elif [[ -x /usr/libexec/java_home ]]; then
  LINGQIMALL_DETECTED_JDK="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  if [[ -n "${LINGQIMALL_DETECTED_JDK}" ]]; then
    export JAVA_HOME="${LINGQIMALL_DETECTED_JDK}"
  fi
fi

if [[ -d "${LINGQIMALL_LOCAL_SDK}/platforms/android-36" ]]; then
  export ANDROID_HOME="${LINGQIMALL_LOCAL_SDK}"
fi

cd "${LINGQIMALL_WEB_DIR}/android"
./gradlew assembleDebug

LINGQIMALL_APK_SOURCE="${LINGQIMALL_WEB_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"
LINGQIMALL_APK_RELEASE="${LINGQIMALL_WEB_DIR}/releases/lingqimall-android-1.3-test.apk"
mkdir -p "${LINGQIMALL_WEB_DIR}/releases"
cp "${LINGQIMALL_APK_SOURCE}" "${LINGQIMALL_APK_RELEASE}"
echo "Android test APK: ${LINGQIMALL_APK_RELEASE}"
