# Forge sovereign flight integration acceptance — 2026-10-17

Forge 1.20.1 is based on `forge-1.20.1` after PR #52. The earlier wings.2/wings.11 visual records in SOVEREIGN_FLIGHT.md describe the NeoForge source implementation, not Forge acceptance.

- Full `test build -PwingTest --offline` passed: 403 tests, zero failures/errors/skips, including standalone boundaries and bundled animation checks.
- Forge adaptations cover Java 17, events, capability synchronization, packets, rendering vertices, mixins and optional client QA fixtures. QA classes are excluded from installable JARs.
- Grounded enhanced sprint remains available while equipped. Attack and consume input ownership are preserved; disguise retracts wings and stops flight.
- Actual Forge client run `wings12-sonic-1792216820499` completed takeoff, upgraded flight and release braking without a crash. Maximum measured speed was 7.68 blocks/tick (153.6 blocks/s); this run did NOT reach sonic speed, and its live wall-collision flag remained false.
- The same run exercised a real inbound movement handler with a non-owner test player: legal packet batches of 1–5 were accepted, 1,000-block excess moves rejected, and wall-crossing rejected. This uses a packet sink, not a remote socket.
- Sampled screenshots verify the wing model renders. They do not establish continuous frame rate or sonic membrane visibility on Forge.

The disposable test fixture is opt-in: `-PwingTest -PwingSonicTest -PwingQaDirectory=<absolute directory> -PclientRunDirectory=<same directory>/client -PquickPlayWorld=<disposable save folder>`. It changes progression and terrain and exits the client. Never use a personal save.

Limitations: Forge sonic visual acceptance and dedicated-server multiplayer acceptance remain outstanding. No release tag is created by this PR.
