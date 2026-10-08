# Native island support

The live notification requests Android 16 promoted ongoing presentation during an active selected reel feed. It supplies a short numerical count and the current Goob icon. Earlier Android versions keep the ordinary silent notification. No fake media/call session, root, screen sharing or island replacement app is used.

Enable Settings > Native island / Live Update, accept notifications, then allow Live Updates / Live Alerts for Doomscore in the phone settings if that option exists. Turn off On-screen Goob counter to avoid a duplicate floating pill. Counting remains independent of notifications.

This is best-effort support, not a guarantee for every built-in island. The OS decides promotion, supported activity categories, placement, icon tint and animation. Android documents additional manufacturer criteria and limits Live Updates to ongoing, user-initiated, time-sensitive activities. A passive reel score may not qualify on all devices. Native mascot colour and custom animations cannot be forced through the standard API. Earlier manufacturer islands may require separate developer approval or integration.

Dismissal suppresses further posts for that active feed session; ending the session resets suppression. The receiver is private and the notification intent immutable. Leaving a feed, pausing, locking or revoking notifications cancels the counter. Lock-screen public content hides the score.

Official API: https://developer.android.com/develop/ui/views/notifications/live-update
Official compact text reference: https://developer.android.com/reference/android/app/Notification.Builder#setShortCriticalText(java.lang.String)

Physical OnePlus verification is pending until a real reel session displays a promoted counter. Passing automated tests does not prove native placement.
