# Complete Blacklight runtime profile

These 251 runtime files include the 27 Devastator/Bulletdive skill clips added
on 2026-10-13. Skill clips retain their tested bytes and provenance; existing
branch-specific armor and weapon data is preserved.
The file inventory is in `../motion-profile-manifest.json` (byte sizes only).
JSON motions contain editable named tracks, times and transform matrices;
`.data` files also use JSON and contain timing, placement, camera and HUD data.

Default `assemble` and `runClient` use this directory. For deliberate development
overrides, `-PbundleMotionDirectory=<directory>` replaces the complete profile.
Do not remove required files or combine unrelated versions per file.

See `docs/BUNDLED_PROFILE_DEV7.md` and `THIRD_PARTY_ASSETS.md` for build contents
and existing provenance records.
