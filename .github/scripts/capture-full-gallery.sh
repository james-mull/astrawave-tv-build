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
adb shell wm size 1080x2400 || true
adb shell wm density 420 || true

capture() {
  local name="$1"
  shift
  adb shell am force-stop com.astrawave.app
  adb shell am start -W "$@" >/dev/null
  sleep 5
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

# Real player surface using a public test HLS stream; tap once so controls are visible.
adb shell am force-stop com.astrawave.app
adb shell am start -W -n com.astrawave.app/.PlayerActivity \
  --es url "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" \
  --es library_title "AstraWave Player Preview" >/dev/null || true
sleep 8
adb shell input tap 540 1200 || true
sleep 2
adb exec-out screencap -p > "$OUT/player.png" || true

# Capture UI hierarchy for troubleshooting and an index of the generated pages.
adb shell uiautomator dump /sdcard/astrawave-gallery.xml >/dev/null 2>&1 || true
adb pull /sdcard/astrawave-gallery.xml "$OUT/final-window.xml" >/dev/null 2>&1 || true
printf '%s\n' "$OUT"/*.png | sed 's#^.*/##' | sort > "$OUT/index.txt"

echo "Captured $(find "$OUT" -name '*.png' | wc -l) AstraWave customer-page screenshots."
