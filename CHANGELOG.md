# Changelog

## 0.4.0-dev.5 (build 8)

- Integrates PRs #1–#3 with fixes for score scrolling, post-zoom cursor/A/B gestures and Android viewport sizing.
- Fixes paused note durations/releases, restored listening voices and passage-loop restart.
- Starts an owner-authorised fresh-install signing generation using the explicitly selected retained CI key; certificate mismatch/key loss stops publication.
- Adds fresh-install and same-key update checks alongside phone/tablet and core regressions.


## 0.3.0

- Full-width landscape practice with a closed navigation drawer, compact score margins and system/light/dark appearance.
- Live pitch/velocity/channel monitor in practice and a dedicated screen.
- Continuous Rhythm-mode cursor and same-score performed-note/hold overlays; saved visual takes reopen from History. Room history migrates in place.
- Offline Salamander acoustic piano samples by Alexander Holm (CC BY 3.0), 128 voices and independent channel/sustain/repeated-strike handling.
- Native class-compliant USB MIDI selection, attachment/removal handling and bounded reconnection alongside general BLE MIDI support.
- Optional voluntary Ko-fi support at the owner-supplied public link; external browser payment, no feature unlocks or ads.
- API 36 target, release APK/AAB workflow, static 16 KB checks, phone/tablet CI jobs, store/testing/privacy drafts.
- 77 passing JVM tests and offline OSMD checks across all 50 studies. Android instrumentation sources include visual take storage, landscape navigation and the voluntary-support explanation; runtime reports are tracked separately.

Physical USB, additional piano models and tablets still require acceptance testing. Google Play approval, personal developer verification, production signing and the required closed test are not supplied by a debug APK.

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
