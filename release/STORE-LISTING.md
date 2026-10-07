# Store listing draft — local-counting release

App name: Doomscore

Short description: Competitive doomscrolling. Stack reels, unlock badges and flex your score.

Full description:

Doomscore turns your reel count into a competitive flex. Stack reels, climb scroll ranks, chase personal bests and unlock the Brainrot Trophy Cabinet with a lively little mascot along for the ride.

After a one-time setup, open a supported app's reel viewer and scroll normally. Doomscore counts sufficiently stable reel views from the interface information Android exposes. Video loops and recently recognized rewatches do not add another reel. Recognized sponsored labels are excluded.

See daily and hourly scores, active scrolling streaks, per-app breakdowns and weekly, monthly or yearly receipts. Higher counts advance your scroll rank. Keep a draggable score in reel feeds, add a home-screen widget and share a flex card with the group chat. More reels, more bragging rights.

Android Accessibility permission is required for automatic counting. With your consent, Doomscore reads visible text, descriptions and control IDs in the apps you select to detect reel changes, visible ad labels and recent repeats. It ignores other apps, takes no screenshots or screen recordings, and does not store captions. Local counts and salted recent-reel hashes stay on your phone. This release does not create battle accounts or upload your counting data.

Instagram Reels is the primary detector. YouTube Shorts support depends on the current app's interface. TikTok and Snapchat detectors are experimental. Changes in app versions, language, labels or exposed metadata can affect recognition. A view must stay stable for about 0.75 seconds; recent-rewatch exclusion uses a five-minute window. Doomscore is not affiliated with Instagram, Meta, YouTube, TikTok or Snapchat.

## Console preparation

- Category suggestion: Productivity. No paid digital features or in-app ads are included in this build.
- Use your team's verified developer identity, contact information and a live Android privacy-policy URL. The included policy is a review draft and has not been published.
- Complete content/age-rating and target-audience forms accurately. Do not select child audiences without a separate review and appropriate product changes.
- For the default local-only binary, counting data is processed/stored on the device. Review Google's Data safety definitions against the actual distributed build. User-initiated recap sharing is described in the privacy text. Do not reuse local-only declarations if enabling Battle or adding telemetry later.
- Declare Accessibility use for app functionality, identify this as a general reel counter rather than an accessibility tool, and submit the disclosure/consent demonstration. The service sets `isAccessibilityTool=false`.
- For newly created personal accounts subject to Google's testing rule, complete the required closed-test cohort and duration before requesting production access. This cannot be substituted by emulator tests.

Official references: [Accessibility declarations](https://support.google.com/googleplay/android-developer/answer/10964491), [user data](https://support.google.com/googleplay/android-developer/answer/10144311), [personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465).
