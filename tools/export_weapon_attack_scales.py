"""Map verified source scale channels to the existing project's animation clocks.

Input decoded files stay outside the repository. Only the selected arm scale
curves and their source hashes are exported; no body animations are overwritten.
"""
import argparse
import bisect
import hashlib
import json
import math
from pathlib import Path
from motion_provenance import motion_notice, motion_provenance


def export(decoded, contract, motions):
    config = json.loads(contract.read_text(encoding='utf-8'))
    sources, cache, clips = {}, {}, {}
    bones = ('Forarm_R', 'Wrist_R', 'Forarm_L', 'Wrist_L')

    def channels(folder, name):
        key = folder + '/' + name
        if key not in cache:
            raw = (decoded / folder / (name + '.json')).read_bytes()
            source = json.loads(raw)
            cache[key] = {g['name']: c for g in source['groups'] for c in g['channels']
                          if g['name'] in bones and c.get('semantic') == 'SCL'}
            sources[key] = hashlib.sha256(raw).hexdigest()
        return cache[key]

    def sample(cfg, stage, frame):
        tracks = channels(cfg['folder'], stage.get('source', cfg['source']))
        row = []
        for bone in bones:
            c = tracks.get(bone)
            if c is None:
                row.extend([1, 1, 1])
                continue
            i = max(0, bisect.bisect_right(c['frames'], frame) - 1)
            j = min(i + 1, len(c['frames']) - 1)
            t = max(0, min(1, (frame-c['frames'][i])/(c['frames'][j]-c['frames'][i]))) if j != i else 0
            xyz = [c['values'][i][a]*(1-t)+c['values'][j][a]*t for a in range(3)]
            # Target meshes extend from the elbow along +Y, source bones along X.
            row.extend([xyz[1], xyz[0], xyz[2]])
        # A blade spans the forearm and distal mesh in one draw group. Keep
        # its parent length in the distal scale instead of fading it back to
        # the wrist's almost-unit scale before the aerial impact.
        if cfg.get('inheritForearmLength'):
            row[4] *= row[1]
            row[10] *= row[7]
        return row

    def generate(cfg, stages, times):
        frames = []
        for time in times:
            clock, previous = 0, None
            for index, stage in enumerate(stages):
                end = clock + stage['seconds']
                if time < end-1e-8 or index == len(stages)-1:
                    u = max(0, min(1, (time-clock)/stage['seconds']))
                    row = sample(cfg, stage, stage['start']+(stage['end']-stage['start'])*u)
                    blend = stage.get('blend', 0)
                    if previous is not None and blend and time-clock < blend:
                        w = max(0, min(1, (time-clock)/blend)); w = w*w*(3-2*w)
                        row = [a*(1-w)+b*w for a,b in zip(previous, row)]
                    frames.append(row)
                    break
                previous = sample(cfg, stage, stage['end'])
                clock = end
        return frames

    def authored_curve(keys, times):
        """Project adaptation for clips whose body source has no weapon SCL track."""
        if len(keys) < 2 or keys[0]['time'] != 0 or abs(keys[-1]['time']-times[-1]) > .025:
            raise ValueError('Project scale envelope must cover the complete body clock')
        previous = -1
        for key in keys:
            if not math.isfinite(key['time']) or key['time'] <= previous:
                raise ValueError('Project scale times must increase')
            previous = key['time']
            if len(key['values']) != 12 or any(not math.isfinite(v) or not .1 <= v <= 8 for v in key['values']):
                raise ValueError('Invalid project scale values')
        result = []
        for time in times:
            a = max(0, bisect.bisect_right([k['time'] for k in keys], time)-1)
            b = min(a+1, len(keys)-1)
            u = max(0, min(1, (time-keys[a]['time'])/(keys[b]['time']-keys[a]['time']))) if a != b else 0
            u = u*u*(3-2*u)
            result.append([x+(y-x)*u for x,y in zip(keys[a]['values'], keys[b]['values'])])
        return result

    for name, cfg in config['moves'].items():
        for suffix, stage_key in [('', 'stages'), ('_charge', 'chargeStages'), ('_charged', 'stages')]:
            path = motions / (name+suffix+'.json')
            if not path.exists():
                continue
            times = json.loads(path.read_text())['animation'][0]['time']
            stages = cfg[stage_key]
            if abs(times[-1]-sum(s['seconds'] for s in stages)) > .025:
                raise ValueError('Body clock and scale contract disagree: '+name+suffix)
            frames = generate(cfg, stages, times)
            envelope = cfg.get('projectScaleEnvelope')
            if envelope:
                frames = authored_curve(envelope['charge' if suffix == '_charge' else 'attack'], times)
            if suffix == '_charged':
                charge = clips[name+'_charge']['frames'][-1]
                cut = round(cfg['release']*60); end = min(len(frames)-1, cut+cfg['chargedBlendFrames'])
                for i in range(cut,end):
                    w = (i-cut)/(end-cut); w = w*w*(3-2*w)
                    frames[i] = [a*(1-w)+b*w for a,b in zip(charge,frames[end])]
            clips[name+suffix] = dict(times=times, frames=frames)
    result = dict(schema=1, bones=list(bones), axisMapping=config['axisMapping'], clips=clips,
                  inheritedForearmLength=[name for name,cfg in config['moves'].items()
                                           if cfg.get('inheritForearmLength')],
                  projectAdaptations={name: cfg['projectScaleEnvelope']['evidence']
                                      for name,cfg in config['moves'].items() if 'projectScaleEnvelope' in cfg},
                  sources=sources, contractSha256=hashlib.sha256(contract.read_bytes()).hexdigest(),
                  privateNotice=motion_notice('Source SCL timing adapted to the existing Blacklight weapon meshes; local segment scales are not cumulative world-space multipliers.'),
                  provenance=motion_provenance())
    path = motions/'weapon-attack-scales.data'
    path.write_text(json.dumps(result,separators=(',',':'),allow_nan=False),encoding='utf-8')
    print(f'Exported {len(clips)} synchronized scale clips to {path}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--contract', type=Path, required=True)
    parser.add_argument('--motions', type=Path, required=True)
    args = parser.parse_args()
    export(args.decoded, args.contract, args.motions)
