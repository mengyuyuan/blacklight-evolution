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
`art/motion-profile-manifest.json` records every current profile file hash and byte count.

The project uses Prototype as a design/animation reference. Existing source
metadata and source-analysis documentation remain intact; adding a profile to
Git does not change those provenance records. The GPL code license does not
relicense separately owned dependency assets. This inventory describes the
actual build contents rather than making a commercial release certification.

A normal clone and `assemble` now include all 251 files in
`blacklight/bundled_motions/`. The `bundled` JAR/ZIP is the complete default build.
An explicit `-PbundleMotionDirectory=<directory>` replaces the whole profile;
that build uses the `external-profile` classifier. Build verification checks
presence and byte equality; repository builds additionally check the manifest
SHA-256 values. The former source-only/local-preview distinction is historical.
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

## Weapon and defense audio (2026-10-17)

Recorded source packs: StarNinjas sword sounds (CC0), artisticdude Swishes (CC0), and Philippe Groarke / Socapex combat sounds (CC BY-SA 3.0). Source URLs, archives and full upstream attribution are in `art/weapon-audio/`. Edited/mixed derivatives are distributed under CC BY-SA 3.0 separately from the code license. Packaged credits: `META-INF/licenses/blacklight-audio/`. See `docs/WEAPON_DEFENSE_AUDIO_ZH.md` for the 41 events / 123 variants and validation scope. No Prototype unpacked audio is added.
