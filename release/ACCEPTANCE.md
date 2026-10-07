# Real-device acceptance — required before public launch

This is an executable test plan for the team, not a record of tests already passed. Record date, phone/OS, installed social-app versions, release APK/bundle hash, expected/observed counts and any failures. Do not upload raw Accessibility dumps from private feeds; use controlled test accounts/content and remove private data.

| Scenario | Expected result |
|---|---|
| Fresh install; decline consent | No hierarchy reading or counting; browsing the dashboard remains possible |
| Consent and enable Accessibility | Counter connects; opening the reel viewer starts counting without another action |
| Twenty deliberately distinct organic reels | Each stable qualifying reel counts once; compare manually observed totals |
| Same reel loops ten times | One count |
| A → B → A within five minutes | Two unique counts, recognized repeat excluded |
| Return to a reel after five minutes | It can count again |
| Ten visibly sponsored items | Recognized items do not increment reel totals; record unsupported labels/languages |
| Comments, keyboard, normal feed, profiles, DMs | No reel increment; no message text stored or uploaded |
| Fast/partial swipes | No count before dwell; transitions do not create extra views |
| Pause, unselect app, disable system permission | Counting and bubble stop; switches update immediately |
| Resume, cold start, reboot, update | Counts persist; system permission state is accurately shown; reconnect is verified |
| Screen off and lock screen | No manufactured viewing time or extra counts |
| Offline operation | Local counting, history and privacy page work |
| Midnight, timezone/clock change | No negative totals or hours of invented viewing |
| Large font, small screen, landscape, TalkBack | Disclosure/actions remain reachable; controls have usable labels |
| Competition scoring | Higher counts advance rank; only active scrolling days extend streaks; no limit reminders |
| Goob island | Mascot colour follows today's rank, drag/tap works, pause/leave removes it, notification permission is requested only for opt-in island-host notification; native OEM/third-party rendering requires device testing |
| Widget and Quick Settings | Correct score/streak/personal best, counter pause/resume; no extra overlay permission |
| Share recap | Correct PNG, functioning chooser/provider; no automatic external send |
| Delete local history | Private counts/hashes removed; no account data is silently deleted |
| Long scrolling session | No crashes/ANRs; measure battery, memory, frame responsiveness and counting drift |
| 16 KB page-size device | Starts and completes counting/share/widget tests without compatibility mode |

Cover the declared minimum Android 8.0 plus representative Android 12/14/15/16 devices, including at least one Samsung and one Pixel if those are target devices. Record results for current Instagram and each app you intend to advertise. Remove unsupported app claims or fix failures before release. TikTok/Snapchat remain explicitly experimental.

If launching Battle, separately verify real staging sign-up/CAPTCHA, owned-profile edits, uploads, invites, friend privacy, revocation, offline reconnect, account deletion, public deletion-request page, terms acceptance, reporting/blocking and moderation handling. Those flows are gated out of the default production build until completed.
