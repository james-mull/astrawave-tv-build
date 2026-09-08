#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-phone}"
APK="app/build/outputs/apk/debug/app-debug.apk"
EVIDENCE_DIR="../device-smoke/${MODE}"

mkdir -p "$EVIDENCE_DIR"

gradle --no-daemon --stacktrace :app:assembleDebug
test -s "$APK"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.astrawave.app
adb shell am start -W -n com.astrawave.app/.AstraWaveEntryActivity
sleep 8

assert_alive() {
  local pid
  pid="$(adb shell pidof com.astrawave.app | tr -d '\r')"
  if [[ -z "$pid" ]]; then
    echo "AstraWave process is not alive" >&2
    adb logcat -d -v time >&2 || true
    exit 1
  fi
}

assert_alive
adb shell dumpsys activity activities | grep -q 'com.astrawave.app'

case "$MODE" in
  phone)
    adb shell settings put system accelerometer_rotation 0
    adb shell settings put system user_rotation 0
    sleep 2
    assert_alive
    adb shell settings put system user_rotation 1
    sleep 3
    assert_alive
    adb shell settings put system user_rotation 0
    ;;
  tablet)
    adb shell input keyevent KEYCODE_TAB || true
    adb shell input keyevent KEYCODE_BACK || true
    sleep 2
    assert_alive
    ;;
  tv)
    adb shell input keyevent KEYCODE_DPAD_DOWN
    adb shell input keyevent KEYCODE_DPAD_RIGHT
    adb shell input keyevent KEYCODE_DPAD_CENTER
    sleep 2
    assert_alive
    adb shell input keyevent KEYCODE_BACK
    sleep 2
    assert_alive
    ;;
  *)
    echo "Unknown smoke mode: $MODE" >&2
    exit 2
    ;;
esac

adb shell uiautomator dump /sdcard/astrawave-window.xml || true
adb pull /sdcard/astrawave-window.xml "$EVIDENCE_DIR/window.xml" || true
adb exec-out screencap -p > "$EVIDENCE_DIR/screen.png" || true
adb logcat -d -v time > "$EVIDENCE_DIR/logcat.txt" || true

if grep -E 'FATAL EXCEPTION|ANR in com\.astrawave\.app|Process: com\.astrawave\.app' "$EVIDENCE_DIR/logcat.txt"; then
  echo 'Crash/ANR signature found in AstraWave smoke log.' >&2
  exit 1
fi

echo "AstraWave ${MODE} smoke passed."
