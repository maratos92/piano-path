# Preview APK distribution parts

These binary files concatenate in filename order into `piano-path-0.3.0-debug.apk`.
They preserve the exact tested build and its original development signing identity
while allowing upload through the available GitHub connector's request-size limit.
No private signing key or authentication token is included.

The Publish preview APK workflow reconstructs the APK, verifies `dist/SHA256SUMS`,
and attaches it to the v0.3.0-preview GitHub release. Use that release's APK download.
