#!/usr/bin/env bash
# Only emulator-owned app data is created/deleted by this installation regression.
set -euo pipefail
candidate="$1"
baseline="${RUNNER_TEMP:-/tmp}/piano-path-previous.apk"
curl --fail --location --retry 3 https://github.com/maratos92/piano-path/releases/download/latest-debug/piano-path-latest-debug.apk -o "$baseline"
adb install "$baseline"
adb shell run-as org.pianopath mkdir -p files
adb shell run-as org.pianopath touch files/upgrade-check
adb install -r "$candidate"
adb shell run-as org.pianopath test -f files/upgrade-check
adb shell am start -W -n org.pianopath/.MainActivity
adb uninstall org.pianopath
