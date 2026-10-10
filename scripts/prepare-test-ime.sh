#!/usr/bin/env bash
# Dedicated CI emulator only: keep its window active and select a software keyboard.
set -euo pipefail
# Semantic Compose actions do not necessarily reset Android's screen-idle timer.
# A sleeping/locked emulator stops lifecycle collection and native-view touch tests.
adb shell settings put system screen_off_timeout 2147483647
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
trip_test_ime="$(adb shell ime list -s | tr -d '\r' | sed -n '1p')"
if [[ ! "$trip_test_ime" =~ ^[[:alnum:]_.]+/[[:alnum:]_.]+$ ]]; then
    echo 'The test emulator has no installed input method. Keyboard tests require one.'
    exit 1
fi
adb shell ime enable "$trip_test_ime"
adb shell ime set "$trip_test_ime"
