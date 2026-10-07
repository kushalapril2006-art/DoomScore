# Security and deployment notes

## Android protections

- Only HTTPS backend origins and `sb_publishable_…` client keys are accepted. Server/secret keys are rejected at build time. Public configuration comes from ignored `local.properties` or environment variables; it remains visible in a compiled APK by design. Authorization depends on database policies, not hiding this key.
- Auth sessions, scoped device credentials and profile caches use AES-GCM with a non-exportable Android Keystore key. Storage-field names are authenticated, so swapping ciphertext between fields fails. Malformed/tampered entries are discarded. Legacy credentials migrate on read. Account/device backups are excluded.
- Counts live in app-private SQLite storage. SQL values are bound parameters; selectable column names come from a closed enum. Captions, screen images and raw reel identifiers are not persisted. Recent identifiers are salted SHA-256 hashes. Aggregate statistics are protected by Android's application sandbox/device storage encryption, rather than a separate encrypted SQLite database. Rooted-device tampering is outside this protection.
- Handles, names, avatars, goals, UUIDs and invites are validated. Deep links accept only the owned invite formats. Server JSON is bounded to 256 KiB (8 KiB for errors), UTF-8 checked and depth limited. Requests are bounded to 8 KiB; redirects, cleartext traffic and unexpected content types are rejected. Stored/queried responses contain only required fields.
- Sign-out stops new synchronization immediately, attempts server credential revocation, then clears local credentials even offline. If offline, server revocation cannot be guaranteed; delete the account or revoke its devices server-side when connectivity returns.
- Online operations serialize access to tokens; late responses cannot restore an account after disconnect. Local counting does not require a login or CAPTCHA.

## Server protections prepared, not deployed

`backend/supabase/migrations/20261007000000_security_hardening.sql` applies after the original schema. It enables row-level security, limits editable profile columns, makes ownership immutable, prevents direct client statistic/credential writes, validates ingest ownership and revocation at the write boundary, rejects duplicate apps/protected payload fields, and persists failed invite-rate-limit attempts. Successful invite/profile contracts remain compatible with the reference iOS app.

The prepared ingestion Edge Function accepts only scoped device credentials; the service-role key is read from Supabase's server environment. It bounds request bodies, validates every accepted payload field, pins its SDK version, and returns short allowlisted errors. Counts remain client-reported: rate/anomaly limits do not provide cryptographic proof of viewing or complete anti-cheat protection.

These files have been tested in an isolated PostgreSQL engine with the original schema. No live migration, remote account creation or deployment was performed. A Supabase administrator must apply the migration and deploy the function before the added server protections are active. See `backend/README.md`.

## Bot protection activation

The optional battle-signup challenge is prepared but unconfigured. Enable Turnstile in Supabase Auth, host `backend/web` over HTTPS, replace its public site-key placeholder, and set `captcha.url` before rebuilding. The private Turnstile secret belongs only in the Supabase dashboard. Server enforcement is required: a client-only challenge does not block direct signup requests.

The native challenge uses a main-frame message port restricted to the configured HTTPS origin/path. It exposes no JavaScript interface to account data, rejects SSL errors and outside navigation, disables local file/content access and mixed content, and keeps challenge tokens only in memory. The hosted page must use the supplied security headers (adapt `_headers` to your hosting provider).

Enabling Supabase CAPTCHA also affects iOS anonymous sign-ups. Coordinate deployment with the iOS team and add their token flow before activation. Existing sessions and Android's local reel counting do not depend on this challenge.

## Dependency checking

`tools/dependency-inventory.gradle` exports resolved Android runtime, test and build-tool dependencies. `tools/scan_dependencies.py` checks exact Maven versions and the backend test/SDK lock against OSV using only public package names/versions. The original scan found six affected build packages; patched versions are configured in the root build file. See `VALIDATION.md` and the generated report for the final scan result. No-advisory results are not a guarantee against unknown vulnerabilities. The hosted Turnstile script and Android's system WebView update independently of this lock.

Release builds need your team's signing key and real-device acceptance testing. This debug APK is a test build. Never distribute `feed-fixture`, which uses Instagram's package name solely inside an isolated emulator.

## Release preparation

Production builds are non-debuggable, use code/resource shrinking, and reject incomplete signing/public legal configuration. The patched Kotlin toolchain uses a compatible R8 optimizer. Online Battle is disabled by default in production and always disabled in the separate optimized `releaseCheck` test variant. It cannot be activated for production without explicit backend and moderation verification settings. Those settings are not proof of deployed policies or completed moderation work.

A new local upload key is in `private-signing/`, with a Windows-user-protected password and restricted file ACLs. It is not an APK/API secret and is not packaged in source exports. Back up the key/password securely; encrypted Windows credentials alone are not a portable backup. See `LAUNCH.md` for publication blockers and signing continuity. No public release, live backend deployment or store submission was performed.

## Global league

League profiles and scores use separate tables with RLS and no direct client-table grants. Public functions expose only the bounded top 50, a three-person podium and the caller's own rank/percentage. Username ownership comes from the authenticated anonymous UUID, never a name supplied as an owner. Parameterized RPC bodies, strict field/date/count validation, atomic username uniqueness, monotonic totals, growth clamps and upload/profile rate limits protect records. Public Instagram handles are optional, validated, self-reported links; they do not prove ownership.

Profile drafts and identity credentials are encrypted with the existing Keystore vault; new UTC counters remain inside private SQLite storage. Reports/blocks stay private, profile suspension is server-only, and account deletion cascades through league records. Online League is disabled until hosted acceptance, Auth/CAPTCHA and configuration are complete. Production also requires community-terms/deletion URLs and moderator coverage verification. Anonymous signup protection must be enforced on Supabase, not merely displayed by the client. Unique usernames cannot prove unique humans, and client-originated score bounds cannot fully prevent a modified client from fabricating plausible counts.
