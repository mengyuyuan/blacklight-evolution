# Creature voice fallbacks

The new `common_attack_*`, `common_death_*`, `hunter_grunt_*`, `hunter_roar_*`
and `hunter_death_*` OGG files are authored adaptations of the project's existing
recorded Socapex library. They are **not Prototype game audio**. Original FIG
events establish the role and phase of a sound; they do not establish that these
fallback voices reproduce its original timbre.

Source: Philippe Groarke / Socapex (tarfmagougou), *Punches, hits, swords and
squishes*, CC BY-SA 3.0. The archive and contributor notice are retained in
[`../weapon-audio/README.md`](../weapon-audio/README.md) and
[`../weapon-audio/Socapex-LICENSES.txt`](../weapon-audio/Socapex-LICENSES.txt).
Source page: https://opengameart.org/content/punches-hits-swords-and-squishes
License: https://creativecommons.org/licenses/by-sa/3.0/

Changes: mono conversion, bounded resampling, optional recorded wet Foley layer,
filtering, fades, limiting and OGG encoding. These derived audio files are also
CC BY-SA 3.0. This does not change the license of code or unrelated assets.
`hunter_hurt` intentionally selects the same recorded grunt bank as
`hunter_grunt`, matching the shared original Patch role. Landing and whoosh
events use the existing credited `infection/ground_*` and `infection/swing_*`
recordings, at separate gains.

Reproduction: `tools/build-creature-voice-fallbacks.py --ffmpeg <ffmpeg> --work <D-drive cache>`.
Asset hashes and exact transforms are in `authored-voice-fallbacks-20261117.json`.
No audio was played during this change. Subjective sound quality, mix and player
acceptance remain pending; code and source inspection are not listening tests.
