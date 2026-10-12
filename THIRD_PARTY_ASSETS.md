# Resource inventory and third-party assets

## Repository runtime profile

At the author's request, standalone dev.7 includes the previously local complete
runtime profile in `art/motion-profile/`: 213 JSON motion files and 11 JSON-based
`.data` files. It includes movement, combat, weapon and utility transformation,
consume actor/victim choreography, armor growth, camera and HUD data. The files
are preserved byte-for-byte from the latest standalone development profile.
`art/motion-profile-manifest.json` records every source hash and byte count.

The project uses Prototype as a design/animation reference. Existing source
metadata and source-analysis documentation remain intact; adding a profile to
Git does not change those provenance records. The GPL code license does not
relicense separately owned dependency assets. This inventory describes the
actual build contents rather than making a commercial release certification.

A normal clone and `assemble` now include all 224 files in
`blacklight/bundled_motions/`. The `bundled` JAR/ZIP is the complete default build.
An explicit `-PbundleMotionDirectory=<directory>` replaces the whole profile;
that build uses the `external-profile` classifier. Build verification checks
presence and byte equality; repository builds additionally check the manifest
SHA-256 values. The former source-only/local-preview distinction is historical.
No original game archives, installers, dependency JARs or audio banks are included
in this commit.

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
