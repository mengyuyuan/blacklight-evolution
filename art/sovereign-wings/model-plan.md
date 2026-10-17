# Current revision: model-v11 / wings.9

User direction: replace the shoulder/upper-body surface; remove the armpit
fringe and excess fragments. The runtime skin is native articulated player
geometry, with covered original skin and clothing faces omitted. Root lobes
join that surface and the wing ribs; caps follow the correct native arm.
2,472 wing cuboids / 29,664 triangles; 35 wing joints. Two 256px wing atlases
and a 128px native-skin-layout body texture. Accuracy remains the priority;
3,400 cuboids is a ceiling, not a density target. See SOVEREIGN_FLIGHT.md for
verification and the client-acceptance limitation.

The following plan is historical and superseded.

# Model v2 — reference correction

Reference: original Sovereign Wings concept and wings-only-blockstyle-v3 sheet.
V1 failure: narrow linearly tapered rods, repeated single thin shingles, planar web and empty root. Game posing further flattened the silhouette.

Preserve the four descending fingers and exact arched silhouette. Broaden the upper 70% of each spar while retaining sharp cuboid tips. Build staggered, angled carapace plates around three-dimensional spars, denser root junctions and recessed crimson fissures. Infer hidden back surfaces symmetrically. Keep dark oxblood membranes and actual angular holes. No humanoid geometry.

Budget: <=3000 cuboids / 36000 triangles, 13 rigid bones, existing 256 atlas; accuracy prioritized. Reuse the established cuboid primitive system, not subdivision. Validate skeleton blockout, detailed orthographic front/side and game appearance separately. Blender/Blockbench MCP tools are unavailable in this session; use the local deterministic asset builder and Blender background renderer. Preserve v1 separately.
