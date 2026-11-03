# Validation and feature limits — 0.3.0

## Evidence

Remote verification: [GitHub Actions run 41674522753](https://github.com/maratos92/piano-path/actions/runs/41674522753) passed the debug build/unit/lint job and **9/9 Android tests on API 36 Pixel 2** plus **9/9 on API 35 Pixel C** emulators. Tests include native WebView sheet rendering, visual-take round trips, landscape navigation and the external-support explanation. These are emulator results, not physical piano/tablet certification.

Local release-candidate checks: **77 JVM tests passed, zero failures**; debug and instrumentation APKs built; unsigned release APK/AAB built; debug/release lint passed; static 64-bit ELF/APK 16 KB alignment passed. All 50 notation renders passed in Chromium. The debug certificate matches the preceding preview. Production signing, Android runtime reports and physical MIDI checks are separate.

- JVM tests cover MIDI parsing/running status/zero velocity/repeated notes/sustain/channel separation; MusicXML/MXL/security; polyphonic pitch/onset/release/latency matching; loops, tempo, scales, fingering and sight reading; full keyboard and roll geometry; playback and additive/sampled PCM; recorded extra/correct note events, physical releases, repeated strikes, velocity and loop reset. See the current test reports and GitHub Actions for exact counts.
- Desktop Chromium renders all 50 scores with the vendored OSMD engine offline. Tests verify matched/missing colours, actual note overlays, held-duration traces, continuous movement between notes, adjustable horizontal margin, dark notation and staff identity. This does not establish Android WebView behaviour by itself.
- Android instrumentation covers navigation, Android XML parsing, local WebView notation, simulated Wait practice, all-88-key/falling-note discovery, unscored listening, private visual-take round trips and expandable landscape navigation. CI includes an API 36 phone and API 35 tablet emulator job. Consult successful workflow reports before describing these as passed.
- The user reports testing on a Google Pixel with a Roland FP-10. Exact device/software versions and per-feature hardware results have not been supplied. No physical tablet, USB or other-instrument test is claimed. Emulator MIDI cannot verify BLE radios, instrument firmware, pedals or acoustic latency.

## Implemented and remaining limits

|Area|Implemented|Limits|
|---|---|---|
|Input|BLE MIDI discovery/native ports/reconnect, USB MIDI input selection/attach/detach, polyphonic parser, channel pedal and panic|Other instruments and USB require physical testing; class-compliant MIDI 1.0 only; identical USB devices require explicit selection|
|Notation|Local OSMD grand staff, MusicXML/MXL import, zoom/layout/margins, continuous cursor, input overlays|One assessed piano part; no PDF/image/MS CZ import; required clef/key/time spacing remains|
|Assessment|Separate pitch/rhythm, missing/extras/chord completion, onsets/releases, tolerance/latency, staff-based hands|Bounded next-onset follower; no arbitrary jump recovery, unfolded repeats, grace-note grading, expressive tempo maps or physical-finger/hand measurement|
|Visual review|Performed pitches, coloured errors, physical key-hold traces, score scrubber, private history reopening|Diagnostic overlay, not quantised transcription or MusicXML export; MIDI extra-note spelling/staff placement inferred; no independent finger measurement|
|Practice|Modes/hands, measure/beat selection, looping/count-in/metronome, saved passages, progressive BPM|Phone metronome is best-effort; calibrate device/audio path; restart after large score jumps|
|Sound|Bundled acoustic piano, 128 voices, velocity gain, sustain/channel/repeated notes, unscored Listen, audio focus|One dynamic layer, 24 kHz mono/ten-second samples; no resonance modelling or score pedal/tempo-map interpretation; physical latency and audio quality need listening tests|
|Training|Scales/arpeggios, curated common-key fingerings, four sight-reading levels, local progress|Uncurated fingerings remain blank; major arpeggios only; not independently teacher-validated|
|Library|50 original CC0 studies with full provenance|A curated historical repertoire collection remains incomplete; import legally obtained editions|
|Release|Debug APK, source, tests, Actions, signed APK/AAB procedure, API 36 target|Production signing/account/disclosures/testing/store approval are still required; no ads; external voluntary Ko-fi support without digital rewards|

Android only. No background service; practice stops on app backgrounding or input loss. Physical key-up is graded independently of pedal-sustained sound. Old Room history is migrated in place; visual takes are available for newly recorded sessions.
