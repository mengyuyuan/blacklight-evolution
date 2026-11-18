"""Static FIG/native evidence for the independent MotherCore close-hit branch."""
from pathlib import Path
import hashlib, json, zlib
ROOT=Path(__file__).resolve().parents[1]; P=Path('D:/PrototypeReference/p1')
rows=json.loads((P/'private/greene-ai-complete.json').read_text(encoding='utf-8'))['records']
selected=[r for r in rows if r['source']=='startup_fig.p3d.rz' and 267820<=r['offset']<269808
          or r['source']=='comparison/mother_fig.p3d.rz' and (20276<=r['offset']<20692 or 28204<=r['offset']<33392 or 36040<=r['offset']<37380 or 70076<=r['offset']<71100)]
raw=zlib.decompress((P/'input/comparison/mother_fig.p3d.rz').read_bytes()[16:])
for r in selected:
    if r['source']=='comparison/mother_fig.p3d.rz' and r['type'] in ('GrabSlot','AITreeEventSend'):
        r['rawPayloadHex']=raw[r['payload']:r['payload']+r['length']].hex()
record={
 'schema':1,'status':'Source audit and independent MC module/clip export; Java, tests, game and original native code were not executed.',
 'sourceHashes':{str(path.relative_to(P)):hashlib.sha256(path.read_bytes()).hexdigest() for path in
    (P/'input/startup_fig.p3d.rz',P/'input/comparison/mother_fig.p3d.rz',P/'private/comparison/mother.p3d',P/'input/prototypeenginef.dll')},
 'identity':{'input':'RightBumper e505977f863e94bb, Pressed 1696ae9aaaedd5da',
    'clips':{'left':'mother_slug_hit_left ba4b9940b9fff8b1','right':'mother_slug_hit_right 5c276e28303a13f0'},
    'notGrab':'GrabSlot tests whether a MotherCore child is attached; neither branch grabs the player.',
    'leftTip':'Slug_02_Spine_5 3cece9c73ab2dbe6','rightTip':'Slug_01_Spine_5 0f78a1667d5bb7f1'},
 'admission':{'source':'startup268248: XZ<25, countdown timer==0, Segment1 OR Segment2 attached. Button pressed0..0.666667 plus Attack action.',
    'motion':'mother28404 and30660 BOTH have Segment1=true; exact raw bytes44ad392298321a7901000000. Each has Random weight10. This asymmetry is preserved, not repaired based on side names.',
    'cooldown':'TimerSet268672 writes2sec on entry. It is not an extra post-animation cooldown. Close-wait reset node269400 resets timer0 when targetXZ>=17.',
    'parallel':'AI parent267820 Spawn267924 close(index15) and267984 shoot(index22). Motion parent20276 Spawn20340 shoot(index32),20400 close(index42). Close must not cancel shooting.',
    'units':'25/17 are original source distance units. Convert once using the accepted Greene model pixels/source-unit divided16 and current presentation scale, not 25/17 literal blocks.'},
 'animation':{'header':'Both PTRN original clips have76 frames,24fps,loop0; offsets4139859/4164126 in private/comparison/mother.p3d.',
    'segments':[{'begin':0,'end':1,'from':0,'to':30,'authoredSpeed':1,'effectiveSpeed':1.25,'blendIn':.33333298563957214,'blendOut':.06666669994592667},
      {'begin':1,'end':1.3333300352096558,'from':30,'to':34,'authoredSpeed':1,'effectiveSpeedFormula':'4/(24*(end-begin))','blendIn':.06666669994592667,'blendOut':.06666669994592667},
      {'begin':1.3333300352096558,'end':-1,'from':34,'to':75,'authoredSpeed':.800000011920929,'blendIn':.06666669994592667,'blendOut':.16666699945926666}],
    'speedProof':'108da7cc..108da7fb supplies track duration/speed/start/end to10941ca0. Positive duration and noncyclic driver:10941cd7..10941d38 fits signed clip seconds to duration using109416d0; negative end-time keeps speed at10941d3a. EndFrame=-1 becomes frameCount-1 and clamp. Tail41/(24*.8)=2.1354166sec, whole nominal3.4687467sec.',
    'completion':'10941b40 checks noncyclic current>=end;108daca0 polls completion;10a7f7b0 ends action;10a7f600 invokes enabled Sequence end callbacks. This is not a fixed2sec animation.',
    'partitions':'Each segment duplicates default priority1 and slug02(left)/slug01(right) priority-1. Shoot default priority0. 108da9fa/108daaee supplies priority;109392bc..c0 stores it at entry+18. Dirty-list sort1093b370->1093b340->1093b1e0/1093aeb0 uses10939150: this.priority>other.priority returns true, i.e. descending priority list. This proves sorting direction, not the final mixer overwrite/weight evaluation order; do not infer that a larger or smaller number wins visually yet.',
    'MCmixer':'Standalone helper retains source frames. While shooting, selected native_Slug_0N chain overlays the same shooting base used by render/contact; otherwise source close supplies whole pose. Piecewise frame fitting is proved; partition precedence/linear crossfades and the fade envelope are explicit MC adapters.'},
 'lifecycle':{'event':'AITreeEventSend29248/31504 payload flags1,begin0,end-1,event665691b9b2771fc4. Action enter101f0c30 sends cat2b/subtype9 withphase0; exit101f0cf0 sameeventphase1. Event spelling is unresolved.',
    'AIwait':'startup268596 OnEvent mask524288 can enter hit/wait268788, whose eventOnEnter is6656... . Opportunity268888 listens to eventOnExit for0..5sec; Sequence268992 returnsclosebank. Native hash event means actual action lifecycle, not HIT damage success.',
    'motionSequence':'left30356/right32612 register Sequence→index42 closebank. Wait32880 temporarily marks idle and polls input event after.166667sec.',
    'MCclock':'Observed20Hz poll-before-finalize-before-advance. Every Hit is activated after the current poll; fixed float priorDelta and strict remaining<0 preserved. begin has no hits; firstTIP is following activation. Caller must dedupe same worldtick; reload never catches up.',
    'MCcompletion':'Clip total computed from proved timing; terminate at first observed .05s clock reaching nominal duration, not an assertion of original engine phase coincidence. Stage/target replacement cancels session. Frame identity/revision prevents stale callback continuation.'},
 'hits':{'order':'TIP first in authored list, thenINNER thenOUTER; no strongest-window reduction.',
    'common':'UseShapecast=false; CollisionCountMax=-1; TimeBetweenHits=-1; IsThrown=false; UseOriginatorToValidate=false; CollideWith0xc030; transform multipliertrue, weaponfalse; SendAlerttrue. Raw full tracks follow below.',
    'mask':'P1 bits4Health,5HitReaction,14PedBehaviour,15VehicleBehaviour, ANY-bit intersection (11253f20 registration,10699b61..87). This mask does not contain Prop bit6. MC entity/category correspondence remains an adapter.',
    'receipts':'Same MotherCore CollisionActionBehaviour,100a4310 bind and100a4970/100a3780 active-count lifetime.10065940 marks BEFORE damage dispatch; a receiver refusing damage still consumes this live receipt. Shared active-scope and>5sec expiry per10065a80. Do not use per-window sets.',
    'shape':'Current-position sphere, joint-local offset. TIP uses actually present Slug_02/01_Spine_5 with[-2,0,0],radius4; rootwindows Motion_Root[0,0,-6],radius5/7. No capsule/sweep/path from previous pose. Source sphere radius fixed through shape transform; accepted model units/presentation convert once.',
    'damage':'500/500/200 source damage; firstFriendlyFire.2 then0/0. These are not Minecraft hearts. Native force/mass, friendliness classification, collision direction and HitType reaction implementation are not reconstructed by these geometry helpers.',
    'MCsafety':'Use same composed pose as renderer, actual victim body boxes/hulls, loaded and border checks. Current-sphere-to-contact block occlusion is an explicit MC wall-protection adapter absent from the proven native overlap layer. No fallback damage if actual geometry misses.'},
 'otherTracks':{'turning':'Both sides spawn bank59 for0..2sec. It uses target-facing bands<10,<30,<120,else; AutoTarget rates3/70/150degsec with hysteresis10/15 at the first band. Root steering integration remains separate.',
    'camera':'LeftExecute30236 at1.666669965 /right32492 at1.700000048→index48 CameraShakeRequest Large/Large.',
    'sound':'Patch a2bde1c6dd816e0b unresolved name. Left1.200000048 and1.799999952; right1 and1.666669965. StopExisting,vol1,pitch1,minDistance25/max55. No original waveform imported/played.',
    'fx':'BothSpawn at1.666669965→effectsindex120. Two shockWave001(03501dfbdb799756) tracks70140/70624, root local[0,1,-10],JointSpace emit/WorldSpaceattach. Firstwindow .0333333..1 withlifeBias2,size2,speed.7; second0...666667 lifeBias.5,size1.5,speed3. Emission windows are not particle lifetimes. No arbitrary white flash/ring is inferred.'},
 'remaining':['Priority DESCENDING sort is proved; final evaluation/overwrite direction and complete partition mixer, including concurrent default and partition weight normalization, remain unclosed.',
   'Exact action/puppet phase completion coincidence beyond the proven clip time; MC uses a stated20Hz completion gate.',
   'Names/receiver implementation of HitType919001ca3cc2c137 and5da4043435eaeeac, event665691b9b2771fc4 and soundpatcha2bd....',
   'Puppet flag4 conditional joint basis normalization at100a3afa and actual MotherCore flag value; shared mesh basis remains the explicit MC contact geometry contract.',
   'Native physics impulse/mass response and exact target-category mapping. No source numerical damage is presented as MC balance.',
   'Live world/renderer/phase interruption acceptance is deferred; independent tests were authored, not run.'],
 'records':selected,
 'implementationFiles':['combat/GreeneCloseAttack.java','combat/GreeneCloseContact.java','models/greene_close_source.json','tools/export_greene_close.py'],
}
encoded=json.dumps(record,ensure_ascii=False,indent=2)+'\n'
(P/'private/greene-close-attack-proof.json').write_text(encoded,encoding='utf-8')
(ROOT/'art/encounters/greene-close-attack-source.json').write_text(encoded,encoding='utf-8')
print('Recorded',len(selected),'selected source records; no Java/runtime validation')
