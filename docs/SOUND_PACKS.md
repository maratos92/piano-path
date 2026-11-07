# Piano Path custom sounds

Piano Path includes four synthesised instruments (Mellow, Bright, Warm and Electric), written within the open-source application, plus the attributed Salamander sampled grand.

## Importing your own sound pack

Go to **Settings → General → Piano sound → Import sound pack**. Pick a ZIP containing numbered, uncompressed **mono PCM16 WAV** files, for example `60.wav`, `64.wav`, `67.wav`, `72.wav`. Nested `piano/60.wav` also works. Filenames are MIDI pitch numbers: 60 is middle C and 69 is A440. The sampler selects the closest available sample and pitch-shifts it. The bank is stored privately on the device, survives app updates and works entirely offline.

The importer checks archive paths, duplicate pitches and WAV structure. It limits content to **48 MiB uncompressed**, **4 MiB per entry**, **96 pitches** and **200 ZIP entries**; malformed or unsupported packs are rejected without replacing the existing bank. Users can delete the imported sound from General settings. Sound banks work for Listen, touch and recorded MIDI replay, independently of the note assessment engine.

**Important:** This first version does **not** decode SF2, SF3, SFZ, FLAC, stereo or multi-velocity sample maps. Simply renaming another sound format to ZIP cannot convert it. A true SoundFont or SFZ synthesiser will be required for full support.

## More openly licensed sounds

- **Salamander Grand Piano** by Alexander Holm, CC BY 3.0 — already packaged with Piano Path's attribution.
- **FreePats**: https://freepats.zenvoid.org/ — many CC0 sample instruments; check each bank's licence and export compatible mono PCM16 note samples before importing.
- **Meta Open Sound**: https://github.com/facebookincubator/Meta-open-sound — CC0 samples and an SFZ map. The original SFZ bank is not directly supported; compatible extracted/converted samples can be used.
- **sfzinstruments community**: https://github.com/sfzinstruments — varied sample instruments and licences; verify rights and convert only formats you are permitted to convert.

Optional large sample libraries are not bundled with the APK. Import only sounds you have a licence to use. Piano Path never uploads the selected file to a server.
