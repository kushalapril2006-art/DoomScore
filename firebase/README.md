# Firebase leaderboard and Google sign-in

This implementation uses Firebase Authentication REST and Cloud Firestore REST with Security Rules. It runs within Spark quotas and does not deploy Cloud Functions or change billing.

## Console setup

1. Open your Firebase project. In **Authentication → Sign-in method**, enable **Google**, choose a support email, and save. Enable **Anonymous** if users should join with a guest identity.
2. In **Project settings → General → Android app**, register package `com.gridcc.doomscore.android`. Register the APK's SHA-1 signing certificate. For Play distribution, also register the Play app-signing certificate; debug and release certificates differ.
3. Download the refreshed `google-services.json` to `app/google-services.json`. It is ignored by Git. The build reads only public client configuration; never put service-account private keys or admin credentials in the app.
4. In **Build → Firestore Database**, create a **Standard edition** database in **production mode**. Choose the location carefully. Keep the locked initial rules until the supplied rules have been deployed.
5. Sign into the Firebase CLI locally using `firebase login`. From this directory run `firebase deploy --only firestore:rules,firestore:indexes --project doomscore-cd386`. This requires your project-owner/deployer authorization. Do not send account tokens, passwords, or private keys in chat.
6. Wait for indexes to finish building. Install the matching APK and exercise Google sign-in, public profile creation, guest linking, two different accounts, hiding, blocking, reporting, cross-device score sync, month rollover and account deletion. Console configuration alone does not verify the complete flow.

The bundled `.firebaserc` points to the original project; use an explicit project flag for your own deployment. Do not loosen rules to make permission errors disappear.

## Data boundaries

- `profiles/{uid}`: private chosen username, optional self-reported Instagram handle, emoji, visibility and update time.
- `usernames/{handle}`: atomically reserved handle mapped to an opaque Firebase UID. Direct lookup is public; listing is denied.
- `seasons/{UTC-month}/entries/{uid}`: opted-in public UID, chosen username, Instagram handle, emoji, monthly count, random contribution ID and update time. Hidden rows are owner-only.
- `contributions/{uid}/devices/{random-id_month}`: private device monthly aggregate. Updates must accompany score deltas. These contain no raw reel identifiers.
- `blocks/{uid}/items`, `reports/{uid}/items`, `limits/{uid}`: private owner-scoped blocking, moderation and rate-limit records.

Google email, name, photo, Google access tokens and raw provider responses are not stored in Firestore or public profiles. Firebase's private **Authentication service can retain Google account metadata** as part of sign-in. Therefore this app does not promise that Google information is absent from Firebase Authentication itself. On the phone, only the UID, Firebase session tokens, expiration and guest status are retained in Keystore-encrypted storage.

First-time joining includes the current local UTC month's counts. Score uploads are batched approximately every five minutes during tracking, with one-minute foreground refresh intervals. Additional phones contribute monotonic per-install totals. Switching account does not copy an existing account's historical total. Signing out keeps published records; account deletion removes them. Guests must link Google before signing out to preserve recovery.

Public profile edits update historic links as well. A profile with more than 60 season records requires support-assisted edits; deletion scans all records in bounded batches. Interrupted deletion is retryable while the identity remains available. Firebase may require a recent Google authentication before deleting its Auth account.

## Verification

With Node 24, JDK 21 and the Firebase CLI installed:

```sh
npm ci --prefix verification --ignore-scripts
firebase emulators:exec --only firestore --project demo-doomscore "node verification/rules.mjs"
```

This uses a demo project and cannot mutate the production database. Android instrumented privacy checks run only on the isolated `DoomscoreVerified` emulator.

## Limits and production requirements

Spark has finite daily reads/writes and storage quotas. Exact ranks use aggregation queries; queries and rule checks can consume reads. Do not add unbounded real-time listeners or treat Spark as unlimited capacity. Monthly top three are read from the previous season during days 1–7; owners can still hide or delete their records. These are not immutable administrator-issued awards.

Schema validation, ownership, atomic contribution checks and time bounds stop several direct tampering attempts. They **cannot prove real viewing**; a modified client can submit plausible false counts. This version has no App Check token integration or enforcement. Production requires appropriate abuse protection, provider/API restrictions, monitoring, moderation, verified deletion, and real-device Google sign-in. Configure `firebase.backendVerified=true` and `firebase.abuseProtectionVerified=true` in ignored local configuration only after completing those checks. The production build gate also requires public terms/deletion URLs and existing legal/signing details.

Email/password sign-up and email-verification screens are not implemented in this version. The legacy friend Battle finalizer and trusted top-ten trophy proofs are also not connected to this Firebase backend.
