# Changelog

## 0.2.0

- Added offline, polyphonic piano-like sound for touch playing, including velocity response, sustain and release envelopes.
- Added unscored **Listen / Stop music** playback for the selected score passage, tempo, hands and loop setting. Generated demonstration notes do not earn practice scores.
- Added **Falling notes / Sheet** tabs. Falling bars show note timing, duration, staff, finger numbers where available and performance feedback. They align with the keyboard at every zoom/pan setting.
- Replaced the C3–B5 keyboard with all **88 keys, A0–C8**, fitted on screen by default. Added **Fit all 88**, key-size controls, **C4** centring and a range slider. Removed scrolling that competed with multi-touch playing.
- Added **Play without a piano** on Home and a visible **Touch piano** toggle in the reader. Kept the keyboard visible in landscape.
- Added audio focus, idle output shutdown and background interruption cleanup. Bumped versionCode to 2; the debug signing identity is retained for an in-place update from 0.1.
- Added 17 JVM tests for PCM synthesis, pitch/velocity, sustain/release/polyphony, all-key hit regions, playback event ordering/clipping and falling-bar geometry, for **66 passing JVM tests** total.
- Added Android test coverage for discovering the full keyboard/falling-note view and listening without an accuracy result. These compile; local runtime execution remains blocked by emulator boot failure.

Sound is generated synthesis, not a sampled acoustic grand piano. Playback follows written order at the chosen BPM and does not interpret score pedal marks, repeats or expressive tempo maps. Physical phone audio output/latency and real Roland FP-10 testing remain unverified.

## 0.1.0

Initial Android implementation: BLE MIDI, MusicXML/MXL, notation, score following, pitch/rhythm evaluation, passage loops, scales, sight reading, original offline studies and local history. The first build had a limited-range silent touch simulator and no falling-note view or score sound playback.
