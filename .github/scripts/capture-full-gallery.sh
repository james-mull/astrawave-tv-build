#!/usr/bin/env bash
set -euo pipefail

APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="../full-page-gallery"
mkdir -p "$OUT"

gradle --no-daemon --stacktrace :app:assembleDebug
test -s "$APK"
adb install -r "$APK"
adb shell settings put system accelerometer_rotation 0 || true
adb shell settings put system user_rotation 0 || true
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put global anr_show_background 0 || true
adb shell wm size 1080x2400 || true
adb shell wm density 420 || true

clear_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
}

astrawave_is_foreground() {
  adb shell dumpsys window windows 2>/dev/null \
    | grep -E 'mCurrentFocus|mFocusedApp' \
    | grep -q 'com.astrawave.app'
}

capture() {
  local name="$1"
  shift

  adb shell am force-stop com.astrawave.app
  adb shell am start -W "$@" >/dev/null
  sleep 5
  clear_system_dialogs
  sleep 1

  if ! astrawave_is_foreground; then
    echo "AstraWave was not foreground for ${name}; retrying once."
    adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
    clear_system_dialogs
    adb shell am start -W "$@" >/dev/null
    sleep 4
    clear_system_dialogs
    sleep 1
  fi

  if ! astrawave_is_foreground; then
    echo "ERROR: AstraWave is not foreground for ${name}."
    adb shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' || true
    return 1
  fi

  adb exec-out screencap -p > "$OUT/${name}.png"
}

# Main customer destinations rendered by the real application shell.
for route in home movies tv live guide sports multiview audio personal-media addons discover search my profiles; do
  safe="${route//\//-}"
  capture "$safe" -n com.astrawave.app/.RebuildMainActivity --es qa_start_route "$route"
done

# Nested account/settings destinations rendered with their real production composables.
for page in subscription playback audio-subtitles downloads notifications appearance backup devices privacy-parental premium-hub debrid diagnostics; do
  capture "$page" -n com.astrawave.app/.CustomerPageGalleryActivity --es page "$page"
done

# First-run experience.
capture "onboarding" -n com.astrawave.app/.OnboardingActivity

# Movie/TV details and watch-options surfaces.
capture "title-details" -n com.astrawave.app/.TitleDetailsActivity \
  --es title "The Expanse" --es media_type "SERIES" --es profile_id "default"
capture "vod-detail" -n com.astrawave.app/.PremiumVodDetailActivity \
  --es title "Dune" --es media_type "MOVIE" --es profile_id "default"

# Real player surface using a public test HLS stream; keep AstraWave foreground and expose controls.
adb shell am force-stop com.astrawave.app
adb shell am start -W -n com.astrawave.app/.PlayerActivity \
  --es url "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" \
  --es library_title "AstraWave Player Preview" >/dev/null
sleep 8
clear_system_dialogs

if ! astrawave_is_foreground; then
  echo "ERROR: PlayerActivity left the foreground before capture."
  adb shell dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' || true
  exit 1
fi

adb shell input tap 540 1200 || true
sleep 2
clear_system_dialogs
if ! astrawave_is_foreground; then
  echo "ERROR: PlayerActivity left the foreground after showing controls."
  exit 1
fi
adb exec-out screencap -p > "$OUT/player.png"

# Capture UI hierarchy for troubleshooting and an index of the generated pages.
adb shell uiautomator dump /sdcard/astrawave-gallery.xml >/dev/null 2>&1 || true
adb pull /sdcard/astrawave-gallery.xml "$OUT/final-window.xml" >/dev/null 2>&1 || true
printf '%s\n' "$OUT"/*.png | sed 's#^.*/##' | sort > "$OUT/index.txt"

echo "Captured $(find "$OUT" -name '*.png' | wc -l) AstraWave customer-page screenshots."
