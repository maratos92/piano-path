Piano Path 0.2.0 is an installable Android preview for Android 8.0/API 26 and newer.

Download **piano-path-0.2.0-debug.apk** below. It is the same development-signed build delivered from the development workspace, preserving its update certificate. This is an evaluation build, not a production-signed release.

- Offline **Listen** playback and audible **Touch piano** keys, without a physical instrument.
- Full **88-key A0–C8** keyboard, Fit all 88, zoom, C4 centring and a range slider.
- **Falling notes / Sheet** views, with bars aligned to their keys.
- Native Android BLE MIDI targeting the Roland FP-10, MusicXML/MXL import, pitch/rhythm feedback, passages/loops, scales, sight-reading and local history.
- 50 original CC0 studies. Both requested Chopin waltzes are user-import only because edition redistribution rights have not been verified.

Validation before publication: **66 JVM tests passed**, debug/release builds and lint passed, and the APK v2 signature verified. Android runtime and real FP-10/audio hardware testing are not claimed from those checks. See the repository's Actions tab for subsequent emulator results and docs/VALIDATION.md for limitations. The sound is synthesized piano-like PCM, not a sampled acoustic grand. Score playback follows written order at the chosen BPM.

The source ZIP below excludes distribution binaries. Source, CI workflows, build instructions and owner-controlled release signing instructions are also in the repository.
