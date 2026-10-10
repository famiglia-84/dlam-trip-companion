#!/usr/bin/env bash
# Dedicated CI emulator only: keep its window active and select a software keyboard.
set -euo pipefail
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
    echo 'This preparation script is for the dedicated test emulator only.'
    exit 1
fi
# Repeated density/window-size changes can ANR Pixel Launcher and leave its
# system dialog above the test activity. Tests launch activities directly and
# do not need a homescreen. Disable only that launcher in this disposable AVD;
# never hide error dialogs or interfere with Trip Companion's own ANR/focus checks.
trip_test_launcher=com.google.android.apps.nexuslauncher
if [[ "$(adb shell pm path "$trip_test_launcher" | tr -d '\r')" == package:* ]]; then
    adb shell am force-stop "$trip_test_launcher"
    adb shell pm disable-user --user 0 "$trip_test_launcher"
    if [[ "$(adb shell pm list packages -d "$trip_test_launcher" | tr -d '\r')" != *"package:$trip_test_launcher"* ]]; then
        echo 'Could not disable the unstable test-emulator launcher.'
        exit 1
    fi
fi
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
