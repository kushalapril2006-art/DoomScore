# Play Protect review preparation — 10 October 2026

The owner reports **App blocked to protect your device** during APK installation. This matches Google's documented sensitive-permission internet-sideloading warning. It is distinct from the separate Harmful App Blocked category. The wording identifies the warning category, not a complete private verifier verdict. Earlier USB installation acceptance and integrity checks do not establish File Manager or Play Protect acceptance.

DoomScore declares an Accessibility service to count visible short-form videos. The service is guarded by Android's BIND_ACCESSIBILITY_SERVICE permission; it is not a runtime permission that can be omitted while keeping the current automatic detector. Removing the service would remove automatic counting. More builds, different names or signatures do not establish approval. The app correctly keeps isAccessibilityTool=false because competition is its purpose, rather than disability assistance.

## Exact review artifact

- App: DoomScore 1.6.10, version code 21.
- Package: com.gridcc.doomscore.android.
- File: artifacts/DoomScore-1.6.10.apk, 2,252,625 bytes.
- APK SHA-256: 8bde57e7f15338ea89afcfb7f601225a326dda9182de7e2dd7844e8fb94e79a7.
- Public signer SHA-256: 89aff9969c921ca1b022a1e799fbb214204a0f8607cfbf51c25bb0a10e4ca26a.
- APK verification: signed, optimized, non-debuggable, target36/min26, standard ZIP headers and restricted permission allowlist. These are build checks, not a malware clearance or Google approval.

## Submission route

The owner has deferred external review while the app is unfinished. Keep the APK private: do not upload it to VirusTotal or submit this appeal unless the owner explicitly authorizes those actions later. This document is a saved preparation draft.

Open [Google's Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals). The inspected form requests the owner's email, package name, SHA-256 of an APK uploaded to VirusTotal and supporting information. The developer name is also available. Use the exact artifact above, not an older APK. Uploading the APK to a scanning service is external disclosure; obtain the owner's authorization before doing it on their behalf. No APK was uploaded and no appeal was submitted during this preparation. Do not share signing keys, passwords, account tokens, google-services.json or phone diagnostics as attachments.

Google decides whether reclassification is appropriate. The form states decisions are final and a response is not promised. Do not represent this draft as proof of eligibility, an approved appeal or a guaranteed resolution.

For a later Google Play release, complete the non-accessibility-tool declaration, accurate data/privacy disclosures and demonstration of the consent flow. Distribution through a reviewed testing/release track is a separate acceptance process; approval must not be assumed. Current public-release/backend/legal gates remain in LAUNCH.md.

## Draft supporting information

I request review of the Play Protect installation block for DoomScore 1.6.10 (com.gridcc.doomscore.android, version code 21). The reported message is “App blocked to protect your device” when the signed APK is installed from Downloads. This app is a competitive short-form-video counter.

The core feature requires Android Accessibility to read visible interface text, descriptions, control IDs and bounds in user-selected supported reel apps. These labels identify readable video changes, visible advertising disclosures and recent repeat views. Before enabling the counter, DoomScore presents a separate in-app disclosure and requires “Agree & open settings”; the user then enables the service in Android Accessibility settings. The service is not declared an accessibility tool for users with disabilities.

The current service implementation checks consent and the selected source app before reading its hierarchy. Captions and creator labels are processed temporarily on the device, not saved or uploaded. Salted local fingerprints support recent repeats and bounded trophy progress. The optional league publishes chosen profile fields and aggregate counts after joining; raw screen labels and reel fingerprints are not uploaded. Android controls service activation, and the user can disconnect it through DoomScore or Android settings.

The APK does not declare SMS-reading, notification-listener, camera, microphone or contact permissions. The Accessibility service does not request screenshot or gesture-injection capability, and does not execute clicks or gestures in other apps. Optional floating counters use Accessibility overlays; optional counter notifications publish aggregate counters. The separate Google sign-in flow is provider-managed and is not read through Accessibility.

The artifact's APK SHA-256 is 8bde57e7f15338ea89afcfb7f601225a326dda9182de7e2dd7844e8fb94e79a7. Please review the classification for this submitted artifact.

## Primary guidance

- [Warning categories, sensitive-permission sideloading and appeals](https://developers.google.com/android/play-protect/warning-dev-guidance).
- [App verification appeal guidance](https://support.google.com/googleplay/android-developer/answer/2992033).
- [Accessibility disclosure and declaration requirements](https://support.google.com/googleplay/android-developer/answer/10964491).
