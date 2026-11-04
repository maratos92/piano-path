# Practice workspace refresh — continuation notes (2026-11-03)

## Later owner-directed follow-up: score scrubbing and sample-free library

- Branch `codex/score-scrub-cleanup-2026-11-03` adds pointer-hit-test mapping from OSMD SVG noteheads/measure geometry into beat numbers. The blue cursor has a touch target and grip. Sheet, Both, and popup connect it through `Android.seek` to ViewModel.seek. Tapping notation also seeks.
- All score/bar seeks work during Listen and when stopped. During graded Practice they terminate the current take, seek, and move the selected passage start, so starting Practice again really begins at that position. This deliberately changes the current practice range instead of corrupting results.
- On-screen piano keys accept touch alongside Listen without requiring the Touch piano switch. They remain on MIDI channel 0; generated playback remains on channels 1–15.
- Ear training now labels C4–C5, C4–C6 and C4–C7 explicitly and zooms/dims the keyboard to focus on the chosen register.
- Sight reading now has a directly selectable 40–200 BPM tempo; Sight/Scale setup groups are divided and concise.
- Owner explicitly rejected the external repertoire links and the 50 generated example-score miniatures. **They are removed**, along with the generator/catalogue. Library shows only user-imported MusicXML/MXL; Home defaults to a generated C-major scale. The Scales and Sight reading exercise generators are still available. Do not silently reintroduce bundled demo pieces or third-party links.
- Bluetooth code and manifest permissions are unchanged from 0.3.0. Connection status on a physical FP-10 still needs owner verification; check Nearby Devices permission after reinstall, and diagnostics for MIDI ON/OFF events.
- Regression map: `app/ui/Notation.kt`, `assets/notation/index.html` (score gestures), `PianoViewModel.kt` (safe seek and Listen touch), `app/ui/Keyboard.kt`/`EarTraining.kt` (octave focus), `Training.kt`, `Storage.kt`, `App.kt`, and `app/src/androidTest`. `scripts/test_notation.py` now builds a test-only score in memory.
- The earlier sections below describe the initial 0.4.0-dev.1 implementation. Where they mention 50 bundled miniatures or disabled scored seeks, **this follow-up supersedes those statements**.


This branch is intentionally separate from `main`. It addresses the owner's feedback across score
display, timing, musical exercises, sound and navigation. **Do not merge blindly:** run
`./gradlew :core:test :app:lintDebug :app:assembleDebug` and emulator/physical-MIDI checks.

## Implemented in this branch

- **Visual held notes**: `ui/NoteMonitor.kt` draws a compact treble/bass staff with noteheads,
  ledger lines and generic sharps. It is used both for the integrated Notes display and Note monitor
  page. MIDI has no actual finger or hand information; staff is chosen by register.
- **Practice layouts**: `ScoreView.COMBINED` shows the OSMD score above the aligned falling roll;
  a score-height slider reallocates vertical space. Sheet and Falling remain separate choices.
  A dismissible floating notation `Dialog` is also provided (it is not a system-level Android
  overlay, which would need different permissions). See `ui/Practice.kt`.
- **Whole-piece position and repeat**: the bottom slider follows score position and can be
  dragged while stopped or listening; scored practice seek is disabled because it would invalidate
  ongoing accuracy results. A new `RangeSlider` in Practice controls selects quarter-beat
  passage boundaries in the same model used by the existing measure steppers and loop toggle.
- **Pause/resume**: `core/Performance.kt` freezes scored events while paused and adjusts timing
  origin on resume. `PianoViewModel` shifts the count-in/metronome start for practice; listening
  pauses its playhead and releases sounding notes. Repeated notes/sustains across pause need device QA.
- **Cursor stability**: visible transport is clamped to nondecreasing beats during a loop; OSMD now
  scrolls only if the playhead exits the viewport. On repeated loop start, transport resets.
- **One-hand practice**: the non-selected staff remains visible for context but is dimmed. Scoring and
  falling-note filtering already operate on the selected staff. This relies on MusicXML staff IDs,
  not actual physical hand detection.
- **Piano tone**: existing sampled Salamander Grand, plus mellow and bright **synthesized** tones
  via `core/PianoSynth.kt`. All remain polyphonic (sampled 128 voices, synth 64).
  The selection is persisted under Settings; no new binary sound packs.
- **Menus and history**: regular-width choices in Scales and Sight reading, awkward separate
  fingering card removed, seven-day practice-activity histogram (per category) in History.
- **Ear training**: `ui/EarTraining.kt` presents hidden pitch questions, 1–3 octaves, and
  1–3 simultaneous notes, replay, touch/MIDI guesses, and local in-session correctness.
  Not yet persisted to History, and this is absolute pitch identification rather than intervals.
- **Classical library discovery**: label the bundled 50 CC0 originals honestly as exercises.
  Link OpenScore and Mutopia for editions, rather than bundling unverified third-party arrangements.
  The importer still only accepts a single piano-part MusicXML/MXL; Mutopia editions may require
  MuseScore Studio conversion.
- **Tempo metadata and listening**: `core/MusicXml.kt` captures explicit `sound tempo` and
  metronome markings throughout a score; `Score.tempoMarks` supports piecewise beat/time conversion.
  `Listen` follows numeric BPM changes at proportional speed and can play an accompanying metronome.
  Words such as *accelerando* are flagged but do not define a numerical curve.
- **Metronome timbre**: `core/MetronomeClick.kt` generates an original short wooden-like tap and
  mixes it with `PianoAudio`, replacing telephone-like ToneGenerator clicks in normal practice.
  Calibration deliberately retains its old beep.

## Known open items / owner feedback requiring follow-up

1. **Tempo-aware scored practice**: the `PerformanceEngine` currently evaluates at a fixed
   `PracticeConfig.bpm`. Variable tempo and repeats require corresponding changes to onset timing,
   release timing, recorded takes, count-in, metronome and result calculation. Do not turn on a
   variable-tempo scoring path without a dedicated test suite.
2. **True expressive accelerando**: MusicXML words alone do not specify a speed curve; support would
   need explicit numbered targets and a user-controlled ramp, or a tempo-map file.
3. **Seeking while scored practice is running** is disabled. Listening seek only makes sense inside
   the active passage; dragging outside its bounds currently returns to the passage.
4. **Loop UX** can improve further with direct notehead drag selection, A/B endpoints in the reader,
   and a shaded selected region on the whole-piece slider. Current selection uses the settings
   range handles (MuseScore-style concept, not identical implementation).
5. **Score popup** is a Compose modal dialog and cannot be dragged outside the app.
   Resize controls are present for Combined mode, not for the popup.
6. **Ear-training analytics** are session-local only. Consider separate Room entities and activity
   tracking for interval/chord identification. Existing 50 studies and synthetic sight-reading
   prompts are not third-party teaching-method exercises.
7. **Technique repertoire**: add properly sourced Hanon/Czerny/Burgmüller/Bach pieces only after
   checking edition/transcription copyright, suitability and single-part parsing.
8. **Metronome timing** is still coroutine/Android-audio-callback based, not a sample-accurate
   scheduler. Measure phone/headphone/BT latency and jitter; verify muted/paused clicks.
9. **User-imported files** are stored privately on the user's device and are absent from GitHub.
   Inspect copies of the actual XML/MXL to confirm their initial BPM and accelerando markings.
10. **One-hand view** dims the inactive staff rather than completely hiding it.
11. **Incomplete owner request #16** ("When score (sheet) is selected then can we ...") needs the
    missing design requirement before implementing a speculative change.
12. **Testing**: this environment can commit to GitHub but does not have a configured local Android
    SDK or attached Roland/Pixel device. Check GitHub Actions and carry out hardware verification
    before publishing any new APK. The old README describes the pre-branch build.

## Implementation and regression map

- `core/Music.kt`, `core/MusicXml.kt`, `core/Performance.kt`, `core/MetronomeClick.kt`:
  pure Kotlin models and clocks; see `core/src/test`.
- `app/PianoViewModel.kt`: session transport, sound settings, MIDI routing, ear quiz, tempo-aware
  Listen. It is important not to score internally generated MIDI audio.
- `app/ui/Practice.kt`, `Notation.kt`, `FallingNotes.kt`,
  `app/src/main/assets/notation/index.html`: combined layout, overlay, scoring display,
  passage selection, progress/cursor coordination.
- `app/ui/Training.kt`, `EarTraining.kt`, `NoteMonitor.kt`, `App.kt`: training menus and pages,
  noteheads, history and library navigation.
- `app/audio/PianoAudio.kt`: sound engines and percussive metronome rendered through AudioTrack.

Each commit intentionally carries a subject explaining the change plus a body containing
scope, rationale and limitations. Future Codex sessions should use this note as a starting
point **and inspect the actual latest code** before acting.
