# Resource inventory and third-party assets

## Repository runtime profile

At the author's request, the release audit extends standalone dev.7's previously local
runtime profile in `art/motion-profile/`: 240 JSON motion files and 11 JSON-based
`.data` files. It includes movement, combat, weapon and utility transformation,
consume actor/victim choreography, armor growth, camera and HUD data. The files
retain their source provenance. The 2026-10-13 addition contains 27 Devastator
and Bulletdive release, charge, recovery, aiming and landing clips, copied
byte-for-byte from the tested dev.9 skill profile. Existing branch-specific armor
data is retained; reference FX textures and source shaders are not added.
`art/motion-profile-manifest.json` records the current profile inventory and byte counts.

The project uses Prototype as a design/animation reference. Existing source
metadata and source-analysis documentation remain intact; adding a profile to
Git does not change those provenance records. The GPL code license does not
relicense separately owned dependency assets. This inventory describes the
actual build contents rather than making a commercial release certification.

A normal clone and `assemble` now include all 251 files in
`blacklight/bundled_motions/`. The `bundled` JAR/ZIP is the complete default build.
An explicit `-PbundleMotionDirectory=<directory>` replaces the whole profile;
that build uses the `external-profile` classifier. Build verification checks
presence, byte equality and manifest byte counts. Legacy manifests with SHA-256
values retain their hash check; release collection computes current input hashes
to compare the JAR and source snapshot without requiring stored hashes.
The former source-only/local-preview distinction is historical.
No original game archives, installers, dependency JARs or audio banks are included
in this commit.

## Current motion authorship and initial lineage

The current motions include the project's ongoing modification, tuning and
Minecraft adaptation work. Initial-source names, hashes and old provenance
labels do not establish that a current clip is an untouched conversion.
The metadata separates `provenance.currentStatus` (project-modified/adapted)
from existing `source` records (initial lineage). The legacy `privateNotice`
key remains for packaging compatibility, but obsolete local-build restrictions
have been replaced with descriptive metadata. This is not a per-keyframe
authorship audit or a claim that every keyframe was authored from scratch.
See [动作来源与本项目修改](docs/MOTION_PROVENANCE_ZH.md).

The 2026-10-14 metadata update preserves all animation and gameplay values.
The manifest contains current file hashes, which change when metadata changes;
source hashes continue to identify the original inputs. License files are unchanged.

## Independent runtime and authored resources

The runtime does not depend on Epic Fight, Epic ParCool or Weapons of Miracles.
The project-authored mathematical player bind hierarchy, continuous skin mesh,
motion sampler, action clocks and gameplay systems are implemented in this repo.
GeckoLib remains a separately installed required dependency; optional ParCool
compatibility does not embed its JAR or assets.

The approved infection geometry is combined with Minecraft's installed native
models and variant/resource-pack textures. Native textures are not copied into
the repository: local wound textures are derived during rendering. Editable
infection-part JSON, source hashes and the exporter are checked in. Hive,
hunter, armor and particle authoring records remain in `art/`, `tools/` and
existing visual documentation.

## Kenney Particle Pack 1.1

- Creator: Kenney Vleugels (Kenney.nl)
- Source: https://kenney.nl/assets/particle-pack
- License: Creative Commons Zero (CC0 1.0)
- License URL: https://creativecommons.org/publicdomain/zero/1.0/
- Imported files: `biomass_slash.png`, `destruction_burst.png`, `impact_smoke.png`
- Modifications: filenames changed; particles are tinted and animated at runtime.

The source pack explicitly permits use in personal and commercial projects. Attribution is
not required, but is retained here to preserve provenance.

## Rokoko free superhero motion capture

The Sovereign Wings body animation adapts two FBX captures from Rokoko's
official free superhero resource, credited to Sam Lazarus. The publisher
permits use in games and commercial projects; this resource is not relabeled
CC0 or covered by the project's code license. Source URL, hashes, exact
conversion steps and project edits are documented in
`art/sovereign-wings/FLIGHT_SOURCES.md`. Raw FBX files are not bundled.

## GreyFrogGames Flying Bat v1.0 — wingbeat animation

- Author: GreyFrogGames, 2024
- Source: https://greyfroggames.itch.io/flying-bat-character
- License: CC BY 4.0, https://creativecommons.org/licenses/by/4.0/
- Original license: `art/sovereign-wings/GreyFrogGames-License.txt`
- Download: `Tier1__FlyingBat.zip`, SHA-256
  `faedcc1eae288bf08a4680e53260616e3994f2ab09c0bd2bc215bc64ce9f4af7`
- Used: sampled `Fly` action, left Arm1–Arm4 rotational curves, in
  `assets/blacklight/animation/sovereign_wingbeat.json`.
- Changes: mirrored for the paired wing rig, retargeted axes and amplitudes,
  slower cadence for large wings, outboard finger spread, directional steering
  and continuous transitions. The original bat mesh and textures are not bundled.
- Reproduction: `art/sovereign-wings/extract_bat_wingbeat.py` with the original
  `FlyingBat.glb` as input. This animation retains its CC BY 4.0 license.
## Weapon and defense audio (2026-10-17)

Recorded source packs: StarNinjas sword sounds (CC0), artisticdude Swishes (CC0), and Philippe Groarke / Socapex combat sounds (CC BY-SA 3.0). Source URLs, archives and full upstream attribution are in `art/weapon-audio/`. Edited/mixed derivatives are distributed under CC BY-SA 3.0 separately from the code license. Packaged credits: `META-INF/licenses/blacklight-audio/`. See `docs/WEAPON_DEFENSE_AUDIO_ZH.md` for the 41 events / 123 variants and validation scope. No Prototype unpacked audio is added.


### Hydra and Hunter continuous skins and motion

`tools/author_connected_creatures.py` authors the runtime shared-vertex surfaces,
smooth joint weights and pixel atlases. The older cuboid authoring scripts supply
editable proportions and rig pivots; their flesh cubes are removed from the final
Gecko geometry. Hydra neck/body and Hunter torso/limbs are connected skins. Teeth,
and the paired mandibles remain separately articulated anatomical parts. The old
capture strand is removed; capture uses the two articulated horns.
The design uses first-Prototype creatures and generated concept sheets as visual
references. Extracted commercial mesh or texture files are not embedded in these
new skin assets.

The animations are **not all authored from scratch**.
`tools/retarget_creature_motion.py` adapts locally decoded Prototype 1 Hydra and
Brawler animation rotations to the project rigs. Hydra idle/emerge/slam/retract/grab/toss
and Hunter idle/walk/run/swipe retain source clip timing, with axis conversion,
retargeted joints and removed horizontal root motion. Emergence/exit vertical
motion is scaled to the authored body height. Exact clip names, input hashes and
joint maps are recorded in `art/hydra/motion-retarget-provenance.json`.
The carrying phases overlay `hyd_act_partitioned_grab` on `hyd_exit` and `hyd_enter`.
`hyd_grab_concrete` supplies the pickup and `hyd_ped_toss` the release motion.
The portal sequence, carrying socket, six-tick blends, and 3.1-second toss release
are project gameplay adaptations, not an original-game dimension travel sequence.
The player response combines the existing source-derived `reaction_body`,
`reaction_fall` and `basic_fall` poses with newly authored bracing, asymmetric
leg recoil, torso contact correction and hand IK. It is not a verified original
Hydra receiver animation. Player transport uses a synchronized animation
constraint, not Minecraft's riding or seated pose.
Hydra feeding and combat damage
windows are server-side adaptations, not a claim of verified original FIG events.
Existing source-derived motion notices and licenses remain applicable.

### Hunter rig calibration and player evade (2026-10-18)

`tools/hunter_retarget.py` calibrates the authored rest axes to the decoded
Brawler joints, corrects support height against Ball joints, and retains
source-derived rotation/timing. `art/hunter/rig-calibration.json` records the
rest-axis differences. These fixes do not change the source-motion lineage.
`tools/export_dodge_motion.py` adapts `alex_loco_diveroll` (FIG owner 7080) to the
independent player skeleton. `art/motion-profile/basic_dodge_roll.json` includes
source hash and frame segments. The source's end frame 55 is clamped to the
actual last frame 52. Six invulnerability ticks and MC displacement are gameplay
adaptations, not verified original values.

### Blackwatch reference rig, cloth and motion (2026-10-25)

Author: **1145homo**. The user supplied `blackwatch_two_hand_sneak.bbmodel`
(SHA-256 `4d1b2213f160aa7a5ece31131c77102d654474277b46a6f93c8624cba71b4696`)
and a screenshot in which the author permits direct use. This records the supplied
permission evidence; it does not invent a separate license. The editable original,
source index and relevant cropped attribution/permission evidence are kept in
`docs/制作资料/参考模型/`.

The runtime export retains the source bone data and 2,174 original linear keyframes.
The native two-segment arm rig, copied or explicitly retinted cloth regions and
light gun materials derive from this reference. The 5,349-cube source gun is not
imported as a runtime mesh. Eight lightweight weapon meshes are separately authored;
reload phases, shoulder constraints, reach correction and first-person hand placement
are project adaptations. Seven source clips are preserved; dodge has no gameplay
binding in this implementation and is not advertised as implemented behavior.

`tools/export_blackwatch_reference.py` reproduces the derived production profile,
atlases and item resources. `tools/preview_blackwatch_reference.py` captures actual
native model vertices and UVs for CPU previews. See the mapping report for exact
source regions, coordinate conversion and runtime sampling; these previews do not
certify an OpenGL client playtest.

### M6 ground vehicles and garages (2026-10-27)

The truck, armored vehicle and tank cuboids, two vehicle pixel palettes/textures,
editable Blockbench exports, named seat/exit/muzzle mounts, and the two dedicated
31 x 10 x 27 garage block layouts are project-authored M6 assets. The authoring
entry is `art/military-vehicles/vehicle-authoring.json`; its current SHA-256 is
`4ccafec7230accdac6d2d84c7ffbd043f2ece8d2b2848e9fe9eb98aa13523a21`.
Older `08cdbb98...` / `7c3c0604...` / `5d...` receipts describe historical authoring stages.

Prototype gameplay screenshots were viewed as silhouette/reference evidence;
source URLs and observations remain in `art/military-vehicles/reference-observations.json`.
Those commercial screenshots, extracted commercial vehicle meshes and commercial
vehicle textures are not included as runtime assets or copied into the M6 making
catalogue. Dimensions, health, speeds, ammunition, fuel and recipes are project
gameplay choices, not verified Prototype statistics or real military specifications.

The current eight scenes under `art/military-vehicles/previews/production/` capture
actual production ModelPart vertices/UVs and use a CPU raster preview. The ninth
PNG is their contact sheet. They are explicitly not Minecraft/OpenGL screenshots.
The new garage previews are offline views of the actual serialized NBT geometry
with approximate vanilla material colors; they are not game screenshots either.

Visible soldier passengers reuse the existing Blackwatch reference-derived rig,
cloth and source-derived motion described above. The new vehicle/garage authorship
statement does not relabel those pre-existing soldier assets as entirely original,
replace their permission evidence, or change their earlier attribution/license.
Current production data, original editable sources and file hashes are indexed in
`docs/制作资料/M6/data/source-manifest.json` and `docs/制作资料/M6_载具与车库.md`.
