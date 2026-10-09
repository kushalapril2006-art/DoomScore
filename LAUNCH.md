# Launch status: release candidate, public launch pending

Version 1.1 adds the Doom League UI, encrypted profile drafts and complete calendar-month views. Online League stays disabled until the new migration, anonymous Auth/CAPTCHA and hosted acceptance are complete. Production additionally checks public terms/deletion URLs and the moderation verification setting. See `LEAGUE.md`; this does not change the existing public-launch blockers below.

The production app is named **DoomScore**, with application ID **`com.gridcc.doomscore.android`** and version **1.6.4**. Debug builds support development; release builds enforce the public-launch checks. A non-debuggable `sideload` variant supports private phone-installation acceptance with the existing release key. Release builds use code/resource shrinking and production signing/configuration checks. The private sideload variant uses the same optimizations and requires protected release signing, while public backend/legal acceptance remains a separate requirement. An in-app privacy page, scrollable consent and store/privacy materials are included.

## Firebase integration

Version 1.5 adds optional Google sign-in and public monthly rankings on Spark. Follow [firebase/README.md](firebase/README.md) for provider, certificate and Firestore setup. The production gate requires `firebase.backendVerified=true`, `firebase.abuseProtectionVerified=true`, and public HTTPS terms/deletion URLs when Firebase is configured. Do not set these flags until live sign-in, ownership/deletion, indexes, moderation and abuse protection are verified. App Check is not integrated in this version; Spark rules do not prove real reel viewing. Email/password screens and trusted online trophy finalization remain unimplemented.

## Production signing

A new 4096-bit RSA upload key was generated for this new Android counterpart in `private-signing/doomscore-upload.p12`. Its random password is encrypted with Windows user protection in `private-signing/upload-password.clixml`; the directory grants access only to the current user, SYSTEM and administrators. The public certificate is in `artifacts/doomscore-upload-certificate.pem` and may be shared. Neither private file is included in the source archive.

Back up the keystore and its password securely before publication. Windows-protected password storage is tied to this Windows user/machine and is not by itself a portable backup. Obtain the password locally using `Import-Clixml`/your password manager on this account; never paste it into chat, commit it, or include it in an artifact. If this is an existing Android app, configure the existing upload key instead before its next release. Google Play App Signing and APK-only distribution have different key-continuity requirements.

For a private phone-installation candidate, run `tools/build_sideload.ps1`. It builds `assembleSideload` and runs `lintSideload`, loads the existing password from Windows-protected local storage, and preserves the public-release gate. Private acceptance does not certify a public launch.

After public release details are configured, build with the bundled JDK 21:

```
./tools/build_release.ps1
```

This helper loads only the protected local password, temporarily sets signing environment variables and runs `:app:bundleRelease :app:assembleRelease`. It restores the prior environment on exit. The production namespace is `com.gridcc.doomscore.android`; confirm it in the team's Play listing. Back up the final signing/publication identity and increase `versionCode` for subsequent updates.
An upload key is not automatically the Google Play app-signing key. Choose your Play App Signing setup before distributing public APKs. A differently signed APK cannot update a debug install; uninstalling loses its private local data and anonymous credentials. Do not uninstall an existing installation without arranging a backup or account migration.

## Required public details

Set the actual values in ignored `local.properties` using `local.properties.example`: `release.developerName`, `release.supportEmail`, and `release.privacyUrl`. The public privacy URL must serve the approved Android policy over HTTPS without requiring login. `release/web/privacy-android.html` is a review draft using the reference repository's public contact; confirm ownership/contact, finalize its effective date and publish it. It has not been hosted, and the existing iOS-only privacy text is not sufficient for Android's Accessibility data handling.

`:app:verifyReleaseConfiguration` blocks production builds with missing public details or signing inputs. It checks configuration, not the truth of declarations, hosted-page availability, store approval or real-world counting accuracy.

## Default first release

`release.battles=false` is the default production mode. It removes the Battle tab and account/CAPTCHA prompts, clears backend client configuration from that binary and prevents network sync. Local counting, history, trophies, sharing, widget and tile remain. Battle is disabled by default in every build until configured.

To launch online Battle, deploy and verify the prepared backend security update/CAPTCHA with the iOS team, provide live privacy/terms/deletion URLs, and complete account-deletion plus profile-reporting/blocking and ongoing moderation handling. Reporting/blocking and terms acceptance are not implemented in the current Battle development UI; they are additional release work. The production gate requires `release.backendVerified=true`, `release.battleSafetyVerified=true`, `release.battles=true`, a configured HTTPS challenge and `release.termsUrl`/`release.deletionUrl`. Do not set verification flags until those checks and work are complete. No live backend updates were performed by this task.

## Public-launch requirements outside the local build

- Run `release/ACCEPTANCE.md` on physical target phones/current Instagram versions. The automated fixture proves the Android path but cannot prove detection against Instagram's current production hierarchy. API 26 and real 16 KB runtime tests remain required; binary alignment was checked separately.
- Publish the approved privacy page, set public contact details, and complete Play Console's Data safety, rating/audience, Accessibility declaration and review video. Passing local checks does not grant Play approval.
- Where Google's new-personal-account rule applies, finish the required 12-tester/14-day closed test and production-access application. No Play account/submission was accessed.
- Run the Play pre-launch report on the actual production-signed bundle and address crashes/ANRs/accessibility/layout findings before rollout. Start with a limited rollout and monitor feedback without introducing undisclosed telemetry.

Store copy and generated icon/feature graphic are in `release/`. Capture store screenshots from the actual release UI; supplied synthetic previews demonstrate testing and do not certify current Instagram accuracy.

References: [signing](https://developer.android.com/studio/publish/app-signing), [Accessibility approval](https://support.google.com/googleplay/android-developer/answer/10964491), [privacy and data](https://support.google.com/googleplay/android-developer/answer/10144311), [account deletion](https://support.google.com/googleplay/android-developer/answer/13327111), [user profiles/moderation](https://support.google.com/googleplay/android-developer/answer/9876937), [closed testing](https://support.google.com/googleplay/android-developer/answer/14151465), [16 KB support](https://developer.android.com/guide/practices/page-sizes), [Kotlin/R8 compatibility](https://developer.android.com/build/kotlin-support).
