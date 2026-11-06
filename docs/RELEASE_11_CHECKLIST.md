# Build 11 — practice reliability and consistent learning screens

Version 0.6.0-dev.1. Includes the previously committed keyboard-profile/robustness backlog and the latest UI feedback. No account/cloud/Room schema changes.

- Bottom navigation on phones and tablets. The reader's expandable navigation has the same destinations/icons and is the only expandable menu. Scales, Sight reading and Listening have matching names/tiles; Home connection card removed.
- MIDI studio combines live playing, velocity/pedal diagnostics, saved MIDI/replay and the played-note strip. Free-play keys fill on input; practice input uses thicker outlines. Controls say Clear sounding notes.
- Automatic listening answers: one attack for single notes, N distinct notes for N-note questions. Default three attempts (1–10 configurable); hidden answer is revealed only after success/exhaustion. Answer notation uses the common sheet renderer. Sequence listening and velocity training are roadmap items.
- A–B selection is an explicit mode, off by default. Handles appear near the cursor when enabled; ordinary seeking preserves passage bounds. Pins and a 10% passage band replace permanent markers.
- Sight reading defaults to Sheet. RHYTHM uses the direct clock; FOLLOW's visual correction slews without changing scoring. Falling display clamps at B during completion; removes stacked animation delays. Notation geometry is indexed, initial layout waits for a measurable viewport, engraving failures expose Retry, and renderer loss is recoverable.
- Favourites in Library; letter or fixed-do note labels; configurable keep-awake, listening attempts and metronome volume. Short filtered wood-tap sound replaces the longer pitched pulse. Actual perceived sound quality needs listening on device.
- Progress uses Overview / Pitch & rhythm / Listening / Sessions tabs. Settings uses a General / Practice / About ribbon with a Connect piano action. Practice shortcuts and the recording footer are removed from Progress.
- 49/61/76/88/custom absolute-MIDI keyboard profiles share geometry across touch/rolls. Outside-range notes remain graded and flagged. Quiet optional score-frame artwork stays outside notation; reduced motion is available.
- Import and review-file validation, repeated-note/channel reset handling, stale score-loading/passages and per-connection MIDI callbacks are hardened. See ROBUSTNESS_REVIEW_2026-11-05.md for reproducible findings and coverage limits.

Automated evidence must be checked on the exact final commit before merge/publication. Browser gesture/scroll and band/drag/reflow regressions pass locally; native tests are run on phone/tablet CI. This checklist is not physical MIDI, rendering performance or Play approval certification.
