#!/usr/bin/env bash
# Prepare an emulator fixture; never distribute the re-signed historical APK.
set -euo pipefail
candidate="$1"
output="$2"
sdk_tools="${ANDROID_HOME}/build-tools/36.0.0"
previous="${RUNNER_TEMP:-/tmp}/piano-path-previous.apk"
curl --fail --location --retry 3 https://github.com/maratos92/piano-path/releases/download/latest-debug/piano-path-latest-debug.apk -o "$previous"
certificate() { "$sdk_tools/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p'; }
code() { "$sdk_tools/aapt" dump badging "$1" | sed -nE "s/^package:.*versionCode='([0-9]+)'.*/\1/p"; }
expected=$(cat config/debug-certificate.sha256)
test "$(certificate "$candidate")" = "$expected"
current_code=$(code "$candidate")
previous_code=$(code "$previous")
test "$current_code" -ge "$previous_code"
if [ "$(certificate "$previous")" = "$expected" ]; then
  cp "$previous" "$output"
elif [ "$current_code" -eq 8 ] && [ "$previous_code" -lt 8 ]; then
  # The owner authorised a fresh-install signing transition for build 8. This
  # fixture tests future same-key upgrades; it is not a legacy upgrade claim.
  echo 'Creating an emulator-only same-key baseline for the authorised build-8 reset.'
  "$sdk_tools/apksigner" sign --ks "$PIANO_DEBUG_KEYSTORE_PATH" --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android --out "$output" "$previous"
else
  echo 'Unexpected published signing identity; refusing an automatic transition.' >&2
  exit 1
fi
test "$(certificate "$output")" = "$expected"
