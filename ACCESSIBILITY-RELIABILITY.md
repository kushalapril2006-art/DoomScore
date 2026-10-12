# Counter connection and score consistency — 1.6.10

## BrainPal inspection

The supplied BrainPal APK identifies itself as `com.brainrot.android`, version 14.14.549, code 549. Its service declares window-content access and gesture capability, uses a 500 ms accessibility event timeout, and filters supported packages in its event handler. It uses coroutine jobs and handles screen state, unbind and destruction. This is the Android Accessibility mechanism, not an Instagram or YouTube counting API.

Its inspected YouTube metadata helper searches interface text/descriptions for a creator handle and longer text after excluding playback/navigation/action labels. It constructs a metadata key, including fallbacks when only one component is found. An Instagram/Facebook path recognizes sponsorship/paid-partnership and promotional labels. These are interface heuristics; they do not establish a guaranteed video ID, perfect repeat recognition or detection of undisclosed ads. Broader text fallbacks can recognize more layouts while also admitting ambiguous identities.

No special payment exemption was found in the inspected service path. A large UPI-package list elsewhere belongs to checkout/payment discovery; it does not demonstrate that payment apps trust an active accessibility service. This inspection cannot establish why a particular BrainPal installation was accepted by a payment app. Proprietary source has not been copied into DoomScore.

DoomScore keeps its existing package-scoped content access, without adding gesture or screenshot capability. BHIM was previously reported to work once DoomScore Accessibility was switched off. Its payment-disconnect action therefore calls Android's `disableSelf()`. The app does not silently re-enable the service or bypass payment checks. OEM native island placement and payment-app acceptance are separate from counter parsing.

### Same-phone BHIM follow-up — 11 October 2026

The owner confirms BHIM opens with BrainPal Accessibility on and DoomScore off on the same phone. This rules out treating the observation as a blanket ban on every enabled Accessibility service. It demonstrates different acceptance of the two configurations, but does not reveal BHIM's private classification rule. The owner cannot connect the phone currently, so the follow-up compares the supplied BrainPal APK with the actual signed DoomScore 1.6.10 APK locally; it does not substitute installed/runtime metadata for APK declarations.

| Packaged property | BrainPal 14.14.549 | DoomScore 1.6.10 |
| --- | --- | --- |
| Window-content access | Yes | Yes |
| Gesture-injection capability | Declared | Not declared |
| Event mask / feedback type | `0x401820` / generic | Same |
| Window flags | IDs, non-important views, interactive windows, default delivery | Same window flags; no default-delivery flag |
| Event timeout | 500 ms | 250 ms |
| Package filter | Runtime handler allowlist; no XML package filter | XML and runtime selected-app filters |
| Declared manifest permissions | 27 | 4 |
| App signature verification | Pass (v3) | Pass (v2) |
| Source-stamp verification | Pass; stamp certificate identifies Google | No verified source stamp present |

The Android SDK verifier checked both signatures and independently verified the source stamp. A stamp identifying Google is a distribution-origin difference, not proof that BHIM trusts this APK or consults stamps. Its certificate SHA-256 is `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d`; the app signer and stamp signer are different roles. DoomScore is properly signed without a source stamp. Switching signature schemes or copying a default-delivery flag would not establish banking acceptance. Android documents that the default flag affects event delivery, not a payment-app exemption: [service configuration](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#DEFAULT).

The packaged comparison does not support the hypothesis that BrainPal avoids the warning by requesting less access. DoomScore guards unrelated-app hierarchy reads and has no production tracking calls that inject clicks, gestures or global actions. BHIM may apply a service/app-specific rule or consult installation/runtime information absent from the supplied APK; these remain hypotheses. No banking screen/data was inspected, no security setting was changed, and neither APK was uploaded. The owner's decision to keep the unfinished APK private remains in force. No app-code change or new build is justified by this comparison alone; the BHIM rejection is still unresolved.

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
