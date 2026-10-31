# Forge full-sync checkpoint: shared rules, state and resources

Source: NeoForge `06ea95b8`; Forge base: `26cf538a`. This is the first dependency group of the complete 1.20.1 catch-up, not a playable parity release.

- Added the shared evolution, metabolism, organ, population and military rules with Java 17 adaptations. Server balance, player progression and tissue competition configurations retain their source defaults.
- Added saved infinite progression, milestone and organ research records through the existing Forge capability storage. The capability clone/respawn invalidation boundary remains Forge-owned.
- Skill settings retain strict DFU 6 boolean validation and old-save defaults, and now carry the precision-wing appearance setting. The full-sync network protocol is changed in the next runtime group before any distributable build.
- Copied all 261 motion-profile source files (247 JSON, 13 data files and the source README). The runtime packer restores all 260 runtime entries byte for byte. No frames or animation precision were removed.
- Added bounded, lossless map/motion readers and Java 17 build packers with pinned XZ 1.10. The map build verified 14,857 currently staged map inputs; remaining Los Angeles detail-core inputs will be staged with world generation. Forge shader attributes/fog/blend adapters were retained.
- Brought blade combo link timing into agreement with the late hit and vanilla hurt-resistance window, and staged the stable evolved mapping resource.

Validation on 2026-10-31: Java 17 main/test compilation and the full currently staged JVM suite passed: **828 tests, 0 failures, 0 errors, 0 skipped** (`forge-phase1-state-packing-r1.log`). Exact-byte packing verified 260 runtime motion entries and 14,857 map inputs. This does not validate the not-yet-integrated client, entities, military runtime, compatibility bundles or server lifecycle. Those remain required before delivery.

The separate source/runtime merge candidates are deliberately outside this checkout until this checkpoint is recorded. They include unresolved API/runtime validation work even though their textual conflicts have been reviewed.
