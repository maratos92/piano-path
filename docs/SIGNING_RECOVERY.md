# Preserve the installed app before changing signing identity

The owner reports dev.2, build5, revision a0478842. That workflow used a temporary debug key without retaining it. The saved APK holds the public certificate, not the private signing key. A version bump, GitHub token or Google identity verification cannot recreate that key.

Do not uninstall or clear storage to get an update installed. The original installed app can continue working. A permanent signing key and a verified copy of its data are prerequisites for any approved migration if the original key cannot be recovered.

## Read-only backup preparation (computer + USB debugging)

These commands are for the owner's computer with Android platform-tools, not the cloud workspace. Finish practice first. Authorise that computer on the phone, then check the device and package:

```sh
adb devices
adb shell dumpsys package org.pianopath
```

After closing the app, stop it so the database and its WAL are copied consistently. This stops the app without clearing its data:

```sh
adb shell am force-stop org.pianopath
adb exec-out run-as org.pianopath tar -cf - . > piano-path-data.tar
```

Use Bash or another shell that preserves binary stdout (avoid older Windows PowerShell redirection). Keep the backup private. Check the command's exit status, nonzero file size, archive readability and the presence of app files, settings and any practice database:

```sh
tar -tf piano-path-data.tar
sha256sum piano-path-data.tar > piano-path-data.tar.sha256
```

A backup archive alone does not prove a successful restoration. Before removing or overwriting anything on the phone, validate a copy against the intended build on an isolated emulator and compare imported scores, saved passages, history, settings and recorded takes. Keep the original app and original archive untouched. Do not run uninstall, clear-data or restoration commands until that validation and the owner's approval of the concrete migration are complete.

If run-as is rejected, stop. Do not root the phone or bypass Android security. Review the actual installed package/build and supported export options first.

## Future signing

Select the restored key explicitly with `PIANO_DEBUG_KEYSTORE_PATH`; AGP's default location can differ from the directory a workflow caches. Store a durable private key in an owner-controlled secret/backup, never in source or public artifacts. CI must verify its certificate against the intended installed population before publication. The current published dev.3 certificate pin does not establish compatibility with the owner's dev.2 APK.
