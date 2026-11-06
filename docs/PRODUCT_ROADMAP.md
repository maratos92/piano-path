# Piano Path: product direction, cloud and input research

Research date: 2026-11-04. Baseline: build 8 (`0.4.0-dev.5`, main `9c833b8`). This is a proposal and review plan, not a claim that the backlog, cloud sync, microphone input or a full robustness audit is complete. No services/accounts are provisioned by this document. Prices and policies must be rechecked before implementation/submission.

## Recommendation

Build an offline-first practice companion: dependable MIDI, easy passage repetition, understandable visual feedback, recordings and visible progress. Keep guest use complete. Add optional Google sign-in for progress summaries after local persistence and review are robust. Keep score files and detailed performances local initially. Support smaller keyboards before attempting full microphone polyphony. Do not add unsolicited repertoire, discovery links or a subscription catalogue to the app.

## What comparable products teach us

This is an analysis of official product descriptions, not a hands-on comparative accuracy benchmark. Marketing claims about educational outcomes are not treated as validation.

| Product | Documented emphasis | Useful lesson for Piano Path |
|---|---|---|
| [flowkey](https://www.flowkey.com/en) | Sheet/video together, wait mode, reduced speed, passage looping, separate hands | Make existing practice controls predictable and immediate; instructional video production is a separate large content project. |
| [Piano Marvel](https://pianomarvel.com/en/feature/sasr) | Adaptive sight-reading excerpts and historical assessment charts; [practice tools](https://pianomarvel.com/en/new-features) include slicing and tempo controls | Level-appropriate exercises, dated progress and targeted repetition are valuable. Do not present our generated levels as their validated assessment or reproduce their curriculum/scoring system. |
| [Synthesia](https://synthesiagame.com/support/guide/recording) | Falling-note practice and Free Play MIDI recording with multiple tracks | A simple persistent record/save/replay workflow can be satisfying without a content catalogue. Start with one track. |
| [Simply Piano](https://play.google.com/store/apps/details?id=com.joytunes.simplypiano) | Guided learning and immediate microphone/MIDI feedback | Clear short activities and visible milestones matter. Its [Android help](https://piano-help.hellosimply.com/en/articles/6210618-ways-to-improve-note-recognition-in-simply-piano-for-android-keyboard-electronic-piano) recommends MIDI for recognition problems, especially advanced music; microphone parity should not be assumed. |
| [PianoBooster](https://www.pianobooster.org/) | Open-source notation-based MIDI practice and accompaniment | A focused companion can complement lessons. Its GPL code cannot simply be copied into an MIT application without a licence decision. |

Piano Path already has wait/rhythm/follow modes, loops, hand filtering, falling notes and feedback. Improvement should focus on the experience and evidence behind them rather than adding duplicate controls. General practice ideas can inform original implementation; do not copy proprietary assets, course material, branding or interfaces.

## Delivery order and review gates

1. **Close existing correctness/UX backlog:** physically test A/B and blue cursor after zoom, wrapped/horizontal/Both/floating layouts; BLE/USB loss, pedal/repeated notes, latency, phone/tablet readability. Complete private signing-key backup beyond Actions cache.
2. **Persistent local free-play recorder:** pitch/velocity/channel/on-off/pedal capture, stop/save/name/delete, recovery after interruption, audible replay, seek/pause/speed. Reuse recorded-performance components without embedding a score when none exists. Keep MIDI data distinct from audio/video recording.
3. **Progress and meaningful practice:** dated ear-training sessions, per-passage trends, persistent goals, optional reminders, and a 5–10 minute queue based on recurring mistakes. Compare like-for-like tempo, mode, hand, tolerance and scoring version. Wait-mode pitch success is not a rhythm or general proficiency score.
4. **Recent-note notation and keyboard profiles:** group near-simultaneous attacks visually, distinguish key duration from pedal sustain, improve readable rhythms/rests, then offer calibrated 49/61/76/88-key and custom ranges. Consider a deliberate hide/dim/show staff preference.
5. **Full robustness review and release gate**, followed by fixes and appropriate retesting. Add focused reviews before new trust boundaries such as cloud and microphone input even if the backlog is still evolving.
6. **Optional summary sync**, then an experimentally validated monophonic microphone mode. Full polyphonic microphone assessment, expressive tempo scoring and video export remain later research projects.

Highest-impact additions after correctness: save/replay a performance; choose today's weak passages; see improvement at a comparable BPM; hear a reference hand while practising the other; a clear visual error timeline/measure heatmap; comfortable keyboard range; optional backup. Reference accompaniment needs separate output/input paths so generated notes never earn accuracy credit. Gentle goals are preferable to punitive streaks. Measure satisfaction through opt-in tester feedback and task completion, without silently adding analytics.

## Robustness review specification

The current 83 JVM and 16-per-device emulator checks are useful evidence, not an exhaustive audit or hardware certification. Review should produce severity-ranked findings with locations, a reproducer, impact, fix and regression evidence; no unqualified claim of being bug-free.

- MIDI ordering, running status, channel separation, repeated notes, all-notes-off, sustain and stuck-note cleanup across disconnect, pause, loop, seek, lifecycle/audio interruption and cancellation.
- Assessment: overlapping voices/unisons, ties and passage boundaries, missing/extra notes, tolerance/calibration, pauses, score jumps and fixed-tempo limits. Use independently labelled recordings or synthetic event corpora; avoid grading tests that merely repeat implementation arithmetic.
- Import/WebView: hostile/large MusicXML/MXL, entity/ZIP limits, path validation, JavaScript bridge arguments and callback flood, external resource requests and pointer capture after rerender. Adding Internet permission later requires retaining a local-only allowlist/network block specifically on the notation WebView.
- Persistence: atomic saves, process death, full disk, Room migration/schema snapshots, record deletion/export, corrupted archives and large histories. `exportSchema=false` and the current history query's 300-row display limit need attention before sync; that limit is not a storage retention policy.
- Audio/UI performance: dense polyphony, prolonged sessions, thread races, battery, memory and frame timing, accessibility, font scaling, short landscape views and physical tablets.
- Architecture: split session, playback, recording and sync responsibilities as they grow; the current ViewModel coordinates many of these. Introduce narrow repositories/controllers where they reduce state coupling, not a wholesale rewrite.
- Supply chain/release: dependency and licence review, signing-key access/backup, untrusted PR/cache exposure, scoped CI permissions, exact-head integration checks, main-commit checks and public APK verification.
- Cloud-specific gate: two-user isolation tests, auth verification, guest merge, account switching, offline conflicts, retries/duplicates, deletion/revocation and cost-abuse tests. Microphone-specific gate: measured pitch/onset/offset performance and device latency against independent ground truth.

Several architecture/privacy documents still describe earlier versions. Update them against the source as part of review; do not treat their old claims as a security assessment.

## Optional Google sign-in and useful synced data

Use Android [Credential Manager / Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg). No password/registration form is necessary. First sign-in still creates an app identity in the chosen auth backend, so account deletion and privacy obligations remain. Google sign-in alone does not provide a database or Drive access.

UX: **Continue without an account** and **Sign in with Google to sync progress**. Guest mode remains local/offline, with no mandatory anonymous cloud account. Signing in offers an explicit merge of local progress. On the tablet, the same account retrieves progress; offline practice continues and queues changes. Sign-out, local-copy choices, account switching, export, delete-cloud-data and delete-account must be distinct and understandable.

Sync an explicit allowlist:

- Stable app user ID, with minimal profile fields needed for sign-in/display; no Google password, contacts or broad Google account access.
- Session UUID/date, active practice duration, exercise ID/parameters, source type, mode, hand filter, BPM, tolerance/scoring version and aggregate pitch/rhythm/error statistics.
- Scale/ear/sight-reading progress, dated results, goals and optional user-authored notes.
- Saved passage names and beat bounds tied to a score identifier, plus limited preferences.

Keep local initially: MusicXML/MXL/PDF, score images, complete expected/performed note streams, original audio/video, diagnostic logs, hardware identifiers and device-specific latency calibration. Timing offsets and layouts should not silently transfer from a phone to a tablet.

**Important source finding:** `TakeArchive.save()` embeds `p.xml` in each visual review. Uploading its JSON would upload the edition. Current archives are not cloud-safe progress DTOs. Build a separate summary schema; do not upload a Room backup or entire private files directory.

Use Room as the local source of truth plus an outbox and a narrow sync repository. Use UUIDs, idempotent writes, immutable session records and server revisions; do not overwrite one giant progress JSON document from two devices. Resolve editable goals/sections explicitly, retain deletion tombstones long enough, and avoid recreating deleted cloud data when an old offline device reconnects. Isolate guest/account A/account B caches. Test anonymous-to-account merge and account deletion races. Firebase [offline support](https://firebase.google.com/docs/firestore/manage-data/enable-offline) helps transport, but last-write-wins does not solve application-level conflicts.

Without score sync, a second device cannot automatically render an imported piece. Show its progress and ask the user to import the same legal file. Match a private per-user identifier/file hash; equivalent exports or different editions may need explicit linking. Do not silently apply passage bounds to another arrangement. Our own generated exercises can be recreated from parameters, seed and generator version.

## Cloud choices and cost

No provider automatically guarantees Google Play approval. These are engineering options with their own security, privacy and operating responsibilities. Prices below are USD list prices checked 2026-11-04, before tax/region-specific extras.

| Option | Cost model and strengths | Trade-off |
|---|---|---|
| [Firebase Auth + Firestore Standard](https://firebase.google.com/pricing) | Native Android integration, Google login, offline client support. Current Firestore no-cost allowance: 1 GiB, 50K document reads/day and 20K writes/day; paid use is metered. | Usage/index/listener costs, provider coupling, auth-product limits and deletion-function costs need modelling. A production deletion backend may require Blaze billing even if data usage is tiny. |
| [Supabase Auth + Postgres](https://supabase.com/pricing) | Google sign-in, SQL/RLS, open-source components and easier conventional data export. Free: 500 MB DB; Pro starts $25/month with one project and compute credit. | Free projects can pause after inactivity. Android offline/outbox sync needs more application work. Self-hosting shifts maintenance/security/backup costs to us; it is not operationally free. |
| User's Google Drive app-data folder | Technically supports private app-specific files and uses the user's storage. | Not recommended as the default here: [Drive terms](https://developers.google.com/workspace/drive/api/terms) list app-content backup as requiring Google's prior written consent. Confirm the exact use case and OAuth requirements before selecting it. It is not a conflict-free database sync engine. |

**Recommendation:** Firebase Auth + Firestore for the first Android summary-sync implementation; Supabase Pro is a reasonable choice if open-source backend portability and a clearer base monthly bill matter more than native offline integration. Start local export/backup first. Do not choose a provider solely for its free tier.

Illustrative workload, not a quote: 1,000 users × 30 sessions/month × 2 KB per summary ≈60 MB/month before indexes, replication, profiles and retention. At five writes per completed session that is about 150K writes/month (5K/day on average). It might fit free allowances initially, but read bursts/listeners, long retention, functions/auth and backups can dominate. Twelve months is already ~720 MB before overhead; raw recordings and embedded XML change the scale substantially. Sync completed summaries/batches, never each MIDI event. Paginate history and establish retention, monitoring, application quotas and an abuse response. Firebase [budget alerts are not a hard spending cap](https://firebase.google.com/docs/projects/billing/avoid-surprise-bills).

Select an appropriate UK/EU data region where supported and review processor agreements, subprocessors and transfers; a region choice does not keep every auth/support process in that country. See [ICO transfer guidance](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/international-transfers/a-guide-to-international-transfers/).

## Google Play/security implications

[Play's account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) require an in-app deletion path and a public web deletion-request resource when the app enables account creation. Federated login is not an escape from app-account responsibilities. Sign-out is not account deletion.

Before release: verify provider-issued identity tokens server-side/through the auth service; authorise every row/document to its owner, validate payloads and reject cross-user access. Never ship an admin/service-role key in the APK. Use TLS and provider encryption at rest without calling that end-to-end encryption. Plan backups/retention/deletion accurately, reauthentication for sensitive actions and revocation. Configure OAuth for the actual installed debug/Play signing certificates. Add quota/abuse controls; device attestation complements rather than replaces authentication/authorisation.

Update the privacy policy, public contact, Data safety and SDK disclosures for actual flows. The current offline/no-account policy is no longer sufficient once sync is added. Choose the intended audience honestly; [Families requirements](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en) matter if children are targeted, especially for auth and microphone data. Do not promise approval based on choosing Firebase or any other provider.

## Scores, copyright and open repositories

For phase one, keep scores local and sync statistics/identifiers only. Private cloud storage and encryption do not create copying rights. Detailed MIDI performances can also reproduce a composition, so do not assume all event recordings are copyright-free merely because they are not MusicXML.

If optional file backup is considered later: no public catalogue/sharing by default; rights/provenance information, user consent, deletion/retention and a takedown process; no scraping paid catalogues or assuming that owning a download grants server redistribution rights. Licence checks must cover composition, arrangement, edition/transcription and any other relevant material, including jurisdiction. Get a focused rights review before enabling uploads.

Useful open resources for research, not additions to the app:

- [OpenScore / Four Score and More](https://fourscoreandmore.org/openscore/) and [OpenScore GitHub](https://github.com/OpenScore): CC0-licensed encoded collections; much is vocal/piano, orchestral or quartet repertoire rather than a ready piano-solo MusicXML catalogue. MuseScore source formats can be exported to MusicXML; the current app may require piano-part extraction.
- [Mutopia](https://www.mutopiaproject.org/) with [per-edition licences](https://www.mutopiaproject.org/legal.html): public-domain/CC editions, commonly PDF/MIDI/LilyPond. It is not a drop-in MusicXML library; conversion and notation verification may be necessary.
- [IMSLP](https://imslp.org/wiki/IMSLP:Licensing_Policy_and_Guidelines): large public-domain/freely licensed archive, often scans rather than structured events. [Jurisdiction and edition rights vary](https://imslp.org/wiki/Public_domain). A PDF cannot currently be imported into our notation/evaluation engine.

Open-source notation software is not blanket permission to redistribute every score on a related website. Preserve exact edition URL/revision/hash and licence evidence. Attribute CC-BY; meet share-alike conditions for adaptations where applicable. Such score conditions do not automatically relicense unrelated app code. No repertoire is bundled or downloaded as part of this research.

## Microphone input feasibility

Useful for acoustic/non-MIDI instruments, but it is a new uncertain sensor, not MIDI through another transport.

| Capability | MIDI | Microphone |
|---|---|---|
| Pitch/chords | Discrete instrument events | Estimated; noise, harmonics, room and overlapping notes can cause misses/extras/octave errors |
| Onset rhythm | Instrument timestamps plus transport/calibration | Capture buffering and detection delay must be measured/compensated |
| Physical release | Key-up event | Sound decay and pedal resonance do not identify physical key-up reliably |
| Velocity/pedal | MIDI values/controllers where sent | Loudness is not MIDI velocity; pedal/finger/hand identity is not directly observed |

Start with on-device single-note naming, ear matching or simple scales, explicit confidence and an uncertain/unscored state. Then evaluate small chords and score-constrained following without forcing detections to match expected notes. Keep stricter release/velocity/pedal grading disabled when the sensor cannot support it. Mute app demonstration audio or use headphones so it does not grade its own output.

[Spotify Basic Pitch](https://github.com/spotify/basic-pitch) is an Apache-2.0 audio-to-MIDI candidate with TFLite models and polyphonic support, but its documented file-transcription workflow is not proof of low-latency Android live assessment. Audit the selected code/weights/dependencies and benchmark before integrating. Do not add Python/server audio upload merely to obtain a prototype.

An engineering prototype for monophonic recognition is a weeks-scale task; reliable full-piano polyphonic assessment is a months-scale validation project, not a schedule promise. No honest accuracy percentage exists for this app until tested. Measure precision/recall, onset/offset error distributions, chord completeness, octave errors, end-to-end latency, thermal/battery behaviour and user-visible false feedback across phones, registers, dynamics, pedals, noise and distance. MIDI-backed ground truth must itself be aligned/calibrated.

Require foreground microphone permission only when selected, visible listening state and immediate stop on exit/background. Keep audio on-device by default. Input events need source/confidence and optional measurements so estimated audio events do not pretend to be exact MIDI events. Choose modes and feedback according to measured capability.

## Smaller keyboards and other instruments

Yes: add 49/61/76/88 presets plus explicit lowest/highest-note setup and a custom range. Key count alone does not identify endpoints; observing a few played notes cannot discover an instrument's range. Preserve absolute MIDI pitch and make octave/transpose settings explicit. Keep falling bars and touch hit-testing aligned, and display an out-of-range passage warning with appropriate exercise/hand/passage choices. Do not silently ignore unplayable expected notes or transpose notation differently from scoring.

Current `PianoLayout` is hardcoded to A0–C8 and 52 white keys; the existing `focusRange` only dims notes. Proper range support requires one shared dynamic geometry model for keyboard, ideal roll, performed roll, labels and hit-testing, plus boundary/black-key/chord tests and range-aware generated exercises. This is moderate work with high practical value.

If “different instruments” means guitar, violin or voice, a cropped piano keyboard is not enough. They need appropriate notation, tuning/transposition/range, pedagogy and sensor handling. Support smaller keyboard instruments first; treat broader instruments as a separate product scope.

## Follow-ups from the November 6 feedback

- **Velocity/dynamics trainer:** show target dynamic bands and trends for velocity consistency, crescendos and accents. MIDI velocity is an instrument-dependent control value, not calibrated loudness. Start with user-calibrated ranges; avoid grading artistic interpretation or claiming identical response between pianos. No feature implemented yet.
- **Sequence listening:** progress from single pitches/triads to short 2–4-event phrases; play the reference, capture an answer sequence, align by ordered onset groups with configurable chord-spread tolerance, then reveal notation and errors. Keep reference-output echo separate from answers, offer slower replay, limit attempts and preserve accessibility. This needs its own generation/alignment tests and is deferred from build 11.
- Physical piano output/echo, sustained-note timing, portrait/landscape readability, device battery/frame-time checks and durable signing-key backup remain owner/hardware gates. No cloud/database work is resumed.
