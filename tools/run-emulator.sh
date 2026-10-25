#!/usr/bin/env bash
# Builds the debug APK, boots the emulator if needed, then installs and starts the game.
#
#   tools/run-emulator.sh            build + install + launch
#   tools/run-emulator.sh --no-build install + launch the last build
#
# Environment overrides: ANDROID_HOME (default ~/Android/Sdk), AVD (default Pixel7_API35),
# JAVA_HOME (default ~/.local/jdk-21*, Gradle 8.10 does not run on newer JDKs).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
AVD="${AVD:-Pixel7_API35}"
PACKAGE="com.tpcstld.twozerogame.noads"
APK="$ROOT/2048/base/build/outputs/apk/debug/base-debug.apk"
ADB="$SDK/platform-tools/adb"

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(ls -d "$HOME"/.local/jdk-21* 2>/dev/null | head -n 1 || true)"
    export JAVA_HOME
fi

if [[ "${1:-}" != "--no-build" ]]; then
    (cd "$ROOT/2048" && ./gradlew -q :base:assembleDebug)
fi

if ! "$ADB" devices | grep -q "^emulator-"; then
    echo "Starting emulator $AVD..."
    # Software rendering: the host GPU path crashes on some Linux/Wayland setups.
    nohup "$SDK/emulator/emulator" -avd "$AVD" -gpu swiftshader_indirect -no-boot-anim \
        > /dev/null 2>&1 &
fi

"$ADB" wait-for-device
until [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
done

"$ADB" install -r "$APK" > /dev/null
"$ADB" shell am start -n "$PACKAGE/com.tpcstld.twozerogame.MainActivity" > /dev/null
echo "2048 is running in the emulator."
