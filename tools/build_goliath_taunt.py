"""Static continuation of P2 bullrush reference58. No DLL/game/test execution.

Imported by build_goliath_charge after the source rig has been exported.
Only the ordinary, intact-leg, non-story_m2/non-grapple pose path is enabled.
"""
from pathlib import Path
import hashlib,json,math,struct
import read_goliath_grounding as evidence
ROOT=Path(__file__).resolve().parents[1]

def extend(resource,motion,pose,rig,clips,table,scale,control):
    raw=evidence.SOURCE.read_bytes();runtime=evidence.RUNTIME.read_bytes();records=[]
    def value(p,o,fmt='f'):
        v=struct.unpack_from('<'+fmt,raw,p+o);return v[0] if len(v)==1 else list(v)
    def record(off,name,size):
        h,n=struct.unpack_from('<QI',raw,off)
        key={'Hit':447541633595,'Sound':8156776023108788129,'Sequence':5190073931069240687,'Execute':1853511208435321225}.get(name,evidence.hash64(name.encode()))
        assert (h,n)==(key,size),(off,name,n)
        records.append(dict(offset=off,kind=name,payloadBytes=n,payloadSha256=hashlib.sha256(raw[off+12:off+12+n]).hexdigest()))
        return off+12
    for off,branch in [(5428976,58),(5429776,60),(5431048,61),(5432024,116),(5428456,44)]:
        p=record(off,'Sequence',48);assert value(p,12,'I')==0 and value(p,16,'i')==branch
        records[-1]['branchIndex']=branch;records[-1]['branchOffset']=control['branches'][branch]['offset']
    assert control['branches'][58]['parent']==57 and control['branches'][57]['parent']==56
    phases=[]
    for ident,off,key,name,prior in [
        (7,5429972,'CHARGE_RETURN','tauntFromBullrush',6),
        (8,5430784,'TAUNT_TELL','headTell',7),
        (9,5433156,'TAUNT','tauntRelease',8),
        (10,5434208,'TAUNT','tauntOrdinaryPose',9),
        (11,5461912,'TAUNT','tauntRecover',10),
        (12,5420640,'TAUNT_HOWL','tauntHowl',11)]:
        p=record(off,'animation',132);clip=clips[table['clips'][key]['source']]
        assert value(p,16,'Q')==evidence.hash64(table['clips'][key]['source'].encode())
        begin,end,speed,first,last=[value(p,o) for o in [8,12,24,36,40]]
        if last<0:last=clip['frames']-1
        seconds=(end-begin)/speed;effective=(last-first)/30/seconds
        assert seconds>0 and [value(p,o,'I') for o in [80,84,88,92]]==[1]*4
        phases.append(dict(id=ident,name=name,clip=key,**{'from':first,'to':last},
            speed=effective,sourceSpeed=speed,seconds=seconds,blendIn=value(p,116),blendOut=value(p,120),
            offset=off,previous=prior,turn=180 if ident==7 else 0,radius=0))
        records[-1].update(begin=begin,end=end,first=first,last=last,effectiveSpeed=effective)
    # Source default64 references anchor5 (5433156), then its own second track.
    p=record(5434184,'__anchorRef',4);assert value(p,0,'I')==5
    assert value(5433156+12,0,'I')==5
    # Only story_m2 takes the extended alternative63; no fake mission flag.
    p=record(5432904,'lua',44);assert b'mms_IsMissionActive( "story_m2" )' in raw[p:p+44]
    p=record(5461804,'__anchorRef',4);assert value(p,0,'I')==0xef
    p=record(5401308,'not',4);assert value(p,0,'I')==0xef
    p=record(5461828,'isDismembered',16);assert value(p,4,'Q')==0xee526f0da59ed41c and value(p,12,'I')==1
    # Howl44 is conditionReference(anchor0xcd)->427->anchor0x102.
    # Its unbound script integer is zero in the ordinary MC adapter.
    p=record(5402504,'conditionReference',12);assert value(p,0,'I')==0xcd and value(p,8,'i')==427
    p=record(5604184,'__anchorRef',4);assert value(p,0,'I')==0x102
    p=record(5597860,'variableIntCompare',24)
    assert value(p,0,'I')==0x102 and value(p,4,'Q')==0x788ee2e27fcf2389 and value(p,12,'Q')==evidence.hash64(b'==') and value(p,20,'i')==0
    resource['phases'].extend(phases)
    for row in resource['phases']:row.setdefault('previous',0 if row['id']==0 else 4 if row['id'] in [5,6] else row['id']-1)
    for key in ['TAUNT_TELL','TAUNT','TAUNT_HOWL']:
        clip=clips[table['clips'][key]['source']];points=[]
        for frame in range(int(clip['frames'])):
            root=pose.world(motion,rig,clip,frame)['Motion_Root']
            assert motion.np.max(motion.np.abs(root[:3,:3]-motion.np.eye(3)))<1e-6 and abs(root[1,3]-6)<1e-6
            points.append((root[:3,3]*[1,1,-1]*scale).tolist())
        resource['rootCurves'][key]=points
    chain=[r[0] for r in rig[36:45]]
    assert chain==['Forarm_L','Wrist_L','Shield_L_2','Shield_L_3','Shield_L_1','Index_Base_L','Index_L','Index_L_End','Middle_Base_L']
    hits={}
    for off,size in [(5590904,404),(5565632,404),(5566056,404),(5431624,380)]:
        p=record(off,'Hit',size);assert value(p,104)==value(p,108)
        hits[off]=dict(offset=off,sourceDamage=value(p,104),radius=value(p,56)*scale,
            sourceRadius=value(p,56),shapecast=bool(value(p,16,'I')),collisionCountMax=value(p,20,'i'),
            timeBetweenHits=value(p,24),sourceOffset=value(p,44,'3f'),collideWith=value(p,72,'Q'),ignoreCollideWith=value(p,80,'Q'),
            sourceBegin=value(p,8),sourceEnd=value(p,12),chain=chain if off==5590904 else ['Wrist_L'] if off!=5431624 else ['Motion_Root'])
    assert hits[5431624]['collideWith']==0x100 and hits[5431624]['sourceDamage']==5000
    assert all(hits[o]['collideWith']==0x1000000c0 for o in [5590904,5565632,5566056])
    windows=[]
    for ident,phase,execute in [(700,7,5430148),(900,9,5431412),(901,9,5431480),
        (1100,11,5462116),(1101,11,5462184),(1200,12,5420860),(1201,12,5420928),(1202,12,5420996)]:
        p=record(execute,'Execute',48);assert value(p,16,'I')==0
        branch=value(p,20,'i');begin,end=value(p,8),value(p,12)
        assert branch in [384,329]
        for off in ([5590904] if branch==384 else [5565632,5566056]):
            hit=hits[off];stop=min(end if end>=0 else phases[phase-7]['seconds'],begin+hit['sourceEnd']) if hit['sourceEnd']>=0 else end
            assert stop>begin
            windows.append(dict(id=ident,phase=phase,begin=begin+hit['sourceBegin'],end=stop,executeOffset=execute,**hit))
    sounds=[]
    for off,phase in [(5431320,9),(5421064,12)]:
        p=record(off,'Sound',72)
        assert value(p,28,'Q')==evidence.hash64(b'Play') and value(p,36,'Q')==evidence.hash64(b'Retrigger')
        assert [value(p,o,'I') for o in [44,48,60]]==[0,1,0] and value(p,24)==1
        sounds.append(dict(phase=phase,at=value(p,8),patch=hex(value(p,16,'Q')),instance=hex(value(p,52,'Q')),
            frequency=value(p,24),trigger='Play',triggerType='Retrigger',stopOnExit=False,physicsOverride=True,autoRepeat=False,offset=off))
    data=dict(schema=1,sourceSha256=evidence.SOURCE_SHA,blocksPerSourceUnit=scale,windows=windows,sounds=sounds,
        soundBoundary='Native event identity/timing only; P2 audio waveform bank is absent locally. No replacement mob voice or claimed source playback.',
        contactBoundary='Health|HitReaction|Player targets (64-bit mask0x1000000c0). Prop-only5000damage mask0x100 is excluded. Shared inner/outer window ID uses strongest accepted contact once per victim as an explicit MC adapter; no additive750+400 claim. Shapecast uses adjacent20Hz poses and current receiving surfaces, not native physics substeps. Joint-index chain ignores offset exactly as102c4550 chain path; single joints apply source offset.',
        pending=['Native AutoTarget vs FaceDirection lambda steering','Story_m2 and player-grapple extended pose','Native leg/head reaction and dismember branches','Source sound waveform bank and playback','Prop/WorldCone damage and effect assets','Native root blending and final support'])
    fixtures=[];reference=pose.world(motion,rig,clips['behemoth_loco_biped_idle'],0)['Motion_Root']
    for phase,frame,ident in [(7,132,700),(11,190,1100),(12,9,1200),(12,104,1201)]:
        segment=phases[phase-7];clip=clips[table['clips'][segment['clip']]['source']]
        age=(frame-segment['from'])/(segment['speed']*1.5)
        window=next(w for w in windows if w['phase']==phase and w['id']==ident)
        assert window['begin']*20<=age<=window['end']*20 and age>=segment['blendIn']*20
        world=pose.world(motion,rig,clip,frame);anchor=reference@motion.np.linalg.inv(world['Motion_Root'])
        points=[dict(joint=joint,worldYaw0=((anchor@world[joint])[:3,3]*[1,1,-1]*scale+[0,.01,0]).tolist()) for joint in chain]
        anchor_point=((anchor@world['Spine_3'])[:3,3]*[1,1,-1]*scale+[0,.01,0]).tolist()
        fixtures.append(dict(phase=phase,frame=frame,age=age,windowId=ident,radius=window['radius'],points=points,bodyAnchor=anchor_point))
    (ROOT/'src/test/resources/goliath-taunt-reference.json').write_text(json.dumps(dict(sourceSha256=evidence.SOURCE_SHA,samples=fixtures),indent=2)+'\n')
    (ROOT/'src/main/resources/assets/blacklight/models/goliath_taunt.json').write_text(json.dumps(data,indent=2)+'\n')
    resource['boundary']='Native ordinary intact-leg taunt successor58/60/61(default pose64)/116/44 is included with real source attack windows via GoliathTaunt. Story mission/grapple/dismember logic, source audio waveforms, native root layer blending and final support remain open. Special return still ends before44; it is not silently mapped to the normal taunt.'
    report=dict(schema=1,sourceSha256=evidence.SOURCE_SHA,runtimeSha256=evidence.RUNTIME_SHA,records=records,
        ordinaryBranchChain=[58,60,61,64,116,44],phaseDurationsTicks=[math.ceil(p['seconds']*20-1e-5) for p in phases],
        normalChargeTicks=82+sum(math.ceil(p['seconds']*20-1e-5) for p in phases),
        sourceConditions={'extended':'branch63 requires script int0xc9fe507a71601ce7<=0 AND mms_IsMissionActive(story_m2); ordinary64 is unconditional fallback.',
            'poseContinuation':'default64 references Sequence5433856 to66, which requires original Player variable0xff5a1b8517baca37==1; otherwise normal finite pose ends. MC has no corresponding native Player grapple state and uses false.',
            'recover':'branch116 conditions Not(anchor0xef=5401308), isDismembered limbHash0xee526f0da59ed41c true. No readable limb name recovered; do not guess leg side.',
            'recoverSuccessors':'Sequence anchor0x7d→441 death path conditions; anchor0x39→44 howl. MC death/expose cancellation remains higher priority; ordinary living path44 only.',
            'howlEntry':'44 conditionReference5402504(anchor0xcd)→branch427→anchor0x102→VariableIntCompare5597860, script int0x788ee2e27fcf2389==0. Current ordinary MC adapter has no producer for this original variable and explicitly uses0; not an unconditional native branch.'},
        runtimeEvidence=evidence.pe_ranges(runtime,[
            ('P2 sound metadata',0x10740240,0x107404d6,'72-byte source layout: begin/end/Patch/Frequency/Trigger/TriggerType/StopOnExit/PhysicsOverride/InstanceName/AutoRepeat/min/max; Frequency is not volume'),
            ('Sound onBegin',0x107c3d50,0x107c3e22,'Play calls107c3790; AutoRepeat false prevents cyclic retrigger'),
            ('Sound onEnd',0x107c3720,0x107c3782,'Stop is gated by action+58 copied from StopOnExit; both source sounds have false'),
            ('Sound constructor',0x107c3660,0x107c36e6,'Copies StopOnExit to action+58'),
            ('Collision masks',0x10a1fe40,0x10a20181,'Ordered bit names: index6 Health,7 HitReaction,8 Prop,32 Player; masks0x1000000c0 versus0x100'),
            ('Hit chain',0x102c4550,0x102c4ea2,'Inclusive indices including unweighted/shield joints; chain origins and intermediate spheres, no local offset application'),
            ('Dismember predicate',0x103744e0,0x10374581,'Queries object group0x10 subtype2 using limb hash then compares configured bool'),
            ('Spawn pose branch',0x107bf420,0x107bf4b5,'Selects target branch by conditions and creates subordinate sequence; default64 references same animation anchor5'),
            ('Execute subordinate Hit',0x107fe930,0x107fe9cd,'Resolves referenced node and schedules subordinate sequence subject to track conditions')]),
        windows=windows,sounds=sounds,adaptations=data['contactBoundary'],pending=data['pending'],
        coverAdapter='Root additionally checks source_Spine_3 central chest to swept contact center, sweep start to center, and center to actual receiving surface. This is explicit Minecraft cover protection, not a native CollisionHit ray. Last valid observed contact transform includes intervening physics; root resets skipped ticks/teleports.',
        soundBoundary=data['soundBoundary'],validation='Static binary/field/curve correspondence only. Java compilation, unit/GameTest, original DLL, audio playback and game remain unexecuted.')
    (ROOT/'art/encounters/goliath-taunt-provenance.json').write_text(json.dumps(report,indent=2)+'\n')
    return report
