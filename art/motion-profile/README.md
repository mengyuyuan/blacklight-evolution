# Complete Blacklight runtime profile

These 224 files are the unchanged profile used by the latest standalone client.
The inventory and original byte hashes are in `../motion-profile-manifest.json`.
JSON motions contain editable named tracks, times and transform matrices;
`.data` files also use JSON and contain timing, placement, camera and HUD data.

Default `assemble` and `runClient` use this directory. For deliberate development
overrides, `-PbundleMotionDirectory=<directory>` replaces the complete profile.
Do not remove required files or combine unrelated versions per file.

See `docs/BUNDLED_PROFILE_DEV7.md` and `THIRD_PARTY_ASSETS.md` for build contents
and existing provenance records.
