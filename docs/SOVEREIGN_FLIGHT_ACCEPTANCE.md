# Sovereign flight integration acceptance — 2026-10-17

NeoForge 1.21.1 is based on main after PR #51 (Mercer model, weapon handoff and combat audio).

- `gradlew test build --offline`: passed, 402 tests, zero failures/errors/skips.
- Standalone boundary and complete bundled-motion checks: passed as part of build.
- Opt-in client fixtures are excluded from the distributable JAR.
- Before the main integration, actual-client wings.13 side-view testing reached 403.2 blocks/s, triggered client/server sonic state, stopped at the wall and braked after input release. Sampled frames showed wing-edge mist/tip wakes and the forward open bow membrane.
- The build above was repeated after integration; this does not constitute a new dedicated-server multiplayer visual test.

The client fixture is opt-in (`-PwingTest -PwingSonicTest`) and must run in a disposable world under `<wingQaDirectory>/client`. Set `-PwingQaDirectory=<absolute QA directory>` and `-PclientRunDirectory=<that directory>/client`. It changes the test player's progression and terrain and exits after completion. Never use a personal save. Add `-PquickPlayWorld=<disposable save folder>` to enter the fixture world.

Limitations: no independent dedicated-server multiplayer acceptance for this revision; sampled screenshots do not measure continuous frame rate. Chunk readiness controls attainable speed.
