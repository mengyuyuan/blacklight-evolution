# Precision wing alignment and conservative cleanup — 2026-10-29

This follow-up preserves the full-repository audit snapshot recorded separately.
It does not replace that audit or claim a new complete repository review.

- Precision flight used a 0.32 stroke multiplier, an 18-degree downstroke limit,
  and reduced steering/pitch; it also omitted the classic elbow/finger chain.
- Reuse the same shared clock and central spar articulation for flight, with
  body-pinned proximal tissue. The outer fan remains rigid; this is not a
  claim of matching every independent classic finger's deformation.
- Ground overlap order and uniform ground retraction remain supported.
- Remove 83 disconnected reconstruction islands (3,021 triangles), retaining
  154,020 triangles and every retained position/normal/UV byte. All five
  texture files are identical to HEAD. No smoothing or blanket decimation.
- The cleanup tool reproduces the packed output from the original binary and
  is idempotent on the cleaned result.

Validation: 72 wing/flight JUnit tests pass. Regression tests
first reproduced the attenuated flight mapping (3 failures out of 4). Checks
cover cycle/steering mapping, body-space stroke, attachment pinning, free long
inner blades, moving-fan head clearance against animated body clips, retained
rigidity, overlap order, and absence of detached geometry. The stationary
accepted collar is checked for body pinning; its pre-existing decorative
contact with the expanded hat envelope is not presented as new clearance.

An isolated real Minecraft NeoForge 21.1.244 client passed appearance switching,
both ground overlaps, airborne rendering, and returning to the classic wing.
Private QA is excluded from the deliverable. No two-client server soak test
or complete multi-joint precision-wing rig is claimed.

Artifact: `Blacklight-Ascension-MC1.21.1-NeoForge-0.3.5-preview.20261029-wing-motion.3-bundled.jar`
SHA-256: `220d8ffd5c1ffa82366bddb7ea73258b047cebdd9a35c450227395a45cc29f90`
Evidence: `E:/Blacklight-Wing-Motion-20261029/acceptance.json`.
