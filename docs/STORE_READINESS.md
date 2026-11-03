# Google Play preparation (Android only)

Piano Path is an installable development preview. It is not yet approved for Google Play. No iOS version is planned in this project.

## Prepared in 0.3

- Android 16 / API 36 target, Android 8 minimum, optional BLE/USB hardware features.
- Offline operation, no accounts, analytics, advertising, payment SDK or Internet permission.
- Release workflow builds a signed APK and Android App Bundle using owner-supplied secrets. Debug signing is never used for production.
- Music and audio attribution, licence snapshots, private history and take storage, Bluetooth permission explanations.
- Phone and tablet emulator jobs. These do not establish physical MIDI interoperability or acoustic latency.

## Before submission

1. Create/verify the owner's personal Play Console developer account and complete required device/identity verification. Supply a contact address and the public details Google requires for the account type and monetisation status.
2. Choose the permanent application ID and production signing/upload-key strategy. Enrol in Play App Signing; back up the upload key separately. Build `:app:bundleRelease` with the signing variables described in RELEASE.md.
3. Host the reviewed privacy policy at a stable public URL, link it in the app and store listing, and complete Data safety accurately. The supplied PRIVACY.md is a draft describing this build; add the owner's real contact before publication. Do not assert a submitted form exists.
4. Complete app access, ads declaration (no ads), content rating, target audience, permission declarations and listing screenshots/graphics. Explain that BLE discovery can require Location on Android 8–11, and that the app never records microphone audio.
5. Run Play internal/closed testing and pre-launch reports; inspect crashes, ANRs, accessibility, edge-to-edge/insets, Android 16 large-screen layouts and native-library 16 KB compatibility. Validate release APK/AAB native dependencies, rather than assuming a debug build proves store readiness.
6. For personal developer accounts created after 13 November 2023, the current production-access process requires at least 12 testers opted in continuously for 14 days, followed by a production-access application. Google may request more testing.
7. Obtain real phone/tablet BLE and USB reports using HARDWARE_TESTING.md, including permission refusal, cable removal, Bluetooth loss, pedal, repeated notes and long polyphonic practice. Pixel/FP-10 success is a user report, not certification for every instrument.
8. Resolve failures and misleading claims before submitting. Store review approval and timing cannot be guaranteed.

New apps and updates currently require API 36 (since 31 August 2026). Recheck at submission: https://support.google.com/googleplay/android-developer/answer/11926878

Testing rule: https://support.google.com/googleplay/android-developer/answer/14151465

## Voluntary support

Settings includes a voluntary-support link to the owner-supplied https://ko-fi.com/maratbatkaev page, without an embedded SDK. Payments occur in the external browser, not inside Piano Path. The owner must review the final page and keep support separate from digital benefits. Google distinguishes contributions without digital benefits from purchases: its FAQ exempts qualifying contributions only when 100% goes to the creator and no digital goods/services are granted. Do not add badges, premium scores, ad removal or feature unlocks to an external support link without reconsidering billing policy. Do not label support to an individual as a tax-deductible charitable donation.

Policy: https://support.google.com/googleplay/android-developer/answer/10281818
Ko-fi payment handling: https://help.ko-fi.com/hc/en-us/articles/115003980093-How-do-I-get-paid

## UK record keeping

A platform balance is not a reliable tax-deferral mechanism. Ko-fi sends payments directly to PayPal/Stripe. Business-related voluntary contributions can be taxable receipts; classification and recognition depend on the facts and accounting method. The trading allowance is generally £1,000 of combined annual gross trading income, before platform fees, with eligibility exceptions. Retain dated gross/fee/refund records and ask HMRC or an accountant how the receipts apply to your circumstances. This document is general information, not an individual tax determination.

https://www.gov.uk/guidance/tax-free-allowances-on-property-and-trading-income
https://www.gov.uk/simpler-income-tax-cash-basis/income-and-expenses-under-cash-basis
https://www.gov.uk/hmrc-internal-manuals/business-income-manual/bim41810

### Activating the support button

The default public URL, supplied by the owner, is `https://ko-fi.com/maratbatkaev`. For a tips-only flow, disable memberships/digital rewards/commissions and review Contributor/platform fees against Play's policy. Set the public repository Actions variable `PIANO_SUPPORT_URL` to that URL, or set the environment variable when building locally. It is public configuration, not an API key. The app shows a voluntary-support explanation and opens the page in the external browser; it contains no payment form or billing credentials. If the environment variable is unset or blank, the owner-supplied default is used. Forks should change or remove that public default before publishing. No placeholder URL is used. The current preview includes the owner's link. Recheck store policy for the final page and any future rewards.

With zero receipts there is no contribution income to report. That does not cancel an existing Self Assessment notice or obligations arising from other income. Keep contribution records once receipts begin. See BETA_TESTING.md for a concrete owner/tester onboarding procedure.
