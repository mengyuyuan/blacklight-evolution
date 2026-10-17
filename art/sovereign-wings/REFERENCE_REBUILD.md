# Reference-first wing reconstruction

The re-supplied 1536x1024 concept is authoritative. Bat footage controls joint
motion only. The procedural common-wrist fan lost the reference's nested arches
and chunky irregular plating; incremental attachment fixes do not correct it.

Visible proportions: shoulder root around model Y22-24, outer apex around Y41,
four individually arched rays descending to near or below foot height. The
inner arches are progressively lower and nearer the body. Width is around four
times the head width per side. Preserve the negative spaces between upper
branches and the ragged dark-red membrane below. Black near-matte plating,
chipped angular contours, broken crimson fissures with sparse hot highlights.
No feathers, matching parallel slats, straight mounting bar or armour backpack.

Reconstruct with four independent upper ribs and four distal rays per side,
rooted in paired skinned shoulder-back tissue. 19 joints including the central
frame. Infer the hidden rear plating and depths symmetrically. Keep the existing
budget of 3000 cuboids / 36000 triangles and two 256px atlases. Use fewer larger
irregular plate groups instead of many same-sized strips. Explicit branching
crack geometry and membranes remain pixel/cuboid style, not a reference decal.

Stages: source silhouette blockout with body-size proxy; orthographic checks;
then plate masses, cracks/tendrils and tissue attachment; real pose sampling,
head/ground clearance and client front/back/turn/flight review. Existing
versions remain comparison artifacts, not accepted release candidates.

## Second reference review — supersedes the generic nested-arch interpretation

The four visible descending branches are not four uniform, independent arches.
The source has an interwoven branching upper structure: a tall outer shoulder,
a lower hooked arch, a nearly vertical long inner blade with a lower bulge,
and an inward-curving shorter branch. Their roots merge into irregular tissue
around the shoulder/back. The outermost tip is notably shorter than the long
inner blade. Plate density, length, width and direction vary by anatomical zone.
Red tissue is exposed between black plates, not Y-shaped symbols over each tile.
The folded inset forms a narrow pointed bundle, not an open fan rotated backwards.

The v6 script now traces left-wing landmark chains from the supplied concept
using head top Y242, soles Y790 and centre X600 as a scale reference. This is a
perspective drawing, so hidden depth and mirrored right-side details remain
inferences. Static source currently has 1904 cuboids / 22848 triangles, 19 joints.
This is a reconstruction draft, not a fidelity-approved or runtime-tested model.
The rigid shoulder transition, repeated plate layout and membranes still need
refinement. No v6 asset was installed into runtime resources; wings.6 remains
the older model-v5 client. Do not call either model a completed faithful copy.


## v7 refined source and random fold overlap

v7 replaces the uniform shoulder connector with six intertwined skinned tissue
routes per side, distinct irregular plates, dark vessel beds beneath crimson
fissures, attached ragged membranes and small upper web remnants. The traced
branches retain unequal lengths, changes in thickness, and inward-curving tips.
Each upper branch and descending blade has an intermediate bend: 35 joints,
2800 cuboids / 33600 triangles. The model remains cuboid/pixel style.

Two fold targets use different depth ordering rather than flipping a rendered
texture. Every hinge is solved at its existing segment length. Tissue/membrane
corrections match the custom two-path skinning. GLB export must set
`export_anim_scene_split_object=False`, otherwise the exporter makes one clip
per node and playback only animates a subset of the wing.

The server chooses order only on flying→not-flying transitions and stores it in
the synchronized flight attachment; LAND→GROUNDED and rendering retain it.
The new rig is wired to NeoForge 1.21.1. Runtime flight regression checks and
client review are separate from neutral model/head/folding verification.
