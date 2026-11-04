#!/usr/bin/env bash
# Only emulator-owned app data is created/deleted by this installation regression.
set -euo pipefail
candidate="$1"
baseline="$2"
adb install "$baseline"
adb shell run-as org.pianopath mkdir -p files
adb shell run-as org.pianopath touch files/upgrade-check
adb install -r "$candidate"
adb shell run-as org.pianopath test -f files/upgrade-check
adb shell am start -W -n org.pianopath/.MainActivity
adb uninstall org.pianopath

# Also verify a clean installation for the explicitly authorised signing reset.
adb install "$candidate"
adb shell am start -W -n org.pianopath/.MainActivity
adb shell run-as org.pianopath test ! -f files/upgrade-check
adb uninstall org.pianopath
