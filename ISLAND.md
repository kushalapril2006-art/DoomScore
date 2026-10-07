# Goob island / Android 1.4

Settings → On-screen Goob counter → Goob island. During detected reel feeds, Doomscore draws a black, draggable pill below the system status bar with Goob and today's total across selected apps. It uses the existing consented Accessibility overlay, not screen capture, system-wide overlay access, or notification access. Tap opens Doomscore; drag avoids reel controls. Turn off the on-screen counter to hide either position. Upgrades preserve the old floating-counter choice; island and notification options start off.

Shared rank colours: 0 cyan, 1 lime, 100 yellow, 500 orange, 1,000 red, 2,500 pink, 5,000 purple. Today's total resets at the local calendar day. The same thresholds and palette drive the app, pill and notification mascot. The mascot pulses only when the count rises and respects Android's animation setting. No perpetual overlay animation.

Optional Settings → Island app notification requests Android notification permission only when selected. It sends an ordinary silent ongoing notification with title, count, source, rank, accent and coloured Goob large icon. For notification hosts such as dynamicSpot, select Doomscore in that host and disable Doomscore's own overlay to avoid duplicate pills. Host software controls layout, persistence and whether it shows the large icon. No guaranteed native OnePlus Live Alerts/Fluid Cloud integration. Native Android promoted live-update rules are designed for time-sensitive activities; this app does not spoof navigation, calls, media playback or progress to force a native island.

Both outputs cancel on pause, leaving a detected feed, unreadable windows, locking the phone, service interruption/destruction; stale notifications are cancelled on app cold start. Notification failures cannot stop the reel-count engine. Notification payloads exclude captions, usernames and reel identifiers. Android notification privacy masks score details on the lock screen by default; authorized notification-listener apps can read the ongoing score notification.

References: https://developer.android.com/develop/ui/views/notifications/live-update and https://play.google.com/store/apps/details?id=com.jamworks.dynamicspot . Real OEM island interoperability requires testing on that host/device.

## Native API update in 1.4.1
The Native island / Live Update toggle now requests Android 16 promoted ongoing presentation, with a numerical chip and Goob icon. This supersedes the earlier ordinary-notification-only implementation described above. Manufacturer criteria and Android activity eligibility still apply. See NATIVE-ISLAND.md.
