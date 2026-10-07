# Global Doom League

The 1.2 trophy update additionally requires `20261007020000_trophy_cabinet.sql` before deploying the updated online client. See `TROPHIES.md` for private rank/win proofs and the separate completed-Battle finalizer requirement.

Implemented: monthly global top 50, own exact rank through #200, ceil(rank / ranked-participants × 100) below that, an optional public Instagram link, private anonymous identity and server-owned username uniqueness. Nonzero, visible profiles make up the ranked population. Ties use total descending then stable internal UUID ascending. Public responses never include internal IDs, email addresses, tokens or daily history.

The no-login flow creates an anonymous Supabase identity only when a person chooses a username and agrees to publish their profile and score. It requests no email or password. Credentials remain encrypted with Android Keystore. Usernames are labels, not authentication secrets; typing one cannot claim or recover another account. One installation may correspond to one identity, but unique usernames cannot prove one human has only one identity. Reinstalling loses access unless an optional recovery mechanism is added later.

## Calendar and counts

Today and Stats show the complete device-local calendar month, from its first day through today, including counts recorded before this update. The global season uses UTC for consistent worldwide boundaries. Database v2 adds an atomic UTC daily reel counter without deleting or rewriting existing local history. Exact UTC league recording starts with this update; older local-day/hour aggregates cannot be converted to exact UTC timestamps and are not presented as exact league scores. There are no synthetic users or fabricated public scores in the application.

The client uploads every nonzero UTC day in the current month in a single bounded request, at most once a minute during use and on foreground/refresh. Offline current-month scores catch up when connected. Server validation ignores no fields: extra keys, duplicate dates, invalid dates, fractional counts, future dates, other months, direct writes and substituted owners are rejected. Values stay monotonic and plausible growth is clamped. Repeated submissions within 30 seconds are rejected. These checks deter accidental/obvious inflation; they cannot prove that a modified client actually watched videos. Play Integrity verification and operational monitoring are recommended before offering prizes.

At 00:00 UTC on the first, the closed month stops accepting submissions. A snapshot of its top three is finalized on the first board request (or by the optional scheduler below). It stays frozen against late edits to score totals and appears until 00:00 UTC on the 8th. Instagram link edits take effect on podium links; hidden or deleted profiles disappear from public display. Deleted podium positions are not reassigned. Users offline across rollover can lose unsynced closed-month competition points; their local history remains intact.

## Deployment needed before real global rankings

1. Review with the iOS/backend owner and apply `backend/supabase/migrations/20261007010000_global_league.sql` after the original and security migrations in a staging project. This change is independent of friend-profile tables and does not expose existing iOS profiles globally. Do not reapply an existing original migration.
2. Enable anonymous sign-ins in Supabase Auth and deploy the existing CAPTCHA page/provider setup described in `backend/README.md`. Server enforcement is required; adding only a challenge page is insufficient. Leave administrator keys on the server. Client configuration accepts only the public publishable key over HTTPS.
3. Test real PostgREST calls with two anonymous identities: unique-name conflicts, private profile lookup, public top 50, own rank, month score upload, hide/edit/delete, and blocked direct writes. Local PostgreSQL tests do not verify hosted Auth or gateway behavior.
4. Configure ignored `local.properties`: `league.backendVerified=true`, `supabase.url`, `supabase.key` (publishable only), `captcha.url` (public HTTPS page), `release.deletionUrl`, `release.termsUrl`. Set `league.safetyVerified=true` only after hosted acceptance and moderator coverage. Rebuild. Production also requires public developer/support/privacy details and upload signing. The current test build leaves the online flag false and shows an honest disconnected state; local profile drafts are not globally reserved.
5. Assign moderators to review the private `league_reports` queue and use the server-only `league_profiles.suspended` flag for enforcement. The app includes profile reporting, blocking and clearing blocks; reports never automatically change a score or suspend a profile. Complete abuse monitoring, privacy policy updates, deletion support, CAPTCHA/device validation and store acceptance before public release. Usernames and Instagram handles are self-reported public text; do not advertise verified Instagram ownership. No real backend credentials, deployment access or administrator session are currently available in this workspace.

Optional Supabase pg_cron job, configured by an authorized administrator after enabling pg_cron:

```sql
select cron.schedule('doomscore-monthly-podium', '0 0 1 * *',
  $$select public.finalize_league_month((date_trunc('month',now() at time zone 'utc')-interval '1 month')::date);$$);
```

The board also finalizes lazily, so it works without cron. `finalize_league_month` is unavailable to anonymous and authenticated clients; there is no client-supplied competition clock, owner ID or rank.

Reference: [Supabase anonymous sign-ins](https://supabase.com/docs/guides/auth/auth-anonymous), [database function permissions](https://supabase.com/docs/guides/database/functions).

## iOS integration contract

The iOS repo was not changed. Your teammates can use the same public project URL/key and an anonymous Auth session. Send the access token for personal/profile calls; no service-role key goes in either app. These are POST endpoints below `rest/v1/rpc/`:

| RPC | Parameters | Result |
| --- | --- | --- |
| `save_league_profile` | `p_username`, optional `p_instagram`, `p_emoji`, `p_visible` | Own sanitized profile; unique usernames are enforced server-side. |
| `get_my_league_profile` | none | Own profile or null. |
| `submit_league_counts` | `p_rows: [{day: "YYYY-MM-DD", reels: integer}]` | `ok`, optional clamp count, or a bounded error; all dates must belong to the open UTC month. |
| `get_global_leaderboard` | none; anonymous browsing allowed | `month`, `previous_month`, `participants`, `top` (at most 50), `me` (rank ≤200 or null plus top_percent/reels), `podium` (at most 3), `spotlight_until`. |
| `league_profile_action` | `p_username`, `p_action` (`block`, `unblock`, `report`, `unblock_all`), optional `p_reason` | Private action; blocked profiles are filtered without reranking others. |
| Existing `delete_account` | none | Deletes the anonymous Auth identity and cascades league/friend data. |

Produce exact UTC daily counter buckets on-device; do not relabel device-local days as UTC or present estimated iOS counts as verified reel detections. Closed-season uploads are intentionally rejected. UTC starts at 05:30 India time; display local equivalent reset times if desired.
