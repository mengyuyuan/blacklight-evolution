"""CPU-only pose/skin diagnostic. Run with Blender; no GPU or scene renderer.

Exports the exact authored mesh deformed with source rotations and calibrated
joint axes. Checks every mapped limb aim against the decoded source, and
exports a contact sheet payload for the lightweight Pillow renderer.
"""
import runpy, sys, json, math
from pathlib import Path
from mathutils import Matrix, Vector, Quaternion
R=Path(__file__).resolve().parents[1]
# Reuse the decoder and execute the reproducible asset export first.
ctx=runpy.run_path(str(R/'tools/retarget_creature_motion.py'))
from hunter_retarget import calibration, rotations, pelvis_height
rig=ctx['hunter'];source=ctx['a'].source;read=ctx['read'];worlds=ctx['worlds'];mapping=ctx['BMAP']
A=R/'src/main/resources/assets/blacklight'
geo=read(A/'geo/blacklight_hunter_authored.geo.json')['minecraft:geometry'][0]['bones']
mesh=read(A/'models/hunter_connected.json')
reference=worlds(rig,read(source/'Brawler-brawler_act_calm.json'),0)
S=Matrix.Diagonal(Vector((-1,1,1)));setup=calibration(geo,reference,mapping,S)
parents={b['name']:b.get('parent') for b in geo}
pivots={b['name']:Vector((-b['pivot'][0]/16,b['pivot'][1]/16,b['pivot'][2]/16)) for b in geo}
samples=[('idle','brawler_act_calm',0),('swipe windup','brawler_pnch_1',11),('swipe contact','brawler_pnch_1',14),('swipe follow','brawler_pnch_1',24),('barrage','brawler_claw_barrage',18),('slam impact','brawler_pnch_fist_slam',37)]
rows=[];worst=0
def skin(matrix):
    qs={}
    for name,m in matrix.items():
        q=m.to_quaternion();t=m.translation
        d=Quaternion((0,*t))@q;d=Quaternion([v*.5 for v in d]);qs[name]=(q,d)
    result=[]
    for v in mesh['vertices']:
        a,b,w=v['w'];qa,da=qs[a];qb,db=qs[b];sign=-1 if qa.dot(qb)<0 else 1
        q=Quaternion([x*(1-w)+y*w*sign for x,y in zip(qa,qb)])
        d=Quaternion([x*(1-w)+y*w*sign for x,y in zip(da,db)]);length=q.magnitude
        q=Quaternion([x/length for x in q]);d=Quaternion([x/length for x in d])
        trans=d@q.conjugated();point=q@Vector(v['p'])+Vector((trans.x,trans.y,trans.z))*2
        result.append(list(point))
    return result
for label,clip,frame in samples:
    current=worlds(rig,read(source/('Brawler-'+clip+'.json')),frame/30)
    for fixed in [False,True]:
        rot=rotations(current,reference,mapping,S,setup) if fixed else {b:(S@(current[s].to_quaternion()@reference[s].to_quaternion().inverted()).to_matrix()@S).to_quaternion() for b,s in mapping.items()}
        transforms={'root':Matrix.Identity(4)}
        scale=18/reference['Pelvis'].translation.y
        y=((current['Pelvis'].translation.y-current['Motion_Root'].translation.y)-(reference['Pelvis'].translation.y-reference['Motion_Root'].translation.y))*scale/16
        if fixed:y=pelvis_height(geo,current,reference,mapping,rot)/16
        for bone in geo[1:]:
            b=bone['name'];parent=parents[b];p=pivots[b]
            local=rot[parent].inverted()@rot[b] if parent in rot else rot[b]
            m=Matrix.Translation(p)@local.to_matrix().to_4x4()@Matrix.Translation(-p)
            if b=='hips':m=Matrix.Translation((0,y,0))@m
            transforms[b]=transforms[parent]@m
        if fixed:
            for b,(child,srcchild) in setup[0].items():
                desired=S@(current[srcchild].translation-current[mapping[b]].translation).normalized()
                actual=rot[b]@setup[2][b];worst=max(worst,actual.angle(desired,0)*180/math.pi)
        joints={b:list(transforms[b]@p) for b,p in pivots.items()}
        rows.append({'label':label,'fixed':fixed,'vertices':skin(transforms),'joints':joints})
out=R/'art/hunter/rig-check.json'
out.write_text(json.dumps({'rows':rows,'faces':mesh['faces'],'parents':parents,'maxAimErrorDegrees':worst},separators=(',',':')))
assert worst<.1,worst
print('RIG_CHECK',out,'max limb aim error degrees',worst)
