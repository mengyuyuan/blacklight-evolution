"""Static exported-matrix/source-fixture correspondence, not a Java/test run."""
from pathlib import Path
import ast,hashlib,json,math
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
def read(path):return json.loads((ROOT/path).read_text())
def matrix(row):
    x,y,z=row[:3];cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    q=np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
    result=np.eye(4);result[:3,:3]=q@np.diag(row[6:9]);result[:3,3]=np.array(row[3:6])/16;return result
def inspect():
    source=read('src/main/resources/assets/blacklight/models/encounter_goliath_source.json')
    charge=read('src/main/resources/assets/blacklight/models/goliath_charge.json')
    taunt=read('src/main/resources/assets/blacklight/models/goliath_taunt.json')
    fixture=read('src/test/resources/goliath-taunt-reference.json');maximum=0
    for sample in fixture['samples']:
        phase=charge['phases'][sample['phase']];poses=source['clips'][phase['clip']]['frames'][sample['frame']]
        transforms=[]
        for i,row in enumerate(poses):
            # Source helper nodes all have pivot0; authored leaves can pivot,
            # but the reference fixture only samples native source joints.
            value=matrix(row);pivot=np.asarray(source['pivots'][i])/16
            value[:3,3]+=pivot-value[:3,:3]@pivot
            parent=source['parents'][i];transforms.append(value if parent<0 else transforms[parent]@value)
        points=sample['points']+[dict(joint='Spine_3',worldYaw0=sample['bodyAnchor'])]
        for p in points:
            actual=transforms[source['bones'].index('source_'+p['joint'])][:3,3]*[-1,1,-1]+[0,.01,0]
            maximum=max(maximum,float(np.linalg.norm(actual-p['worldYaw0'])))
    assert maximum<.0004
    assert len(taunt['windows'])==11 and len(set(w['id'] for w in taunt['windows']))==8
    assert all(w['collideWith']==0x1000000c0 and w['sourceDamage']<=750 for w in taunt['windows'])
    assert len(charge['phases'])==13 and len(source['clips'])==17
    # Independently count the source phase times; no implementation or JUnit is
    # imported/executed. This catches stale tests/data after program extension.
    ticks=[math.ceil(s['seconds']*20-1e-5) for s in charge['phases']]
    assert sum(ticks[i] for i in [0,1,2,3,4,6,7,8,9,10,11,12])==312
    for name in ['build_goliath_taunt.py','build_goliath_charge.py','rebind_goliath_source.py','read_goliath_charge_fig.py']:
        ast.parse((ROOT/'tools'/name).read_text(encoding='utf-8'))
    report=dict(validation='Static JSON/Python AST/matrix data correspondence only. No Java compilation, unit/GameTest, game, DLL execution or audio.',
        rawJointSamples=len(fixture['samples'])*10,maxNativeExportedJointErrorBlocks=maximum,
        phases=len(charge['phases']),normalDurationTicks=312,clips=len(source['clips']),sourceFrames=sum(len(c['frames']) for c in source['clips'].values()),
        windows=len(taunt['windows']),independentDamageGroups=8,pending=['Java compilation and all unit/GameTests','Actual player full-taunt collision and cover validation','Source waveform bank/playback','Full original story/grapple/dismember conditions','Final giant-fist grounding'])
    (ROOT/'art/encounters/goliath-taunt-static-audit.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':inspect()
