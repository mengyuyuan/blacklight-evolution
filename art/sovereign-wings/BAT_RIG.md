# Bat-informed articulation revision

The user rejected the wings.3 flight structure on 2026-10-16. It is a
superseded experiment, even though its automated/runtime checks pass.

## Reference and reconstruction

Reference: Breuer Lab's wind-tunnel footage of Cynopterus brachyotis,
https://sites.brown.edu/breuerlab/media/ and the linked Cyno.m4v (2010 study).
The 19.87-second, 400-square slow-motion clip was examined as successive
frames. At recovery the arm retracts and the handwing folds; it opens before
the next power stroke. Supporting paper: Fan et al. 2024,
https://arxiv.org/html/2411.01434v1, particularly figure 1 and its distinction
between wrist flexion and whole-wing elevation. The footage is reference
only, outside the repository/distribution. No downloaded animation is claimed.

Maintain the original concept's angular black armour, red fissures, torn
oxblood tissue, four long rays and descending rest silhouette. This is a
fictional back-mounted appendage, not a literal bat or a humanoid replacement.
Infer the hidden surfaces symmetrically. Use the already-established
accuracy-first budget: <= 3,000 cuboids / 36,000 triangles, two 256px atlases,
opaque cutout + emissive passes. No smooth tubes or realistic fur.

## Structural correction

The former root owned the entire shoulder-to-tip arch, while finger bones
were parented to an unrelated elbow pivot. Membranes were rigid on that elbow
even when the fingers moved. There was no wrist, and braces bypassed joints.

Split the leading spar at its actual elbow. Add a wrist at the high arch.
Originate four finger chains at that wrist, with metacarpal sections leading
to the existing descending rays. Assign armour to its actual rigid section;
remove braces that bypass the elbow. Attach membranes/vessels to neighbouring
finger transforms with vertex weights, while armour remains rigid cuboids.
Target 15 joints (mount + 2 * shoulder/elbow/wrist/four fingers).

The Blockbench cuboid source preserves geometry and the rigid hierarchy.
Continuous membrane weights are a runtime extension, not native Bedrock
animation data; do not claim the .bbmodel reproduces that deformation.

## Motion and checks

Downstroke: extend arm/wrist and spread fingers. Late downstroke/recovery:
flex elbow, fold the handwing in toward the body, twist and narrow the fan.
Reopen smoothly before the next power stroke. Preserve directional blending
and stable clocks, with a slower fictional large-wing cadence, not a claim
of reproducing an actual bat's frequency or measured joint angles.

Validate blockout before detail, then joint attachment, membrane boundary
motion, mirrored flight, uninterrupted loops, and actual game capture. Keep
model-v2 and its captures intact. Blender/Blockbench MCP is unavailable;
use the deterministic source builder and installed Blender CLI.

## Back attachment correction (wings.5 / model-v4)

The wings.4 saddle sat at Y=24 model units, near the neck. The captured
cruise/boost head also leans backwards relative to the chest; checking only
hover fingertips therefore missed intersections with roots and inner rays.
The complete source rig and geometry move down 7.2 units and back 1.6 units.
Each side moves out 3.2 units; the central saddle widens to join both hinges.
The flight fan opens outboard (root Z -35 degrees mirrored) and gains 15
degrees of rear sweep. A 55-degree wrist rest compensation keeps the long
rays on their own side as the shoulder opens outward. The power stroke and joint recovery remain active.
Deployment scales about the actual JSON mount pivot, no longer a hard-coded
neck-height point. These are mesh/visual changes only, not camera or physics.

The new head-clearance regression samples six actual body clips, 24 body
positions, 24 independent wing phases and left/neutral/right steering. It
checks cuboid corners and centres (including two-bone membrane weights) against an enlarged head-space box. This
is a sampled geometry regression, not a continuous collision proof, and visual review in the client is still required.

Grounded wrist rest compensation remains active after landing, raising the
long tips above the feet despite the lowered saddle. Flight interpolation
keeps this wrist rest value rather than dropping back to zero on touchdown.

## Authoritative current revision

The low saddle / flattened fan experiment above was rejected by the user.
Use model-v5 and ORGANIC_ROOT.md for the current wings.6 design. The original
concept determines appearance; bat references inform joint motion only.
