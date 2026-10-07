# Prepared backend security update

The global monthly Doom League is implemented in `20261007010000_global_league.sql`. It uses independent opt-in tables and preserves the existing iOS friend-only profile rules. See `../LEAGUE.md` for deployment, UTC timing, no-email/password identities, moderation and the iOS RPC contract. This backend is prepared locally; no live migration or hosted Auth/CAPTCHA changes were made.

No live backend changes were made. Apply these files through an authorized Supabase administrator after reviewing them with the iOS team. Do not put an administrator token or service-role key in the Android project, source archive, or chat.

1. Back up the existing database. Confirm the original `20260930000000_doomscore_init.sql` migration is present; it is included for a new local setup and must not be reapplied to a populated project.
2. Link the Supabase CLI to the team's project using its normal authenticated workflow. Review `supabase db push --dry-run`, then apply the pending security migration with `supabase db push`. Alternatively, execute only the new migration in the dashboard SQL editor. The transaction includes permission changes and replacement RPCs; failed deployment rolls back.
3. Deploy the prepared multi-file handler from this backend directory with `supabase functions deploy ingest`. Its `verify_jwt=false` setting is intentional: the handler validates its scoped device token and the database rechecks its owner/revocation. Keep the service-role key in the server's environment. Retain any existing server-only APNs secrets for iOS compatibility.
4. Verify own-profile upserts, leaderboards, a valid invite, a failed invite, revocation and bounded aggregate upload in a staging project before production. The local tests prove PostgreSQL rules, but do not prove deployed PostgREST/custom-header or Edge Function behavior.
5. For signup bot protection, create a Turnstile widget restricted to the chosen hosting hostname. Replace `REPLACE_WITH_PUBLIC_TURNSTILE_SITE_KEY` in `web/battle-challenge.html`, host the three page assets over HTTPS, and apply `web/_headers` or equivalent provider headers. Use a dedicated path without redirects. Verify current Turnstile WebView support on real devices.
6. Set `captcha.url=https://YOUR_DOMAIN/battle-challenge.html` in ignored Android `local.properties`, rebuild, and add the equivalent CAPTCHA-token flow to iOS. Enable Turnstile protection under Supabase Authentication settings using the private secret there only. Test fresh sign-ups from both apps. An unset Android challenge URL cannot satisfy server-enforced CAPTCHA; local counting remains available.

## Isolated database and payload tests

Requires Node 24 (native TypeScript support):

```
npm ci --prefix tests --ignore-scripts
npm test --prefix tests
```

Tests use PGlite's real PostgreSQL with pgcrypto/citext, the original schema, and the new migration. Authentication identities are synthetic; no live Supabase credentials or records are used. The npm lock also records the pinned Supabase SDK's transitive dependencies for scanning. Generate/review the deployment platform's lock if using a different Deno/CLI version; the Edge Function itself pins the SDK to `2.117.2`.

Documentation: [public versus secret keys](https://supabase.com/docs/guides/getting-started/api-keys), [CAPTCHA setup](https://supabase.com/docs/guides/auth/auth-captcha), [column permissions](https://supabase.com/docs/guides/database/postgres/column-level-security), [PostgREST transactions](https://docs.postgrest.org/en/v12/references/transactions.html).
