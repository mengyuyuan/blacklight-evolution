# Optional YSM appearance bridge (preview)

NeoForge 1.21.1 can display the dedicated `alex_mercer_blacklight_bridge` YSM model while Blacklight owns its combat pose. Other YSM models continue through the existing rendering path. The integration requires official YSM and YSM Mapping API; neither implementation names nor third-party model assets are embedded in this change.

Build the opt-in model from our existing Alex Mercer YSM source:

```sh
python tools/build_ysm_bridge_model.py --help
```

The converter preserves the body texture and geometry, adds pose/visibility channels, and writes into a separate destination. Select the resulting `alex_mercer_blacklight_bridge` pack in YSM. Armor remains Blacklight's original `armor_block.json` geometry; this is not a new YSM armor model.

The bridge converts articulated pose matrices into local Bedrock channels and matches the reflected X basis when drawing weapon/utility attachments. It suppresses covered skin regions for the native block armor.

## Validation and remaining review

- All main Java sources compiled with Java 21 against the existing NeoForge classpath. This is not a successful Gradle build: Gradle is currently blocked by the local loopback/socket environment.
- A deterministic 240-pose, 35-joint round-trip check covers both normal and grip matrices.
- Official YSM 2.6.5 + Mapping API 0.1.8, Iris/Sodium and Complementary Reimagined were used for private gameplay recordings. Weapon poses, flight, native armor and slime reconstruction have visual samples.
- Full multiplayer, resource-reload recovery, all combinations and complete continuous visual acceptance remain unverified. This PR stays a draft until those checks are completed.

The disposable camera, recording controls, test-world setup and runtime implementation diagnostics are intentionally outside this repository and must not be packaged.
