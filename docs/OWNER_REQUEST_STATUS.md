# Owner feedback — current implementation status

Updated 2026-11-04. This checklist describes the **source on this development branch**,
not the old `v0.3.0-preview` APK. Consult `docs/CONTINUATION_2026-11-04.md` for handoff
details and known risks. Status "implemented" means code has been added and must still
be confirmed on the owner's physical Android/MIDI piano.

| # | Request | Status | What exists / remaining work |
|---|---|---|---|
| 1 | Played MIDI notes on a score, switch integrated vs separate | **Partial** | `LiveNotes` shows last N notes as an ordered staff, with approximate rhythmic values on key release and pauses. N defaults to 5 and can be set up to 32. Integrated toggle and in-app popup remain; clef/rhythm spelling is approximate, popup isn't movable outside the app. |
| 2 | Score above falling notes, adjustable height | **Implemented** | Sheet / Falling / Both controls; combined layout score-height slider. Check smaller phones. |
| 3 | Whole-piece scrub progress bar and cursor | **Implemented; device QA** | Enlarged 44px blue drag handle, shorter CSS transition, full-score tap-to-seek, bottom slider. Seeking while scoring ends the current take first. Smoothness and grip responsiveness still need physical-phone QA. |
| 4 | Pause | **Implemented** | Pause/resume for Listen and scored practice with shifted clock origin. Device MIDI/pedal QA required. |
| 5 | Piano selection, 2–3 polyphony | **Implemented (interpretation)** | Sampled Grand, Mellow Synth and Bright Synth; all polyphonic (64–128 voices). Ear training also offers 1–3 simultaneous pitches; ask if different piano sound models were intended. |
| 6 | Scale and all menu button widths/layout | **Partial** | Scale/Sight setup now have labelled and divided groups, including Tempo, Hands, and Octaves. Main navigation is now Home/Practice/Library/Settings, with general subsections in Settings. |
| 7 | One-hand practice: hide left-hand staff? | **Implemented by dimming** | Non-selected notes dim on the score instead of being hidden; scoring already filters staff; no hide/show toggle. |
| 8 | Better-known, open-source repertoire | **Changed per owner request** | All 50 generated demo pieces and third-party library links have been removed. Library shows imported files only; do not add unrequested samples or outbound discovery links. |
| 9 | Remove “Fingering you can trust” | **Implemented** | Panel removed; concise MIDI limitation remains. |
| 10 | History trend/practiced weekdays | **Implemented** | Seven-day per-category activity histogram and totals. No advanced skill trend yet. |
| 11 | Known scales and sight-reading exercises | **Partial** | Existing generated exercises remain; sourced named studies and curricula not added. |
| 12 | Backward/forward cursor jumps | **Partial fix** | Monotonic visible beat during practice, less eager OSMD scroll; needs validation against real performances. |
| 13 | Listening/guessing exercises with custom octave(s) | **Implemented** | Ear training selects any C1–C7 starting octave and a 1–3-octave range, with 1–3 simultaneous pitches. Saved cumulative points, attempts, accuracy and streak appear in History; per-day training history remains missing. |
| 14 | Loop selection by highlighting piece à la MuseScore | **Implemented; device QA** | Gold A/B handles on the staff edit the actual loop bounds, range tinted, bottom A/B indicator and falling boundary lines. Practice controls retain range slider and a Repeat switch; physical touchscreen validation required. |
| 15 | Natural metronome sound | **Implemented** | Generated percussive taps in PCM; coroutine scheduling accuracy and hardware latency remain unverified. |
| 16 | “When score (sheet) is selected then can we …” | **Clarification needed** | Original message ended before describing the requested behavior. |
| 17 | Imported BPM, accelerando, metronome tempo changes | **Partial** | Parses explicit BPM changes and expressive words. Listen/metronome follow numeric tempo marks; scored practice stays at fixed BPM; text-only gradual speed changes cannot be reconstructed. Actual user files are stored on-device, so their markers have not been inspected. |

## Further requested updates (November 4)

| Item | Status | Detail |
|---|---|---|
| In-place APK upgrades | **Partial** | Current published signer is pinned; cache loss/mismatch stops the build. Optional same-key secret restoration is supported. Durable private-key provisioning remains an owner task. |
| A/B markers on falling notes | **Implemented** | Gold A/B lines appear in the scrolling preview when their beat enters view. |
| Keyboard touch without toggle, full 88 keys | **Implemented** | Always touch-enabled; zoom and Fit all 88 controls removed. |
| Compact Played notes and MIDI indicator | **Implemented** | Toolbar icon replaces second row; green/grey piano icon replaces connection text. |
| Score padding under global Settings | **Implemented** | Settings → General; Practice control no longer contains a margin slider. |
| Understand adaptive tempo | **Explained** | +4 BPM only on the next loop after completed assessed repetition with at least 90% overall accuracy. |
| Show last 5 notes, durations and rests | **Implemented; approximate notation** | MIDI note-down and note-up capture, 1–32 configurable count, pause threshold; displayed note values are estimates from BPM. |
| Visualise recordings like YouTube falling bars | **Partial** | Saved scored MIDI takes are viewable/animated in History's Recorded roll. No video export, audio recording or free-play capture yet. |
| Ear-training custom octave and game | **Implemented with limits** | C1..C7 start and configurable width, points/attempts/streak and aggregate saved accuracy. No per-question dated trend chart. |
| Navigation and structured settings | **Implemented first pass** | Home, Practice hub, Library, Settings with four settings subsections. Needs owner usability review. |
| Bluetooth pairing | **Owner confirmed working** | MIDI backend and manifest permissions unchanged; no connection regression identified. |

## Shipping rules

- Version `0.4.0-dev.4` (Android build code 7) adds staff A/B handles, chronological note display, MIDI-roll review, specific octave/game stats, navigation/settings restructure, debug-key caching and full-width 88-key touch. Earlier versions lack these.
- **Settings → About Piano Path** displays the exact BuildConfig version, build code,
  Debug/Release status and source revision.
- The root README links `latest-debug`, a rolling prerelease built **only** from a
  successful full `main` build/test workflow, including unit tests, lint, phone and
  tablet emulators. Failed or unmerged code is not published.
- `latest-debug` is debug-signed, not the signed production/Play APK. The build must match the pinned current preview certificate and pass emulator update checks. Missing keys fail rather than being regenerated. Older APKs using another signer cannot be upgraded by a version bump; do not uninstall to bypass errors.
- Future Codex sessions should update this checklist and commit rationale when
  adding features, not silently promote Partial items to Implemented.
