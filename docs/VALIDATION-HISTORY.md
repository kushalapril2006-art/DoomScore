# Historical validation records

These records describe earlier app versions and retired build identities. They do not describe the current production package. Original evidence follows.

# Competitive Doomscore 1.3 / UI experience validation — 7 October 2026

- 90 automated tests pass: 50 JVM, 24 isolated PostgreSQL/payload and 16 Android integration tests. New cases cover rank thresholds, no penalty at high counts, active-day streaks/gaps/future dates, retired settings and notification permission, actual personal-best aggregation, and a padded year of batched counts/durations/ad/rewatch/hour bins. Existing real Accessibility, Keystore, database upgrade and earned-trophy UI cases still pass.
- 45 optimized-build UI checks pass: 18 competition/UX checks, 15 counter regressions and 12 profile/league checks. They exercise in-place score preservation, the competition copy and no cap controls, recap Close/Share, year loading, retained Stats period/scroll, Back-to-Today, large fonts, system animations disabled, profile keyboard/save/invalid inputs/restart/deletion, and real synthetic organic/rewatch/ad/pause/recap flows. No real phone, Instagram users or live backend was modified.
- History uses two indexed range queries instead of 730 for a 365-day view; the monthly dashboard uses one aggregate query. Screens load database snapshots on a background dispatcher. Visible UI sampling does not alter real counter events. Short transitions and a continuous, lifecycle-aware mascot loop use native Compose animation timing. These structural changes and emulator acceptance are verified; no real-device FPS guarantee is claimed.
- Debug/optimized APK/AAB builds, lint (0 errors, 65 warnings), signature/HTTPS/backup/permission/16 KB binary alignment and official bundletool validation pass. Notification permission is absent. Dependencies are unchanged from the previous 238-version advisory scan.
- Version 1.3.0/code 4 APK SHA-256: `c61b77f64e84d32480e7f28ac0febfbfb2abab5499c82565668a4af9768d342f`. It upgrades the existing release-test identity while preserving counts and trophies. Old cap/reminder settings and the old notification/channel are removed. Public/global and completed-Battle deployment limitations remain unchanged; see COMPETITION.md, UI-EXPERIENCE.md, TROPHIES.md and LAUNCH.md.

Reports: `test-results/competition-validation.json`, `competition-ui-smoke.json`, `competition-counter-smoke.json`, `competition-league-smoke.json`, `competition-apk-verification.json`, `competition-android-tests.log`, `competition-backend-tests.log`, `competition-ux-build.log` and `competition-bundle-validation.log`. Previews are synthetic app states, not real phone or Instagram captures.

## Previous version 1.2 baseline

# Brainrot Trophy Cabinet 1.2 validation — 7 October 2026

- 84 automated tests pass: 47 JVM, 24 isolated PostgreSQL/payload and 13 Android integration tests. New cases cover exact unlock thresholds, permanent first-earned dates, lifetime rewatch suppression after recent expiry/reopening, per-app uniqueness, daily cap/bounded retention, seven-calendar-day streak gaps, upgrade preservation, immutable completed results and private online proof ownership.
- 29 optimized-build UI flows pass: 14 cabinet checks and 15 counter regressions. All eight names/rules, Today/Stats entry, locked states, restart persistence, accessible large-font dismissal and the existing organic/rewatch/ad/pause/recap checks pass. A separate Android test unlocks the first badge through 100 qualified synthetic views and captures its earned state. No physical phone, real Instagram or live/public backend was modified.
- Debug and optimized non-debuggable APK/AAB builds, lint (0 errors, 61 warnings), APK integrity/signature/HTTPS/backup/permissions/16 KB binary alignment and official bundletool validation pass. Dependencies are unchanged from the prior 238-version advisory scan. Emulator checks were run sequentially to limit PC memory use.
- Release-test APK SHA-256: `c20595d6dea0de1172c6011eefecd9eba7b3cf7b3514376e967f5509b489fcc2`. Version 1.2.0/code 3 upgrades the existing release-test identity; the newly named trophy APK avoids confusing older same-name exports.
- Five local trophies work without an account. Three online trophies require verified results and remain locked in this offline release-test build. Battle has no completed-match finalizer yet; its two win badges are deliberately not inferred from a live board. The new trophy migration is prepared/tested, not deployed. Unique milestones start at this update because older aggregate counts cannot establish lifetime uniqueness; exposed metadata hashes have the recognition limitations documented in `TROPHIES.md`.

Reports: `test-results/trophy-validation.json`, `trophy-ui-smoke.json`, `trophy-counter-smoke.json`, `trophy-apk-verification.json`, `trophy-android-tests.log`, `trophy-backend-tests.log`, `trophy-final-build.log` and `trophy-bundle-validation.log`. Screenshots: `trophy-earned.png` (synthetic earned example) and `trophy-cabinet.png` (locked initial state).

## Previous version 1.1 baseline

# Doom League 1.1 validation — 7 October 2026

- 70 automated tests pass: 42 JVM, 20 isolated PostgreSQL/payload, and 8 Android integration tests. New cases cover #200/percentile boundaries, uniqueness and owner isolation, all-month aggregation, frozen podium timing, private reporting/blocking, deletion, SQLite upgrade preservation and UTC counter isolation.
- 27 optimized-build UI flows pass: 12 new league/profile/calendar-month checks and the 15 existing counter checks. Separate Instagram names, invalid-input disabling, encrypted restart persistence, deletion and large-font dismissal were exercised on a synthetic emulator. No real Instagram, public users or live server writes were used.
- Debug APK, optimized release-check APK/AAB, zero-error lint (59 warnings), APK signature/permissions/backup/HTTPS/16 KB alignment and bundletool validation pass. All dependency versions are unchanged from the prior 238-version advisory scan.
- The PC ran low on memory during concurrent verification; build background memory was freed, the synthetic emulator restarted with a lower memory limit, and interrupted checks passed sequentially. Source and private signing material were preserved.
- New APK SHA-256: `0297a7a32db4e04fdbf344cb086c8e179f463c4ea9b23db983e08faa2ee1bc1b`. The connected OnePlus previously installed/opened version 1.0; version 1.1 has not been installed on that real phone because it disconnected.
- Online League is intentionally disabled pending actual backend deployment/Auth/CAPTCHA and hosted acceptance. Usernames saved in this APK are local drafts, not globally reserved. See `LEAGUE.md` for exact UTC/historical-count limitations and the deployment/iOS contract.

Reports: `test-results/league-validation.json`, `league-ui-smoke.json`, `league-counter-smoke.json`, `league-apk-verification.json`, `league-backend-tests.log`, `league-store-tests.log`, `league-regression-tests.log`, `league-final-build.log` and `league-bundle-validation.log`.

## Previous version 1.0 recovery baseline

# Validation and crash recovery — 7 October 2026

The original source files/XML and Gradle wrapper were intact. Damaged generated Gradle caches/resources were preserved with crash-backup names and rebuilt. The interrupted test emulator was preserved, and validation used an isolated `DoomscoreVerified` emulator. The supplied iOS repository remains unchanged.

## Completed release checks

- Final debug and optimized, non-debuggable release-check APK/AAB builds succeed. The production build uses code/resource shrinking. Kotlin 2.4.20 and R8 9.1.56 are compatible; the earlier metadata-parser warnings were eliminated.
- 38 JVM tests pass: 13 detector, 10 engine, 11 input/network security and 4 time/streak cases.
- 11 isolated PostgreSQL/payload tests pass against the original schema and prepared security migration. Record visibility, ownership substitution, protected columns, failed-invite rate limits, token revocation, payload validation and request limits are covered.
- 6 final Android integration tests pass: real Accessibility callbacks; three Keystore encryption/tamper/migration cases; WebView/native challenge messaging; and actual SQLite expiry cleanup on reopening the app.
- 15 black-box checks pass on the optimized release-check app: privacy before consent, disabled online features, declining consent, first reel/loops, new reels, recent rewatches, sponsored items, comments/ordinary feeds, immediate pause-switch updates, paused counting, resume, process-restart persistence, statistics, recap PNG/FileProvider chooser, and large-font dialog dismissal. No external recipient was selected and no message/recap was sent.
- APK binary checks pass: not debuggable, backups and cleartext traffic disabled, expected restricted permissions, v2 signature, archive integrity, ZIP alignment and all 64-bit native LOAD segments aligned to at least 16 KB. No recording/sharing, usage-access or separate overlay permission is requested.
- Google's official checksum-verified bundletool validates the final test app bundle.
- Release lint reports 0 errors and 54 warnings. Warnings include optional Kotlin style helpers, untranslated strings, newer library versions and cursor `.use` cleanup that lint does not recognize.
- OSV checks 238 exact resolved Maven/npm versions and reports 0 affected packages after patching the original six build dependencies. This covers known advisories, not unknown vulnerabilities or a full supply-chain audit.
- Visual review confirms native store icon/feature graphic dimensions, readable system bars, synthetic counter UI and large-font controls. The launcher uses an adaptive icon.
- Production configuration checks reject a missing developer identity, public contact and hosted HTTPS Android privacy policy. With the protected signing password loaded, the signing inputs pass; public legal fields still block publication. No placeholder policy URL was substituted.

## Privacy and signing

The service ignores even event metadata before consent and while paused, and does not inspect unselected apps' hierarchies. Credentials/profile caches use field-bound Keystore AES-GCM. Statistics contain aggregates and salted recent hashes, with expired hashes pruned during use and on opening. Captions/images are not persisted or uploaded. Privacy information is available from onboarding and settings, and consent is scrollable on small screens. Recap encoding runs off the main thread and failed sharing/deletion is reported.

A separate RSA-4096 upload key was created locally. Its password uses Windows user protection and private-file ACLs; private material is excluded from source artifacts. Back up the key and password securely before publication. The release-check APK/AAB use a debug certificate and a separate package ending `.releasecheck`; they are test artifacts and must not be uploaded as the production app.

## Public-launch blockers

Public developer/support details and a hosted approved Android privacy page are not configured. Google Play declarations/Accessibility review, account-specific testing requirements and a pre-launch report have not been completed. Physical-device/current-Instagram tests, the declared Android 8 minimum and an actual 16 KB runtime still need acceptance testing. Binary alignment checks alone do not replace that device testing.

No live backend migration, deployment, account creation or remote data modification was performed. The default production/test-release variant removes Battle and its backend configuration. Launching Battle requires deployed security/CAPTCHA, staging acceptance, public terms/deletion pages, terms acceptance and reporting/blocking/moderation work; see `LAUNCH.md`. Debug development flows are not certified for live production use.

The synthetic fixture reproduces IDs/labels and exercises actual Android Accessibility; it cannot prove compatibility with every Instagram release. Recognition depends on exposed metadata and visible ad labels. Identical metadata, unlabelled ads, app updates or localization can affect counts. Recent-rewatch suppression uses a five-minute window. YouTube needs current-release validation; TikTok/Snapchat remain experimental. Passing checks cover stated scenarios and do not promise zero bugs or Play approval.
