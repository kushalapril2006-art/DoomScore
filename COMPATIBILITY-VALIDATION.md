# Compatibility validation — 1.5.2

Validated on 2026-10-09. Package `com.gridcc.doomscore.android`, version code 10. This is a development-signed APK, with the same signing certificate as 1.5.1; production release configuration gates remain enabled.

| Check | Result |
| --- | --- |
| Build and unit checks | 59 passed |
| Android lint | 0 errors, 71 warnings |
| Android instrumentation, isolated API 36.1 emulator | 9 passed |
| Floating pill / notification UI acceptance | 22 passed |
| APK ZIP integrity, signing, 16 KB ZIP and 64-bit ELF alignment | Passed |
| APK architectures | arm64-v8a, armeabi-v7a, x86, x86_64 |

Instrumentation verifies actual scoped service subscriptions, counts from synthetic accessibility events, ads/rewatches/panels, a floating mascot attached to a real overlay window, resume inside a static feed, service reconnection without duplicate counting, full system accessibility disconnection without data loss, encrypted/trimmed Firebase session storage, and notification eligibility/dismissal/recovery.

UI acceptance verifies placement, bounded touch area, dragging, tapping, organic/ad/rewatch score display, hiding outside feeds and while paused/locked, notification refusal/revocation, notification-only mode, large text and disabled animations. Revoking notification permission terminates Android's instrumented process; the emulator-only observer is restarted and the service is rebound for inspection. This is not an application workaround or a promise of automatic permission re-enabling.

The 22 UI checks ran before the final sibling-caption refinement; UI/service/notification code is unchanged in the final binary. The final detector refinement passed all 59 unit checks and the 9 Android integration checks again. The first instrumentation attempt was blocked by the emulator's System UI ANR dialog; it passed after recovering that environment. The UI harness was updated for current labels, runtime permission requests, process restarts, unique per-run fixture identities and asynchronous layout changes.

The synthetic feed fixture and UI inspection instrumentation are separate APKs, excluded from the delivered application. No physical phone, real Instagram feed, banking transaction, OEM native island, older Android runtime or 16 KB runtime device was exercised. These checks do not establish universal compatibility or exact real-world video identification. Physical POCO F7 / HyperOS verification remains necessary.

APK SHA-256: `99151954331b0b8c2ea1d81ec2f8b3d7bf78047960c20c7417d0626346dc8012`.

See [device compatibility](DEVICE-COMPATIBILITY.md) for setup and platform limits. Local detailed reports are in `test-results/device-compatibility-checks.txt`, `test-results/compatibility-pill/island-ui-smoke.json` and `test-results/compatibility-apk.json`.
