# Signed release builds

The supplied debug APK is installable and development-signed. It is not a production release. A release identity belongs to the project owner: keep that private key backed up and out of Git.

## Local

1. Create your private signing identity. `keytool` prompts for secrets; do not paste them into source or commits.

```sh
keytool -genkeypair -v -keystore /secure/path/piano-release.jks \
  -alias piano-path -keyalg RSA -keysize 4096 -validity 10000
```

2. Set `PIANO_KEYSTORE` to that absolute path and `PIANO_KEY_ALIAS` to the alias. Provide `PIANO_STORE_PASSWORD` and `PIANO_KEY_PASSWORD` through your local secret manager or shell prompt. They are read only by the Gradle signing configuration. Never commit them.
3. Run `./gradlew :core:test :app:lintRelease :app:assembleRelease :app:bundleRelease`.
4. Verify `app/build/outputs/apk/release/app-release.apk`:

```sh
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Without `PIANO_KEYSTORE`, `assembleRelease` deliberately produces `app-release-unsigned.apk`. It cannot be installed until signed. It never falls back to the development signing identity.

## GitHub Actions

Create a GitHub environment named `release` and add these environment secrets:

- `PIANO_KEYSTORE_BASE64`: base64 of your binary JKS (single line).
- `PIANO_STORE_PASSWORD`
- `PIANO_KEY_ALIAS`
- `PIANO_KEY_PASSWORD`

Run **Signed release APK** manually or push a `v*` tag. The workflow restores the key in the runner's temporary directory, builds/tests/lints, verifies the signature, uploads the signed APK, Android App Bundle and checksum artifact and removes the temporary key. It does not publish to the Play Store or create a public release automatically.

A release APK signed with your key cannot upgrade the delivered debug APK under the same package ID because their certificates differ. Uninstall the debug app first (this removes its local data), or use a separately suffixed application ID for internal testing. Future versions must preserve the production key and increment `versionCode`.

For Google Play preparation and remaining owner tasks, see STORE_READINESS.md. A locally unsigned release bundle is not a production submission artifact.

The development `v0.3.0-preview` tag may advance to a newer verified preview commit. Its APK and source ZIP are republished together, with an updated checksum. Pin a commit/checksum when reproducing a particular preview; production release tags should be immutable.


## Consistent preview signing (build 8 onward)

The owner authorised a one-time fresh installation, so build8 starts a new preview signing generation. The private test key is retained by CI cache and selected explicitly with `PIANO_DEBUG_KEYSTORE_PATH`; the public certificate is pinned in `config/debug-certificate.sha256`. Cache is a temporary fallback, not durable backup. Configure repository secret `PIANO_DEBUG_KEYSTORE_BASE64` with a private backup of THIS key (debug alias/password), and retain an offline backup. No secrets API is available to this development connector, so secret provisioning cannot be silently claimed complete. Do not put private keystores in Git or public artifacts.

The install regression re-signs a pre-build8 APK as an emulator-only fixture for the authorised transition. Future updates use the published same-key APK directly. Both a clean install and same-key update must pass; no legacy signer compatibility is claimed.
