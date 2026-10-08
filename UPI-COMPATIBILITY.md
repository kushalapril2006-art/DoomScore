# Payment-app compatibility

## Confirmed symptom

On the user's OnePlus phone, BHIM works when DoomScore's accessibility service is turned off. This identifies the enabled service as the compatibility trigger. It is not proof of a malware verdict or of every payment app's policy. The updated build still needs a real BHIM check on that phone.

## Version 1.5.1

- The accessibility XML declares a fixed supported-app list instead of receiving events from all packages. At runtime the list narrows to selected supported apps plus DoomScore's own package, and to its own package only when consent/counting is off. A non-empty list avoids accidentally subscribing globally.
- Floating Goob overlays default to off. Existing defaults migrate to off once; an explicit later opt-in is preserved. The optional notification counter remains independent.
- Pausing immediately stops polling and removes the floating window and live notification. Leaving a readable supported feed also removes them.
- **Settings → Disconnect counter for payments** calls Android's `AccessibilityService.disableSelf()`. This removes the service from Android's enabled-service list, rather than merely pausing counting. It preserves counts, trophies and account data.
- Re-enable the counter through Android Accessibility when ready to count again. The app cannot silently re-enable this permission. Automatic reel counting needs an enabled accessibility service; it cannot be guaranteed to coexist with a payment app that blocks such services.

No payment-app data is inspected. No gesture injection, keyboard capture, screenshot capture, root, hidden service, fake accessibility-tool declaration or privileged settings workaround is added. The service still needs window-content access for real reel identification and remains honestly declared as a non-disability accessibility service.

If an older DoomScore version has a separate accessibility entry, disable that entry too; a newer package cannot disconnect another application's service. Do not uninstall an old version solely to move counts without arranging migration.

## What this does not certify

Package scoping and turning overlays off do not remove the accessibility capability. Android, BHIM, Play Protect and other providers make their own security decisions. This is a compatibility mitigation and supported disconnection flow, not a promise of an exemption or approval. Distribution through Google Play and a successful Play review do not guarantee a payment-app exception.

References: [Android disableSelf](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#disableSelf()), [Android package filtering](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#packageNames), [Google Pay overlay restrictions](https://support.google.com/pay/india/answer/10728104), [Google Pay security alerts](https://support.google.com/pay/india/answer/16886058).

## Verification — 2026-10-09

Debug version 1.5.1 (code 9) compiles and passes 51 JVM checks and 9 isolated Android checks. Android checks exercise actual accessibility counting (organic reels, repeat suppression, ads and panels), runtime package scopes, overlay migration, `disableSelf()` removing the enabled service, count retention, encrypted auth-session trimming, and live notifications. Lint has zero errors and 71 warnings. The Quick Settings fallback is guarded below API 34; newer Android uses the PendingIntent overload.

BHIM was not operated by automation and no financial transaction was attempted. The physical phone was not connected during verification; the user confirmed that disabling the old service restores BHIM. This new build's BHIM behavior requires the user's phone check. Production release gates and the live Firebase setup remain separate, pending requirements.
