# Focused robustness review — 2026-11-05

Initial source snapshot: `baccf92`, following build 10. This is a targeted review, not an exhaustive audit or a claim that the app is bug-free. Runtime changes are owned by the implementation agent; findings below describe the reviewed snapshot and require regression evidence before being marked resolved.

## Confirmed JVM findings

The following probes ran against `core/build/classes/kotlin/main` with the workspace JDK 21 JShell and Kotlin standard library. No app source was changed by this review.

| ID | Severity | Location | Reproducer and observed result | Required correction |
|---|---|---|---|---|
| R1 | P1 | `core/.../MusicXml.kt`, numeric parsing in `parse` | Import a single pitched note with `<duration>NaN</duration>`. Parsing succeeds with `Score.endBeat=NaN` and note duration `NaN`. Invalid values then reach UI geometry and transport. | Reject nonfinite/negative/out-of-range timing before publishing a score; validate cumulative beats, divisions, pitch, staff and time-signature bounds. |
| R2 | P2 | `core/.../MusicXml.kt`, `unpack` entry loop | ZIP with 300 directory entries and one valid score is accepted. The purported 256-entry limit checks distinct stored files, excluding directories and duplicate names. | Count every entry independently and reject duplicate paths; retain total expanded-byte bound. |
| R3 | P2 | `core/.../Performance.kt`, `acceptScored` CC120/123 branch | At 120 BPM: C4 On on channel 0 at t=0; CC123 on channel 1 at 100 ms; C4 Off on channel 0 at 500 ms. `durationErrorMs=null`, instead of the correct 0 ms. | Release/close only the addressed channel; preserve independent channel duration tracking and close recorded holds at reset time. |
| R4 | P2 | `core/.../Midi.kt`, `KeyboardState.accept` | Two On events for the same pitch/channel followed by one Off produces `held={}`. Recorder and synth retain a second held voice. | Track repeated strikes consistently with FIFO/count semantics, including pedal and controller reset. WAIT completion must not mistake the first release for all keys released. |

Minimal R1 score: `<score-partwise><part id="P1"><measure number="1"><note><pitch><step>C</step><octave>4</octave></pitch><duration>NaN</duration></note></measure></part></score-partwise>`.

## Source-confirmed race and validation findings

These have concrete reproducers from control flow; device timing and corrupted-file UI cases were not executed by this reviewer.

| ID | Severity | Location | Reproducer / impact | Required correction |
|---|---|---|---|---|
| R5 | P2 | `PianoViewModel.load`, `openImport`, related exercise loaders | Start loading a large score A, then select smaller B. A can complete last and overwrite B. Starting practice during a pending load can also be overwritten without finishing its newly started engine. | Assign a generation to the complete user selection, including initial file IO. Ignore/cancel superseded completions, and stop current transport immediately before an accepted publish. |
| R6 | P2 | `PianoViewModel.saveSection` / `useSection` | Save an A passage, switch to B during DAO suspension; A's sections are assigned to B. Applying one can produce invalid bounds. | Only publish sections if the score identity still matches; validate section ownership and bounds before applying. |
| R7 | P2 | `BleMidi` shared receiver/parser and `onFlush` | An old port callback arriving after reconnection reads the new mutable generation and can deliver old events into the new connection. An old flush has no generation check and clears only visual keyboard state. | Bind receiver/parser to an immutable connection token at ingress. Guard flush and route accepted reset through recorder/monitor/assessment state cleanup. |
| R8 | P2 | `TakeArchive.load` and downstream `Notation`/`Practice` | Modify a valid private take's `endMeasure` to 9999 while retaining valid `startMeasure`. Loader returns invalid UI; later composition indexes the end measure outside the caller's read-error catch. | Validate measure bounds, offsets, finite tempo/timing/pitch/channel values and collection limits before returning UI state. This concerns private-file corruption, not a demonstrated remote write path. |

## Scope and limits

Reviewed recorder serialization/recovery ordering, replay and pause/seek paths, local progress persistence, MIDI input/reference cleanup, MusicXML unpack/parse, and the local notation bridge. Recorder files already use streaming bounded input, safe IDs and atomic replacement; generated/reference output remains separate from scored input. No new critical recorder-write defect was established in this pass.

No Bluetooth/USB instrument, audio latency benchmark, OEM notification delivery, process-kill/full-disk fault campaign, WebView renderer-crash campaign, or durable signing-key backup was tested here. XML entity/network restrictions were inspected, but no comprehensive exploit assessment was performed. Verification of fixes belongs in test/validation records, with actual commands and results; the findings table is not evidence of their resolution.

## Build 11 corrections

R1–R8 have source fixes and focused regressions: finite/bounded score timing and complete ZIP-entry accounting; FIFO/channel-local releases; score-selection generations and passage ownership; isolated connection/flush receivers; validated/atomic review archives. Renderer failure now has a recoverable retry; backup/transfer exclusions are explicit. Reviewer follow-up caught flush silencing while independent transports continued; flush now stops those transports before clearing sound. Core regression suite passed before final UI integration; consult final CI for the exact delivered evidence. Physical timing, real interruption/crash/full-disk campaigns and signing backup remain unverified.
