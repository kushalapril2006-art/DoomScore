# Phone installation

## Latest private candidate: 1.6.10

`artifacts/DoomScore-1.6.10.apk` (version code 21, 2,252,625 bytes) retains the chart interactions, score consistency, colour and connection changes below. The shared Shorts parser distinguishes action-rail thumbnails from inline caption icons and stops menu exclusions at the selected Short, preventing whole-app navigation wrappers from hiding readable metadata.

SHA-256: `8bde57e7f15338ea89afcfb7f601225a326dda9182de7e2dd7844e8fb94e79a7`.

All 127 JVM checks pass. The signed, optimized APK passed the restricted-permission, non-debuggable, v2-signature, ZIP and 16 KB alignment checks; lint reports zero errors and 75 warnings. Its package and signing certificate are unchanged. Standard USB update succeeded on the connected OnePlus without clearing data, and Accessibility rebound automatically. YouTube counts now increase; detailed playback and revisit acceptance are recorded in [Shorts verification](SHORTS-COMPATIBILITY.md). These results do not guarantee every future feed layout or OEM installation policy. No new dependencies, permissions, screen capture or security bypass were added.

## Previous candidate: 1.6.8

`artifacts/DoomScore-1.6.8.apk` (version code 19, 2,252,621 bytes) adds hold/drag chart readouts, previous-month browsing, balanced cyan/violet/gold accents and clearer score/trophy labels. Accepted counts and trophy updates are atomic. A dedicated worker and binding-generation checks protect the counter from UI stalls and stale reconnect work; Android Accessibility settings remain the recovery path when the OS leaves it unbound.

SHA-256: `9aa8cb28ce8dfd9f461fd785c73e69be5281a5cd8ec953116c87e012ddfe2a09`.

The signed, optimized APK passed restricted-permission, non-debuggable, v2-signature, ZIP and 16 KB native-alignment checks. The package and signing certificate are unchanged. The source passes 124 JVM checks; lint reports zero errors and 75 warnings. It installed over 1.6.6 through standard USB installation on the connected OnePlus CPH2767 without clearing data; the existing score was preserved and Accessibility rebound automatically. This is USB update acceptance, not a new File Manager-installation result. No dependency, screenshot or gesture permission was added. Physical-phone acceptance is recorded in [UI details](UI-EXPERIENCE.md) and [Shorts verification](SHORTS-COMPATIBILITY.md); build checks do not establish universal feed compatibility or OEM service persistence. [Counter reliability](ACCESSIBILITY-RELIABILITY.md) explains the reconnect and payment boundaries.

## Previous candidate: 1.6.7

`artifacts/DoomScore-1.6.7.apk` (version code 18, 2,236,233 bytes) keeps the Scoreboard theme and animated Goob, distinguishes off-state switches, removes the empty hourly-chart prompt and highlights first place with a taller gold podium. Manual refresh requests fresh standings instead of reusing the short-lived cache and rotates only while the request runs. The shared Shorts parser now handles qualified caption buttons, virtual creator targets and additional compact metadata panels; the optional local check includes numeric layout totals without captions/usernames.

SHA-256: `47ca2793dca3ae044c526f75d67433af7b77e2958e9318ef55d101a4fcbf64c5`.

The optimized APK passed non-debuggable/manifest, restricted permission, v2-signature, ZIP and 16 KB native-alignment checks; its certificate matches the existing upload key. Lint reported zero errors and 74 warnings. The source passes 116 unit checks. Three instrumented checks passed for Goob interaction, standings/pinned-position behavior and trophy navigation. The corrected focused refresh test still needs its runtime rerun; the emulator was stopped after repeated host memory-pressure failures. The live YouTube attempt was inconclusive and does not confirm the exact reported phone layout. No dependencies changed from 1.6.6's scan of 257 exact versions with no known OSV advisories. These checks do not certify every phone, future feed layout or public release. See [UI details](UI-EXPERIENCE.md) and [Shorts verification](SHORTS-COMPATIBILITY.md).

This supersedes earlier private candidates. The package/signing identity is unchanged from 1.6.2 onward; updating the same installed package preserves its local data. File Manager installation, physical-phone counting and native OEM island placement still need device acceptance; the APK does not change phone security settings.

The Android 16 OnePlus phone rejected the 1.6.0 development APK when opened from Downloads. Its installer log reported `INSTALL_FAILED_VERIFICATION_FAILURE` (status -22, `Install not allowed`). The phone copy matched the PC SHA-256, the OnePlus File Manager had `REQUEST_INSTALL_PACKAGES` allowed, and `android:testOnly` was absent. Both Google and OnePlus verification receivers are registered; the log did not identify the exact rejecting rule or receiver. These observations do not prove debug signing alone caused the refusal.

## Private installation candidate

Version **1.6.2**, code **13**, uses `assembleSideload`: non-debuggable, code/resource optimized and signed with the existing configured upload key. The protected key/password stay outside Git. The public certificate matches the previously exported upload certificate. The older internal `com.gridcc.doomscore.android.releasecheck` app has a different package and signing key; this candidate uses `com.gridcc.doomscore.android`. Its old local counts are not automatically migrated. No uninstall command was issued during this task. The final phone query did not list the older internal package; its data preservation cannot be confirmed.

Run `tools/build_sideload.ps1` to build a private candidate from protected local signing credentials. The helper restores password environment variables after building. It also runs Android lint. This is private acceptance, not public-launch approval.

## Checks on 9 October 2026

- Build and lint succeeded; zero lint errors (72 warnings remain).
- Verified APK signing, package identity, restricted permission allowlist, disabled backups/cleartext, absence of a test-only restriction, ZIP integrity and 16 KB native/ZIP alignment.
- The Credential Manager dependency added `USE_BIOMETRIC` and `USE_FINGERPRINT`. Version 1.6.2 removes these unused transitive permissions: the app requests only `GetSignInWithGoogleOption`; the exact 1.5.0 SDK source routes this through the Google sign-in intent controller, which does not invoke BiometricPrompt. There is no app-side biometric/passkey flow. Full Google/Firebase acceptance remains outstanding. The APK verifier keeps a strict allowlist.
- Both bundled font files and full font licenses survive resource shrinking.
- Signing matches the configured upload certificate. Private build fails without protected signing inputs; public release still fails with incomplete legal/backend acceptance. No verification flags were falsely enabled.
- Phone Downloads copy matches the PC file. Phone verification settings were not modified.
- Version 1.6.1: user reported File Manager installation failed; standard ADB installation also returned `INSTALL_FAILED_VERIFICATION_FAILURE`. Version 1.6.2: standard ADB installation returned the same code after unused-permission removal. On the first attempt neither candidate was installed.
- After the user reported accidentally cancelling a prompt and requested another attempt, standard `adb -d install -r` succeeded for 1.6.2. The installed package independently reports version 1.6.2 / code 13. No phone-security setting was modified. This shows that the earlier failure code alone did not establish an unsafe classification.
- Direct File Manager reinstallation of 1.6.2: awaiting user confirmation.
- Current user 0 has no effective installation restrictions and Android Advanced Protection is off. Both `com.android.vending` and `com.oplus.stdsp` are required verifiers. Their private verdict reasons were not exposed in the available logs.

Candidate artifact: `artifacts/DoomScore-1.6.2.apk`, 2,137,789 bytes, not tracked in Git.

SHA-256: `28e700f56d52bb22df1db12bb2ae1fa152ced7203f63fefee138478a0cd8a109`.

Public legal URLs, live Firebase/provider/rules/index acceptance, abuse protection and store review remain outstanding. Release signing does not guarantee acceptance by every device or verifier.

## Interpretation

Google documents that apps using Accessibility may be blocked when installed from file managers in supported fraud-protection markets. This is a plausible explanation, not proof that Google (rather than the OnePlus verifier) refused this app. See [Google developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

The 1.6.2 USB retry succeeded after the user approved the installation. No appeal was submitted or is justified solely by the earlier generic failure code. Direct Downloads acceptance should be verified independently. A real confirmed verifier classification would require supported review rather than changes that hide permissions or turn off protection. The production release remains gated on backend/legal/policy acceptance.

## Confirmed warning wording — 10 October 2026

After 1.6.10 USB acceptance, the owner reports “App blocked to protect your device” during installation. This matches Google's documented sensitive-permission internet-sideloading warning category; it is not confirmation of the separate “Harmful App Blocked” category. DoomScore's necessary Accessibility service can trigger this restriction. No app update has been demonstrated to remove it. The current warning supersedes the earlier pending Downloads acceptance. A supported review preparation, exact artifact hash and factual draft are in [PLAY-PROTECT-REVIEW.md](PLAY-PROTECT-REVIEW.md). No external scanner upload or appeal submission occurred.

The owner deferred external scanning and review while the app is unfinished. The APK remains private; the Play Protect block remains unresolved. Keep the review draft for later rather than submitting it without renewed authorization.
