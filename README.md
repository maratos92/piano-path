# Piano Path

## Install the latest tested app

**[Latest fully tested debug APK](https://github.com/maratos92/piano-path/releases/download/latest-debug/piano-path-0.5.0-dev.2-debug.apk)** · [Latest passing release notes](https://github.com/maratos92/piano-path/releases/tag/latest-debug) · [Build/test runs](https://github.com/maratos92/piano-path/actions/workflows/debug.yml)

The `latest-debug` download is automatically updated **only after all tests, lint and phone/tablet emulators pass on a merge to `main`**. It is a debug-signed APK for personal testing; it is **not** the privately signed Play Store release. If the link is not yet live after the first merge, find the newest successful `main` build under Actions → Build and test Android → Artifacts → `piano-path-debug-apk` (unzip `app-debug.apk`).

**This release: 0.5.0-dev.2 (build 10).** Settings → About shows the installed version, build code and source revision.

> **Updating:** build 8 and later use the same pinned signing identity and update in place. Uninstall an older dev.2/dev.3 app once, then install the APK above. The owner has authorised resetting the old local data. Future builds use the pinned key and can update this installation; CI stops if that key is missing or changed. The private key must also be backed up securely—Actions cache alone is not permanent storage.

An offline, open-source Android piano practice app for compatible Bluetooth LE MIDI and class-compliant USB MIDI instruments. **An installable, debug-signed APK is supplied with this delivery.** Android 8.0 (API 26) or later is required.

The source is version 0.5.0-dev.2: a working initial implementation with automated music-engine tests, rather than a hardware-certified piano tutor. **The user reports Pixel / Roland FP-10 testing. Other pianos, USB and physical tablets still require acceptance testing.** See [validation and limitations](docs/VALIDATION.md).

## Install and try it

1. Download the `latest-debug` APK linked at the top of this README (or unzip `app-debug.apk` from a successful Actions build).
2. Open the APK on your Android phone and allow installation from your browser/file manager when Android prompts. This build is signed with a development key; use it for evaluation.
3. Open **Connect piano → Find piano**, grant Bluetooth access, and select your instrument. On Android 8–11 also enable system Location for BLE discovery. Enable Bluetooth MIDI on the piano, and close other apps using its connection.
4. Use **Library → Import MusicXML / MXL** for your own piece, or **Practice → Scales**, **Sight reading** or **Listening** for exercises.
5. In the reader, drag the gold A/B handles on the score to select a loop. The range is highlighted on the sheet and marked on falling bars. The tune icon contains mode, hand, tempo and other passage controls.
6. Tap **Practice**. Complete a repetition or tap **Finish** for separate pitch/rhythm results and note-level timing details. Open History for saved repetitions.

**Without a piano:** tap **Home → Play without a piano**. The full on-screen 88-key piano is always touch-enabled and uses the selected sound. For evaluated touch practice, choose Wait/Follow/Rhythm in Practice controls and tap Practice; Free Play lets you play without scoring. Touch/test sessions are marked in history.

**Listen to music:** open any score and tap **Listen**. The selected passage plays aloud at the chosen BPM, with separate left/right/both-hand filtering and the passage loop setting. **Stop music** stops playback. Listening never feeds generated notes into assessment and never awards a practice accuracy result. Turn up Android's media volume. Touch keys use a fixed velocity; MIDI diagnostics still report the instrument’s actual velocity. Sound uses Alexander Holm’s Salamander Grand Piano samples (CC BY 3.0), with 128 voices, sustain and velocity-controlled amplitude. The compact bank has one dynamic layer, rather than a full multi-layer piano instrument; no download is required.

**Falling bars:** choose **Falling** beside **Sheet** in the reader (falling notes is the default). Each note falls onto its matching key; bar length represents notated duration. Blue = left staff, green = right staff; played/missed notes change colour during assessment. Listen animates the bars automatically; Practice uses the selected practice mode. In Wait mode bars wait for the correct pitches. In Follow mode they follow the performance. Free Play has no enforced score transport, so use Listen to watch a demonstration.

**Whole keyboard:** all **88 keys, A0–C8**, remain visible and touch-enabled in the reader. Keyboard zoom/pan controls have been removed as requested; a narrow phone necessarily makes each key small.

**Score cursor:** touch a note on the score or drag the blue cursor handle to move through the piece. This also works while listening. Seeking during scored Practice ends the current take first and sets the next practice start to that beat, so timing results remain valid.

**Looping:** the selected A/B passage is tinted on the score; drag the gold A and B grips to select its precise boundaries. The falling view shows A/B lines as they scroll into view. The footer shows the selected beats and a Repeat icon. Looping restarts at A after the end B. Adaptive tempo (off by default) raises the next repetition by 4 BPM only when the just-completed assessed repetition scored at least 90%; this is optional speed-building practice.

**Recorded MIDI roll:** completed scored sessions save private MIDI takes. In History, choose **Replay MIDI roll** to view actual played pitches, held lengths and extra notes as falling bars, or **Review score** for traditional notation. The Recorded view supports scrubbing and a visual animation; it is not an audio or video exporter, and scored replay remains a visual animation. Free-play recordings have a separate audible transport.

**Free-play recorder:** open Home → Free play recordings (also in Practice). Record Bluetooth/USB MIDI or touch keys without loading a score. Takes preserve on/off, velocity, channel and controllers, with three-second crash checkpoints, save-retry recovery, names/deletion, audible replay, seeking and 0.5×–2× speed. Replay never enters assessment. Recordings are private MIDI data, not microphone recordings or video. Maximum: two hours / 200,000 source events per take; the app visibly stops and saves at the limit. Uninstall/clear storage deletes local files.

**Local progress:** Home → Your practice shows a gentle daily minutes goal, seven-day activity, dated ear rounds and pitch/rhythm trends only for matching passage/tempo/hand/mode/tolerance/latency/input contexts. A 5–10 minute queue reopens weak measures, with a two-minute target that ends at a loop boundary; pause/count-in do not count. Recording time includes rests. Optional daily notifications are off by default and approximate. Existing Room history is retained without a schema change; no cloud or account was added.

**Ear training game:** choose a specific starting octave (C1–C7), span 1–3 octaves and simultaneous note count 1–3. The 88-key keyboard dims notes outside the selected range; points, attempts, accuracy and streak are saved locally and summarised in History.

**Visual review:** choose Sheet, then Practice. Rhythm mode (the default) starts the cursor after count-in; Follow starts on the first expected pitch, and Wait advances on chord completion. Green markers show matched input, amber early/late input, red extras and dashed rings missed notes. Hold traces show physical key-down duration; they are not pedal sustain. After Finish select Review on score, scrub the score, or reopen a take in History. This is a diagnostic overlay, not a quantised MusicXML transcription/export. Wrong pitches use a conventional sharp spelling; MIDI does not carry enharmonic spelling or physical-hand identity.

**Played notes:** the monitor is integrated above Sheet, Falling or Both, at full width. It follows the newest notes; near-simultaneous attacks group visually, with ledger lines and octave marks for extreme pitches. Tap the compact note icon in the score toolbar or open Settings → MIDI → Played notes. The staff shows the last five key presses in sequence by default, with approximate note durations after release and pause markers for long gaps. Settings → Practice adjusts note count (up to 32) and gap threshold. Diagnostics has raw MIDI events.

**Navigation:** Home, Practice, Library and Settings are the four main sections. Practice opens Sight reading, Listening/ear training, Scales, current score and free play. Settings has General / Practice / MIDI / About subsections. Sheet padding is in Settings → General; notation zoom remains in the reader. Landscape uses the menu overlay rather than an inactive P button.

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
- Full 88-key always-on touch piano, an aligned falling-note view, and unscored offline score listening. 128-voice acoustic sample playback, sustain, voice limits, audio focus and background cleanup.
- Responsive Compose screens, full-width practice with a closed-by-default navigation drawer, system/light/dark appearance, local Room persistence. No Internet permission, accounts, analytics or backend.

## Score library

**No sample scores are bundled.** Library lists only your imported MusicXML/MXL pieces. The separate Scales and Sight reading pages still generate exercises offline. PDF, image scores, MIDI files and `.mscz` cannot be imported. [Score licensing](docs/SCORE_LICENSING.md).

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
# Optional desktop rendering check: Python playwright + Chromium required
python3 scripts/test_notation.py
```

GitHub Actions runs tests/lint, uploads a debug APK, and includes API 36 phone and API 35 tablet emulator tests. The project is published at https://github.com/maratos92/piano-path. [The release-candidate run](https://github.com/maratos92/piano-path/actions/runs/41674522753) passed 77 JVM tests and 9 Android tests on each phone/tablet emulator. The preview-publishing workflow verifies the delivered APK checksum and publishes that exact development-signed binary; it does not expose any signing key. This is historical 0.3 evidence. The dev.4 source passed 83 local JVM tests, debug/instrumentation builds, lint and browser scrolling/gesture checks. Current source emulator results are tracked in PR #4; delivery remains blocked by the signer mismatch. [Signed-release procedure](docs/RELEASE.md), [architecture](docs/ARCHITECTURE.md), [FP-10 testing](docs/FP10_TESTING.md), [validation](docs/VALIDATION.md), [third-party notices](docs/DEPENDENCIES.md).

## Important practical limits

- Follow mode searches only the current and next onset group. It handles small deviations and pauses, but does not perform arbitrary jump/repeat recovery or machine-learned alignment. Restart/select a passage after a large jump. Rhythm mode uses a fixed timeline; Follow mode adapts phase after completed onset groups.
- Evaluation follows written order and a single practice BPM. OSMD can display repeats, ornaments, tempo changes and tuplets, but assessment does not unfold repeats/Da Capo, evaluate grace notes or follow tempo maps. Export/unfold repeats and use a fixed tempo for assessed practice.
- Only one MusicXML piano part is assessed. Hand attribution uses staff number, so cross-staff passages and unison pitches require care. Staff membership is not a measurement of the physical hand. Indistinguishable unison duplicates across voices may be undercounted.
- Release timing measures physical note-off against notated duration, including merged ties. Pedal extends sounding display, not expected finger duration. Legato articulation and half-pedal artistry are not graded. Metronome is a best-effort phone tone, not an audio-clock sequencer; calibrate your device and audio path.
- Sampled playback uses written order and the selected BPM; it does not unfold repeats, implement expressive tempo maps or interpret score pedal markings. Touch sustain and incoming MIDI CC64 are handled separately. Speaker/headphone audio latency has not been measured on a physical phone.
- No background service: keep the app visible while practising. Generated exercise difficulty is approximate; MIDI does not detect physical fingering.

## Licence

App source: [MIT](LICENSE). Piano sound samples: Alexander Holm, Salamander Grand Piano, [CC BY 3.0](licenses/CC-BY-3.0.txt), adapted from [Tone.js/audio](https://github.com/Tonejs/audio/tree/869b6f8d9cddb47d966238c012041480b1ce517a/salamander); see the bundled provenance and [notices](docs/DEPENDENCIES.md). OSMD: BSD-3-Clause plus upstream notices. Imported scores remain private and are never uploaded by the app.



The continuation fixes score scrolling around slurs/ties, cursor and A/B dragging after zoom, paused note durations/releases and listening resume. CI tests the combined source on phone and tablet emulators before publication, verifies the pinned certificate, and checks a fresh install and same-key update. For the build-8 reset, the historical APK is re-signed only as an emulator fixture; this is not a claim that differently signed old installations can be upgraded.

Private signing keys are not stored in Git. The preview key is currently retained in CI cache; `PIANO_DEBUG_KEYSTORE_BASE64` supports durable secret restoration. Back up that same key privately and never generate a new one silently. See [release signing](docs/RELEASE.md). Previous recovery notes are historical; the owner has authorised a fresh installation instead.

## Latest practice feedback

Looping defaults off. Enable Repeat for a selected passage; Settings → Practice (also Practice controls) sets 0–5 seconds of rest before the next count-in, default 2 seconds. Count-in is preparation clicks; adaptive tempo optionally adds 4 BPM after a successful loop. Both remain optional.

The cursor remains visible and darkens during drag. Keyboard fills preview notes sounding at the cursor; outlines show actual input. Falling notes support vertical dragging and independent stretch/compress controls, including Both view. The sheet uses a continuous A–B band, cached performance data and lighter overlay updates.

Home shows recently opened imports. Home / Practice / Library / Progress / Settings share navigation; Progress includes trends, listening rounds and session history. General settings offer accent presets/custom hex and optional falling-note glow.

Ear training adds full A0–C8 range, longer octave spans and major/minor triads by default for three notes. Optional MIDI reference output sends to a connected instrument's receive port; input-only instruments use phone sound. **Actual piano sound/echo behaviour needs hardware testing.** Reference playback is excluded from answers/capture. Sight reading supports 20 BPM. See [the detailed feedback checklist](docs/FEEDBACK_2026-11-05.md) for limits and deferred decorative backgrounds.
