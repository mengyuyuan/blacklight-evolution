# Forge 1.20.1 full catch-up — runtime integration checkpoint

This is a development checkpoint, not a tested playable release. The source is the NeoForge 1.21.1 production revision `06ea95b8` and its subsequent test-isolation work; the Forge base is `26cf538a`. Platform capability, network, damage, Java 17, rendering and lifecycle adapters remain Forge implementations.

The branch integrates current infection/evolution, infinite player attributes and passive gates, city ecology/world geography, building assimilation, evolved combat, precision wings, consumption branches, military/vehicle/firearm and client control/presentation code. A text-clean merge was insufficient: omitted historical features were recovered by comparing current source declarations and relevant method bodies. Notable corrections include shared consumption settlement, variable supporting limb counts, world dimension registration and building-core/attachment registration.

Forge's old vertex, block-use, SavedData and item-NBT APIs replace the 1.21 APIs. Final damage remains at Forge's existing pre-absorption boundary, and the server/client protocol is incompatible with prior Forge builds. Client and server must update together. Optional integration availability is not equivalent to verified compatibility.

## Evidence

- Phase-one checkpoint: 828 JVM tests passed, no failures/errors/skips.
- Full current integration: **1,118 JVM tests in 251 classes passed**, no failures/errors/skips (`forge-runtime-jvm-r6.log`, `runtime-jvm-results-r6.json`, under `E:/Blacklight-Dual-Closeout-20261031`).
- Initial full integration run had nine failures: missing editable military/evolved sources, old SavedData reflection signatures and a missing plain-JVM Forge bootstrap transformation. These were corrected; the final complete run passed. Forge's actual event and capability transformers prepare the test classpath; no transformed framework classes enter the release artifact.
- City palette and required vanilla tag checks use the actual 1.20.1 registries. Short grass uses its 1.20.1 name; one newer full-cube copper decoration maps to the corresponding aged/waxed cut copper. The unavailable vanilla armadillo references are optional. Geography, palette indices and binary block positions remain intact.
- Exhaustive lossless map packing verified 17,931 source inputs. Selected encoding: 78,013,597 bytes versus 111,115,398 source bytes. No unused whole-city archives were added.
- All 260 runtime motion files retain their exact decoded bytes. Editable models and the original authorized Blackwatch reference are included as source; the six Forge shader adapters are preserved.

## Required before delivery

The final reobfuscated jar-in-jar build, complete actual server regression, private client validation, multiplayer/optional-mod combinations and final packaged-resource verification are still pending. In particular, the vehicle collision override, full entity tick/damage boundaries, native species movement, updated consumption and actual mechanical-target damage must pass in Minecraft. The opt-in QA port is being compiled separately; a JVM pass alone does not establish these results.

No user save or installed playable instance was replaced. Delivery will use E: folders, not desktop ZIPs. Android/FCL device testing has not been performed.


## Actual runtime follow-up, 2026-10-31

The earlier 1,118 JVM checks passed before these native-runtime corrections. That result is not an acceptance claim for the final package.

- Forge launch exposed an ordinary saddle access interface inside the reserved Mixin package. It now lives outside that package; pig and strider accessor mixins implement the same interface.
- XZ is both embedded for distribution and declared as a Minecraft library for userdev launches. Compiling with an implementation dependency alone did not make it available to Forge's game classloader.
- Both dimension types retain the 1.20 UniformInt `value` envelope. A regression parses both files through the actual 1.20 DimensionType codec. The sample advancement also uses 1.20 item-predicate arrays.
- Native 1.20 bowl foods, suspicious stew and honey bottles predate consumable remainder components. Their containers now use a shared version adapter; custom crafting remainder hooks retain precedence.
- LivingBreatheEvent in 1.20 has a separate canRefillAir gate. Acquired gills and exposed tracheal vents now enable that gate when their paid physiology supplies a refill.
- Ordinary infected-host melee preserves the bounded 1.21 attack box rather than the older width-squared Mob fallback. The existing tissue-strike controller is connected to the host tick again; an omitted call left the full implementation idle. Existing real organ windup/contact/grab/retraction tests exercise this path.
- The full opt-in runtime suites and their test-only food/lifecycle/position adapters are reproducible through repository build flags. Default builds do not compile QA roots.
- Forge's GameTest entry initially generated a normal overworld. The incomplete r6 run was interrupted during sustained chunk generation; its 47 observed failures and thread dump are retained externally. Fresh GameTest runs now explicitly prepare a disposable flat directory and reject an existing incompatible configuration without modifying it.
- r7 is still running; it already passes the original bowl/vent/clone-fixture checks but has further unresolved failures. Several changes above were made after its classes loaded. Neither this document nor a Gradle exit code alone establishes final server/client/compatibility acceptance.

The obsolete city transit recipe has been removed from source. The next normal resource build and final archive check must verify that no stale recipe remains. The legacy registry item stays available for save compatibility.
### Actual Forge server round 7 follow-up

The fresh flat server completed all 526 cases with 44 required failures (`forge-server-r7.log`); it is not accepted. The previous 1,118-test JVM pass predates these runtime corrections.

The runtime failures exposed two further missed connections in the catch-up: `BuildingInfectionSystem` had not been registered on the Forge bus, and the host's existing `tickTissueStrike` had no caller. Both connections are restored. The consumption settlement also still called the obsolete second heal after `AssimilationSystem.settle(..., true)`; the extra call is removed and the existing exact-once reward checks are retained.

The ecology frontier fixture now excludes its unrelated architectural core and restores its altered spawn interval on failure as well as success. Forge's different batch placement could put that core in the neighbour cell; its first assertion then left a 24,000-tick interval behind, contaminating later birth tests. The carrier fixture retains real AI navigation and allows its actual bounded search/approach/transfer phases to finish. No production readiness, nutrient, protection or population gate was bypassed. These changes require a new server run.

The ordinary landing regression also exposed Forge 1.20 reporting a successful hurt call after a canceled LivingHurtEvent. Its physical impulse now requires real health or absorption loss; a veto restores the previous velocity. Fresh dedicated QA worlds use seed 0, matching reproducible geography assumptions instead of an arbitrary random seed. Full server and final artifact acceptance remain pending.

### Continued parity review and actual server rounds 8–12

The shared feature source is kept on the existing Forge adapter layer. Body comparisons are review candidates, not a proof of behavioral parity. Additional omitted calls were restored: destination-aware Blacklight World travel rather than the old hard-coded city, organ grab range, crest knockback attributes, tissue-grapple restraint, and the current combo transition window. The obsolete tutorial popup and developer-only mouse-key interception are removed from normal play.

Client review also restored the scoped consume-render lifecycle, including cancelled or nested render calls; after-Post cleanup alone leaks a pushed pose when another renderer cancels the event. Infection rendering now publishes the existing late organ grafts, surface geometry, transformed native parts and copied anatomical coats. The obsolete generic box decoration is no longer combined with the newer anatomy renderer. These changes require actual client rendering checks; compilation and server passes cannot establish visual quality.

Actual native results remain explicitly distinct:

- r8: 526 completed, 5 required failures.
- r9: 526 completed, 1 required failure (civilian slots fixture).
- r10: 526 completed, 9 required failures, so the earlier near-pass is not accepted. The civilian fixture counted infected bodies against the wrong budget; it now establishes exactly two civilian slots and retains the exact two-birth assertion. Late disk-loaded bodies from preceding component batches are isolated only in the disposable GameTest server; new births still use production quotas, collision and join callbacks. The mature carrier keeps real navigation on a supported floor and restores fixture state after either outcome.
- r11: incomplete, server crashed while a common infected executed capture checks after `super.tick()` removed its body on the final death tick. Forge correctly invalidated its capability. The body now returns immediately after removal, without reviving or inventing capability state. Two actual final-death cases cover common infected with and without AI.
- r12: started with the full suites and the additional entity-lifecycle suite; not yet an accepted result at the time of this entry.

The 1,119-test JVM pass predates these latest changes. Final JVM, server, client, coinstalled-mod, multiplayer and ordinary packaged-artifact checks remain required. No zero-bug or final-package claim is made.

### Actual client login and render acceptance in progress

r12 completed all 535 required server checks: 534 passed; the idle carrier scenario alone failed. Its isolated eight-case actual-server subset passed. The full fixture now establishes an idle subject without stale nearby combat actors from earlier disposable batches; it still exercises real navigation and resource transfer. Another full run is required.

The first actual client exposed a separate login regression: the registry now has 34 synchronized attachment fields, but the old envelope decoder allowed only 32. The bounded envelope now accepts at most 64 fields, validates its outgoing count too, and negotiates protocol `forge-1.20.1-18-full-sync`. The login-envelope test builds its table from the actual registered synchronized fields, rather than a hand-written 31-field sample. Seven packet-codec checks passed with no failures, errors or skips. The next actual client successfully joined and consumed the expanded snapshot.

The native visual fixture completed 66 captures, but this is not a visual acceptance claim. Inspection exposed an out-of-frame photographer and an unlit artificial high platform. A QA-only camera wrapper now applies the observer immediately after the actual camera setup, before the view matrix. That corrected wall framing. Lighting diagnostics distinguish real native light values from rendering failures; the photographic stage now uses the naturally lit flat-world ground. These fixture changes do not alter production camera or lighting rules.

An independent fresh-world native framebuffer check passed with actual skylight 15, baseline luminance 0.8303, sprint luminance 0.8112, a preserved foreign framebuffer binding and OpenGL error 0. The earlier attempted fixture copied GameTest metadata containing test-only registry IDs and stopped at a missing-mapping confirmation; it was retained as evidence and replaced by a new native fresh-world bootstrap. Such GameTest worlds are not playable release saves.

Updated full JVM, combined full server, remaining mod combinations, actual multiplayer, all final visuals and ordinary reobfuscated artifacts remain pending. QA observer sources and config are opt-in and must be absent from distributable jars.


### Server completion and release-input audit

The native Forge r13 run completed all **535 required checks successfully** and exited normally after chunk storage finished flushing. The shutdown thread dump showed region-file writes, not a deadlock. This result predates the following QA-only changes and does not replace remaining compatibility, multiplayer or packaged-artifact checks.

Linux CI exposed an outdated release-input manifest: six existing motion entries had stale sizes and seven newly synchronized files were absent. All 260 Forge input files were compared byte for byte against the current NeoForge profile before copying its manifest. The collector now includes `evolved-poses.data`, matching the runtime packer. All ten release-collector tests pass. No motion bytes or checks were removed.

The Forge bulk GameTest suite also exposed NightConfig autosave/file-watcher interference: hundreds of transient `ConfigValue.set` calls caused concurrent disk reload/correction, duplicated TOML tables and reset scenario values. A QA-only GameTest-server bootstrap now requires actual framework-loaded config files to parse and validate from disk, neutralizes their test-process reload callbacks while retaining the watch entries for normal unload, and binds independent parsed in-memory configurations for scenario overrides. Normal servers, integrated play and client settings keep the normal Forge config behavior; the helper is excluded from release jars. A fresh combined CI run remains required.

The current NeoForge revision `069f1b5d` passed cloud build, version policy and its actual 524-case server suite, plus 13 independent-passive and 17 coinstalled-passive checks. Forge's real TaCZ/Potentia/Spore/Twilight combination is being run separately; these results do not yet establish that combination.


### Real optional-mod round and compressed artifact verification

The first actual TaCZ 1.1.8 / Potentia / Spore 2.2.0j / Twilight 4.3.2508 server combination completed 31 checks with three failures. TaCZ's actual thirty-round armor-penetration magazine and a 60,000-block-height dive against the registered Potentia zombie both passed. A later round passed 31 of 32 checks, including cancelled Lich and Hydra hits; its remaining Titan attack fixture had selected an unrelated Java base present in the same vendor JAR. The registered zombie is a MCreator Monster implementation. The fixture now calls that actual mob's melee path with a large attack attribute; this does not establish support for different Titan implementations or arbitrary direct setHealth/deletion code.

Forge 1.20 can return true after LivingHurtEvent cancels damage. The optional Twilight bridges now require real health/absorption loss before accepting the mechanism hit or removing a shield. The budget-refresh fixture now observes the configured single-hit cap (6% by default), separately from the multi-hit window cap (15%). A fresh combination run is pending.

The old Gradle and Python artifact checks still expected uncompressed motion/map ZIP entries. Both now invoke the actual bounded Java decoders to compare complete logical inventories and restored bytes. Source-manifest, Forge refmap, Java 17 class and reobfuscated-jar identity checks remain. The Linux server workflow now includes the same release/building/gunplay suites as the local 535-case round. These corrected gates need a fresh cloud run.
