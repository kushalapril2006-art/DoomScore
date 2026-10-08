# UI refresh: 1.6.0

Space Grotesk now provides the wordmark, headings and score numbers. DM Sans handles readable labels, body copy and controls. Both are original, bundled variable fonts under SIL OFL 1.1; font loading needs no network connection. Copyright and license notices ship in the APK and their provenance is documented in [licenses/fonts](licenses/fonts/README.md).

Navigation, settings, stats, source apps and trophy badges use original outline icons instead of emoji. Personal avatar choices retain emoji. The Today score has a clearly labelled card, the dark surfaces and muted labels have stronger contrast, and section titles reserve space for their right-side details. Goob's animation and rank colours remain. Shared recap images and the floating counter use the new typography too.

## Verification on 9 October 2026

- Debug APK and instrumentation APK built successfully; 59 unit tests passed.
- Android lint completed with zero errors and 72 warnings. These are not treated as proof that the whole app is warning-free.
- 17 existing competition UI checks passed on the isolated DoomscoreVerified emulator (Android 16 / API 36.1, 720 x 1600). Includes navigation, retained tab/scroll state, 150% text scaling and system animations disabled.
- Earned-badge instrumentation check passed: 100 qualified views unlock the first trophy; unverified online badges stay locked.
- Reviewed screenshots of onboarding, Today, League, the profile editor, the earned trophy cabinet and large-text Stats. No live identities or leaderboard records were created.
- APK ZIP integrity, bundled fonts/license notices, signing and 16 KB ZIP/64-bit native alignment verified. Installed successfully on the emulator. Package remains `com.gridcc.doomscore.android`; versionCode is 11; signing certificate matches the previous development APK.
- Changed text files scanned for credential patterns; font hashes match their recorded provenance.

Installable development build: `artifacts/DoomScore-1.6.0.apk` (not tracked in Git).

SHA-256: `321c092679b97cf02267222d01f46195bd311b6b4342660e8fba6d0bb1456439`.

This verifies the UI update on the specified emulator. Physical-device, live Firebase and manufacturer-native-island validation remain separate requirements described in the repository. This is a development-signed APK, not a Play production release.
