"""Map verified source scale channels to the existing project's animation clocks.

Input decoded files stay outside the repository. Only the selected arm scale
curves and their source hashes are exported; no body animations are overwritten.
"""
import argparse
import bisect
import hashlib
import json
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
            if suffix == '_charged':
                charge = clips[name+'_charge']['frames'][-1]
                cut = round(cfg['release']*60); end = min(len(frames)-1, cut+cfg['chargedBlendFrames'])
                for i in range(cut,end):
                    w = (i-cut)/(end-cut); w = w*w*(3-2*w)
                    frames[i] = [a*(1-w)+b*w for a,b in zip(charge,frames[end])]
            clips[name+suffix] = dict(times=times, frames=frames)
    result = dict(schema=1, bones=list(bones), axisMapping=config['axisMapping'], clips=clips,
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
