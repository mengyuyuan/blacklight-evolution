"""Check source-derived profile integrity and render native-skeleton contact sheets."""
import hashlib,json,math
from pathlib import Path
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
PROFILE=ROOT/'art/motion-profile'
PAIRS=['carry_sprint','carry_air','air_throw','leg_slam','normal_head_stomp','normal_beatdown','normal_knee_head',
       'stealth_neck','stealth_bearhug','stealth_nelson','stealth_guillotine','stealth_jugular']
SOLOS=['ground_stomp','block_throw_one_slam','block_throw_two_slam']
names=SOLOS+['consume_'+key+'_'+role for key in PAIRS for role in ['actor','victim']]
manifest=json.loads((ROOT/'art/motion-profile-manifest.json').read_text())['files']
placement=json.loads((PROFILE/'consume-placement.data').read_text())
events=json.loads((PROFILE/'consume-receiver-events.data').read_text())['profiles']
for name in names+['consume-placement','consume-receiver-events']:
    name+= '.data' if name.startswith('consume-') else '.json'
    raw=(PROFILE/name).read_bytes()
    assert manifest[name]=={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()},name
for name in SOLOS[1:]:
    clip=json.loads((PROFILE/(name+'.json')).read_text())
    release=clip['source']['releaseTick']
    assert 2<=release<clip['blacklight']['durationTicks']
    assert clip['source']['animation']=='alex_weap_2hnd_slam'
air_throw=json.loads((PROFILE/'consume_air_throw_actor.json').read_text())
assert 2<=air_throw['source']['releaseTick']<air_throw['blacklight']['durationTicks']
assert air_throw['source']['animation']=='alex_grap_hmn_jump_throw_gv'
assert (PROFILE/'consume-placement.data').stat().st_size<=8*1024*1024
assert (PROFILE/'consume-receiver-events.data').stat().st_size<=16*1024
for key in PAIRS:
    actor=json.loads((PROFILE/('consume_'+key+'_actor.json')).read_text())
    victim=json.loads((PROFILE/('consume_'+key+'_victim.json')).read_text())
    assert actor['animation'][0]['time']==victim['animation'][0]['time']
    frames=len(actor['animation'][0]['time'])
    for section,columns in [('contacts',46),('projections',51),('motion',3),('actorMotion',3)]:
        a=np.array(placement[section][key]);assert a.shape==(frames,columns),(key,section,a.shape)
        assert np.isfinite(a).all()
        assert np.max(abs(a))<=20
    a=np.array(placement['projections'][key])[:,3:].reshape(-1,4)
    assert np.max(abs(np.linalg.norm(a,axis=1)-1))<1e-6
    if key in events:
        scene=events[key];end=actor['animation'][0]['time'][-1]
        assert 0<=scene['deformSeconds']<scene['finishSeconds']<end
        assert scene['effectSeconds']+scene['settleSeconds']<scene['finishSeconds']
        assert all(0<=t<scene['finishSeconds'] for t in scene['hitSeconds'])
print('PASS: hashes, pair clocks, placement rows, unit quaternions and scene event bounds')

# Plot data, not a gameplay screenshot. This exposes mirrored limbs/contact drift.
from PIL import Image,ImageDraw
bind=json.loads((ROOT/'src/main/resources/assets/blacklight/animation/player_bind.json').read_text())
parents={}
rest_world={}
def visit(n,parent=None):
    parents[n['name']]=parent
    m=np.array(n['transform']).reshape(4,4)
    rest_world[n['name']]=(rest_world[parent] if parent else np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]]))@m
    for c in n['children']:visit(c,n['name'])
visit(bind['hierarchy'][0])
axis=np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]])
def pose(name,seconds):
    d=json.loads((PROFILE/(name+'.json')).read_text());tracks={t['name']:t for t in d['animation']}
    worlds={};i=min(range(len(tracks['Root']['time'])),key=lambda i:abs(tracks['Root']['time'][i]-seconds))
    for n,p in parents.items():
        m=np.array(tracks[n]['transform'][i]).reshape(4,4);worlds[n]=(worlds[p] if p else axis)@m
    points={n:w[:3,3]*.9375 for n,w in worlds.items()}
    for side in ['L','R']:
        leg='Leg_'+side;thigh='Thigh_'+side
        ankle=2*rest_world[leg][:3,3]-rest_world[thigh][:3,3]
        points['Foot_'+side]=(worlds[leg]@np.linalg.inv(rest_world[leg])@np.array([*ankle,1]))[:3]*.9375
    return points,i
dest=ROOT/'build/context-actions-qa';dest.mkdir(parents=True,exist_ok=True)
sheet=Image.new('RGB',(1440,300*(len(SOLOS)+len(PAIRS))),'white');draw=ImageDraw.Draw(sheet)
for row,key in enumerate(SOLOS+PAIRS):
    clip=key if key in SOLOS else 'consume_'+key+'_actor'
    d=json.loads((PROFILE/(clip+'.json')).read_text());end=d['animation'][0]['time'][-1]
    for col,t in enumerate([0,end*.25,end*.55,end*.85]):
        a,i=pose(clip,t)
        def project(p):return (col*360+180+90*(.8*p[0]+.6*p[2]),row*300+260-85*p[1]+22*(.6*p[0]-.8*p[2]))
        draw.rectangle((col*360,row*300,col*360+359,row*300+299),outline='#dddddd')
        draw.text((col*360+10,row*300+10),key+' / '+str(round(t,2))+'s',fill='black')
        draw.line((project(np.array([-1.5,0,0])),project(np.array([1.5,0,0]))),fill='#bbbbbb')
        draw.line((project(np.array([0,0,-1.5])),project(np.array([0,0,1.5]))),fill='#bbbbbb')
        bodies=[(a,'#1976d2')]
        if key in PAIRS:
            v,_=pose('consume_'+key+'_victim',t)
            f=placement['contacts'][key][0];travel=np.array(placement['motion'][key][i])*.9375
            offset=np.array(f[:3])*.9375-np.array(f[3:6])+travel
            offset[1]=max(-placement['contacts'][key][i][6],offset[1]);offset[2]*=-1
            actor_travel=np.array(placement['actorMotion'][key][i])*.9375;actor_travel[2]*=-1
            if key in ('carry_sprint','carry_air','air_throw'):
                f=np.array(placement['contacts'][key][i])
                offset=f[:3]*.9375-f[3:6]
                gap=offset+f[10:13]-f[7:10]*.9375
                distance=np.linalg.norm(gap[[0,2]])
                if abs(gap[1])<.85 and distance<.64:
                    direction=np.array([gap[0],0,gap[2]])/distance if distance>.02 else np.array([0.,0.,1.])
                    offset+=direction*(.64-distance)
                offset[1]=max(0,offset[1]);offset[2]*=-1
                actor_travel=np.zeros(3)
            bodies=[({n:p+actor_travel for n,p in a.items()},'#1976d2'),({n:p+offset for n,p in v.items()},'#ef6c00')]
            if key=='air_throw' and t>=air_throw['source']['releaseTick']/20:
                bodies=bodies[:1]
                draw.text((col*360+10,row*300+26),'Receiver released to ballistics',fill='#666666')
        for body,color in bodies:
            for n,parent in list(parents.items())+[('Foot_L','Leg_L'),('Foot_R','Leg_R')]:
                if parent:
                    p,q=body[parent],body[n];draw.line((project(p),project(q)),fill=color,width=3)
            p=project(body['Head']);draw.ellipse((p[0]-6,p[1]-6,p[0]+6,p[1]+6),fill=color)
sheet.save(dest/'context-actions-skeletons.png')
print('Rendered',dest/'context-actions-skeletons.png')
