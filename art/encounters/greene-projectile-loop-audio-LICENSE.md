The `sounds/greene/projectile_biomass_loop.ogg` asset is an adaptation of the
existing `infection/resonance_charge_1.ogg`, `infection/mend_2.ogg` and
`infection/tension_3.ogg` project assets. Their recorded sources are Philippe
Groarke / Socapex (tarfmagougou), *Punches, hits, swords and squishes*, licensed
under CC BY-SA 3.0. This derivative is also CC BY-SA 3.0.

Source and license notices: [weapon audio credits](../weapon-audio/README.md)
and [contributor licenses](../weapon-audio/Socapex-LICENSES.txt).
Original publication: https://opengameart.org/content/punches-hits-swords-and-squishes
License: https://creativecommons.org/licenses/by-sa/3.0/

The `sounds/greene/projectile_fire_loop.ogg` asset is adapted solely from the
project-authored `vehicles/helicopter_jet.ogg` synthesis. It contains no external
recording or original game audio. Its source generator is
`tools/build_helicopter_source_layers.py`.

Changes for both loops: circular two-window overlap-add, resampling to an
integer cycle count, DC removal, peak normalization, a 5 ms cosine bridge at the
loop boundary and mono Vorbis encoding. The source file hashes and resulting
boundary/peak/RMS measurements are in `greene-projectile-loop-foley.json`.

Prototype metadata determines roles, parameters and event timing. These
waveforms are presentation adapters, not original Prototype audio or claims of
matching its timbre. No audio was played or subjectively evaluated in this change.
