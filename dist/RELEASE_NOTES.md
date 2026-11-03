# Piano Path 0.3.0 — Android preview

Install `piano-path-0.3.0-debug.apk`. It retains the previous preview's development certificate for an in-place update.

- Full-width landscape practice, expandable scrollable navigation, compact adjustable score margins and system/light/dark themes.
- Acoustic grand-piano samples, 128 voices, sustain and unscored Listen. No piano is required for touch playing or listening.
- Live note names, velocity/channel monitor and a dedicated Note monitor screen.
- Continuous Rhythm cursor, performed-note/error/hold overlays on the score and saved visual reviews in History.
- Class-compliant USB MIDI input selection and reconnection alongside general BLE MIDI support.
- Voluntary support through the owner's Ko-fi page, opened externally. Every feature remains free. No ads.
- Android 16/API 36 target; signed APK/AAB workflow, tester guide and store-readiness documentation.

77 JVM tests passed; all 50 scores rendered in Chromium; debug/instrumentation APKs and unsigned release APK/AAB built; debug/release lint and static 16 KB alignment checks passed. All 9 instrumentation tests passed on the API 36 phone emulator and all 9 on the API 35 tablet emulator: https://github.com/maratos92/piano-path/actions/runs/41674522753. The owner reports Pixel/FP-10 use; USB, other instruments and physical tablets still need acceptance testing. No Bluetooth hardware certification or Google Play approval is claimed.

The library contains 50 original CC0 studies. Import legally obtained MusicXML/MXL editions for other repertoire. Visual review is a diagnostic overlay, not a transcription exporter. The acoustic bank uses one dynamic layer. Follow the Google Play closed-testing and release-signing procedures before production publication.

Piano samples: Alexander Holm, Salamander Grand Piano, CC BY 3.0, adapted to compact mono PCM. Full attribution/licence/provenance are bundled and available in Settings.
