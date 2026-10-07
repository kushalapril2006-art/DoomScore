# Competitive Doomscore

Doomscore is a playful competitive doomscrolling app. More counted reels mean a higher score. There are no daily limits, under-limit rewards, break reminders, blocking tools or wellness coaching.

## Scroll ranks

Ranks describe today's score; the count keeps increasing beyond the final tier. Progress points toward the next rank, not toward a limit.

| Reels today | Rank |
| --- | --- |
| 0 | unranked |
| 1–99 | warming up |
| 100–499 | certified scroller |
| 500–999 | scroll goblin |
| 1,000–2,499 | doom lord |
| 2,500–4,999 | algorithm menace |
| 5,000+ | final boss |

Today shows score, session count, next rank, personal best day, active scrolling streak, monthly score and the Brainrot Trophy Cabinet. The widget shows score/streak/personal best; the floating bubble shows the count without a denominator. Wrapped cards challenge friends to beat the score and show a personal best instead of a cap.

A scrolling streak requires at least one counted reel on consecutive device-local calendar days. High counts never break it; empty days cannot earn it. Yesterday's active streak stays live while today's day is still in progress. A fully missed day breaks the current streak; best history remains. Future dates and days before installation do not inflate it.

Friend Battle always sorts higher reel counts first with stable identity ordering for ties. The global league already ranks by highest monthly reel count. Neither uses caps or remaining-time scores. The ironic top-10 badge “Touch Grass Is a Threat” keeps its requested name and awards competitive rank, not reduced scrolling.

## Upgrade and privacy

Version 1.3 removes the legacy cap/reminder preferences and cancels the retired notification/channel. Its notification permission was removed; version 1.4 adds an optional silent counter notification for island hosts, described in ISLAND.md, with no reminders. Existing local counts, salted reel hashes, trophies, profile drafts and encrypted credentials are preserved. Android no longer uses, requests or sends a daily-goal profile field; new profile caches exclude it. The legacy server column remains for compatibility with the team's existing iOS schema and has no effect on Android scoring. No live database changes are required for this theme update.

Counter pause/resume, app selection, explicit Accessibility consent, privacy information, local-history deletion and online-identity deletion remain practical data controls. They do not limit reels or block Instagram. Automatic counting still requires Android Accessibility, without screen capture/sharing. Recognition retains the limitations documented in README.md and TROPHIES.md.

Online leaderboards still require the previously prepared backend/Auth/CAPTCHA deployment. Completed-Battle awards still need a trusted match finalizer. The default optimized test APK remains offline; it does not show fake public competitors or wins.
