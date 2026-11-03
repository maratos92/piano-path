# Android MIDI compatibility testing

The user reports successful testing with a Google Pixel and Roland FP-10. Exact Pixel model, Android version, firmware, pedal and tested features have not been recorded. Other instruments, physical Android tablets and USB MIDI are unverified until a report is supplied. No iPhone support is included.

Support is based on standard BLE MIDI and Android's class-compliant USB MIDI 1.0 service, rather than a Roland-specific protocol. This makes other compatible instruments plausible; it does not prove every model works. Bluetooth audio alone is insufficient. Driver-dependent USB interfaces and MIDI 2.0-only endpoints are outside the present compatibility claim.

Record app version/commit, instrument/firmware, phone/tablet model, Android/WebView versions, connection type, cable/adapter and pedal. Use the same passage and fixed BPM for comparison.

1. BLE: enable the instrument's MIDI mode, close competing apps, grant requested permissions and connect inside Piano Path. Test denial and later grant. On Android 8–11 test the Location system switch. See FP10_TESTING.md for the detailed original instrument procedure.
2. USB: use the instrument's USB-to-host/data port, a USB data cable and a phone USB host/OTG adapter. Use standard/class-compliant mode. Connect via Connect piano → USB MIDI; confirm port names. Bluetooth permissions must not be needed. Try a powered hub if the phone cannot supply sufficient power.
3. In Note monitor/Diagnostics play single notes, fast repeated notes, ten-note chords, legato overlap, low/high keys and soft/loud strikes. Compare pitch names, velocities, channels, physical key releases and pedal state to the instrument.
4. During held chords unplug the USB cable or power off Bluetooth. Confirm all keys clear, active practice stops, and no sound remains stuck. Reattach the same USB device or restore BLE. Confirm reconnection, restart practice explicitly, and test manual disconnect does not automatically reconnect. With two identical USB devices select the input explicitly.
5. Practice deliberate correct, extra, missing, early/late and shortened/lengthened notes. Review the overlay on the original score and reopen it from History after restarting the app. A MIDI reference recorder is preferable to subjective tapping for timing validation.
6. Test Listen, touch sound, high-polyphony sustain, interruption by another audio app, background/foreground, full keyboard, dark/system mode, rotation and a ten-minute loop on both a phone and a physical tablet.

Report each check as pass/fail/not tested, with screenshots or event logs where helpful. Simulated input and emulator passes must be labelled separately from hardware results.
