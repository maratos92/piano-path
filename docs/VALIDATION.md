# Validation and feature limits — 0.2.0

## Verified in this workspace

- Full debug APK build and unsigned release APK build with Kotlin 2.0.21, Gradle 8.9, AGP 8.7.3, full JDK 21 and Android SDK 35.
- Debug and release Android lint: no errors. Remaining warnings concern dependency updates, the version-specific manifest flag, backup policy and kapt migration; these are not suppressed using a baseline.
- **66 JUnit tests passed, zero failures**: MIDI running status/fragmentation/realtime/SysEx/zero velocity, repeated notes, sustain/channel separation/panic; chords, arpeggiation, onset/release/latency math, missing/extra notes, hand filtering, pauses, loop boundaries, tempo scaling; MusicXML voices/chords/dots/fingerings/ties/transposition/MXL/security; scale pitches/minor descent/fingering, sight-reading determinism/measure boundaries and complete MusicXML-to-MIDI performance replays; all 88 touch hit regions, roll/key alignment through zoom/pan, bar onset/release/selection, playback event ordering/clipping, and synthesized PCM pitch/velocity/polyphony/sustain/release/panic behaviour.
- All 50 original score assets parse in the JVM engine and have CC0 attribution metadata.
- All 50 score assets engrave in the **actual vendored OSMD 1.9.2** in headless Chromium. Cursor movement/backward reset, played-note green colouring, missed-note colouring, range dimming and zoom/layout changes are exercised. No page errors or warnings were reported. This is desktop renderer verification, not proof of Android WebView compatibility on every phone.
- APK signature checked using Android `apksigner`; APK Signature Scheme v2 verifies. Manifest minimum API is 26, target API 35; no Internet permission. ARM64, ARMv7, x86 and x86_64 native graphics libraries are present.

Commands and reproducible procedures are in the README. JUnit XML/HTML, lint and signature reports are included with delivery artifacts. Build tasks and test sources are committed to Git.

## Android runtime tests

Instrumentation sources cover dashboard/library navigation, Android's own XML parser across all assets and generated exercises, local notation rendering in Android WebView, a simulated complete Wait-mode session, full-keyboard/falling-view discovery, and listening without an accuracy result. They compile into a separate test APK. The CI workflow runs them on API 35; no remote workflow run is claimed until the project is pushed to a GitHub repository.

For 0.1, local emulator instrumentation could not be completed: this workspace has no KVM device; software-emulated API 35 hit the disk limit, and an API 28 fallback did not complete its Android storage/boot sequence reliably. The instrumentation APK compiles, but its tests are **not claimed as passed** without a successful device report. No APK installation/launch or Android WebView runtime verification is claimed locally. Emulated MIDI cannot verify BLE radio or FP-10 firmware behaviour.

## Not verified

**Real Roland FP-10 + physical Android phone:** no hardware was available. Discovery, connection, reconnection, pedal/controller behaviour, end-to-end input latency, timestamp jitter and real piano performance scoring remain hardware acceptance tasks. Follow [FP10_TESTING.md](FP10_TESTING.md). Code implementation and unit tests must not be described as hardware verification.

A production signing identity was not supplied; the release build is deliberately unsigned. The provided debug APK is development-signed and installable. Owner-controlled signing is documented in [RELEASE.md](RELEASE.md).

## Significant scope limits

|Area|Implemented|Limit|
|---|---|---|
|MIDI|Native BLE MIDI, polyphony, velocity, release, CC64, resets, reconnection|Hardware validation pending; keep app visible|
|Import|Single piano-part MusicXML/MXL, grand staff/voices/ties and offline engraving|No PDF/MIDI/MSCZ or multi-instrument assessment; 12 MB cap|
|Following|Bounded current/next onset matcher; expressive pauses preserve position|No arbitrary jumps, repeat unfolding, Da Capo or learned alignment|
|Rhythm|Fixed practice BPM, onset/release errors, tolerance/latency and metronome|No tempo-map/ornament assessment; phone tone scheduling is not an audio sample clock|
|Hands|Per-staff pitch completion|Physical hands/fingers and ambiguous unisons cannot be identified from MIDI|
|Loops|Measures + quarter-beat offsets, saved passages, results/count-in/tempo progression|Numeric controls rather than drag-selecting the engraving|
|Playback and bars|Offline piano-like synthesis, unscored Listen, aligned falling bars, full 88-key fit/zoom/pan|No sampled acoustic grand, tempo-map/repeat/pedal-mark playback, or measured physical-device audio latency|
|Technique|All tonics and six exercise families, one/two octaves, two directions|Major triad arpeggios only; fingering curated for common keys, others explicitly blank|
|Sight reading|Fresh seeded exercises, four complexities, keys/time/hands/preview|Heuristic training levels, not independently graded exam standards|
|Library|50 original CC0 miniatures with full metadata|Requested curated historical repertoire remains incomplete; both Chopin waltzes import-only|
|History|Room aggregate results/category, passages and imports|Current note-level details are not archived; no cloud synchronization/export|

Wrong-pitch inputs are counted as extra events and the unplayed expected pitch remains missing. These are not two independent wrong-note detections. Pitch scoring uses `matched / (expected + extra)`. Chord completion requires every expected note in the group. Wait mode deliberately has no rhythm score. Free Play does not produce a scored session.

Version 0.2 adds an offline synthesized audio engine and falling-note view; physical speaker/headphone output and touch latency still require real-device testing. JVM PCM tests verify generated samples, rather than claiming an audible phone test.

The 0.2 local API 28 emulator retry again failed in Android storage encryption before PackageManager started. No on-device audible output, screenshot or instrumentation pass is claimed. The six instrumentation tests compile and remain available to run on a normal emulator/phone or in CI.
