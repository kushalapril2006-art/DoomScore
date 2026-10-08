# DoomScore validation

Version 1.4.2 uses the final application identity `com.gridcc.doomscore.android` and the display name DoomScore. The temporary separately named build variant has been removed.

Previous 1.4.1 checks passed 51 JVM cases and 4 notification integration cases, with no lint errors. See [historical validation records](docs/VALIDATION-HISTORY.md) for earlier version-specific evidence.

The current identity cleanup passed Android compilation and lint on 8 October 2026 (0 errors, 67 warnings). The built APK was independently inspected: label DoomScore, package com.gridcc.doomscore.android, version 1.4.2/code 7. Production signing, hosted privacy/support details, Firebase integration and physical-device acceptance remain separate requirements.
