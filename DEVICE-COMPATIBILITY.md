# Phone compatibility

DoomScore 1.5.2 supports Android 8 (API 26) and newer. The development APK contains arm64-v8a, armeabi-v7a, x86 and x86_64 libraries. Device support, counting accuracy and native-island placement are separate checks; none is guaranteed by an Android version alone.

## What changed

- A service that connects or resumes while Reels is already open discovers the current feed without requiring another swipe.
- Temporary unreadable roots break dwell continuity and are retried instead of stopping the counter permanently.
- Detection uses the app window's actual bounds, including offset/split-screen windows. Horizontally adjacent pages and caption nodes from known neighboring pages cannot contaminate the current identity. Ambiguous overlapping pages are skipped until stable.
- Settings reports permission versus actual service connection, the last feed check, whether the floating mascot is attached, and notification/promotion availability. These labels contain no screen content or account identifiers.
- The notification publisher recovers an unexpectedly lost notification while respecting explicit dismissal, and updates when promotion availability changes.
- Android 16's standard notification-promotion settings can be opened directly, with a normal notification-settings fallback when the manufacturer does not expose that screen.

## POCO F7 / Xiaomi / Redmi

1. Install the new APK as an update and finish the accessibility disclosure/setup.
2. In DoomScore Settings, enable automatic counting and the selected reel app.
3. Enable **Floating Goob pill** for the draggable mascot below the camera. Floating overlays remain opt-in for payment compatibility.
4. For native presentation, enable **Native island / Live Update**, allow notifications, and open **Phone Live Update / island settings**. Enable promotion if the phone exposes the option.
5. Open Reels or Shorts and leave an organic reel visible for at least 0.75 seconds. Goob appears only when readable reel metadata is detected.
6. If counting stops in the background, open **DoomScore phone settings** and inspect battery/background restrictions and HyperOS background autostart. Setting names and available controls vary by firmware; these are recovery options, not unconditional requirements.

Xiaomi explicitly limits HyperIsland to selected devices and apps, with integration and availability varying by firmware and region. An Android Live Update requests promotion; it cannot force a vendor's island to accept DoomScore. A successful promotion flag also does not prove placement inside HyperIsland. The standard floating pill and silent notification remain alternatives. No private vendor payloads, root tools, notification impersonation or extra notification-listener access are used.

Primary references:

- [Xiaomi HyperOS / HyperIsland availability](https://www.mi.com/global/hyperos/)
- [Android Live Update eligibility and manufacturer criteria](https://developer.android.com/develop/ui/views/notifications/live-update)
- [Android notification-promotion settings](https://developer.android.com/reference/android/provider/Settings#ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
- [Android accessibility service/root APIs](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)

## Accuracy and payment limits

The counter recognizes accessibility metadata, not video pixels or Instagram's internal video IDs. Missing/changing metadata, identical captions, language changes and app experiments can still produce missed views or imperfect rewatch/ad filtering. Fast swipes under 750 ms intentionally do not count. No screen capture is used. Instagram/YouTube updates require real-device revalidation; TikTok/Snapchat are beta.

BHIM may reject the enabled accessibility service even when overlays are off. **Disconnect counter for payments** turns it off through Android; re-enable it manually afterward. This release preserves that behavior and never silently re-enables the service.

## Validation scope

The POCO F7 report is a user report, not a verified physical-device pass. Native HyperIsland placement and real Instagram accuracy on that phone remain unverified. See [the validation record](COMPATIBILITY-VALIDATION.md) for completed automated checks. Only Android 16.1 emulator images are installed in this workspace; Android 8–15 physical/emulator acceptance remains outstanding.
