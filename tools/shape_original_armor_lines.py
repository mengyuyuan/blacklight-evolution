"""Reference native chest-to-waist lines while retaining the old Minecraft cuboid armor."""
from pathlib import Path
import copy,json,uuid,base64
import numpy as np

repo=Path(__file__).resolve().parents[1];source=repo/'art/armor-scaled-original';dest=repo/'art/armor-vshape-preview'
dest.mkdir(parents=True,exist_ok=True)
layout=json.loads((source/'layout.json').read_text(encoding='utf-8'))
original=copy.deepcopy(layout['models']['armor'])
bb=json.loads((source/'armor.bbmodel').read_text(encoding='utf-8'))
elements={e['name']:e for e in bb['elements']}
core=next(b for b in original if b['name']=='Torso core')
core_element=copy.deepcopy(elements['Torso core'])
changed=[];boxes=[]

for box in original:
    name=box['name'];bone=box['bone']
    if name=='Torso core':continue
    lo=np.array(box['from_'],float);hi=np.array(box['to'],float);center=(lo+hi)/2;size=hi-lo
    if bone=='head':sx=sz=.9;anchor=new_anchor=0
    elif bone=='torso':
        sx=.72;sz=.82;anchor=new_anchor=0
        if name.startswith(('Pectoral upper','Pectoral lower','Clavicle','Back shoulder','Rear neck')):sx=.96;sz=.88
        elif name.startswith(('Flared hip','Hip crest')):sx=.81;sz=.82
    elif bone.endswith('_arm'):
        s=1 if bone.startswith('left') else -1;anchor=s*6;new_anchor=s*5.4
        sx=.90 if center[1]>=18 else .82;sz=.84 if center[1]>=18 else .82
    else:
        s=1 if bone.startswith('left') else -1;anchor=s*2;new_anchor=s*1.9;sx=.88;sz=.86
    nc=center.copy();nc[0]=new_anchor+(center[0]-anchor)*sx;nc[2]*=sz
    size[0]*=sx;size[2]*=sz
    if name.startswith('Deltoid outer overlap'):size[0]*=.90;size[2]*=.94
    if name.startswith('Narrow abdomen'):
        size[0]=4.45;size[2]=3.05
    elif name.startswith('Oblique rib'):
        i=int(name.rsplit(' ',1)[1]);s=-1 if ' -1 ' in name else 1
        nc[0]=s*(3.20-.45*i);size[0]=.88;size[2]=2.85
    elif name.startswith('Waist organic seam'):
        nc[0]=np.sign(center[0])*2.45;size[0]=.34
    elif name.startswith('Abdomen shell'):
        i=int(name.rsplit(' ',1)[1]);nc[0]=np.sign(center[0])*(.68+i*.13);size[0]=1.50+i*.24;size[2]=.9
    elif name.startswith('Back lower plate'):
        nc[0]=center[0]*.76;size[0]*=1.06
    if name.startswith('Thigh '):
        # Strong upper thigh, tapering toward the knee; preserve the slim lower leg.
        i=int(name.rsplit(' ',1)[1]);factor=[1.16,1.13,1.08][i]
        anchor=np.sign(nc[0])*1.9
        nc[0]=anchor+(nc[0]-anchor)*factor;nc[2]*=factor
        size[0]*=factor;size[2]*=factor
    if name=='Rear neck mantle':
        size[1]=4.25;size[2]=1.65;nc[1]=24.25
    box['from_']=(nc-size/2).tolist();box['to']=(nc+size/2).tolist();box['pivot']=nc.tolist()
    boxes.append(box)
    e=elements[name];e['from']=box['from_'];e['to']=box['to'];e['origin']=nc.tolist();changed.append(e)

# The old full-width rectangular core prevented any visible narrowing of the waist.
# Five connected cuboids follow the native V-shaped contour, without smooth imported meshes.
core_ids=[]
for index,(bottom,top,width,depth) in enumerate([(12,14,5.30,3.20),(14,16,4.90,3.05),(16,18,5.80,3.25),(18,20.5,6.85,3.45),(20.5,24,8.00,3.6)]):
    box=copy.deepcopy(core);box['name']='Torso tapered core '+str(index)
    box['from_']=[-width/2,bottom,-depth/2];box['to']=[width/2,top,depth/2]
    box['pivot']=[0,(bottom+top)/2,0];boxes.append(box)
    e=copy.deepcopy(core_element);e['name']=box['name'];e['uuid']=str(uuid.uuid5(uuid.NAMESPACE_URL,'blacklight-armor-vshape-core/'+str(index)))
    e['from']=box['from_'];e['to']=box['to'];e['origin']=box['pivot'];changed.append(e);core_ids.append(e['uuid'])
# Broad sloped muscle plates overlap the rear neck mantle and shoulder, without horns.
trap_ids=[]
for side in [-1,1]:
    for index,(x,y,w,h,d) in enumerate([(2.05,24.65,2.50,1.80,3.35),(3.55,23.75,2.40,1.50,3.50)]):
        box=copy.deepcopy(core);box['name']=f'Trapezius slope {side} {index}'
        center=np.array([side*x,y,.25]);size=np.array([w,h,d])
        box.update(from_=(center-size/2).tolist(),to=(center+size/2).tolist(),
                   pivot=center.tolist(),rotation=[0,0,-side*22.5],material='plate')
        boxes.append(box)
        e=copy.deepcopy(core_element);e.update(name=box['name'],uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,box['name'])),
            **{'from':box['from_'],'to':box['to'],'origin':box['pivot'],'rotation':box['rotation']})
        changed.append(e);trap_ids.append(e['uuid'])
bb['elements']=changed
def replace_core(groups):
    out=[]
    for g in groups:
        if isinstance(g,str):out.extend(core_ids+trap_ids if g==core_element['uuid'] else [g])
        else:g['children']=replace_core(g['children']);out.append(g)
    return out
bb['outliner']=replace_core(bb['outliner'])
bb['name']='旧方块战甲 · 原版线条参考 · 收腰力量版'
texture=(source/'armor_block.png').read_bytes()
bb['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(texture).decode()
bb['textures'][0]['name']='armor_block.png'
layout['models']['armor']=boxes
report={'base':'old Minecraft cuboid armor','reference':'native unpacked armor torso contour',
        'previewOnly':True,'runtimeInstalled':False,'relativeScale':1.2,'cuboids':len(boxes),
        'waistCoreWidthBefore':8,'waistCoreWidthAfter':4.9,
        'chestCoreWidthAfter':8,'originalPixelAtlasUnchanged':True,'collarSpikesRemoved':True,
        'design':'Broad pectorals and shoulders; tapered connected waist core, inclined abdominal and hip plates.'}
layout['refinement']=report
assert len(boxes)==123
assert [b for b in boxes if b['name'].startswith('Neck flange')]==[]
# Preserve height and original angles on retained plates.
for b in boxes:
    if b['name'].startswith(('Torso tapered','Trapezius')) or b['name']=='Rear neck mantle':continue
    old=next(o for o in original if o['name']==b['name'])
    assert b.get('rotation',[0,0,0])==old.get('rotation',[0,0,0])
    assert abs((b['to'][1]-b['from_'][1])-(old['to'][1]-old['from_'][1]))<1e-8
(dest/'layout.json').write_text(json.dumps(layout,ensure_ascii=False,indent=2),encoding='utf-8')
(dest/'armor.bbmodel').write_text(json.dumps(bb,ensure_ascii=False),encoding='utf-8')
(dest/'armor_block.png').write_bytes(texture)
(dest/'refinement-record.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
