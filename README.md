# DoomScore

**Stack reels. Climb ranks. Become the final boss of brainrot.**

![DoomScore — competitive doomscrolling with Goob](release/assets/play-feature-1024x500.png)

DoomScore is a native Android app that turns scrolling into a scoreboard. After a one-time accessibility setup, open Instagram Reels and your score starts building automatically. Track your stats, unlock trophies, and watch **Goob**, your colour-changing mascot, evolve with your scroll rank.

Built with **Kotlin and Jetpack Compose**. No screen recording or screen sharing is required.

## Features

- **Automatic counting:** Instagram Reels and YouTube Shorts, with beta detectors for TikTok and Snapchat Spotlight.
- **Repeat and ad filtering:** stable-view detection, continuous-loop suppression, a recent-rewatch window, and recognized sponsored-label exclusion.
- **Your scrolling stats:** daily totals, full calendar-month scores, app/hour breakdowns, historical charts, personal bests, and active-day streaks.
- **Goob everywhere:** animated in-app mascot, draggable floating score pill, and rank colours from cyan through violet.
- **Quick access:** home-screen score widget, Quick Settings pause/resume tile, and optional live counter notifications.
- **Brainrot Trophy Cabinet:** persistent milestones, daily achievements, streaks, and verified online trophies.
- **Share the flex:** recap cards exported as PNGs.
- **Focused UI (1.6.5):** Goob, today's score and the hourly chart on Today; monthly scores, trophies and Wrapped in Stats. League opens with a top-three podium and numbered table, with your position pinned below. Account controls have their own sheet. Space Grotesk titles/totals and DM Sans body text are bundled offline; navigation uses outline icons. Motion is brief, with no perpetual Goob bounce. See [UI details](UI-EXPERIENCE.md) and [font licenses](licenses/fonts/README.md).

### Doom League

The Firebase integration supports monthly global competition on the **Spark free plan**:

- Browse public participants in pages of **50**, your exact rank through **#200**, and your top-percentile position below that.
- Optional **Google sign-in**, or a guest identity with a unique chosen username. Browsing and local counting do not require sign-in.
- Optional Instagram handles; Google emails, names and photos are never copied into leaderboard records.
- Previous month's **#1, #2 and #3** featured with Instagram links during UTC days **1–7**.
- Profile reporting, blocking, hiding, and account deletion.
- Encrypted session storage and validated, owner-scoped database access rules.

**Live setup is required:** enable the authentication providers, create Firestore, deploy the included rules/indexes, and exercise real sign-in before inviting users. See [Firebase setup](firebase/README.md). No paid Cloud Functions are used. Spark quotas limit capacity; batched uploads and paginated reads reduce usage but do not make it unlimited.

Guest identities are tied to the installation until linked to Google. A username alone cannot recover an account. Google sign-in alone does not publish a profile. Only users who choose a public profile appear on the board. League months use **UTC**; local daily stats use the phone's calendar. The older Supabase implementation remains available as a legacy backend.

## Brainrot Trophy Cabinet

| Badge | Unlock |
| --- | --- |
| One More Then I Sleep | 100 unique reels |
| For You? For Me. | 1,000 unique reels |
| Final Boss of Brainrot | 10,000 unique reels |
| Bed Rot Any% | 500 unique reels in one day |
| Chronically Online | Scroll 7 days in a row |
| Bro Got Outscrolled | Win your first completed Battle |
| Unemployed Behaviour | Win 5 completed Battles in a row |
| Touch Grass Is a Threat | Reach the global top 10 |

Online badges require verified server results. Battle-win badges stay locked until a trusted completed-match finalizer is connected; leading the live friend comparison does not award a win. See [TROPHIES.md](TROPHIES.md) for uniqueness and retention rules.

## Installing a private build

Version **1.6.5** adds the focused Today/Stats layout and sports-style League table. It retains the shared fixes from **1.6.4**, which broaden YouTube Shorts recognition across explicit, nested and virtual caption layouts, prioritizes visible content in large hierarchies, and resolves the focused feed when Goob becomes the active window. Settings includes an optional privacy-preserving counter check for unsupported layouts. The non-debuggable, optimized installation candidate is signed with the existing protected release key. Build it with `tools/build_sideload.ps1` after configuring your own signing key. This requires no screen sharing and does not disable phone security checks. The production `release` variant continues to require the live backend and public-launch checks. See [Shorts verification](SHORTS-COMPATIBILITY.md), [installation findings](INSTALLATION.md) and [launch status](LAUNCH.md).

## How counting works

```text
Selected app's accessibility hierarchy
                  ↓
Recognize a reel in the app window and exposed metadata
                  ↓
Exclude recognized ads, comments and unstable transitions
                  ↓
Require 750 ms of stable visibility
                  ↓
Suppress loops and recently seen identifiers
                  ↓
Save local totals → update stats, Goob and trophies
```

YouTube detection selects the active Shorts page rather than decorative player layers. It supports explicit caption IDs, nested virtual captions and qualified creator/caption groups without panel IDs, ignores playback/subscription/audio controls when identifying a Short, and reads sibling metadata outside letterboxed video bounds. Goob remains visible in a recognized Shorts feed even when a caption is temporarily unavailable. A 900 ms presentation grace period also covers brief layout gaps; counting still requires a fresh uninterrupted 750 ms readable view.

The counter reads exposed accessibility information from selected apps. It hashes identifiers with a per-install salt and keeps a **five-minute recent-rewatch window**. It does not count generic swipes or estimate reels from app usage time. Trophy uniqueness uses separately retained, bounded fingerprint sets.

**Accuracy depends on what other apps expose.** Identical or changing metadata, app updates, languages, and missing ad labels can affect recognition. “Unique” means a distinct metadata fingerprint, not a guaranteed unique underlying video. Not every ad or rewatch can be identified. TikTok and Snapchat support is beta.

## UPI compatibility

Some payment apps block enabled accessibility services even when counting is paused. Version **1.5.1** scopes event subscriptions to selected reel apps, makes floating overlays opt-in, and adds **Settings → Disconnect counter for payments**. This turns off the accessibility service through Android and keeps your data. Re-enable it in Android Accessibility to resume counting. Real BHIM compatibility still needs verification on the phone. See [UPI-COMPATIBILITY.md](UPI-COMPATIBILITY.md).

## Native islands and live counters

Version **1.4.1** requests an Android 16 **Live Update** containing the count and Goob icon during an active reel session. Enable **Settings → Native island / Live Update**, allow notifications, and enable the phone's Live Alerts/Live Updates setting for DoomScore if available.

The phone controls promotion, placement, icon colours, and animation. Support is **not guaranteed for every built-in island**, and Android/manufacturer eligibility rules may exclude a passive reel counter. Actual native-island placement on OnePlus and POCO phones remains unconfirmed. Version **1.5.2** adds phone setup diagnostics, notification-promotion settings, recovery after service binding/resume and temporary unreadable screens, and app-window-aware feed detection. See [phone compatibility](DEVICE-COMPATIBILITY.md). Earlier Android versions receive a regular silent notification.

The **Floating Goob pill** is a separate accessibility overlay. Disable **On-screen Goob counter** when testing native placement to avoid a duplicate pill. Notifications are optional and independent of counting. See [NATIVE-ISLAND.md](NATIVE-ISLAND.md) and [Android's Live Updates documentation](https://developer.android.com/develop/ui/views/notifications/live-update).

## Build locally

| Requirement | Version |
| --- | --- |
| Minimum Android | Android 8.0 / API 26 |
| Compile / target SDK | 36.1 / 36 |
| JDK | 21; Java compilation targets 17 |
| Gradle | 9.2.1, included wrapper |
| Android Gradle Plugin | 9.0.1 |
| Kotlin / R8 | 2.4.20 / 9.1.56 |

1. Clone this repository and open its root folder in Android Studio.
2. Install the required SDK and select Android Studio's bundled JDK.
3. Copy `local.properties.example` to `local.properties` and set your SDK path. Android Studio can also create the SDK entry.
4. Build and check the app:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat` with the same tasks. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

For the signed, optimized production build, first configure the signing key and public release details described in [LAUNCH.md](LAUNCH.md), then run:

```bash
./gradlew :app:assembleRelease :app:bundleRelease :app:lintRelease
```

App name: **DoomScore**. Application ID: **`com.gridcc.doomscore.android`**. APK: `app/build/outputs/apk/release/app-release.apk`. Play Store bundle: `app/build/outputs/bundle/release/app-release.aab`. APKs and signing keys are excluded from this source repository.

## Start counting on your phone

1. Install your built APK and open DoomScore.
2. Read and accept the accessibility disclosure.
3. Enable **Doomscore reel counter** in Android Accessibility settings.
4. Open the full-screen Instagram Reels viewer and scroll.
5. Optionally enable a floating counter, native Live Update, widget, or Quick Settings tile.

Android may restrict accessibility for sideloaded APKs. If **Allow restricted settings** is available in the app's system App info menu, confirm it and return to Accessibility. The flow depends on the phone and Android version.

## Backend setup

Local counting needs no backend. For the current Google sign-in and leaderboard, follow [Firebase setup](firebase/README.md). The following is the legacy Supabase setup; review [backend/README.md](backend/README.md), [LEAGUE.md](LEAGUE.md), and [SECURITY.md](SECURITY.md) before applying migrations or deploying the ingest function.

- Configure an **HTTPS** Supabase URL and **publishable client key** in ignored `local.properties` or the documented environment variables.
- Keep service-role keys, CAPTCHA secrets, and signing passwords on the server or in protected local configuration.
- Deploy migrations, the ingest function, and the hosted CAPTCHA page to a staging project first.
- Verify ownership, row-level access, rate limits, CAPTCHA, moderation, and deletion before enabling online release flags.

Cloning or building does not deploy a live database. This Android app was originally developed as a counterpart to [berlinflix/doomscore](https://github.com/berlinflix/doomscore); its prepared backend contracts support coordination with that iOS project.

## Privacy and security

- No screenshots, screen capture, OCR, microphone, or camera are used for counting.
- Counting stays local unless the user opts into a configured online feature. Raw captions and trophy fingerprints are not uploaded.
- Android Keystore protects auth/device credentials; counting history lives in the app's private storage.
- HTTPS is enforced and app backups are disabled.
- Firestore rules scope record access, allow only approved fields, reserve usernames atomically, and bound aggregate uploads. Counts still originate on the phone; rules cannot prove someone actually watched a reel.
- Firebase Authentication may retain private Google identity metadata. DoomScore does not copy that metadata into Firestore or public records.
- Production requires verified abuse protection; App Check enforcement is not yet integrated in this build.
- Private configuration, signing keys, generated builds, device verification data, and dependency folders are excluded from Git.

Accessibility can expose sensitive screen information. The app provides a prominent disclosure and limits tracking to selected supported apps. Review the included policy template and complete the Google Play accessibility declaration before public distribution.

## Verification and release status

The previous **1.4.1** build passed **51 JVM unit tests** and **4 notification integration tests**, including promotable notification characteristics, real dismissal actions, session cancellation, and mascot colour changes. Lint reported **0 errors and 67 warnings**; APK signing and 16 KB native alignment checks passed. These checks do not certify universal device compatibility or store approval. Version **1.5.0** adds Firebase Google sign-in and a paginated monthly leaderboard. It passes 51 JVM checks, 16 Firestore rule checks and 6 isolated emulator checks, with zero lint errors and 71 warnings; see [the validation record](firebase/VALIDATION.md). Live sign-in, production rule deployment, abuse protection and device acceptance must be verified separately; building the app does not complete that setup.

Isolated PostgreSQL/security tests (Node 24):

```bash
npm ci --prefix backend/tests --ignore-scripts
npm test --prefix backend/tests
```

Android instrumented tests and the scripts in `tools/` are designed for an isolated emulator. **`feed-fixture` deliberately uses Instagram's package name to provide synthetic reels: never distribute it or install it on a phone with real Instagram.**

Production signing, public legal/support details, hosted backend verification, completed Battle finalization, and physical-device acceptance still need completion. [LAUNCH.md](LAUNCH.md) lists release gates; [VALIDATION.md](VALIDATION.md) records earlier validation runs.

## Project map

| Path | Purpose |
| --- | --- |
| `app/` | Android app, accessibility service, Compose UI, local storage, tests |
| `firebase/` | Spark-compatible auth/leaderboard rules, indexes and verification |
| `backend/` | Supabase migrations, ingest function, CAPTCHA page, database tests |
| `feed-fixture/` | Emulator-only synthetic reel feed |
| `gradle/` | Pinned Gradle wrapper |
| `release/` | Store graphics, listing draft, legal templates, acceptance checklist |
| `tools/` | APK verification, dependency scanning, emulator checks, release helpers |

More details: [Competition](COMPETITION.md) · [Trophies](TROPHIES.md) · [League](LEAGUE.md) · [UI](UI-EXPERIENCE.md) · [Security](SECURITY.md).
