# Recorded weapon and defense audio

Source libraries selected for Blacklight by 末者. Edits: trim, mono conversion, pitch variation, layering, level matching and OGG encoding. Build with tools/build_weapon_audio.py (Python, numpy and FFmpeg).

- StarNinjas — 20 Sword Sound Effects (Attacks and Clashes), CC0 1.0: https://opengameart.org/content/20-sword-sound-effects-attacks-and-clashes
- artisticdude — Swishes Sound Pack, CC0 1.0: https://opengameart.org/content/swishes-sound-pack
- Philippe Groarke / Socapex (tarfmagougou) — Punches, hits, swords and squishes, CC BY-SA 3.0: https://opengameart.org/content/punches-hits-swords-and-squishes

Complete upstream contributors and source links for Socapex are included in Socapex-LICENSES.txt. Retain that file with this notice.

The derived audio cues under assets/blacklight/sounds/weapons and assets/blacklight/sounds/defense are provided under CC BY-SA 3.0. This audio notice does not change the license of the mod code or other assets. Source archives remain under their respective licenses above.

License links: https://creativecommons.org/licenses/by-sa/3.0/ and https://creativecommons.org/publicdomain/zero/1.0/

The draw, swing and heavy variations ending in _2 and _3 under sounds/weapon/backspines use the credited Socapex combat recordings and artisticdude swishes, trimmed, pitched and layered for the shoulder organ. These six mixes are licensed under CC BY-SA 3.0.

The combat remix applies new gain staging, soft limiting and mono Vorbis encoding to the six weapon banks and shoulder quill recordings. The recordings mixed for the new consume and skill cues come from the same attributed weapon Foley library listed above. These modifications and the resulting files under sounds/consume and sounds/skill are distributed under CC BY-SA 3.0. The shoulder impact events also reference these mixes. The recipe is tools/build_combat_audio_mix.py. Pass --baseline a pre-remix release JAR, --project the checkout and --evidence an output directory. No commercial game sound recordings were used in the remix.
