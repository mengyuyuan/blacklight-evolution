"""Export Blacklight motion data with separate initial-source and project-adaptation metadata.

Reads decoded source inputs and adapts them to Minecraft. This script does not
claim the resulting motion was wholly authored from scratch, and does not upload files.
"""

from motion_provenance import motion_notice, motion_provenance
import argparse
import bisect
import copy
import hashlib
import json
from pathlib import Path
import runpy

parser=argparse.ArgumentParser()
parser.add_argument('--contract',type=Path,required=True)
parser.add_argument('--decoded',type=Path,required=True)
parser.add_argument('--motions',type=Path,required=True)
parser.add_argument('--moves',nargs='+',default=['hammer_quake'])
args=parser.parse_args()
configs=runpy.run_path(str(args.contract))['MOVES']
bones=('Forarm_R','Wrist_R','Forarm_L','Wrist_L')
sources={}

def source(name):
    if name not in sources:
        path=args.decoded/(name+'.json')
        raw=path.read_bytes();data=json.loads(raw)
        channels={g['name']:c for g in data['groups'] for c in g['channels']
                  if c.get('semantic')=='SCL' and g['name'] in bones}
        sources[name]=(channels,hashlib.sha256(raw).hexdigest())
    return sources[name][0]

def sample(name,frame):
    channels=source(name);out=[]
    for bone in bones:
        c=channels.get(bone)
        if c is None:out.append(1.0);continue
        # These hammer channels are uniform XYZ scales. Refuse a lossy export.
        if any(max(v)-min(v)>1e-6 for v in c['values']):raise ValueError('Nonuniform '+name+'/'+bone)
        frames=c['frames'];i=max(0,bisect.bisect_right(frames,frame)-1);j=min(i+1,len(frames)-1)
        t=max(0,min(1,(frame-frames[i])/(frames[j]-frames[i]))) if j!=i else 0
        out.append(c['values'][i][0]*(1-t)+c['values'][j][0]*t)
    return out

def generate(stages,default,times):
    result=[]
    for time in times:
        clock=0;previous=None
        for index,stage in enumerate(stages):
            end=clock+stage['seconds'];name=stage.get('source',default)
            if time<end-1e-8 or index==len(stages)-1:
                u=max(0,min(1,(time-clock)/stage['seconds']))
                row=sample(name,stage['start']+(stage['end']-stage['start'])*u)
                blend=stage.get('blend',0)
                if previous is not None and blend and time-clock<blend:
                    w=max(0,min(1,(time-clock)/blend));w=w*w*(3-2*w)
                    row=[a*(1-w)+b*w for a,b in zip(previous,row)]
                result.append(row);break
            previous=sample(name,stage['end']);clock=end
    return result

clips={}
for name,cfg in configs.items():
    if name not in args.moves:continue
    def motion(id):
        data=json.loads((args.motions/(id+'.json')).read_text())
        return data,data['animation'][0]['time']
    base,times=motion(name)
    charge,chargeTimes=motion(name+'_charge')
    a,b,seconds=cfg['charge']
    rows=generate(cfg['stages'],cfg['source'],times)
    chargeRows=generate([cfg['stages'][0],dict(start=a,end=b,seconds=seconds,blend=1/30)],cfg['source'],chargeTimes)
    chargedRows=copy.deepcopy(rows)
    cut=round(cfg['release']*60);end=min(len(rows)-1,cut+3)
    for i in range(cut,end):
        w=(i-cut)/(end-cut);w=w*w*(3-2*w)
        chargedRows[i]=[a*(1-w)+b*w for a,b in zip(chargeRows[-1],rows[end])]
    for id,ts,data in [(name,times,rows),(name+'_charge',chargeTimes,chargeRows),(name+'_charged',times,chargedRows)]:
        clips[id]=dict(times=ts,frames=data)

payload=dict(schema=1,bones=list(bones),clips=clips,
             sources={n:sha for n,(_,sha) in sources.items()},
             contractSha256=hashlib.sha256(args.contract.read_bytes()).hexdigest(),
             privateNotice=motion_notice('Original-derived scale channels mapped to local clip time; private local use only.'), provenance=motion_provenance())
output=args.motions/'hammer-scales.data'
output.write_text(json.dumps(payload,separators=(',',':'),allow_nan=False),encoding='utf8')
print(f'Exported {len(clips)} synchronized hammer scale tracks to {output}')
