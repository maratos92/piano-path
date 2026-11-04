# Codex continuation — loop, note monitor and navigation (2026-11-04)

Owner feedback came with a screenshot of combined Sheet/Falling practice and asks for
direct MuseScore-like A/B selection, better animation, a compact last-N notes monitor,
88 keys with no touch/zoom controls, recordable performed-note roll, specific ear-training
octaves, saved game accuracy, better settings and navigation, and installable APK updates.

## Implemented on this branch

- **Score A/B editing**: `app/src/main/assets/notation/index.html` renders gold A and B
  vertical handles and a translucent selected passage in SVG staff coordinates. Pointer
  drags quantize to 1/4 beats and use `Android.setLoop(a,b)`; Compose
  `ui/Notation.kt` forwards the callback to `PianoViewModel.setPassage()`. This
  also enables the loop switch. During playback/assessment selection is read-only;
  stop to edit it. The blue cursor grip is larger (44px), with a short CSS transition.
  **Physical phone QA is critical** for pointer capture, repositioning after score
  re-engraving, and wrapped staff systems.
- **Falling loop and motion**: `ui/FallingNotes.kt` interpolates position over 85ms
  and draws gold A/B lines when the bounds enter the falling viewport. The ideal score
  roll still shows only the selected passage. The footer shows A/B instead of "Piece".
  `ui/Practice.kt` also exposes an active Repeat icon and keeps advanced range steppers
  collapsed until requested.
- **88-key piano and menu**: `ui/Keyboard.kt` removes zoom and range controls; touch
  is always enabled. Played notes is a compact score-toolbar icon. A green/grey piano
  indicator replaces repeated "MIDI connected" text. Bluetooth code/permissions
  are not modified. The owner confirmed Bluetooth now works.
- **Played-note history**: `PianoViewModel.captureKey` stores last 128 MIDI/touch
  note-on events with key-down duration populated on the matching note-off (capped at
  12s for display). `ui/NoteMonitor.kt` displays the last N pitches across the staves,
  approximate whole/half/quarter/eighth/sixteenth noteheads and symbols, and a gap
  pause marker. Default N=5, max N=32; configurable gap 0.5–10 seconds. No
  unbounded timer. It is a *live in-memory window*, not a persisted archive.
  Key-down time is not sustain-pedal duration; rhythmic glyphs are approximate.
- **Recorded-note roll**: existing `TakeArchive` stores scored sessions with note
  beat, duration, pitch, etc. `ui/PerformedRoll.kt` visualises those played events as
  full-88-key falling bars; `Practice` adds Score/Recorded and Play/Pause animation
  in review, and History offers "Replay MIDI roll". The animation is in-app and
  silent: this branch **does not** record free play, export video/audio or perform
  live backward-rising MIDI trails. Those remain explicit follow-up ideas.
- **Ear game**: `PianoViewModel.earQuestion(startOctave,span,voices)` covers exact
  starting C1..C7, spans 1..3 octaves (up to C8) and 1..3 simultaneous pitches.
  `ui/EarTraining.kt` shows range, points, correct/attempts, accuracy, streak, best
  streak and 10-question progress. SharedPreferences stores cumulative aggregates;
  History shows a summary. Per-question history/trend and game rewards are not added.
- **Settings and navigation**: main Home / Practice hub / Library / Settings tabs;
  Practice hub links Sight reading, Ear training, Scales, current score and free play.
  Settings uses General (theme, piano type, sheet padding), Practice (timing, last-N,
  gap threshold), MIDI (connection/diagnostics) and About (version/licences/support).
  Removed inactive landscape "P" rail header and redundant touch piano setting.
- **Adaptive tempo**: its label now explains the actual previous behavior:
  *when loop enabled and a completed scored repetition reaches >=90% overall
  accuracy, the next repetition is +4 BPM*. Remains disabled by default.
- **Debug APK signing**: GitHub Actions build job now caches
  `~/.android/debug.keystore` under a fixed key across builds, rather than using
  a newly generated debug signing identity on each ephemeral runner. Emulator jobs
  are excluded from the shared cache to avoid first-run race. **Important**: first
  install from the new certificate may require uninstalling the old signed debug
  APK. Caches can be evicted or deleted; a long-term, consistently signed
  production APK needs a PRIVATE keystore in GitHub release secrets. Never commit
  a release signing key.
- New development version: `0.4.0-dev.3`, Android `versionCode=6`.

## Regression and open design questions

- Run `./gradlew :core:test :app:lintDebug :app:assembleDebug` plus phone/tablet
  Android tests before merging. Inspect the *latest* Actions run head SHA; earlier
  jobs on the same branch are cancelled when a new commit is pushed.
- New Android smoke tests verify chronological note captures with immutable
  release durations and ViewModel loop A/B bounds. The About-version test now
  clicks the About tab.
- **A/B responsiveness**: direct manipulation uses WebView Pointer Events; verify
  an actual Android phone: (1) drag A handle, (2) drag B past old end, (3) switch
  Sheet/Both/floating and verify shared selection, (4) loop through B to A,
  (5) score scroll and rerender, (6) no accidental scored seek while dragging loop.
- **Recorder future work**: persist free-play MIDI sequences, add a dedicated
  unlimited-performance roll playback and optional MP4 video export with owner
  specified resolution/frame rate. Current `PerformedRoll` is for already
  archived graded takes only. Avoid claiming the app records microphone audio.
- **Live monitor future work**: accurate note spelling, clefs and rhythm
  quantization would require tempo grid and metre awareness; the current
  display infers durations from key hold time. Chords are timestamped
  individually. For a high number of notes, horizontal scrolling is intentional.
- **Ear practice history**: aggregates are stored in SharedPreferences, not a
  dedicated date-stamped Room table; add per-session trends when required.
- **Debug signing**: consider an owner-provided persistent signing keystore secret
  in a restricted `release` environment instead of temporary GitHub cache,
  without ever exposing private key material.
- **Other known limitations**: MusicXML numeric tempo changes only in Listen;
  practice scoring fixed BPM; no named third-party repertoire bundled; pointer
  animation and Bluetooth timing still require physical device testing.

## Key files

`ui/Practice.kt`, `ui/Notation.kt`, `assets/notation/index.html`,
`ui/FallingNotes.kt`, `ui/PerformedRoll.kt`, `ui/NoteMonitor.kt`,
`ui/Keyboard.kt`, `ui/EarTraining.kt`, `ui/App.kt`,
`PianoViewModel.kt`, `.github/workflows/debug.yml`, `README.md`.

Commits should continue to include what was changed, the reason, tests and limitations.
Merge only when a build on the exact head commit is fully green; merging to `main`
triggers the second full test suite and then the stable `latest-debug` APK release.


## Codex reconciliation after the handoff — dev.4

Inspected merged PRs #1–#3 against main `42282d0`; older interrupted local work is checkpointed on `fix/install-and-scrolling` and was not merged wholesale. In particular the removed library and new navigation/score gestures remain intact.

The focused continuation fixes pause-clock/release durations (including key-up during pause), restores sounding voices after Listen resume/seek, restarts audio after long rests, freezes preview/inter-loop countdown while paused and starts later Listen repetitions at A. System-based cursor geometry replaces the changing measure-based scroll target, with gesture priority and width-only debounced re-engraving. Pointer events are observed in capture phase so score handles cannot leave auto-follow permanently paused; releasing an A/B handle no longer also seeks the blue cursor.

Version is `0.4.0-dev.4`, code7. Signing is pinned to the current dev.3 certificate (full digest in `config/debug-certificate.sha256`). Cache loss now fails closed. Optional `PIANO_DEBUG_KEYSTORE_BASE64` restores the SAME private key with the Android debug alias/password; do not create a replacement. No private key is committed or requested in chat. Durable secret provisioning is still an owner task: this patch does not claim a cache is permanent storage, or that the dev.3 signer can update old 0.3 previews signed with another key.

Phone/tablet CI first installs the published APK, writes a private marker and updates to the exact candidate artifact before running instrumentation. Publication refuses stale-main runs and supplies an APK checksum. Follow the actual run outcomes before calling runtime tests or publication complete.

Free-play recording, video export, dated ear analytics, richer monitor quantisation and variable-tempo assessment remain open. This continuation concentrates on the highest-priority delivery and scrolling defects.

Browser pointer tests additionally reproduced three gesture defects: rebuilt handles lacked listeners after zoom; cursor and loop handlers shared a legacy function binding that could leave cursor dragging active on release; inverse hit-testing used per-measure pitch extents and could select the wrong measure around high/low notes. Handlers now reattach idempotently, have distinct finish functions, and use stable system bounds for hit-testing. The regression drags the blue cursor and B handle after re-engraving and verifies exact callbacks and that loop dragging does not also seek.


Verification: 83 local JVM tests, debug/instrumentation build and debug lint passed. Browser renderer and the slur/tie/zoom/loop/pointer regression passed. The first CI run correctly failed its signing pin: the restored cached key does not sign the published dev.3 APK. Publication and upgrade checks remain blocked until the SAME published key is restored. Source phone/tablet tests run independently of that delivery gate. Public certificate/path diagnostics are provided; no private key bytes are printed or uploaded. Do not weaken the pin or claim that these changes are shipped before this is resolved.


Signing root cause confirmed by CI `signingReport`: AGP uses `/home/runner/.config/.android/debug.keystore` while the workflow cached `/home/runner/.android/debug.keystore`. The cached key has public fingerprint `d9adde74c012f99f451d7ac2618a8bd62959deccc1b6073675745f9581e9415e`, different from published dev.3 `1b7982e8fdb44fdc9316ca57da6444e211b7788f6a04ef100580cff69f158681`. A new random candidate used yet another key. Gradle now explicitly selects `PIANO_DEBUG_KEYSTORE_PATH` supplied after restore. The pin remains unchanged; restoring the unused cached key is not enough to upgrade dev.3. An original dev.3 key backup or an owner-approved, verified data-preserving migration is required. Do not pretend to recover a private key from an APK or silently replace the pin.


First source emulator run: 15/16 tests passed on both API35 tablet and API36 phone. The new scroll test caught WebView reporting an entire 2026px horizontal score as its viewport (zero horizontal travel). Enable `useWideViewPort` and disable overview fitting so the device-width meta viewport is honored. Rerun native tests on this fix before claiming 16/16 passed.
