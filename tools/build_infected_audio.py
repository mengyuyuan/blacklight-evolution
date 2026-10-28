"""Recorded-source infection bank. See art/weapon-audio for CC0/CC BY-SA source credits.

Python/numpy + FFmpeg. No generated noise/oscillators; only layering, filtering,
enveloping and resampling the approved Foley recordings. Existing banks are untouched.
"""
from pathlib import Path
import argparse, functools, hashlib, json, subprocess, zipfile
import numpy as np

p=argparse.ArgumentParser();p.add_argument('--output',type=Path,default=Path('build/infected-audio'));args=p.parse_args()
repo=Path(__file__).resolve().parents[1];assets=repo/'src/main/resources/assets/blacklight'
out=args.output;out.mkdir(parents=True,exist_ok=True);folder=assets/'sounds/infection';folder.mkdir(parents=True,exist_ok=True)
SR=44100
@functools.lru_cache(None)
def source(kind,name,speed=1):
    archive={'body':'combat-0.zip','air':'swishes-0.zip','bone':'swords-1.zip'}[kind]
    name={'body':f'Socapex - {name}.wav','air':f'swish-{name}.wav','bone':f'sword_clash.{name}.ogg'}[kind]
    with zipfile.ZipFile(repo/'art/weapon-audio'/archive) as z:
        match=[s for s in z.namelist() if '__MACOSX' not in s and (s.endswith('/'+name) or s==name)];assert len(match)==1,match
        data=z.read(match[0])
    raw=subprocess.check_output(['ffmpeg','-v','error','-i','pipe:0','-ac','1','-ar',str(SR),'-f','f32le','pipe:1'],input=data)
    a=np.frombuffer(raw,dtype='<f4').copy();a-=a.mean();active=np.flatnonzero(abs(a)>.002)
    if len(active):a=a[max(0,active[0]-150):min(len(a),active[-1]+2205)]
    a*=min(3,.45/max(.0001,float(max(abs(a)))))
    return np.interp(np.arange(0,len(a),speed),np.arange(len(a)),a).astype('float32')
def mix(layers,seconds,peak):
    a=np.zeros(min(int(SR*seconds),max(int(d*SR)+len(s) for s,g,d in layers)),dtype='float32')
    for s,g,d in layers:
        start=int(d*SR);n=min(len(s),len(a)-start)
        if n>0:a[start:start+n]+=s[:n]*g
    n=min(len(a)//2,220);a[:n]*=np.linspace(0,1,n);n=min(len(a)//2,2205);a[-n:]*=np.linspace(1,0,n)
    a*=peak/max(.0001,float(max(abs(a))));return a
def cue(name,v):
    rate=[.95,1.02,1.08][v]
    wet=source('body','slow squish',rate);hit=source('body',['new_hits_3','new_hits_5','new_hits_8'][v],rate)
    mass=source('body',['big punch','new_hits_1','new_hits_6'][v],rate*.82)
    air=source('air',[4,8,12][v],rate*.86);bone=source('bone',v+1,rate*.8)
    if name=='tension':return mix([(wet[::-1],.8,0),(air[::-1],.2,.12)],.85,.43)
    if name=='swing':return mix([(air,.8,0),(wet,.22,.015)],.55,.43)
    if name=='tear':return mix([(hit,.9,0),(wet,.4,.025)],.65,.56)
    if name=='pierce':return mix([(bone,.43,0),(hit,.85,.012),(wet,.22,.045)],.58,.55)
    if name=='crush':return mix([(mass,1,0),(hit,.6,.02),(bone,.18,.055)],.9,.66)
    if name=='lash':return mix([(air,.9,0),(wet,.45,.045),(hit,.18,.12)],.72,.51)
    if name=='retract':return mix([(wet[::-1],.75,0),(air[::-1],.25,.04)],.7,.38)
    if name=='launch':return mix([(wet,.8,0),(air,.65,.015),(mass,.2,.025)],.6,.54)
    if name=='burst':return mix([(mass,.85,0),(wet,.8,.008),(hit,.65,.03)],1,.65)
    if name=='erupt':return mix([(mass,.6,0),(bone,.35,.015),(wet,.65,.025),(hit,.35,.06)],.75,.6)
    if name=='resonance_charge':return mix([(wet[::-1],.5,0),(bone[::-1],.2,.08),(air[::-1],.5,.04)],1.2,.39)
    if name=='resonance':return mix([(mass,.7,0),(air,.55,.005),(bone,.22,.025)],.85,.59)
    if name=='mend':return mix([(wet,.6,0),(wet[::-1],.35,.12),(bone,.08,.18)],1.1,.42)
    if name=='ground':return mix([(mass,1,0),(bone,.3,.012),(hit,.45,.04)],.95,.66)
    raise ValueError(name)
names=['tension','swing','tear','pierce','crush','lash','retract','launch','burst','erupt','resonance_charge','resonance','mend','ground']
zh=['肌腱拉紧','血肉挥动','血肉撕裂','骨刺穿入','重肢砸击','触须甩动','组织回抽','肉瘤喷出','肉瘤爆裂','触须破壳','共振蓄势','血肉共振','组织织合','重击落地']
events=json.loads((assets/'sounds.json').read_text('utf8'));report=[];preview=[]
for name in names:
    entries=[]
    for v in range(3):
        a=cue(name,v);target=folder/f'{name}_{v+1}.ogg'
        subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i','pipe:0','-af','highpass=f=55,lowpass=f=12500','-c:a','libvorbis','-q:a','4',str(target)],input=a.astype('<f4').tobytes(),check=True)
        raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(target),'-ac','1','-ar',str(SR),'-f','f32le','pipe:1']);b=np.frombuffer(raw,dtype='<f4')
        peak=float(max(abs(b)));rms=float(np.sqrt(np.mean(b*b)));assert np.isfinite(b).all() and peak<.95 and rms>.002,(name,peak,rms)
        report.append(dict(event='infection.'+name,file=target.relative_to(assets).as_posix(),seconds=len(b)/SR,peak=peak,rms=rms,bytes=target.stat().st_size,sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
        entries.append(dict(name=f'blacklight:infection/{name}_{v+1}',attenuation_distance=24))
        if v==0:preview.extend([b,np.zeros(int(SR*.4))])
    events['infection.'+name]=dict(subtitle='subtitle.blacklight.infection.'+name,sounds=entries)
(assets/'sounds.json').write_text(json.dumps(events,indent=2)+'\n','utf8')
for locale in ('zh_cn','en_us'):
    f=assets/'lang'/f'{locale}.json';data=json.loads(f.read_text('utf8'))
    for name,label in zip(names,zh):data['subtitle.blacklight.infection.'+name]=label if locale=='zh_cn' else 'Blacklight '+name.replace('_',' ')
    f.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf8')
subprocess.run(['ffmpeg','-v','error','-y','-f','f32le','-ar',str(SR),'-ac','1','-i','pipe:0','-c:a','libmp3lame','-q:a','2',str(out/'infection-cues.mp3')],input=np.concatenate(preview).astype('<f4').tobytes(),check=True)
(out/'validation.json').write_text(json.dumps(report,indent=2),'utf8')
(out/'试听顺序.txt').write_text('\n'.join(f'{i+1}. {label} / {name}' for i,(label,name) in enumerate(zip(zh,names))),'utf8')
print(f'{len(report)} recorded cues, {sum(r["bytes"] for r in report):,} bytes, maximum decoded peak {max(r["peak"] for r in report):.3f}')
