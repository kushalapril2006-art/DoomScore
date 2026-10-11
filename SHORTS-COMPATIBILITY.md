# YouTube Shorts compatibility — 1.6.10

The 1.6.9 phone counter check showed a recognized feed, 32 readable labels and zero caption/creator candidates. This confirmed that the service could read text but the metadata filter rejected it. The inspected structure placed the entire player under `navigation_bar_divider_frame`. The inferred-panel filter treated that global ancestor as a navigation menu and discarded all creator markers.

Menu exclusions now stop at the selected Short's page boundary. Real menu, delegated-promotion and action containers inside the page remain excluded. The action-rail/inline-icon distinction from 1.6.9 is retained. A third anonymous regression reproduces the global-wrapper failure before the change and passes after it, while checking that an actual menu container stays excluded. All 127 JVM checks pass. No device model, YouTube-version branch, new permission, gesture capability or capture feature is involved.

## Physical-phone acceptance — 10 October 2026

The signed 1.6.10/code21 update installed normally over the existing package on the connected OnePlus CPH2767, Android 16/API36. Counts/settings were preserved; the bound Accessibility service reconnected automatically and showed live. YouTube changed from zero to positive counts during watched Shorts. After the user stopped swiping for the controlled check, continued playback held the source total at 5. One settled forward transition increased it to 6; a settled reverse transition to the recent previous Short left it at 6. The first immediate-after-app-switch gesture did not establish a new-video result; the acceptance uses settled transitions instead.

The final own-app snapshot matched source totals to today, summed the day buckets to the month, and matched counted views to the Trophy Cabinet. Lifetime unique was separately labelled and one below counted views, consistent with stored history; it was not rewritten to fake equality. No app data was cleared. The temporary native checker used the flag that preserves active Accessibility services, exported no YouTube captions/usernames/screenshots, and was uninstalled afterward. The phone Downloads APK hash matches the verified PC artifact.

The unchanged charts retain the 12 physical hold/drag checks from 1.6.8 described in [UI details](UI-EXPERIENCE.md). This acceptance covers the observed phone/layout; other OEM/API versions, force-stop recovery and future feed layouts remain acceptance boundaries. Recent-rewatch suppression uses the existing five-minute history, not a permanent underlying video-ID guarantee.

# YouTube Shorts compatibility — 1.6.9

The physical-phone follow-up to 1.6.8 still reported no Shorts counts. A temporary native checker preserved active accessibility services and inspected only YouTube's control IDs, roles, bounds, text lengths and handle-presence flags. No captions, creator names, screen images or Google account data were retained for this diagnosis. The real layout put its caption and the right-side sound thumbnail under one footer ancestor. The detector incorrectly treated that unrelated thumbnail as an inline caption/audio icon and rejected the caption.

The shared parser now recognizes the separate right-side action column when deciding whether an icon belongs to caption metadata. Caption-owned/audio-row icons remain excluded; right-rail action labels and changing like totals cannot identify a video. This is a normalized geometry/role rule with no phone-model or YouTube-version switch. Two authored synthetic regression cases reproduce the failure before the fix and pass afterward, covering changing captions/likes, absent captions, scaled/offset viewports and disclosed ads. All 126 JVM checks pass; debug app and Android-check APK compile. The existing dwell, recent-rewatch, ad, viewport and ambiguous-page rules remain.

The signed 1.6.9 update installed, but the phone still reported waiting for readable metadata. Its numeric check confirmed labels were present; the additional global-wrapper filter problem is addressed in 1.6.10 above. These checks do not establish universal compatibility with future app layouts.

# YouTube Shorts compatibility — 1.6.8

The 1.6.8 update retains the shared 1.6.7 parser below and moves hierarchy scanning, engine work and database writes onto one dedicated worker. One scan runs at once; scan exceptions retry, stale bindings cannot publish observations after disconnect, and app resume wakes a still-bound poller. This addresses stalls/reconnect handling without substituting swipe counting for readable reel identity. Android remains responsible for binding the service. See [reliability details](ACCESSIBILITY-RELIABILITY.md).

The source passes 124 JVM checks. Physical-phone acceptance of the signed update is recorded separately below; this does not establish compatibility with every YouTube version or OEM. No screen capture, screen sharing or gesture capability was added.

## Retained parser update — 1.6.7

A supplied 1.6.6 counter check showed a recognized YouTube feed with 120 retained nodes, no traversal limit, and zero recognized caption/creator fields. This narrows the problem to metadata recognition; it does not reveal the underlying node roles or prove that every visible label is exposed to Accessibility.

The parser now supports caption buttons and nested text beneath caption-button wrappers, qualified creator navigation targets that report a virtual/button role instead of ImageView, and additional metadata-panel ID suffixes. A known compact metadata panel can supply a qualified caption row even when its creator avatar has no accessible label. Unknown panels still need a separate creator/handle group. Button candidates must occupy the left, wide caption area; action IDs/labels, the right action rail, icons, subscription/audio controls and ambiguous creator-only groups stay excluded.

The new structural regression cases reproduce the missing-identity path, then check stable identity across roles, new views after the 750 ms dwell, continued playback, recent back navigation and disclosed ads. This is a shared parser change, without a phone-model or YouTube-version switch. The phone's exact exposed hierarchy still needs acceptance with the updated APK.

Copy counter check now includes numeric label, metadata-panel and button totals, alongside the existing caption/creator counts. It exports no labels, captions, usernames, hashes or raw resource IDs and is not uploaded automatically.

The current update passes 116 JVM unit checks, including eight new structural Shorts cases. A live YouTube check on the isolated SDK emulator was inconclusive because the emulator hung under host memory pressure; it supplies no live-counting acceptance result. The exact reported phone layout must still be checked with 1.6.7.

# YouTube Shorts compatibility — 1.6.4

This update changes shared detection logic for all supported devices. It has no OnePlus/POCO-specific detector, phone-model allowlist or YouTube-version switch. It broadens compatibility; it does not establish that every phone or future YouTube layout works.

## Shared fixes

- Read explicit caption fields and nested text children, and virtual caption panels without assuming the caption is row 1. Support reordered rows, overlapping wrappers, a single caption row, multiline text, deeper wrappers and short/non-Latin titles.
- Retain full captions with inline hashtag/link buttons or duplicated virtual child labels. Extra metadata rows and flat creator/avatar siblings do not have to follow one fixed row order.
- An empty legacy title placeholder no longer masks a readable virtual caption. Actual caption words such as “Saved” and “Liked” are retained rather than treated as playback controls.
- Where the panel has no resource ID, infer only a compact creator/handle plus a separate caption group inside an already verified Shorts page. Exclude the action rail, navigation, delegated promotions, subscription/audio controls and ambiguous groups. Do not hash the entire screen or count generic swipes.
- A temporarily hidden audio icon still marks its owning row as audio, keeping sound controls out of video identity. Known caption chevrons remain allowed.
- Prioritize visible branches over cached/off-screen nodes and bound traversal to 1,600 retained nodes, 4,096 acquired nodes and 96 levels. Release acquired children even on provider failure. Large hierarchies are reported as limited in the local check.
- If Goob becomes the active accessibility window, resolve a uniquely input-focused application window. Never reuse an old package or choose a cached/PiP window. Check the root package before reading a hierarchy. Interactive-window access uses the existing disclosed Accessibility capability; no new manifest permission is added.
- Add Settings → **Copy counter check**. Its optional clipboard text contains app/Android versions, source, counts, field/node totals and fixed status labels. It contains no captions, usernames, Google information, hashes or screen contents and is not uploaded automatically.

The existing 750 ms uninterrupted readable dwell, recognized-ad filtering, five-minute recent-rewatch history and comment/viewport exclusions remain. Readability can arrive after a feed transition; waiting on a known page does not justify inventing a video identity.

## Acceptance boundaries

- The app supports Android 8/API 26 and later. This change uses shared Android APIs, but runtime verification here used API 36.1 only; older API/OEM runtime acceptance remains outstanding.
- **104 unit checks passed.** Coverage includes legacy/virtual and ID-less panels, six sample title languages/scripts, density changes, deep/nested layouts, placeholders, cached pages, ads, controls, bounds, hidden audio icons, traversal limits and ambiguous focused windows. These are synthetic structural fixtures, not recordings from every YouTube version.
- The Instagram accessibility integration and four live-notification checks passed (five Android checks). Final YouTube caption refinements are covered separately by the unit and live checks below.
- Real YouTube verification uses preinstalled **20.10.41** on the isolated `DoomscoreVerified` emulator without YouTube sign-in or replacing its APK. The checks exercise new Shorts, stable playback, recent back navigation, promotion exclusion, moving Goob and live totals. A delegated promotion had no readable caption/disclosure and stayed uncounted without incrementing the recognized-ad total. An initial fixed-delay probe stopped during metadata loading; subsequent checks wait for the readable view to settle. A separate live linked-caption case exposed a real missing-identity path in the earlier parser: aggregate captions were discarded because they owned clickable tags. The final parser retains those captions.
- No physical phone, latest YouTube build, every Android release or native OEM island placement has been verified by these checks. Missing/identical metadata and undisclosed ads remain limits; a metadata fingerprint is not a guaranteed underlying video ID.

Accessibility provides the interface tree that the other app chooses to expose, including virtual views, and information can change while it is being read. That is why reliable counting cannot be promised for every future layout without continued acceptance checks. See [Android node documentation](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo) and [window-content documentation](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#retrieving-window-content).

No screen capture or screen-sharing feature was added. Public-launch and live-backend acceptance gates remain separate.

---

## Previous verification — 1.6.3

The previous detector treated decorative player layers as competing videos and required older title resource IDs. Modern virtual-view captions could therefore disappear from recognition, preventing stable counting and repeatedly removing Goob.

## Changes

- Select the active Shorts page; exclude cached neighbouring pages and decorative layers.
- Support legacy and newer player IDs, sibling captions outside letterboxed video bounds, and virtual caption panels with an optional audio row.
- Identify videos from caption/creator metadata rather than playback state, like counts, subscription controls or sound controls.
- Keep Goob and its notification visible while a recognized feed temporarily lacks a usable identity. Do not count through that gap. Cover brief layout transitions with a 900 ms presentation grace period.
- Keep the existing uninterrupted 750 ms dwell requirement, recognized ad filtering, recent-rewatch protection, viewport bounds and comment-panel exclusion.

## Verification on 9 October 2026

- 75 unit checks passed, including legacy/virtual layouts, sibling captions, cached pages, changing controls, two-row panels, ads, comments and split-screen coordinates.
- The isolated Android 16/API 36.1 `DoomscoreVerified` emulator ran the **real preinstalled YouTube 20.10.41**, version code `1553337840`, without signing into YouTube or replacing its APK. New organic Shorts increased the count; a disclosed ad did not; returning to a recently watched Short did not add a count. Goob and its silent notification remained visible and updated with the total. Continued playback did not add another count.
- The Instagram accessibility integration passed. Four notification checks passed after isolating the standalone notification publisher from the running accessibility service, which otherwise cancels/updates the same notification ID during testing.
- Diagnostic UI exports were confined to the isolated emulator and removed after inspection. No phone hierarchy was collected. The retained local report contains only counters/status and no video captions, creator names or account credentials.

The sideload APK retains production signing and the existing permission allowlist. Public-launch acceptance gates remain separate.

This is evidence for the tested YouTube build and exposed layouts, not a guarantee for every version, language or device. Missing/identical metadata and undisclosed ads remain limitations. Native island placement still depends on Android and the manufacturer; an emulator cannot verify OnePlus or POCO island placement. No screen capture or screen-sharing feature was added.
