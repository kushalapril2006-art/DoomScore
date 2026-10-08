# Brainrot Trophy Cabinet

The cabinet opens from Today and Stats, without creating an account. It shows all eight requested badge names, shield-style icons, locked/unlocked states, progress and local unlock dates.

| Badge | Unlock |
| --- | --- |
| One More Then I Sleep | 100 unique reels |
| For You? For Me. | 1,000 unique reels |
| Final Boss of Brainrot | 10,000 unique reels |
| Bed Rot Any% | 500 unique reels in one calendar day |
| Chronically Online | Scroll 7 calendar days in a row |
| Bro Got Outscrolled | Win the first completed Battle |
| Unemployed Behaviour | Win 5 completed Battles in a row |
| Touch Grass Is a Threat | Reach an actual global rank from 1 to 10 |

## Local counting and retention

Unique means a distinct salted metadata fingerprint within an app. It does not guarantee a unique underlying video when apps expose identical or changing metadata. Ads and sub-dwell skips never enter organic history. Repeat visits/loops do not advance lifetime unique progress, even after the existing five-minute counter window expires. Each qualified organic fingerprint can count once per device-local calendar day for the daily trophy. A scrolling streak needs at least one counted reel per day; gaps/zero days break it, and future dates are ignored when evaluating history. Today and the home-screen widget also show an active scrolling streak; higher counts never break it.

The database upgrades from v1/v2 to v3 without rewriting previous reel totals. Old qualified daily counts can establish a scrolling streak. Old totals cannot prove lifetime uniqueness, so unique milestones start with this update; no fabricated historical unique counts are assigned.

The private database retains at most 10,000 lifetime trophy hashes; reaching the final lifetime milestone removes that set and preserves its count/awards. The daily set resets with the day, is capped at 500, and is removed/stops accumulating once Bed Rot Any% is earned. Aggregate progress and local earned dates persist across normal restarts and day/month changes. Delete local counting history explicitly resets local trophies and their hash history. These hashes never sync.

## Verified online trophies

Apply `backend/supabase/migrations/20261007020000_trophy_cabinet.sql` after the global-league migrations before enabling the updated online app. `get_my_trophy_proofs()` is authenticated, accepts no owner/rank/count parameters, and returns only the caller's owner ID, Battle win count, best consecutive win run, and persistent top-10 achievement. Native parsing binds the owner to the active credential and stores its small proof cache encrypted with Android Keystore. Anonymous/public viewers cannot access it, and direct client table writes are denied.

The RPC derives top-10 status from the real monthly ranking, records the award once, and retains it after the rank drops or the season changes. It derives Battle wins from immutable completed results; a draw or loss breaks the run. Completed outcomes are ordered by completion time and battle UUID, and retries do not create extra wins. The trusted Battle finalizer must validate actual participants/winners and invoke `record_trophy_battle_result(battle_id,user_id,outcome,completed_at)` with its server credential. Conflicting rewrites are rejected. Client apps cannot invoke this function or submit wins.

The current Battle screen is a live friend comparison without completed-match lifecycle/finalization. No finalized wins exist merely because somebody leads that screen. The two Battle trophies therefore stay locked until that trusted completed-Battle integration exists. Online global trophies also remain locked in the current offline release-check build. No sign-in UI, fake scores, demo unlocks or client claim buttons were added.

Online awards/results cascade when the anonymous identity is deleted; local counting trophies are unaffected by deleting only that identity. Clearing only local counting history does not erase already verified online trophies. Lost anonymous credentials cannot be recovered by typing a username.
