"""Build MC combat schedules from retargeted P1 clips; run AFTER retarget_creature_motion.py.
FIG timing is preserved where decoded; cross-state choices, distances and joins are MC adaptations.
No private source paths or raw FIG blobs are shipped.
"""
from pathlib import Path
import json,math,bisect,sys
R=Path(__file__).resolve().parents[1];A=R/'src/main/resources/assets/blacklight'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sample(anim,bone,t,channel='rotation'):
 data=anim['bones'].get(bone,{}).get(channel,{})
 if not data:return [0,0,0]
 keys=sorted((float(k),v) for k,v in data.items());ts=[k for k,v in keys]
 hi=bisect.bisect_right(ts,t)
 if hi==0:return keys[0][1]
 if hi==len(keys):return keys[-1][1]
 lo=hi-1;f=(t-ts[lo])/(ts[hi]-ts[lo]);return [a+(b-a)*f for a,b in zip(keys[lo][1],keys[hi][1])]
def compose(anims,segments):
 names=list(anims[segments[0][2]]['bones']);duration=segments[-1][1]
 bones={b:{'rotation':{}} for b in names}
 for b in names:
  if any('position' in anims[s[2]]['bones'].get(b,{}) for s in segments):bones[b]['position']={}
 for f in range(round(duration*30)+1):
  t=f/30;index=next((i for i,s in enumerate(segments) if t<=s[1]+1e-6),len(segments)-1)
  start,end,clip,first,last=segments[index]
  source=(first+(last-first)*max(0,min(1,(t-start)/(end-start))))/30
  for b in names:
   value=sample(anims[clip],b,source)
   # Cross-clip joins are our adaptation. Keep event time unchanged while
   # easing out of the prior pose instead of snapping to another clip's frame 0.
   if index and segments[index-1][2]!=clip and t-start<.1:
    previous=segments[index-1];old=sample(anims[previous[2]],b,previous[4]/30)
    w=max(0,min(1,(t-start)/.1));w=w*w*(3-2*w)
    value=[a+((v-a+180)%360-180)*w for a,v in zip(old,value)]
   if f:
    previous=list(bones[b]['rotation'].values())[-1]
    value=[a+((v-a+180)%360-180) for a,v in zip(previous,value)]
   bones[b]['rotation'][str(round(t,6))]=value
   if 'position' in bones[b]:
    position=sample(anims[clip],b,source,'position')
    if index and segments[index-1][2]!=clip and t-start<.1:
     previous=segments[index-1];old=sample(anims[previous[2]],b,previous[4]/30,'position')
     w=max(0,min(1,(t-start)/.1));w=w*w*(3-2*w)
     position=[v+(n-v)*w for v,n in zip(old,position)]
    bones[b]['position'][str(round(t,6))]=position
 return {'loop':False,'animation_length':duration,'bones':bones}
p=A/'animations/blacklight_hunter.animation.json';doc=read(p);anims=doc['animations']
# Preserve raw source clips so rerunning this script does not compound retiming.
anims.setdefault('source_swipe',anims['swipe']);anims.setdefault('source_dodge',anims['dodge'])
plans={
 'swipe':[(0,.5,'source_swipe',6,11),(.5,1.462963,'source_swipe',11,37),(1.462963,1.82963,'source_swipe',37,48)],
 'combat_barrage':[(0,.4,'barrage',0,3),(.4,2.833333,'barrage',3,76)],
 'combat_slam':[(0,.5,'slam',10,25),(.5,.733333,'slam',30,37),(.733333,1.233333,'slam',37,45),(1.233333,1.566667,'slam',45,55),(1.566667,2.133333,'slam',55,72)],
 'combat_leap':[(0,.4,'leap',0,12),(.4,.9,'leap',12,27),(.9,1.3,'air_slash',4,28),(1.3,1.5,'air_slash',28,34),(1.5,2.4,'air_slash',34,45)],
 'dodge':[(0,1,'source_dodge',0,30),(1,1.2,'source_dodge',30,36)]}
for name,segments in plans.items():anims[name]=compose(anims,segments)
p.write_text(json.dumps(doc,indent=2),encoding='utf8')
# The retired Brawler resource remains a compatibility alias of the canonical Hunter.
(A/'animations/brawler.animation.json').write_text(p.read_text(encoding='utf8'),encoding='utf8')
names=list(anims['swipe']['bones']);table={'fps':30,'bones':names,'clips':{}}
# The independently decoded counter opening is appended by export_hunter_counter.py.
# Rebuilding the older schedules must not remove its runtime pose-table entry.
for name in ['idle',*plans,*(['combat_counter'] if 'combat_counter' in anims else [])]:
 clip=anims[name];table['clips'][name]=[[[round(math.radians(x)*(-1 if i<2 else 1),7) for i,x in enumerate(sample(clip,b,f/30))]+sample(clip,b,f/30,'position') for b in names] for f in range(round(clip['animation_length']*30)+1)]
(A/'models/hunter_combat_motion.json').write_text(json.dumps(table,separators=(',',':')),encoding='utf8')
provenance=R/'art/hydra/combat-schedule-provenance.json'
if provenance.exists():
 report=read(provenance);report['hunter']=plans
 report['hunterRevision']='2026-11-14: P1 FIG wrist windows; swipe release speed .9; barrage frames 3-76; air slash release speed 2. Contact uses the unchanged authored rig.'
 provenance.write_text(json.dumps(report,indent=2),encoding='utf8')
if '--hunter-only' in sys.argv:
 print('Built Hunter source frame schedules only; Hydra assets unchanged')
 sys.exit(0)
p=A/'animations/blacklight_hydra.animation.json';doc=read(p);anims=doc['animations']
hydra_plan=[(0,1,'slam',5,20),(1,2.5,'slam',20,35),(2.5,2.766667,'slam',35,44),(2.766667,6.666667,'slam',44,52),(6.666667,7.4,'slam',52,73)]
anims['combat_slam']=compose(anims,hydra_plan);p.write_text(json.dumps(doc,indent=2),encoding='utf8')
p=A/'models/hydra_motion.json';table=read(p);names=table['bones'];clip=anims['combat_slam']
table['clips']['combat_slam']=[[[round(math.radians(x)*(-1 if i<2 else 1),7) for i,x in enumerate(sample(clip,b,f/30))]+[0,0,0] for b in names] for f in range(223)]
p.write_text(json.dumps(table,separators=(',',':')),encoding='utf8')
(R/'art/hydra/combat-schedule-provenance.json').write_text(json.dumps({'hunter':plans,'hydraSlam':hydra_plan,'boundary':'Source frame slices; AI selection, leap impulse, barrage windup and recovery joins are Minecraft adaptations. Player tactical-hop immunity is requested by the user; its six-tick window is MC tuning, not a decoded original duration. Hit records do not bypass vanilla damage immunity.'},indent=2),encoding='utf8')
print('Built hunter combat poses and segmented hydra slam')
