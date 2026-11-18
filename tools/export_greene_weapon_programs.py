"""Export MotherCore weapon programs from the local decoded FIG (no engine execution)."""
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
REF = Path('D:/PrototypeReference/p1')
SOURCE = 'comparison/mother_fig.p3d.rz'
records = [r for r in json.loads((REF/'boss-fig-audit.json').read_text())['records']
           if r['source'] == SOURCE and r['owner'].startswith('mother_core/')]
by_offset = {r['offset']: r for r in records}
motion = json.loads((ROOT/'src/main/resources/assets/blacklight/models/encounter_elizabeth_greene_source.json').read_text())
clip = motion['clips']['VOLLEY']

def program(name, execute, shots, state=None):
    command = by_offset[execute]
    owner = command['owner']
    tracks = sorted((r for r in records if r['owner'] == owner and r['type'] == 'PuppetAnimation'), key=lambda r:r['fields']['TimeBegin'])
    segments = []
    for track in tracks:
        f = track['fields']
        segments.append(dict(at=f['TimeBegin'], end=f['TimeEnd'], **{'from':f['StartFrame']}, to=f['EndFrame'],
                             speed=f['Speed'], blendIn=f['BlendInTime'], blendOut=f['BlendOutTime'], offset=track['offset']))
    last = segments[-1]
    end = last['end'] if last['end'] >= 0 else last['at'] + ((len(clip['frames'])-1 if last['to'] < 0 else last['to'])-last['from'])/clip['fps']/last['speed']
    entries = []
    for offset in shots:
        track = by_offset[offset]
        f = track['fields']
        assert track['owner'] == owner+'/shoot'
        entries.append(dict(slot=int(f['GrabSlot']['name'].removeprefix('VomitGun')),
                            begin=command['fields']['TimeBegin']+f['TimeBegin'], end=command['fields']['TimeBegin']+f['TimeEnd'],
                            ammo=f['AmmoProfile']['name'], user=f['UserProfile']['name'], fixed=bool(f['FixedDirection']),
                            space=f['FixedDirectionSpace']['name'], euler=f['FixedDirectionDirection'], offset=offset))
    assert all(0 <= s['begin'] < s['end'] <= end for s in entries)
    return dict(id=name, owner=owner, state=state, executeOffset=execute, shots=entries,
                action=dict(clip='VOLLEY', animation='mother_vomit', mode='greene_weapon', fps=clip['fps'], seconds=end,
                            range=175, rangeMode='adapted', segments=segments,
                            windows=[dict(begin=s['begin'], end=s['end'], radius=0, joint='', localOffset=[0,0,0], damage=0) for s in entries]))

programs = [program('DEATH_FLOWER',21916,[22304,22404,22504,22604,22704],'DeathFlower'),
            program('SHOTGUN',23564,[23948,24048],'ShotgunBlast'),
            program('INSECT_SWARM',25532,[25972,26072,26172],'InsectSwarm'),
            program('RIFLE',27144,[27528],'Rifle'),
            program('ANTI_TANK',12636,[13224]),program('ANTI_AIR',12636,[13424])]
output = dict(schema=1, source=SOURCE,
              sha256=hashlib.sha256((REF/'input'/SOURCE).read_bytes()).hexdigest(),
              timing='Original seconds retained. MC requests fire at server samples in [begin,end); no fabricated 60 Hz substeps or inner shotgun batch.',
              selection='startup_fig on-foot parent XZ<150; ordered zero-weight Nodes select >60 DeathFlower, >40 Rifle, >25 ShotgunBlast, >1 InsectSwarm. Outer encounter scheduling and 175-unit acquisition/launch cap remain MC adapters; anti-vehicle programs require the matching occupied hostile hull.',
              deathFlowerTrackGroup=dict(offset=22240, begin=0, end=0.30000001192092896,
                                         note='All five nested Shoot windows start at zero and end before this group; Execute offset is added once.'),
              programs=programs)
path=ROOT/'src/main/resources/assets/blacklight/models/greene_weapon_programs.json'
path.write_text(json.dumps(output,indent=2)+'\n',encoding='utf8')
print('Exported',len(programs),'programs and',sum(len(p['shots']) for p in programs),'Shoot tracks')
