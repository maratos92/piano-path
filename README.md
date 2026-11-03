# Piano Path

**Practice workspace preview (0.4.0-dev.1, this branch):** [Find the most recent Android build](https://github.com/maratos92/piano-path/actions/workflows/debug.yml?query=branch%3Acodex%2Fpractice-workspace-2026-11-03) → open a successful run → download **piano-path-debug-apk** under Artifacts → unzip and install `app-debug.apk`. This contains the combined score/falling notes, whole-piece seek slider, pause, live noteheads, ear training, and improved history. [Changes and handoff in PR #1](https://github.com/maratos92/piano-path/pull/1).

**Previously published stable preview (0.3.0, `main`):** [Download old Android APK](https://github.com/maratos92/piano-path/releases/download/v0.3.0-preview/piano-path-0.3.0-debug.apk) · [Old release page](https://github.com/maratos92/piano-path/releases/tag/v0.3.0-preview). **This older download does NOT include the features in this pull request.**

> The whole-piece seek bar is intentionally locked while **scored Practice is running** to avoid corrupting note accuracy; it can be dragged while stopped or in Listen mode. Passage loop boundaries use the two handles in Practice controls. A new debug APK may use a different signing certificate, requiring uninstalling the previously installed debug app; back up local data first.

An offline, open-source Android piano practice app for compatible Bluetooth LE MIDI and class-compliant USB MIDI instruments. **An installable, debug-signed APK is supplied with this delivery.** Android 8.0 (API 26) or later is required.

The older main release is version 0.3.0. This PR branch is version 0.4.0-dev.1: a working initial implementation with automated music-engine tests, rather than a hardware-certified piano tutor. **The user reports Pixel / Roland FP-10 testing. Other pianos, USB and physical tablets still require acceptance testing.** See [validation and limitations](docs/VALIDATION.md).

## Install and try it

1. Download `piano-path-debug.apk` from the delivery, or the `piano-path-debug-apk` artifact under GitHub Actions → Build and test Android. Unzip the Actions artifact first.
2. Open the APK on your Android phone and allow installation from your browser/file manager when Android prompts. This build is signed with a development key; use it for evaluation.
3. Open **Connect piano → Find piano**, grant Bluetooth access, and select your instrument. On Android 8–11 also enable system Location for BLE discovery. Enable Bluetooth MIDI on the piano, and close other apps using its connection.
4. Open the **Library**. Choose an offline study, or **Import MusicXML / MXL** to copy a score into private local storage.
5. Open the tune icon in the score reader. Choose mode, hands, tempo and passage measures. Optional quarter-beat offsets allow finer boundaries. Enable looping/count-in and save a named passage.
6. Tap **Practice**. Complete a repetition or tap **Finish** for separate pitch/rhythm results and note-level timing details. Open History for saved repetitions.

**Without a piano:** tap **Home → Play without a piano**, or enable **Touch piano** in any score. The keys make sound through a offline sampled grand piano. For evaluated touch practice, choose Wait/Follow/Rhythm in Practice controls and tap Practice; Free Play lets you play without scoring. Touch/test sessions are marked in history.

**Listen to music:** open any score and tap **Listen**. The selected passage plays aloud at the chosen BPM, with separate left/right/both-hand filtering and the passage loop setting. **Stop music** stops playback. Listening never feeds generated notes into assessment and never awards a practice accuracy result. Turn up Android's media volume. Touch keys use a fixed velocity; MIDI diagnostics still report the instrument’s actual velocity. Sound uses Alexander Holm’s Salamander Grand Piano samples (CC BY 3.0), with 128 voices, sustain and velocity-controlled amplitude. The compact bank has one dynamic layer, rather than a full multi-layer piano instrument; no download is required.

**Falling bars:** choose **Falling notes** beside **Sheet** in the reader (falling notes is the default). Each note falls onto its matching key; bar length represents notated duration. Blue = left staff, green = right staff; played/missed notes change colour during assessment. Listen animates the bars automatically; Practice uses the selected practice mode. In Wait mode bars wait for the correct pitches. In Follow mode they follow the performance. Free Play has no enforced score transport, so use Listen to watch a demonstration.

**Whole keyboard:** all 88 keys, **A0–C8**, fit on screen initially in both orientations. Tap **Fit all 88** to restore this view. For playable-size touch keys, tap **+**, use **C4** to centre middle C, and move the **A0–C8 range slider** to reach lower or higher notes. The falling bars use the identical zoom and position, so they stay aligned. Fitting 88 keys on a narrow phone necessarily makes each key small; landscape or zoom is recommended for touch performance.

**Visual review:** choose Sheet, then Practice. Rhythm mode (the default) starts the cursor after count-in; Follow starts on the first expected pitch, and Wait advances on chord completion. Green markers show matched input, amber early/late input, red extras and dashed rings missed notes. Hold traces show physical key-down duration; they are not pedal sustain. After Finish select Review on score, scrub the score, or reopen a take in History. This is a diagnostic overlay, not a quantised MusicXML transcription/export. Wrong pitches use a conventional sharp spelling; MIDI does not carry enharmonic spelling or physical-hand identity.

**Live note names:** toggle Notes in practice, or open the navigation menu → Note monitor. Diagnostics provides timestamps, channels, velocity and controller events.

**Landscape:** practice hides the navigation rail. The top-left menu opens navigation as an overlay. Practice controls offer horizontal score layout and a 0–32 px side margin. Required clef/key/time spacing remains. Appearance follows the system by default and can be overridden in Settings.

**USB:** connect a data cable/OTG adapter, select a class-compliant MIDI input under Connect piano → USB MIDI. Bluetooth grants are not needed. See [compatibility testing](docs/HARDWARE_TESTING.md).

**Google Play and voluntary support:** see [store preparation](docs/STORE_READINESS.md) and [privacy draft](docs/PRIVACY.md). No ads or in-app payments are included. Settings → Support Piano Path opens [the owner’s Ko-fi page](https://ko-fi.com/maratbatkaev) in a browser for voluntary tips; every feature stays free. Production publication still requires the owner's account, signing, disclosures and testing.

## What is implemented

- Native Android `MidiManager.openBluetoothDevice`, BLE MIDI service discovery, MIDI output-port reception, connection status, bounded exponential reconnection and connection timeout. Version-specific Bluetooth/location permissions. Disconnection stops active assessment and clears keys.
- Streaming MIDI 1.0 parsing with running status, fragmented packets, zero-velocity note-on, embedded real-time bytes, SysEx skipping, channel-aware sustain, all-notes-off and panic/reset. Android decodes BLE packet timestamps/framing.
- MusicXML partwise and MXL import (12 MB compressed/expanded cap); grand staff, multiple voices, chords, rests, dots, accidentals, transposition, tied-duration merging, fingering and initial tempo. Secure external-entity handling. Bundled OSMD 1.9.2 renders traditional notation, with zoom, vertical/horizontal layouts, cursor and played/missed note colours. Unselected noteheads are dimmed.
- Follow, Wait, Rhythm and Free Play modes, independently combined with left/right/both staff selection. Adjustable tempo, input offset and tolerance. Polyphonic matching, arpeggiation allowance, pitch/missing/extra/chord results, onset/release deviations in milliseconds, early/late counts, hand-specific completion and onset-variation statistics.
- Measure and quarter-beat passage controls, looping, count-in, metronome, named saved passages, per-loop Room history and optional +4 BPM after a 90% repetition.
- All twelve tonics, major/natural/harmonic/melodic minor, chromatic and major arpeggio exercises; one/two octaves, hands, ascent or ascent+descent. Curated conventional fingering for common keys; uncurated keys are left unnumbered. MIDI does **not** measure actual finger choice.
- Seeded sight-reading generator, four complexity settings, nine major key signatures, 3/4, 4/4 and 6/8, hands, count-in/metronome and limited/no preview. Shared notation/evaluation engine and category-filtered practice history.
- Full 88-key fit/zoom/pan touch piano, an aligned falling-note view, and unscored offline score listening. 128-voice acoustic sample playback, sustain, voice limits, audio focus and background cleanup.
- Responsive Compose screens, full-width practice with a closed-by-default navigation drawer, system/light/dark appearance, local Room persistence. No Internet permission, accounts, analytics or backend.

## Score library

The APK contains **50 original CC0 pedagogical miniatures**, ten in each approximate difficulty band. These are original studies, not a curated historical repertoire collection; difficulty has not been independently graded. [Full catalogue](docs/CATALOG.md) and [edition licensing](docs/SCORE_LICENSING.md).

Obtain a legally reusable MusicXML/MXL edition yourself, or export your own piano engraving from MuseScore, then import it. PDF, image scores, MIDI files and `.mscz` are not accepted. Select the piano part before exporting multi-instrument scores.

## Build

Install a **full JDK 21** (including `jlink`), Android SDK platform 36, build-tools 36.0.0 and platform-tools. Android Studio's SDK manager can install these. The Gradle wrapper pins 8.11.1; AGP is 8.10.1 and Kotlin is 2.0.21.

```sh
# Point local.properties at your SDK; do not commit it.
printf 'sdk.dir=/absolute/path/to/android-sdk\n' > local.properties
./gradlew :core:test :app:lintDebug :app:assembleDebug
# Result: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The first build needs network access for dependencies. The installed app works offline. There are no API keys or service credentials. On a network with a custom CA/proxy, configure Gradle/JDK trust normally; never disable TLS checks. In restricted workspaces, set `ANDROID_USER_HOME` and `GRADLE_USER_HOME` to writable directories.

```sh
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest # running emulator/device required
python3 scripts/generate_library.py     # reproduce the original score library
# Optional desktop rendering check: Python playwright + Chromium required
python3 scripts/test_notation.py
```

GitHub Actions runs tests/lint, uploads a debug APK, and includes API 36 phone and API 35 tablet emulator tests. The project is published at https://github.com/maratos92/piano-path. [The release-candidate run](https://github.com/maratos92/piano-path/actions/runs/41674522753) passed 77 JVM tests and 9 Android tests on each phone/tablet emulator. The preview-publishing workflow verifies the delivered APK checksum and publishes that exact development-signed binary; it does not expose any signing key. Separately rebuilt CI debug APKs use disposable CI keys, so they may require uninstalling an earlier debug build before installation. [Signed-release procedure](docs/RELEASE.md), [architecture](docs/ARCHITECTURE.md), [FP-10 testing](docs/FP10_TESTING.md), [validation](docs/VALIDATION.md), [third-party notices](docs/DEPENDENCIES.md).

## Important practical limits

- Follow mode searches only the current and next onset group. It handles small deviations and pauses, but does not perform arbitrary jump/repeat recovery or machine-learned alignment. Restart/select a passage after a large jump. Rhythm mode uses a fixed timeline; Follow mode adapts phase after completed onset groups.
- Evaluation follows written order and a single practice BPM. OSMD can display repeats, ornaments, tempo changes and tuplets, but assessment does not unfold repeats/Da Capo, evaluate grace notes or follow tempo maps. Export/unfold repeats and use a fixed tempo for assessed practice.
- Only one MusicXML piano part is assessed. Hand attribution uses staff number, so cross-staff passages and unison pitches require care. Staff membership is not a measurement of the physical hand. Indistinguishable unison duplicates across voices may be undercounted.
- Release timing measures physical note-off against notated duration, including merged ties. Pedal extends sounding display, not expected finger duration. Legato articulation and half-pedal artistry are not graded. Metronome is a best-effort phone tone, not an audio-clock sequencer; calibrate your device and audio path.
- Sampled playback uses written order and the selected BPM; it does not unfold repeats, implement expressive tempo maps or interpret score pedal markings. Touch sustain and incoming MIDI CC64 are handled separately. Speaker/headphone audio latency has not been measured on a physical phone.
- No background service: keep the app visible while practicing. Do not treat initial scores as validated pedagogical or examination material. Fingering for black-key scales and chromatic exercises is not yet curated. Arpeggios currently use major triads.

## Licence

App source: [MIT](LICENSE). Original generated score files: [CC0-1.0](app/src/main/assets/scores/CC0-1.0.txt). Piano samples: Alexander Holm, Salamander Grand Piano, [CC BY 3.0](licenses/CC-BY-3.0.txt), adapted from [Tone.js/audio](https://github.com/Tonejs/audio/tree/869b6f8d9cddb47d966238c012041480b1ce517a/salamander); see the bundled provenance and [notices](docs/DEPENDENCIES.md). OSMD: BSD-3-Clause plus upstream notices. Imported scores remain private and are never uploaded by the app.
