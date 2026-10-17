"""Blender Python: export the independent character rig and retargeted YSM keyframes."""
from pathlib import Path
import json,math,shutil,uuid,zipfile
from mathutils import Matrix,Vector
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mercer_rig_math import load_context
REPO=Path(__file__).resolve().parents[1]
OUT=REPO/'build/mercer-ysm'
SOURCE=REPO/'art/mercer-v4'
DEST=OUT/'YSM/alex_mercer_blacklight'
DEST.mkdir(parents=True,exist_ok=True)
context=load_context(REPO)
mesh=context['meshdata'];pivots={b['name']:b['pivot'] for b in mesh['bones']}
parents={b['name']:b['parent'] for b in mesh['bones']}
layout=json.loads((SOURCE/'layout.json').read_text())
geo=json.loads((SOURCE/'main.json').read_text());bones=geo['minecraft:geometry'][0]['bones'];byname={b['name']:b for b in bones}
coats={'CoatFrontLeft':[1.5,17,0],'CoatFrontRight':[-1.5,17,0],'CoatBack':[0,17,1]}
for name,p in coats.items():
    bone={'name':name,'parent':'UpBody','pivot':[-p[0],p[1],p[2]],'cubes':[]};bones.append(bone);byname[name]=bone
    layout['pivots'][name]=p;layout['parents'][name]='UpBody'
upcubes=byname['UpBody']['cubes'];authorcubes=[c for c in layout['cubes'] if c['bone']=='UpBody'];assert len(upcubes)==len(authorcubes)
kept=[];moved={}
for model,author in zip(upcubes,authorcubes):
    target=None
    if 'Front hem' in author['name'] or 'Lower flare' in author['name']:target='CoatFrontLeft' if author['name'].startswith('Left') else 'CoatFrontRight'
    if author['name']=='Back lower hem':target='CoatBack'
    if target:byname[target]['cubes'].append(model);moved[author['name']]=target;author['bone']=target
    else:kept.append(model)
byname['UpBody']['cubes']=kept
geo['minecraft:geometry'][0]['description']['identifier']='geometry.alex_mercer_blacklight'
(DEST/'main.json').write_text(json.dumps(geo,ensure_ascii=False,indent=2),encoding='utf8')
(DEST/'layout.json').write_text(json.dumps(layout,ensure_ascii=False,indent=2),encoding='utf8')
for name in ['arm.json','mercer.png']:shutil.copy2(SOURCE/name,DEST/name)
bb=json.loads((SOURCE/'alex_sovereign_v4.bbmodel').read_text());bb['name']='Alex Mercer / Blacklight independent rig'
groups={}
def visit(items):
    for v in items:
        if isinstance(v,dict):groups[v['name']]=v;visit(v['children'])
visit(bb['outliner'])
for name,p in coats.items():
    group={'name':name,'origin':p,'rotation':[0,0,0],'uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'mercer-blacklight:'+name)),'children':[]}
    groups[name]=group;groups['UpBody']['children'].append(group)
for element in bb['elements']:
    if element['name'] in moved:
        groups['UpBody']['children'].remove(element['uuid']);groups[moved[element['name']]]['children'].append(element['uuid'])
(DEST/'mercer_blacklight.bbmodel').write_text(json.dumps(bb,ensure_ascii=False,indent=2),encoding='utf8')

def under_head(name):
    while name:
        if name=='Head':return True
        name=parents[name]
    return False
binds={}
for b in mesh['bones']:
    name=b['name'];m=Matrix.Rotation(math.radians(-12),4,'X') if under_head(name) else Matrix.Identity(4)
    m.translation=Vector(b['pivot']);binds[name]=m
anims=json.loads((SOURCE/'main.animation.json').read_text())
generated=[]
for path in sorted((REPO/'art/motion-profile').glob('*.json')):
    clip=json.loads(path.read_text());tracks={t['name']:t for t in clip.get('animation',[]) if isinstance(t,dict) and 'transform' in t}
    if not tracks or clip.get('format')!='MATRIX':continue
    duration=max(t['time'][-1] for t in tracks.values());key=path.stem
    frames=max(1,math.ceil(duration*30));bones_out={};last_eulers={}
    for frame in range(frames+1):
        time=min(duration,frame/30);ts=f'{time:.5f}';posed={}
        context['traverse'](context['hierarchy'],context['axis'],posed,tracks,time)
        skin=context['retarget'](posed,key.startswith('consume_') or key.startswith('grab_'))
        globals={n:skin[n]@binds[n] for n in binds}
        for b in mesh['bones']:
            name,parent=b['name'],b['parent']
            # Facial and coat bones inherit their parents; facial Molang remains additive.
            if b['driver'] is None:continue
            local=globals[parent].inverted()@globals[name] if parent else globals[name]
            rest=binds[parent].inverted()@binds[name] if parent else binds[name]
            euler=local.to_euler('XYZ',last_eulers[name]) if name in last_eulers else local.to_euler('XYZ');last_eulers[name]=euler.copy()
            base=rest.to_euler('XYZ');angles=[math.degrees(euler[i]-base[i]) for i in range(3)]
            rotation=[-angles[0],angles[1],-angles[2]]
            position=list((local.translation-rest.translation)*16)
            bone=bones_out.setdefault(name,{'rotation':{},'position':{},'scale':{}})
            bone['scale'][ts]=[round(local.to_scale()[i]/rest.to_scale()[i],6) for i in range(3)]
            bone['rotation'][ts]=[round(v,4) for v in rotation];bone['position'][ts]=[round(v,5) for v in position]
    loop=key.startswith(('basic_idle','basic_wall_run','basic_wall_climb','basic_glide')) or key in ('basic_run','basic_walk','basic_sprint','basic_fall')
    anims['animations']['blacklight_'+key]={'animation_length':duration,'loop':loop,'bones':bones_out};generated.append(key)
# YSM main controller uses these names. Native Blacklight mode samples its own final
# pose and the same independent rig, so these files are not a substitute for gameplay state.
aliases={'idle':'basic_idle','walk':'basic_walk','run':'basic_sprint','jump':'basic_jump_rise','fall':'basic_fall'}
for name,key in aliases.items():
    if 'blacklight_'+key in anims['animations']:anims['animations'][name]=anims['animations']['blacklight_'+key]
# Correct standing/moving crouch roles inherited from the earlier generic draft.
anims['animations']['sneak'],anims['animations']['sneaking']=anims['animations']['sneaking'],anims['animations']['sneak']
(DEST/'main.animation.json').write_text(json.dumps(anims,ensure_ascii=False,separators=(',',':')),encoding='utf8')
manifest=json.loads((SOURCE/'ysm.json').read_text());manifest['metadata']['name']='A哥 · 黑光独立骨架 V4.4'
manifest['metadata']['tips']='35骨骼 / 三格高 / 基础动作与黑光姿态适配 / 翅膀独立'
(DEST/'ysm.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
(DEST/'blacklight-rig.json').write_text(json.dumps({'bones':mesh['bones'],'motionKeys':generated,'runtime':'Blacklight native pose adapter; YSM alone does not dispatch Blacklight skill states.'},ensure_ascii=False,indent=2),encoding='utf8')
with zipfile.ZipFile(OUT/'Alex-Mercer-Blacklight-V4.4-YSM.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in DEST.iterdir():z.write(p,Path('alex_mercer_blacklight')/p.name)
    guide=REPO/'docs/MERCER_MODEL_ZH.md'
    if guide.exists():z.write(guide,'使用说明.md')
print(json.dumps({'bones':len(bones),'retargeted_clips':len(generated),'total_clips':len(anims['animations']),'folder':str(DEST)}))
