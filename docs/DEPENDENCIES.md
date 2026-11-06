# Third-party notices

OSMD 1.9.2 is vendored without modification from the published npm archive. Its licence and authors are in `app/src/main/assets/notation/OSMD-LICENSE` and `OSMD-AUTHORS`. The upstream JavaScript banner names a companion licence file, but npm omits it. We bundle full notices for its declared runtime dependency family in `assets/notation/opensheetmusicdisplay.min.js.LICENSE.txt` (MIT/ISC/Zlib; JSZip uses its MIT option). The notice source package versions and URLs are recorded in that file; it is not a claim of a reconstructed binary dependency lockfile.

Package integrity: `sha512-EKTb0H7f9eiJ1Q1kmtylN5lWNDzzwfH0Pmka1t1m9Gh6TODeCXjanw2y/IuNs+zgNdwhc0Qa7mB8tS5X6MI3nQ==`

Source: https://github.com/opensheetmusicdisplay/opensheetmusicdisplay/tree/1.9.2

Runtime dependencies declared by upstream:

- jszip 3.10.1
- vexflow 1.2.93
- loglevel ^1.8.0
- @types/vexflow ^1.2.38
- typescript-collections ^1.3.3

AndroidX (Compose, Lifecycle, Room), Kotlin and Coroutines are Apache-2.0. JUnit 4 is EPL-1.0 and used for tests only. The Gradle wrapper is Apache-2.0. Android SDK components are build tools and are not bundled as source dependencies.

## Acoustic piano samples

Salamander Grand Piano, recorded by Alexander Holm, licensed under **CC BY 3.0**. Source: https://github.com/Tonejs/audio/tree/869b6f8d9cddb47d966238c012041480b1ce517a/salamander

Preserved upstream notice: `licenses/SALAMANDER-README.txt`; licence: `licenses/CC-BY-3.0.txt`. Both are also installed under `assets/piano`. Each source/asset SHA-256 and URL is recorded in `app/src/main/assets/piano/provenance.json`. Thirty MP3 samples, one every minor third across A0–C8, were decoded to mono PCM16 at 24 kHz, capped to ten seconds, with a final 300 ms fade. A single sampled dynamic layer is used; velocity controls amplitude. The adaptation and licence apply to the sample assets separately from the MIT app source and original CC0 test exercises. Attribution and full licence are accessible offline in Settings.

## Music font

Bravura, Steinberg Media Technologies, distributed unmodified under SIL Open Font License 1.1. Bundled font/notice/source record: `app/src/main/assets/fonts/Bravura.otf`, `Bravura-LICENSE.txt`, `Bravura-SOURCE.txt`. Native played/attempt staves use its SMuFL clefs, noteheads, flags, rests and accidentals. This font licence is separate from the MIT app source.

## Offline notices

Settings → About → Open-source notices and licences displays the asset credits, OSMD and JavaScript dependency notices, Apache 2.0 runtime-library licence and Piano Path MIT licence. Texts are bundled explicitly under `assets/licenses` because Android packaging excludes duplicate META-INF licence resources. The font and all 30 piano WAV hashes were checked against their source/provenance records. No third-party score catalogue or externally sourced decorative pictures are bundled. These licences grant redistribution permission; no paid asset licence or separate author approval is needed for the included uses. Preserve the notices when redistributing.
