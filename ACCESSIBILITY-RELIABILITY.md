# Counter connection and score consistency — 1.6.10

## BrainPal inspection

The supplied BrainPal APK identifies itself as `com.brainrot.android`, version 14.14.549, code 549. Its service declares window-content access and gesture capability, uses a 500 ms accessibility event timeout, and filters supported packages in its event handler. It uses coroutine jobs and handles screen state, unbind and destruction. This is the Android Accessibility mechanism, not an Instagram or YouTube counting API.

Its inspected YouTube metadata helper searches interface text/descriptions for a creator handle and longer text after excluding playback/navigation/action labels. It constructs a metadata key, including fallbacks when only one component is found. An Instagram/Facebook path recognizes sponsorship/paid-partnership and promotional labels. These are interface heuristics; they do not establish a guaranteed video ID, perfect repeat recognition or detection of undisclosed ads. Broader text fallbacks can recognize more layouts while also admitting ambiguous identities.

No special payment exemption was found in the inspected service path. A large UPI-package list elsewhere belongs to checkout/payment discovery; it does not demonstrate that payment apps trust an active accessibility service. This inspection cannot establish why a particular BrainPal installation was accepted by a payment app. Proprietary source has not been copied into DoomScore.

DoomScore keeps its existing package-scoped content access, without adding gesture or screenshot capability. BHIM was previously reported to work once DoomScore Accessibility was switched off. Its payment-disconnect action therefore calls Android's `disableSelf()`. The app does not silently re-enable the service or bypass payment checks. OEM native island placement and payment-app acceptance are separate from counter parsing.

## Reconnect safeguards

The 1.6.8 lifecycle/worker safeguards are retained in 1.6.10. Metadata traversal, counter-engine work and score writes now use one dedicated worker, leaving view/overlay updates on the main thread. Only one scan runs at once. Scan exceptions schedule another attempt; preference changes and app resume wake a still-bound poller. Window changes trigger a fresh root check. Polling stays scoped to the selected feeds and does not read a payment app's hierarchy.

Each binding has a generation token. Disconnecting invalidates queued observations and UI updates, and teardown from an old service instance cannot clear a newer binding's state or notification. The pause/consent checks also run after accessibility-provider calls. The payment disconnect invalidates queued work before asking Android to disable the service.

If the permission is enabled but Android has not bound the service, the UI shows a reconnect state and opens Android Accessibility settings. It cannot force Android to rebind after an OEM restriction or user disable. Existing battery/background-settings assistance remains available.

Android owns accessibility binding and invokes connection/unbind callbacks: [AccessibilityService lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService). Moving repeated work off the UI thread addresses one potential stall mechanism; it does not establish the cause of every OEM disconnect. See [Android ANR diagnosis](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs) and [Doze/App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

## Scores versus achievements

A counted view now writes its score, hourly bucket, UTC league total, recent identity and unique trophy progress in one SQLite transaction, followed by one score revision. A failed score write rolls back the other changes. History snapshots hold the store lock across their related queries.

Scores remain counted views for the selected period, with the existing five-minute rewatch suppression. Lifetime trophies track distinct hashed identities, bounded at the final 10,000-reel milestone. An older repeat can increase a score without increasing lifetime-unique progress. Unique progress starts when trophies were introduced; missing historical identities are not invented. The cabinet now labels counted views and lifetime unique separately.

## Chart inspection

Hour/day/month charts support tap, hold and drag, including zero-valued bars. Readouts show the exact calendar date or month and its count. Year mode groups the last 12 calendar months; month mode includes previous/next navigation with future months disabled. More-stats source bars and reel-based trophy progress also expose their counts on hold. Keyboard and accessibility actions select the same underlying buckets.

## Verification boundaries

The updated source passes 127 JVM checks, including chart dates/aggregation, stale-binding rejection and atomic engine acceptance. Three additional Android storage checks compile, covering transaction consistency, rollback and historical month boundaries; they are not claimed as executed on the user's data-bearing phone. Physical UI/counting acceptance is recorded separately in UI and Shorts documentation. No universal OEM, Android-release or future feed-layout guarantee is made.
