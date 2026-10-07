# UI experience update

The competitive theme uses short score/rank fades, animated progress/chart updates, selected-tab feedback and light directional page transitions. Native Compose animation timing respects the device animator scale. Goob only animates while the screen is resumed; its drawing state avoids invalidating the whole page.

Tab scroll positions and the Stats period survive navigation/rotation. Back returns from a primary tab to Today while sheets retain their own dismissal. Recap Close and Share stay visible while its content scrolls. Counter setup opens expanded. Onboarding Start/Explore/Privacy stay visible while the introduction scrolls. Username and Instagram errors appear alongside their fields; server errors stay beside Save. Counters/privacy/deletion stay available without wellness goals.

History now loads with two indexed range queries instead of two per day (730 reads for a 365-day view). The monthly dashboard uses one aggregate query. Database snapshots load on a background dispatcher; revisions are sampled for visible score refreshes and collected only while the UI is active. Real counter events are not sampled, discarded or slowed by this UI sampling. Existing counter, aggregation and trophy rules are unchanged.

Loading failures offer retry, and refreshes keep the previous good snapshot. Source checks and emulator acceptance verify behavior; physical-phone frame-time acceptance is still needed before claiming a specific FPS improvement.
