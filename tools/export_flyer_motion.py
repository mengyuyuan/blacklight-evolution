"""Retarget verified P2 flyer curves; preserve every authored cube and UV.

Only wing ownership changes: humerus/membrane, radius, then wrist membrane.
Flight controller constants are Minecraft tuning, not decoded FIG parameters.
"""
import hashlib, json
from pathlib import Path
import export_encounter_source_motion as source

ROOT=Path(__file__).resolve().parents[1]
geo_path=source.ASSETS/'geo/flyer_mc.geo.json'
doc=json.loads(geo_path.read_text(encoding='utf8'))
bones=doc['minecraft:geometry'][0]['bones']
before=sorted(json.dumps(c,sort_keys=True) for b in bones for c in b.get('cubes',[]))
if not any(b['name']=='left_forearm' for b in bones):
    revised=[]
    for b in bones:
        revised.append(b)
        if b['name'] not in ('left_arm','right_arm'):continue
        side=b['name'].split('_')[0];sign=1 if side=='left' else -1
        cubes=b['cubes'];assert len(cubes)==17
        b['cubes']=[cubes[0],cubes[2]]
        revised.extend([
            {'name':side+'_forearm','parent':side+'_arm','pivot':[sign*12.7,16.1,.8],'cubes':[cubes[1]]},
            {'name':side+'_hand','parent':side+'_forearm','pivot':[sign*20.4,14.5,3.3],'cubes':cubes[3:]}])
    doc['minecraft:geometry'][0]['bones']=bones=revised
assert before==sorted(json.dumps(c,sort_keys=True) for b in bones for c in b.get('cubes',[]))
geo_path.write_text(json.dumps(doc,separators=(',',':')),encoding='utf8')

old_mapping=source.mapping_for
def mapping(species,rig,geo):
    result=old_mapping(species,rig,geo)
    result.update(tail='Tail_1',left_leg='Knee_L',right_leg='Knee_R')
    return result
source.mapping_for=mapping
clips={
    'idle':'flyer_com_idle','flap':'flyer_loco_casual_flapping','glide':'flyer_gliding_idle',
    'boost':'flyer_com_attackspeed','ram':'flyer_com_air_ram',
    'collision':'flyer_gethit_surface_collision','hit_front':'flyer_gethit_front',
    'hit_back':'flyer_gethit_behind','birth':'flyer_birth_from_pustule'}
profile=(Path('D:/PrototypeReference/p2/input/p2/flyer.p3d'),'flyer_skeleton','flyer_com_idle',clips)
report=source.export('flyer',profile)
report['geometry']='Existing authored 63 cuboids and UVs preserved; wings articulated at authored elbow/wrist endpoints.'
report['geometrySha256']=hashlib.sha256(geo_path.read_bytes()).hexdigest()
report['boundary']='Clip names/curves/skeleton verified in P2. FIG dive controller hash 3cbd105ad588347f references idle/attackspeed/air_ram; collision references surface_collision. Unknown packed controller fields are NOT labeled as decoded speed, damage or timing. Phase transition and flight steering constants are Minecraft adaptations.'
report['controllerOffsets']=[984801,985329,985865]
dest=ROOT/'art/encounters/flyer-source-motion.json'
dest.write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
