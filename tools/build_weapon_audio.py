"""Build Blacklight cues from the approved recorded sample libraries.

Requires Python + numpy + FFmpeg. Sources and their attribution are retained
in art/weapon-audio; no procedural oscillator/noise layer is added.
"""
from pathlib import Path
import argparse, functools, hashlib, io, json, subprocess, zipfile
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
parser.add_argument('--output', type=Path, default=Path('build/sample-audio'))
args = parser.parse_args()
sources = args.repo/'art/weapon-audio'
out = args.output; out.mkdir(parents=True, exist_ok=True)
assets = args.repo/'src/main/resources/assets/blacklight'
SR = 44100

@functools.lru_cache(None)
def recording(kind, name):
    archive = {'sword':'swords-0.zip','clash':'swords-1.zip','body':'combat-0.zip','air':'swishes-0.zip'}[kind]
    with zipfile.ZipFile(sources/archive) as z:
        matches = [p for p in z.namelist() if p.endswith('/'+name) or p==name]
        matches = [p for p in matches if '__MACOSX' not in p]
        assert len(matches)==1, (archive,name,matches)
        data=z.read(matches[0])
    raw=subprocess.check_output(['ffmpeg','-v','error','-i','pipe:0','-ac','1','-ar',str(SR),'-f','f32le','pipe:1'],input=data)
    x=np.frombuffer(raw,dtype='<f4').copy(); assert len(x) and np.isfinite(x).all()
    active=np.flatnonzero(np.abs(x)>.002)
    if len(active): x=x[max(0,active[0]-132):min(len(x),active[-1]+2205)]
    x-=x.mean()
    x*=min(3,.5/max(.0001,float(np.max(np.abs(x)))))
    return x

def sample(kind, index=1, speed=1):
    name = {'sword':f'sword.{index}.ogg','clash':f'sword_clash.{index}.ogg','air':f'swish-{index}.wav'}.get(kind)
    if kind=='body':name='Socapex - '+index+'.wav'
    a=recording(kind,name)
    return np.interp(np.arange(0,len(a),speed),np.arange(len(a)),a).astype('float32')

def mix(layers, level=.56, limit=1.5):
    n=min(int(limit*SR),max(int(delay*SR)+len(a) for a,gain,delay in layers))
    x=np.zeros(n,dtype='float32')
    for a,gain,delay in layers:
        start=int(delay*SR);count=min(len(a),n-start)
        if count>0:x[start:start+count]+=a[:count]*gain
    fade=min(110,len(x)//2);x[:fade]*=np.linspace(0,1,fade);tail=min(1323,len(x)//2);x[-tail:]*=np.linspace(1,0,tail)
    peak=float(np.max(np.abs(x)));x*=min(1.3,level/max(.0001,peak))
    return x

def weapon(form,cue,v):
    speed=[.94,1,1.06][v]
    air=sample('air',[4,8,12][v],speed)
    cut=sample('sword',[1,4,7][v],speed*(.82 if form=='blade' else 1.06))
    flesh=sample('body',['new_hits_3','new_hits_4','Swordsmall_1'][v],speed)
    thud=sample('body',['big punch','new_hits_1','new_hits_6'][v],speed*(.72 if form=='hammerfists' else .86 if form=='muscle_mass' else 1))
    wet=sample('body','slow squish',speed*(.9 if form=='hammerfists' else 1.12))
    metal=sample('clash',v+1,speed*.85)
    if cue=='morph':return mix([(wet,.75,0),(cut,.22,.09),(metal,.17,.23)],.48,1.25)
    if cue=='swing':
        if form=='blade':return mix([(cut,.8,0),(air,.35,0)],.43,.8)
        if form=='claws':return mix([(air,.6,0),(cut,.4,.025),(air,.18,.07)],.40,.65)
        if form=='whipfist':return mix([(air,.9,0),(cut,.32,.045)],.46,.7)
        return mix([(sample('air',8+v,speed*(.68 if form=='hammerfists' else .82 if form=='muscle_mass' else 1)),.9,0)],.40,.8)
    if cue=='hit':
        if form in ('blade','claws'):return mix([(flesh,.9,0),(cut,.32,.015)],.55,.85)
        if form=='whipfist':return mix([(flesh,.75,0),(air,.45,0)],.55,.8)
        return mix([(thud,1,0),(flesh,.16,.015)],.56,.9)
    if cue=='heavy':return mix([(thud,.9,0),(flesh,.6 if form in ('blade','claws','whipfist') else .28,.025),(cut,.22 if form=='blade' else .08,0)],.65,1.2)
    return mix([(thud,1,0),(metal,.25,.045),(flesh,.22,.10)],.67,1.35)

def defense(form,cue,v):
    speed=[.94,1,1.06][v]*(.8 if form=='armor' else 1)
    metal=sample('clash',v+4,speed)
    wet=sample('body','slow squish',speed)
    thud=sample('body','big punch',speed*.85)
    cut=sample('sword',v+1,speed)
    if cue=='deploy':return mix([(wet,.5,0),(metal,.40,.05),(cut,.20,.17),(metal,.20,.30)],.53,1.6)
    if cue=='retract':return mix([(wet[::-1],.5,0),(cut[::-1],.25,.05),(metal,.20,.12)],.43,1.1)
    if cue=='deflect':return mix([(metal,.8,0),(cut,.22,.01)],.48,.65)
    if cue=='hit':return mix([(metal,.5,0),(thud,.35,0)],.49,.7)
    if cue=='heavy':return mix([(thud,.8,0),(metal,.55,0),(wet,.15,.05)],.61,1.0)
    return mix([(metal,.6,0),(wet,.65,.02),(cut,.4,.10),(metal,.3,.18)],.61,1.1)

events=json.loads((assets/'sounds.json').read_text(encoding='utf8')) if (assets/'sounds.json').exists() else {}
report=[]; previews={}
for group,forms,cues,fn in [
    ('weapon',['none','claws','blade','hammerfists','whipfist','muscle_mass'],['swing','hit','heavy','ground','morph'],weapon),
    ('defense',['armor','shield'],['deploy','retract','hit','heavy','deflect','break'],defense)]:
    folder=assets/'sounds'/('weapons' if group=='weapon' else 'defense');folder.mkdir(parents=True,exist_ok=True)
    for form in forms:
        preview=[]
        for cue in cues:
            if form=='armor' and cue=='break':continue
            names=[]
            for v in range(3):
                a=fn(form,cue,v);name=f'{form}_{cue}_{v+1}';target=folder/(name+'.ogg')
                subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i','pipe:0','-c:a','libvorbis','-q:a','5',str(target)],input=a.astype('<f4').tobytes(),check=True)
                decoded=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(target),'-f','f32le','-ac','1','pipe:1']),dtype='<f4')
                peak=float(np.max(np.abs(decoded)));assert np.isfinite(decoded).all() and peak<.95
                report.append({'event':f'{group}.{form}.{cue}','file':str(target.relative_to(assets)),'seconds':len(a)/SR,'peak':peak,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
                names.append({'name':f'blacklight:{folder.name}/{name}','volume':1,'pitch':1,'attenuation_distance':20 if cue=='swing' else 24})
                if v==0:preview.extend([a,np.zeros(int(SR*.35))])
            events[f'{group}.{form}.{cue}']={'subtitle':f'subtitle.blacklight.{group}.{form}.{cue}','sounds':names}
        data=np.concatenate(preview).astype('<f4')
        subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i','pipe:0','-c:a','libmp3lame','-q:a','2',str(out/(form+'.mp3'))],input=data.tobytes(),check=True)
(assets/'sounds.json').write_text(json.dumps(events,indent=2)+'\n',encoding='utf8')
(out/'validation.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(f'Validated {len(report)} recorded-sample cues; peak {max(r["peak"] for r in report):.3f}')
