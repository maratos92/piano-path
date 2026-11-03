# Owner feedback — current implementation status

Updated 2026-11-03. This checklist describes the **source on this development branch**,
not the old `v0.3.0-preview` APK. Consult `docs/CONTINUATION_2026-11-03.md` for handoff
details and known risks. Status "implemented" means code has been added and must still
be confirmed on the owner's physical Android/MIDI piano.

| # | Request | Status | What exists / remaining work |
|---|---|---|---|
| 1 | Played MIDI notes on a score, switch integrated vs separate | **Partial** | `LiveNotes` draws noteheads on a compact grand staff; `Practice` offers an integrated toggle and floating in-app Dialog. Physical staff hand/instrument spelling is approximate; dialog is not a movable Android overlay. |
| 2 | Score above falling notes, adjustable height | **Implemented** | Sheet / Falling / Both controls; combined layout score-height slider. Check smaller phones. |
| 3 | Whole-piece scrub progress bar and cursor | **Partial** | Bottom whole-score slider updates score position when stopped/listening. Locked during scored practice to protect timing results; playback seek outside active passage needs design. |
| 4 | Pause | **Implemented** | Pause/resume for Listen and scored practice with shifted clock origin. Device MIDI/pedal QA required. |
| 5 | Piano selection, 2–3 polyphony | **Implemented (interpretation)** | Sampled Grand, Mellow Synth and Bright Synth; all polyphonic (64–128 voices). Ear training also offers 1–3 simultaneous pitches; ask if different piano sound models were intended. |
| 6 | Scale and all menu button widths/layout | **Partial** | Scale and sight-reading selectors use equal-width groups; many other menus still use their earlier layout. |
| 7 | One-hand practice: hide left-hand staff? | **Implemented by dimming** | Non-selected notes dim on the score instead of being hidden; scoring already filters staff; no hide/show toggle. |
| 8 | Better-known, open-source repertoire | **Partial** | Library links OpenScore/Mutopia and labels bundled originals honestly; actual classical transcriptions not bundled yet. |
| 9 | Remove “Fingering you can trust” | **Implemented** | Panel removed; concise MIDI limitation remains. |
| 10 | History trend/practiced weekdays | **Implemented** | Seven-day per-category activity histogram and totals. No advanced skill trend yet. |
| 11 | Known scales and sight-reading exercises | **Partial** | Existing generated exercises remain; sourced named studies and curricula not added. |
| 12 | Backward/forward cursor jumps | **Partial fix** | Monotonic visible beat during practice, less eager OSMD scroll; needs validation against real performances. |
| 13 | Listening/guessing exercises with custom octave(s) | **Implemented** | Ear training: 1–3 octaves, 1–3 simultaneous notes, touch/physical MIDI answers; history persistence missing. |
| 14 | Loop selection by highlighting piece à la MuseScore | **Partial** | RangeSlider for passage endpoints in Practice controls; not in-sheet dragging/shaded A/B selection. |
| 15 | Natural metronome sound | **Implemented** | Generated percussive taps in PCM; coroutine scheduling accuracy and hardware latency remain unverified. |
| 16 | “When score (sheet) is selected then can we …” | **Clarification needed** | Original message ended before describing the requested behavior. |
| 17 | Imported BPM, accelerando, metronome tempo changes | **Partial** | Parses explicit BPM changes and expressive words. Listen/metronome follow numeric tempo marks; scored practice stays at fixed BPM; text-only gradual speed changes cannot be reconstructed. Actual user files are stored on-device, so their markers have not been inspected. |

## Shipping rules

- A distinct `0.4.0-dev.1` version code 4 distinguishes new feature builds from the old 0.3.0 APK.
- **Settings → About Piano Path** displays the exact BuildConfig version, build code,
  Debug/Release status and source revision.
- The root README links `latest-debug`, a rolling prerelease built **only** from a
  successful full `main` build/test workflow, including unit tests, lint, phone and
  tablet emulators. Failed or unmerged code is not published.
- `latest-debug` is debug-signed, not the signed production/Play APK. CI signing
  may change across builds; uninstalling an existing app may delete private data.
- Future Codex sessions should update this checklist and commit rationale when
  adding features, not silently promote Partial items to Implemented.
