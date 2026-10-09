# YouTube Shorts compatibility — 1.6.3

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
