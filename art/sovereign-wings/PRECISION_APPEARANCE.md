# Optional precision wing appearance

The existing classic wing mesh, textures and animation remain the default. The
skill screen's Settings → Wing appearance entry switches the rendered player's
appearance immediately. The choice is a server-owned SkillSettings attachment,
saved independently of skill unlocks, copied on death, and synchronized to clients.
Old saves default to classic. Protocol standalone-39-wing-appearance rejects old
clients that use the previous settings tuple.

The optional model comes from the user's sovereign_wings_precision.glb, refined
into sovereign_wings_neutral-v11.glb. Its SHA-256 is
`3a60ad19e56bb0f767c55f9c561699f0110ba10be6e6ef480ec95dd4e1569dba`.
The editable neutral GLB is checked in at
`precision/sovereign_wings_neutral-v11.glb`; it can be imported in Blender without
reconstructing the runtime binary. The full-resolution supplied original and
Blender working files remain separate from the installable mod. All 157,041 triangles of the accepted playback mesh,
including front, back and independent side walls, are preserved in PWG1.

## Motion boundary

This is a conservative rigid binding: one fixed chest attachment and two wing
fans. The attachment follows the player's chest; each fan rotates around its own
root using the existing flight clock. Ground folding mirrors the root rotations,
reverses the overlapping order with the existing random fold variant, and retracts
uniformly to 58%. There is no cross-finger skin blending or local stretching.
This does not claim a finished multi-joint retarget of the Prototype 2 flyer.

The same surface interface supplies leading edges and tips for the existing flight
effects. It does not change wing abilities, upgrades, movement speed or damage.

## Rebuild

The runtime assets are checked in; normal Gradle builds do not need Python or
Blender. To regenerate after explicitly reviewing the editable source,
install NumPy and Pillow, then run from the repository root:

```powershell
python art/sovereign-wings/pack_precision_rigid.py art/sovereign-wings/precision/sovereign_wings_neutral-v11.glb --report build/reports/precision-wing-export.json
```

The packer verifies the accepted source hash and emits bounded big-endian mesh
groups with deterministic gzip compression. It preserves every color triangle;
only fully black padded texture rectangles can be skipped in the additive glow
pass. Color and emission UVs are independent. Runtime data is shared once across
renderer instances. PrecisionWingModelTest checks the actual exported mesh for
rigid shape preservation, a stationary attachment, ground clearance, overlap
reversal and matching effect anchors. SkillSettingsTest covers saved and network
round trips and preservation of other preferences.

## Materials

The refined front/back colors and dedicated side atlas were made with the built-in
image generator, keeping the supplied silhouette and biological material layout.
The emission artwork is preserved from the supplied asset. The base pass receives
Minecraft scene lighting; the restrained emission pass is full bright. This does
not require a shader pack. See PRECISION_TEXTURE_PROMPTS.md for generation records.
