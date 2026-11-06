# Build 13 — responsive input and transport cleanup

Version 0.6.0-dev.3, build 13. No accounts, cloud storage or new external assets.

## Investigation

Source tracing found growing CPU/allocation work rather than evidence of a retained WebView leak: the scorer scanned the full results per MIDI message, the 12 ms presentation tick copied the whole growing take, native code queued unacknowledged JavaScript commands, and wrong-note overlays triggered full score recolouring and SVG reconstruction. WAIT continued this work even while its beat stood still. The renderer could therefore process old transport commands after Stop.

## Changes

- Match directly to the consumed score note. Cache immutable result sets and recent-take snapshots. Keep full takes for finished results/review; live UI/notation uses the latest 256 attacks. MIDI scoring remains lossless within the existing 20,000-event take recording limit; further attacks still score.
- Publish transport presentation at most about 30 times/second (successful matches publish immediately). WAIT has no idle presentation rebuilds. One JavaScript command may be outstanding, with one replaceable latest pending snapshot; serialize only changed payloads.
- Reuse performed glyph nodes and paint only changes. Separate score colour changes from error/release overlays; use indexed lookup and avoid recolouring unchanged notes. Stale autonomous cursor motion expires after 500 ms without a transport heartbeat. Normal playback still animates on every display frame between heartbeats.
- Practice and Listen replace each other, including paused transports; simulator/reference/calibration work is cancelled. Stop cancels jobs and clears markings immediately. Finish preserves results for review; Clear sheet markings is available while stopped. Starting practice clears the sheet and returns to the selected start.
- Cursor grip belongs to the current system line, rather than the window's top. Centred engraving glyph is three times larger with three randomly chosen motion styles (reduced motion respected). Countdown has a contrasting outline. Sight preview uses the central countdown and replaces the extra count-in.
- Next sight exercise preserves difficulty/key/meter/preview and current hand/tempo. Exercise settings is accessible from the practice controls. Scale/sight setup choices survive navigation within the app.
- Compact common page headings; listening actions on one row with no Clear button; answer keyboard green when guessed and yellow when attempts expire. Replaying the revealed correct answer after releasing keys advances the question. Adjacent chord noteheads use collision lanes. Recent-note scrolling observes both the new attack and the newly measured scroll range.

## Validation and limits

Core regressions include 10,000 wrong-note pairs followed by a complete chord/next note and bounded immutable live snapshots. Real-OSMD stress exercises more than 20,000 wrong-note overlays: no score recolouring for mistakes, existing glyph identity retained, no idle WAIT redraw and a bounded live DOM; clearing removes all overlay nodes. Cursor tests include stale transport expiry, pause, gaps, empty measures and a system-attached circular pin. Native regressions cover both stop APIs, paused transport replacement, wrong-note bursts, sight preview and next settings.

Exact-head GitHub CI must pass unit tests, lint, browser suites, Android phone/tablet tests and clean/update installation before publication. The APK keeps the existing pinned debug signer. A sustained physical-device session is still needed to verify the reported hour-long slowdown and perceived latency; automated stress tests do not certify hardware memory/FPS behaviour.
