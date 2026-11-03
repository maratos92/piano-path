# Getting to Google Play production

Only Google can approve production access and a store submission. Source code, a signed APK or a GitHub release is not store authorisation. The project can be prepared and tested now; the owner's account verification, genuine testers and Google's review cannot be replaced by automation.

## Owner setup

Create a **personal** developer account at https://play.google.com/console and complete its requested identity, contact and Android-device checks. Use the personal account, never an employer's organisation. Do not send passwords, identity documents, card details or one-time codes in chat. Create the Piano Path app entry and use the store-readiness checklist. Generate the private upload key and configure the release workflow as described in RELEASE.md.

## Recruit testers

Ask friends/family with Android phones, other pianists, a piano teacher or local music group. Open-source beta communities can help when their rules permit recruiting; explain clearly that it is an offline piano app and that a real instrument is optional. Ask for genuine use and feedback, rather than buying automatic opt-ins or fabricated reviews. Recruit a few more than the minimum in case somebody opts out.

For personal accounts created after 13 November 2023, Google currently requires **at least 12 testers opted in continuously for 14 days** before applying for production access. This is an opt-in continuity requirement; do not promise that merely waiting guarantees approval. Check the live Console instructions for the actual account.

1. In Play Console choose Testing → Closed testing, create a track and add the consenting testers via an email list or Google Group.
2. Upload the signed release AAB, complete required app-content declarations and roll out the closed-test release. An internal test alone does not satisfy the closed-test requirement.
3. Send the track's actual opt-in URL to testers. They must sign in with an allowed Google account, opt in, and install through Google Play. GitHub APK installs do not count. Do not publish testers' email addresses in this public repository.
4. Keep the required testers opted in for the full period. Encourage repeated practice and collect real feedback about bugs, usefulness and stability. Record changes made in response; Google asks about testing and production readiness.
5. Apply for production access when Console permits it, answer honestly, and resolve any requests for more testing. Then submit the production release for Google's separate review.

Official rule: https://support.google.com/googleplay/android-developer/answer/14151465

## Invitation template

> I'm testing Piano Path, a free Android piano practice app with offline scores, a touch piano, Bluetooth/USB MIDI and visual feedback. Would you join its Google Play closed test and try it over two weeks? A real piano is optional. Please stay opted in for the test period and send honest feedback about crashes, confusing controls and practice results. I'll send the Play opt-in link privately after adding your consenting Google account to the tester list. You do not need to pay, donate or leave a public review.

## Useful test routes

- Everyone: installation, library, Listen, touch keys, full keyboard/zoom, falling bars, system/dark/light mode, landscape menu and visual review/history.
- Piano owners: BLE or class-compliant USB connection, chords/repeated notes/pedal, intentional mistakes, unplug/reconnect and a longer loop.
- Tablet owners: portrait/landscape, readability, resizing and controls; record actual device model and Android version.

Report app version, device/Android version, connection type, steps, expected/actual behaviour and whether it repeats. Avoid uploading private scores or personal payment details.
