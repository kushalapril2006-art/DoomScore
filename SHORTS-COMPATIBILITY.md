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
