"""Author additive helicopter Foley; original P1 samples are never read or copied.

Durations follow parsed source role metadata. Spectra/envelopes below are original
project synthesis, not an attempted waveform-identical reconstruction. Encoding
assets does not launch a client or play media.
"""
from pathlib import Path
import hashlib
import json
import subprocess
import math
import random
import sys
from array import array

ROOT = Path(__file__).resolve().parents[1]
ROOTS = (ROOT, Path('D:/blacklight-evolution-forge-1.20.1'))
FFMPEG = Path('D:/AI/VideoTools/node_modules/ffmpeg-static/ffmpeg.exe')
RATE = 44100
DURATIONS = {'jet': 3.5853333333333333, 'startup': 13.722993197278912,
             'winddown': 12.400181405895692}


def create(name, seconds):
    rng = random.Random({'jet': 17318, 'startup': 17319, 'winddown': 17320}[name])
    signal = array('f')
    low = high = tonal_phase = blade_phase = 0.0
    low_alpha = 1 - math.exp(-math.tau * 180 / RATE)
    high_alpha = 1 - math.exp(-math.tau * 3500 / RATE)
    hz = round(560 * seconds) / seconds
    for i in range(round(RATE * seconds)):
        time = i / RATE
        noise = rng.uniform(-1, 1)
        low += low_alpha * (noise - low)
        high += high_alpha * (noise - high)
        air = high - low
        if name == 'jet':
            # A distinct exhaust/air layer; existing turbine remains the engine layer.
            value = .28 * air + .09 * math.sin(math.tau * hz * time) + .04 * math.sin(2 * math.tau * hz * time)
        else:
            phase = time / seconds
            spool = .12 + .88 * math.sin(min(1, phase * 1.4) * math.pi / 2) ** 2
            if name == 'winddown':
                spool = .12 + .88 * math.cos(phase * math.pi / 2) ** 2
            tonal_phase += math.tau * (110 + 480 * spool) / RATE
            blade_phase += math.tau * (3 + 15 * spool) / RATE
            beats = max(0, math.sin(blade_phase)) ** 4
            value = (.18 * math.sin(tonal_phase) + .06 * math.sin(tonal_phase * 2)
                     + air * (.11 + .18 * spool) + beats * .12 * math.sin(tonal_phase / 8))
            # Authored envelope; no extracted source amplitude envelope.
            envelope = math.sin(min(1, phase * 8) * math.pi / 2) ** 2
            envelope *= math.cos(max(0, (phase - .65) / .35) * math.pi / 2) ** 2
            value *= envelope
        signal.append(value)
    if name == 'jet':
        edge = min(round(RATE * .005), len(signal) // 2)
        for i in range(edge):
            weight = math.sin(i / (edge - 1) * math.pi / 2) ** 2
            signal[i] *= weight
            signal[-i - 1] *= weight
    mean = sum(signal) / len(signal)
    peak = max(abs(value - mean) for value in signal)
    for i in range(len(signal)):
        signal[i] = (signal[i] - mean) * .62 / max(.001, peak)
    return signal


def main():
    report = []
    for name, seconds in DURATIONS.items():
        pcm = create(name, seconds)
        pcm_peak = max(abs(value) for value in pcm)
        relative = Path('src/main/resources/assets/blacklight/sounds/vehicles') / ('helicopter_' + name + '.ogg')
        path = ROOT / relative
        if sys.byteorder != 'little':
            pcm.byteswap()
        subprocess.run([str(FFMPEG), '-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
                        '-f', 'f32le', '-ar', str(RATE), '-ac', '1', '-i', 'pipe:0',
                        '-c:a', 'libvorbis', '-q:a', '5', str(path)], input=pcm.tobytes(), check=True)
        data = path.read_bytes()
        for root in ROOTS[1:]:
            (root / relative).write_bytes(data)
        report.append({'cue': name, 'sampleRate': RATE, 'mono': True, 'loop': name == 'jet',
                       'seconds': len(pcm) / RATE, 'authoredPcmPeak': pcm_peak,
                       'sha256': hashlib.sha256(data).hexdigest()})
    record = {'origin': 'Project-authored synthesis; source metadata informs role duration only. No original audio imported.',
              'generator': 'tools/build_helicopter_source_layers.py', 'cues': report,
              'validation': 'Asset encoding only. No playback, client rendering, compilation or tests in this batch.'}
    for root in ROOTS:
        (root / 'art/military-vehicles/helicopter-source-layer-foley.json').write_text(
            json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
