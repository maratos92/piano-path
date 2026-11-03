# Third-party notices

OSMD 1.9.2 is vendored without modification from the published npm archive. Its licence and authors are in `app/src/main/assets/notation/OSMD-LICENSE` and `OSMD-AUTHORS`. The JavaScript bundle contains upstream dependency licence notices; preserve them.

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

Preserved upstream notice: `licenses/SALAMANDER-README.txt`; licence: `licenses/CC-BY-3.0.txt`. Both are also installed under `assets/piano`. Each source/asset SHA-256 and URL is recorded in `app/src/main/assets/piano/provenance.json`. Thirty MP3 samples, one every minor third across A0–C8, were decoded to mono PCM16 at 24 kHz, capped to ten seconds, with a final 300 ms fade. A single sampled dynamic layer is used; velocity controls amplitude. The adaptation and licence apply to the sample assets separately from the MIT app source and CC0 studies. Attribution and full licence are accessible offline in Settings.
