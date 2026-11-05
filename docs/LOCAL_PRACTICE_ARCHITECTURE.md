# Local practice delivery architecture

Scope: roadmap delivery items 1–3, based on build 8 (`9c833b8`). This document records the design and review gates for the local implementation; it does not certify physical-device behaviour or completion of the later full robustness review.

## Boundaries

- Retain Room version 2, its entities and existing migration unchanged. New recorder and practice-progress data use private local files. No account, backend, network permission, cloud transport or database schema change belongs to this delivery.
- Keep real input, assessment and audio output separate. Physical MIDI and deliberate touch input may enter capture. Score demonstration, ear-training reference playback and recording replay must never feed capture or assessment.
- Core Kotlin owns event/state reconstruction and comparison rules. A recorder controller owns recording/replay transitions; a repository owns file validation and serialized atomic persistence. The ViewModel coordinates screens and existing practice without becoming the file writer or replay clock.

## Recorder contract

Each take contains an ID, title, creation time, duration, completion/recovery state and an ordered event list. Event offsets use monotonic elapsed microseconds; creation dates use wall time. Preserve pitch, velocity, channel, note-on/off, controller values and simultaneous-event order. Velocity-zero note-on is a release. The format represents MIDI performance data, not captured audio, and contains no score XML.

Repeated strikes of one pitch/channel require FIFO key releases. Physical holds and notes sustained by pedal are separate state. Seek reconstructs state strictly before its boundary; the normal scheduler emits events at the boundary once. When restoring a pedal-latched note and a physically held note on the same key, emit the latched on/off pair first, then the held note, so the synthetic release cannot consume the held note.

Record start includes already sounding input from time zero. Stop appends releases and pedal cleanup. Disconnect, background, clear and cancellation explicitly clear live state so the next take cannot inherit stale keys. Pause, seek, stop and interruption silence the old replay voices; resume reconstructs the selected position. Speed changes rebase elapsed playback time while preserving current voices. Playback must work on all recorded channels, including channel zero, without depending on the score player's reserved channel range.

The PCM output may idle before the first recorded event. Replay restarts it when needed and keeps it alive after recorded output begins, preserving controller/pedal state across later rests. Pause, seek and stop release that keep-alive state. Audio-focus loss interrupts the recorder transport rather than allowing it to keep advancing silently or reclaim focus on the next attack. Each output thread owns its render scratch buffers.

Take duration/event caps must be explicit and stop/save visibly when reached. Live state also needs a bound outside capture: repeated note-on or a continuously held pedal must not cause unlimited growth. Bound file size/event count before loading and validate timestamps, channels, pitches, controller values and safe file IDs. Library listings should retain metadata instead of all event payloads. Avoid a queue of whole-take checkpoint copies.

## Durable local storage

Serialize writes on an IO owner with atomic replacement and last-good recovery. Periodic draft checkpoints limit loss after abrupt process death; the UI must not promise that unsaved events after the last completed checkpoint survive. Final save must supersede queued draft writes, and rename/delete must not race a pending checkpoint. A controller scope ending must not cancel a final write that the UI already reports as saved.

The controller's FIFO writer drains queued final writes after ViewModel closure. Limit pending final takes, not just checkpoints, so rapid record/stop actions cannot accumulate whole performances in memory. A failed final save retains the in-memory performance for explicit retry; reopening a recovered draft remains possible even if writing its final file fails.

Loading a valid unfinished draft closes its voices and marks it recovered. Invalid, oversized or unreadable files produce an understandable error without destroying other valid takes. Save/full-disk failure must preserve the last durable file and remain visible. Local files are not a durable off-device backup; Android backup remains disabled.

## Progress semantics

Store dated ear-training results with exercise parameters and a stable attempt/round identity. Aggregate only completed outcomes; reference playback and revealed unanswered questions do not earn correctness credit. A persistent goal measures practice duration; free-play recording duration includes rests. Saved recording IDs reconcile to local summaries after startup so recovery and shutdown saves receive progress credit without duplicates.

Optional reminders default to disabled. They use a local inexact AlarmManager alarm, notification permission/channel checks and a nonexported receiver that reschedules after boot, app replacement and time-zone/clock changes. The UI explains that Android may delay delivery; no exact alarm or remote scheduler is used. OEM delivery reliability requires device evidence.

Each passage result stores its original score identity, beat bounds, tempo, mode, hand, tolerance, latency, input source and scoring version. Freeze assessment configuration at loop start. Retries opened from a visual review retain the original score identity, rather than creating a new comparison group for each archive. Wait-mode results expose pitch success without a rhythm or general proficiency claim.

Active duration uses a monotonic clock and excludes preview, count-in, paused time, inter-loop delay, persistence and replay. Capture duration/context before asynchronous persistence or a new loop. Wall time is used only for dated history and local-day goals. Existing Room history may remain visible, but absent historical context must not be guessed into comparable trends.

Daily totals assign a recording to its creation date and a scored result to its saved date; this version does not split activity across midnight. Practice-summary writes use a separate draining scope so ViewModel cancellation does not close Room while a final result is being persisted.

Compare the latest two trials only within identical assessment contexts. Queue candidates come from the latest result per passage/context, so an old mistake does not override a later clean result. A 5–10 minute queue needs visible time allocation and actionable review/passages; stale or missing review files must fail clearly. Empty history receives a useful empty state instead of invented progress.

## Evidence and release gates

Required automated checks include FIFO repeated strikes, channel separation, velocity-zero releases, pedal restoration with a concurrently held identical key, seek boundary ordering, reset/stop cleanup, capture limits, malformed files, save/reload/rename/delete, draft recovery, and comparability/date/pause cases. Emulator checks should exercise the visible record/save/replay/progress flows and background interruption. Report actual commands/results in validation records; this architecture document is not a test result.

Physical BLE/USB loss, pedal timing, audible latency, prolonged device performance, phone/tablet readability, and A/B/cursor interaction across zoom and layouts still require hardware checks. Durable private signing-key backup cannot be established by an Actions cache or by this local implementation. These are explicit remaining release gates, not reasons to withhold the authorised local code and automated verification.
