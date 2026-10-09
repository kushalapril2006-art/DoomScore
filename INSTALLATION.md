# Phone installation: 1.6.2

## Latest private candidate: 1.6.3

`artifacts/DoomScore-1.6.3.apk` (version code 14, 2,137,789 bytes) includes the YouTube Shorts compatibility fix. It uses the same package and release signing certificate as 1.6.2. The optimized APK passed manifest, permission, v2-signature, ZIP and 16 KB native-alignment checks; lint reported zero errors. See [Shorts verification](SHORTS-COMPATIBILITY.md).

SHA-256: `b244d27d07b2b5f56fbf4e7b26f30043d4ab930292a5a01530481331053459f5`.

This supersedes the 1.6.2 private artifact below. File Manager installation and native OEM island placement still need device acceptance; the APK does not change phone security settings.


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
