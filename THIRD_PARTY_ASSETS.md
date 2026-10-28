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

A normal clone and `assemble` now include all 254 files in
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

The 2026-10-25 Evolved sample uses standard Minecraft player cuboid dimensions,
a project-authored 64x64 Runner1-inspired tactical skin. The adapted arm surfaces
retain the locally decoded `evolved_1stRunner_Blades_geo_runner1_arm_bladeShape`
geometry: its cutting profile, dorsal hooks and tendon openings are fitted to
Minecraft shoulder/elbow proportions. Source hashes and the adaptation are
recorded in `docs/design/evolved-complete-arms.json`. Source game torso/head
meshes and game textures are not embedded. `evolved-poses.data` contains the complete
67-joint Prototype 2 Evolved hierarchy and 27 locally decoded action/stance channels;
the original clip names and input hashes are recorded in
`docs/design/evolved-form-motion-sources.json`. The body retains
Minecraft-sized rest proportions. The original parent hierarchy, elbow, knee,
and action rotations drive the surface. Anatomical aim frames and bone lengths
adapt those channels to the standard block body, retaining the source's -Z
forward axis. `tools/fit_p2_evolved_arms.py` adapts the source arm geometry and
authors new pixel materials; the player weapon mesh is not used for these arms.
The reference channels retain their source lineage.

The Evolved combat revision also samples original motion-root travel. Minecraft
collision, navigation, contact windows and server damage are project code;
they are not a port of the source game's fight bytecode. Blade trails and charge,
impact and ground feedback use project-authored geometry and existing mod/vanilla
particles and sounds, guided by local effect references. Source shader programs,
particle textures and source audio are not embedded by this revision.

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

The infection audio bank (2026-10-28) uses the same approved Swishes and Socapex
recordings: 14 events, three variants each. These layered/filtered derivatives
retain CC BY-SA 3.0 and the same packaged upstream credits. Reproduction:
`tools/build_infected_audio.py`. No new proprietary game audio is bundled.


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

## Additional P2-derived Evolved weapon surfaces (2026-10-27)

`models/evolved_variants/` adds fitted weapon geometry from locally extracted
Prototype 2 `alex_hammerfist3`, `alex_claws3`, `alex_tendril3`, `heller_shield_3`,
`alex_claws2`, and `alex_tendril2`, alongside the previously disclosed Runner1 blades.
These surfaces remain source-game derivatives; they are not claimed as original
weapon geometry. Original topology is fitted onto the existing 67-joint MC rig.
Source colour regions are mapped to a small MC vertex palette; original game texture
files are not shipped. The model JSON and `docs/design/evolved-variants-geometry.json`
record source hashes and face counts. The new character clothing details and special
combat behavior are mod adaptations, not claims that those named variants or
mechanics exist unchanged in Prototype 2. Existing source-derived asset notices apply.

## Whole municipal Los Angeles Arnis scenery (2026-10-26)

Map data © OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright .
Geofabrik Southern California 2026-10-24; municipal relation 207359 frozen 2026-10-25.
The derived geographic database and block templates retain ODbL 1.0.
Arnis 3.3.0 (Apache-2.0) generated all municipal batches, including procedural interiors.
Elevation: Mapzen Terrain Tiles, USGS SRTM/GMTED2010, NOAA ETOPO1.
Land cover: ESA WorldCover 2021 v200, contains modified Copernicus Sentinel data (2021), CC BY 4.0.
Climate: Beck et al. (2023) Köppen-Geiger, CC BY 4.0.
Ecoregions: RESOLVE Ecoregions 2017 / Dinerstein et al., CC BY 4.0.
Native schematic tree author: paleozoey, https://www.planetminecraft.com/member/paleozoey/ .
Arnis assets/tree-packs/ATTRIBUTION.md credits the artist without stating a separate tree-art license;
the generator's Apache license is not relabeled as the artist's independent license.
Source links, modifications, hashes and reproduction: docs/worldgen/LOS_ANGELES_SOURCES.md.

## User-supplied precision Sovereign Wings appearance (2026-10-28)

The optional precision appearance is derived from the user-provided
`sovereign_wings_precision.glb`, original SHA-256
`e5067dba2f4388d8f462ca64b9b6271b5d68a57b2433f2b4e131a8d902542738`.
The user requested its integration and finer materials. This is not a claim that
the supplied model or its emission artwork was authored from scratch in this PR.

Front/back color refinement and a separate side-wall atlas were generated with
the built-in image generator; prompts and integration notes are preserved in
`art/sovereign-wings/PRECISION_TEXTURE_PROMPTS.md`. Original emission is retained.
The editable accepted neutral GLB, its SHA, material UV derivation and lossless
runtime packer are recorded in `art/sovereign-wings/PRECISION_APPEARANCE.md`.

Only the 157,041-triangle accepted playback mesh and refined textures are loaded
by the mod. The separate full-resolution original is not shipped as a runtime
asset. Fixed chest binding, mirrored whole-wing rotations and uniform folding
are project adaptations using the existing wing-motion controller. No new
Prototype 2 flyer motion or sound files are included. The classic wing model,
existing animation lineage and earlier attribution remain unchanged.
