# Hydra death Foley

The `sounds/hydra/death_roar_*.ogg` and `death_debris_loop.ogg` files are
project-authored derivatives of the existing Socapex recorded Foley library.
They are **not original Prototype audio**. Original private Patch metadata
establishes event roles, pitch intervals, loop identity and reference durations.

Credit: Philippe Groarke / Socapex (tarfmagougou), *Punches, hits, swords and
squishes*, CC BY-SA 3.0. The original archive and complete contributor notice
are retained in [the weapon-audio notice](../weapon-audio/README.md) and
[Socapex-LICENSES.txt](../weapon-audio/Socapex-LICENSES.txt).

Changes: voice resampling and time stretching, recorded wet/ground Foley layers,
mono conversion, filters, fades, limiting and OGG Vorbis encoding. These four
derived audio files remain CC BY-SA 3.0. Existing swing recordings are referenced
for the whoosh role under their existing attribution. No game waveform is copied.

Reproduction: `tools/build_hydra_death_audio.py`. No audio was played during this
work; timbre, mix, subtitles and real client playback still require acceptance.
