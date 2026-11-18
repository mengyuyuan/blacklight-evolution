# Vehicle audio

Reproduce with `node tools/build-vehicle-audio.mjs D:/path/to/ffmpeg.exe`.
All outputs and caches must remain on D: in this workspace.

The engine, wheel, track and rocket cues are deterministic project-authored synthesis.
Autocannon and coaxial variants edit the repository's existing firearm cues; the
tank cannon events reuse its existing cannon recordings. No Prototype recordings
are copied into these additions. This notice does not relabel any pre-existing
asset's authorship or licensing. See `manifest.json` for source paths and hashes.

Loops are four seconds, mono, 44.1 kHz. The client varies pitch and volume and
positions them at their vehicle. New one-shots have short fades and retain peak
headroom after Vorbis decoding. Runtime loading and playback need client QA in
addition to these file-level checks.
