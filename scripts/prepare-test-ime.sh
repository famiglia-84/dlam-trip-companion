#!/usr/bin/env bash
# Dedicated CI emulator only: select its installed software keyboard before UI tests.
set -euo pipefail
trip_test_ime="$(adb shell ime list -s | tr -d '\r' | sed -n '1p')"
if [[ ! "$trip_test_ime" =~ ^[[:alnum:]_.]+/[[:alnum:]_.]+$ ]]; then
    echo 'The test emulator has no installed input method. Keyboard tests require one.'
    exit 1
fi
adb shell ime enable "$trip_test_ime"
adb shell ime set "$trip_test_ime"
