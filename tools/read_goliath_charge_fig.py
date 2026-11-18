"""Read-only P2 FIG index/predicate audit. Does not execute the original runtime.

Branch hashes are interpreted ONLY where a branch is expected. The previous
shared structural index omitted passiveNode/commandBank/commandNode (28 nodes,
including commandBank children), making every subsequent BranchReference wrong.
"""
from pathlib import Path
import hashlib, json, re, struct
import read_goliath_grounding as grounding

ROOT = Path(__file__).resolve().parents[1]

def decode():
    raw = grounding.SOURCE.read_bytes()
    runtime = grounding.RUNTIME.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == grounding.SOURCE_SHA
    assert hashlib.sha256(runtime).hexdigest() == grounding.RUNTIME_SHA
    h = lambda name: grounding.hash64(name.encode())
    names = {h(m[0].decode()):m[0].decode() for data in [runtime,raw]
             for m in re.finditer(rb'[A-Za-z_][A-Za-z0-9_]{0,120}',data)}
    kinds = {h(n):(n,extra) for n,extra in {
        'bank':0, 'node':0, 'store':0, 'reference':0, 'passiveNode':0,
        'commandBank':0, 'commandNode':0, 'contentNode':4, 'moveNode':8}.items()}
    # Native registry names use these exact P2 hashes, not guessed casing.
    kinds[0xf55e99665d47c7d3] = ('contentNode',4)
    kinds[0x332ae1869088edab] = ('moveNode',8)
    properties = {h(n):n for n in ['conditions','onBegin','tracks','onEnd','commands']}
    branches=[]
    def unpack(fmt,p): return struct.unpack_from('<'+fmt,raw,p)[0]
    def string(p):
        length=unpack('I',p)
        return raw[p+4:p+4+length].decode().rstrip('\0'),p+4+(length+3)//4*4
    def branch(p,parent=None,root=False):
        key,size=struct.unpack_from('<QI',raw,p);end=p+12+size;q=p+12
        if root:
            assert key==7021078959221846271
            q+=4;name=unpack('Q',q);q+=16
            path,q=string(q);reference=unpack('i',q);declared=unpack('I',q+4);q+=8
            kind='chunk';siblings=1
        else:
            assert key in kinds,(p,hex(key),'unknown branch in branch context')
            kind,extra=kinds[key];name=unpack('Q',q);q+=8
            path,q=string(q);siblings=unpack('I',q);q+=4
            if kind=='reference': referencePath,q=string(q);reference=unpack('i',q);q+=4
            q+=extra
        row=dict(index=len(branches),offset=p,kind=kind,name=names.get(name,'#'+hex(name)[2:]),
                 nameHash=hex(name),parent=parent,properties={},children=[])
        branches.append(row)
        while q<end:
            key,n=struct.unpack_from('<QI',raw,q)
            if key in properties:
                assert not row['children'],(q,'property after branch child')
                assert raw[q+12+n:q+20+n]==b'\0'*8
                row['properties'][properties[key]]=dict(offset=q,payloadBytes=n)
                q+=20+n
            else:
                row['children'].append(len(branches));q=branch(q,row['index'])
        assert q==end and raw[q:q+8]==b'\0'*8,(p,q,end)
        if root: assert declared==len(branches)==569
        return q+8
    end=branch(5400572,root=True)
    assert end==5400572+20+unpack('I',5400580)
    expected={44:5420464,51:5425948,58:5429876,424:5603724,434:5605824,437:5606248}
    for index,offset in expected.items(): assert branches[index]['offset']==offset
    def record(offset,kind,size):
        key,n=struct.unpack_from('<QI',raw,offset)
        assert key==h(kind) and n==size,(offset,kind,hex(key),n)
        p=offset+12
        return dict(offset=offset,kind=kind,payloadBytes=size,
                    sha256=hashlib.sha256(raw[p:p+n]).hexdigest())
    records=[]
    def variable(offset,kind,key,op,value,size):
        records.append(record(offset,kind,size));p=offset+12
        assert unpack('Q',p+4)==key and unpack('Q',p+12)==h(op) and unpack('i',p+20)==value
    variable(5422108,'variableIntSet',0x699a40927dae69dd,'=',0,28)
    variable(5572216,'variableIntSet',0x699a40927dae69dd,'+=',1,28)
    variable(5426244,'variableIntCompare',0x699a40927dae69dd,'==',0,24)
    variable(5426520,'variableIntCompare',0x241c1eb8d73b7498,'>',0,24)
    variable(5603768,'variableIntCompare',h('aggro_disable'),'>',0,24)
    variable(5605868,'variableIntCompare',0xe35936b5e97879e5,'>',0,24)
    variable(5428548,'variableIntSet',0xe35936b5e97879e5,'=',0,28)
    for off,inc in [(5424756,1),(5425896,0)]:
        records.append(record(off,'timerSet',24));p=off+12
        assert unpack('Q',p+4)==0xfc024ee0c022da31
        assert (unpack('f',p+12),unpack('I',p+16),unpack('f',p+20))==(0,inc,0)
    records.append(record(5426580,'timerCompare',24));p=5426592
    assert unpack('Q',p+4)==0xfc024ee0c022da31 and unpack('Q',p+12)==h('<') and unpack('f',p+20)==2.5
    records.append(record(5426320,'angle',156));p=5426332
    assert unpack('Q',p+132)==h('XZ') and unpack('Q',p+140)==h('==')
    assert (unpack('f',p+148),unpack('f',p+152))==(0,120)
    records.append(record(5426656,'distance',88));p=5426668
    assert unpack('Q',p+68)==h('Y') and unpack('Q',p+76)==h('<') and unpack('f',p+84)==0
    records.append(record(5426864,'supportingSurface',8));assert unpack('I',5426880)==0
    records.append(record(5573124,'false',4));assert unpack('I',5573136)==0xd3
    records.append(record(5606292,'__anchorRef',4));assert unpack('I',5606304)==0xd3
    for off,idx,n in [(5426192,51,728),(5426940,51,80),(5428456,44,48),(5428976,58,48)]:
        records.append(record(off,'sequence',n));p=off+12
        assert unpack('f',p+8)==0 and unpack('I',p+12)==0 and unpack('i',p+16)==idx
    return dict(schema=1,source=str(grounding.SOURCE),sourceSha256=grounding.SOURCE_SHA,
        runtimeSha256=grounding.RUNTIME_SHA,branchCount=len(branches),branches=branches,records=records,
        indexCorrection='541 old rows + 21 passiveNode + 2 commandBank + 5 commandNode = 569. Parse hashes by parent context; no hand-added reference index offsets.',
        loop=dict(branch=51,sequenceOffsets=[5426192,5426940],
            expression='(playerHitCount == 0 && (abs(targetBearingXZ) <= 60 || angleOverride > 0) && elapsedCycleParentSeconds < 2.5 && (actorY - playerY < 0 || !playerSupportingSurface)) || forceCycle > 0',
            hitCounter=dict(hash='0x699a40927dae69dd',reset=5422108,increment=5572216,
                condition='reactionEventConditionGroup Receiver.onlinePlayerIndex >= 0 at 5572100/5572140; this counts Goliath hitting a player, not damage taken by Goliath'),
            timerStart=5424756,timerStop=5425896,angleOverride='0x241c1eb8d73b7498',forceCycle='0xe35936b5e97879e5'),
        exit=dict(expression='condition(437) || aggro_disable > 0 || forceCycle > 0',
            disabled437='false anchor 0xd3 at 5573124 is the first AND operand at 5606292; subsequent Player/vehicle clauses cannot enable this branch',
            special=dict(offset=5428304,clip='behemoth_com_biped_bullrush',frames=[117,150],nextBranch=44,clearForceOnEnd=5428548),
            normal=dict(offset=5428708,clip='behemoth_bullrush_to_aggro',frames=[117,125],nextBranch=58,
                nextEntry='branch58 animation5429972 frames125..144 fits its explicit0.4s; ordinary intact-leg continuation is decoded/ported by build_goliath_taunt.py; story/grapple/dismember paths remain open')),
        animationTiming='P2 positiveTimeEnd fits the selected frame interval to (timeEnd-timeBegin)/abs(sourceSpeed). It does not play raw speed and hold the endpoint. ENTER66..85 in0.6666669846s has effectiveSpeed0.94999955. Native non-loop evaluator106829c0 and duration10682460 prove this independently of P1.',
        targeting='AutoTarget5423060/5424892/5428860 each has timeBegin0,timeEnd-1,useInput0,useCamera0,checkArc0,checkDistance0. Whole-owning-phase rates are120/45/180 degrees/sec; source target selection/filter is still a committed-target MC adapter.',
        runtimeEvidence=grounding.pe_ranges(runtime,[
            ('BranchReference index resolution',0x10763c90,0x10763cc8,'Direct root vector index; fallback advances normal child/sibling preorder.'),
            ('Condition AND/OR/NOT evaluator',0x10711610,0x107116b2,'Default AND; type1 OR and type2 NOT; false437 short-circuits before vehicle condition.'),
            ('Angle arc evaluator',0x104c7740,0x104c79e7,'Equality compares signed angle inside center +/- half arc; 120 means +/-60 degrees.'),
            ('Signed coordinate distance',0x103e2810,0x103e28aa,'Second position minus first. Distance caller supplies otherPosition then position: source Y is actorY-playerY.'),
            ('SupportingSurface condition',0x107166b0,0x10716760,'Compares object support query to configured bool; this track supplies false.'),
            ('Sequence action creation',0x107fe690,0x107fe728,'Creates Action with flag0x400; virtual7=107fe750 (onEnd), no update callback.'),
            ('Sequence condition evaluation',0x107fe750,0x107fe7bf,'OnEnd evaluates current conditions and requests resolved branch with priority.'),
            ('Action completion dispatch',0x106b2520,0x106b2797,'After finish flag, invokes virtual7 when0x400 then removes action. Sequence is not a per-frame restart.'),
            ('Pending branch request',0x106b1e80,0x106b1ed8,'Stores pending branch at+1c and priority; equal/lower priority does not replace earlier accepted request.'),
            ('Pending branch consumption',0x106b2280,0x106b2323,'Consumes pending branch, ends prior action set, creates new branch action before next clock advance.'),
            ('P2 Puppet requested duration',0x1040af6d,0x1040afa3,'Calls track duration getter then divides by absolute supplied playback speed.'),
            ('P2 Puppet clip window and duration',0x106829c0,0x10682a67,'Sets frame begin/end; positive duration above1e-5 and non-loop playback fit frame span to duration. Otherwise direct speed.'),
            ('P2 Puppet native interval duration',0x10682460,0x106824db,'abs((endFrame-startFrame)/(fps*speed)); fitting resets speed1 before calculating this value.'),
            ('P2 AutoTarget field metadata',0x10427fc0,0x104283a5,'Ordered fields timeBegin,timeEnd,turningVelocity,useInput,useCamera,direction,arc,maxDistance,checkArc,checkDistance,orientOffset and target filters.'),
            ('P2 AutoTarget time interval',0x10463690,0x10463697,'Track duration=end(+30)-begin(+2c); negative end belongs to owning parent until completion.')]),
        boundary='Only static source decoding. Script variables are inputs, not synthesized from boss health. Original global Player becomes committed Player in multiplayer MC; non-player target has no borrowed global player. Native root blend/support, taunt story/grapple/dismember paths, source audio playback and scripted scenario ownership remain unported. Ordinary taunt pose/contact continuation has separate goliath-taunt-provenance.json.',
        validation='No Java/Gradle compilation, unit/GameTest, client, audio or native DLL execution.')

if __name__=='__main__':
    report=decode()
    (ROOT/'art/encounters/goliath-charge-fig-control.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(dict(branchCount=report['branchCount'],predicateRecords=len(report['records']))))
