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
