# Firebase integration validation — 2026-10-08

Version 1.5.0, code 8, package `com.gridcc.doomscore.android`.

- Final debug APK and Android instrumentation APK compile successfully.
- 51 JVM checks pass, zero failures.
- Android lint: zero errors, 71 warnings. Warnings remain and are not a clean-lint certification.
- 16 Firestore security checks pass using Firebase 13.0.0 and rules-unit-testing 6.0.0 in an isolated demo database. These cover real `google.com` provider claims, guest access, unique usernames, field/owner tampering, hidden rows, atomic score contributions, historic season totals, reporting limits, deletion and contributions after a local reset.
- Resolved Android/legacy verification dependency scan: 265 exact versions, zero OSV advisories found. Firebase verification npm audit: zero advisories after pinning patched grpc-js 1.14.6.
- Public-source scan excludes client configuration and private signing material and detects no matching credential literals.
- 6 isolated Android instrumentation checks pass: Google/provider metadata is discarded, malformed account IDs are rejected, session storage is encrypted, and the 4 live notification checks pass.

Not yet verified: live Google sign-in/guest linking, production Firestore creation and rules/index deployment, cross-device live sync, production abuse protection, account deletion with a real provider, manufacturer native-island placement or Google Play approval. A successful local build/rules check does not establish these outcomes.
