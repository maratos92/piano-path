# Validation and feature limits — 0.5.0-dev.1 / build 9

## Evidence

Local checks for this delivery: **97 JVM tests passed, zero failures/errors**. Added recorder tests cover raw chord/channel/velocity events, FIFO repeated strikes, pedal seek restoration with the same held pitch, boundary ordering, limits, closure/recovery, pause clocks, comparison contexts, latest-result queues and daylight-saving dates. The final local `:core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` run passed (lint: zero errors, seven warnings). See Actions for final phone/tablet execution and installation/update results. Tests compiled locally are not described as device passes.

The preceding build 8 passed [main run 41762909998](https://github.com/maratos92/piano-path/actions/runs/41762909998), including 16 tests per API36 phone/API35 tablet emulator and install/update checks. New local persistence and UI tests are added to those suites. A passing old run is not validation of the new source.

The owner reports Google Pixel / Roland FP-10 Bluetooth use. No agent has physically verified BLE, USB, another piano, physical tablet, pedal/latency or speaker fidelity. Android emulator tests do not establish those capabilities. Reminder delivery/power restrictions and prolonged recorded playback need real-device acceptance checks.

## Implemented and limits

| Area | Implemented | Limits |
|---|---|---|
| Input | Native BLE MIDI, class-compliant USB MIDI, channels/polyphony/pedal, parser and diagnostics | Physical USB/other instruments/reconnect reliability still require owner tests; MIDI1.0 only |
| Notation | Offline OSMD grand staff, MusicXML/MXL, zoom/layout, A/B range, score and performed-note overlays | One assessed piano part; no PDF/image import; no quantised performance transcription/export |
| Assessment | Pitch, chord completeness, missing/extras, onset/key-release timing, hands, calibration, loop resets | Bounded follower; no arbitrary jump recovery, unfolded repeats, grace-note grading, expressive practice tempo maps or measured finger identity |
| Recorder | Raw MIDI/touch take files, naming/deletion, three-second draft checkpoints, audible replay, pause/seek/speed, pedal/channel restoration | MIDI synthesis, not captured audio/video/export. Seek restarts note envelopes. Two hours/200K source events per take. Last uncommitted checkpoint seconds may be lost on process death. Recovered take minutes reconcile on reopen. No off-device backup |
| Progress | Dated ear rounds, seven-day minutes, goals, comparable passage trends, short weak-passage queue | Historical results lack context and stay in History. No proficiency claim. Recording duration includes rests; minutes assigned to the session's stored date, not split at midnight. Deleting a recording retains its practice summary |
| Practice queue | Latest weak contexts, actionable passage loading, two-minute loop-boundary target | Approximate 5–10 minutes; a long repetition can exceed the target. Empty-history suggestions have manual duration |
| Reminders | Optional native, inexact daily notifications, boot/timezone restoration | Android permission/power/force-stop rules can delay or suppress delivery; disabled by default |
| Audio | Bundled sampled piano, velocity amplitude, sustain, isolated recording replay | One dynamic layer, compact 24kHz mono samples; no resonance model. Actual audible replay and latency require device listening |
| Exercises | Scales, common-key fingerings, four sight-reading levels, ear game | Major arpeggios only; incomplete advanced fingering data; no teacher certification |
| Library | User's private legal MusicXML/MXL imports | No historical repertoire or original demo catalogue bundled |
| Release | Pinned debug signing, CI phone/tablet and prior-APK upgrade checks, separate release procedure | Production account/signing/disclosures/tester requirements still apply. Durable signing key backup is an owner task |

No account, cloud backend, microphone or new database schema is included. Existing Room v2 remains unchanged. Practice/replay stop on background/interruption. New recorder/progress storage is private local atomic JSON; uninstall/clear storage deletes it. A later full robustness audit remains roadmap item5; targeted reviews for this delivery do not replace it.

## Owner acceptance checks

1. Update build8 directly to build9 and confirm About version/revision and retained imports. Older lost-key generations require the already-authorised one-time fresh install.
2. Record a chord, repeated note, quiet/loud notes and sustain. Stop, reopen, seek into a pedal-held note, pause/resume, vary speed and listen through a rest longer than two seconds. Verify no stuck notes after exit/background/audio-focus interruption/disconnect.
3. Kill the process during capture after a checkpoint, reopen, confirm a labelled recovered draft. Rename/delete it; verify no resurrection. Simulated disk/full-file tests cannot substitute for all real low-storage conditions.
4. Try Sheet/Both/Recorded landscape on Pixel and a physical tablet. Open/close Played notes; zoom during automatic playback across high slurs/ties and staff-system changes; edit A/B in the floating score. Report the file, zoom and orientation if shaking recurs.
5. Complete two same-settings passage attempts plus a different-tempo attempt; only the comparable pair should show a delta. Check dated ear rounds, goal minutes, optional notification permission denial and a reminder at an upcoming time. No device pass is claimed until observed.

Desktop regression evidence: `scripts/test_scrolling.py` passed five horizontal and three wrapped zoom configurations with slur/tie stress, stable system bounds, monotonic following, cursor visibility, loop restart, manual/idle pan, zoom anchors and resize checks. `scripts/test_notation.py` passed the offline vendored-renderer/overlay/score-tap checks (32 graphical notes, no errors). `actionlint`, static native 16KB segment alignment and APK zip alignment passed locally. These do not certify a physical Android display or runtime 16KB environment.
