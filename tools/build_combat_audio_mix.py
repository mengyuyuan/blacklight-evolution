"""Master combat Foley and build absorption cues using only the existing credited library.

Usage: python tools/build_combat_audio_mix.py --baseline PREVIOUS.jar --project PROJECT --evidence DIR
Requires Python, numpy, FFmpeg with libvorbis. The baseline is the release before
the combat remix; newly added cues mix its existing licensed stems.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse,subprocess,zipfile,json,io,os,wave
import numpy as np
parser=argparse.ArgumentParser()
parser.add_argument('--baseline',type=Path,required=True)
parser.add_argument('--project',type=Path,required=True)
parser.add_argument('--evidence',type=Path,required=True)
parser.add_argument('--ffmpeg',default='ffmpeg')
args=parser.parse_args()
sr=44100
out=args.project/'src/main/resources/assets/blacklight'
prefix='assets/blacklight/'
evidence=args.evidence;evidence.mkdir(parents=True,exist_ok=True)
def ffmpeg(options,data):
    return subprocess.run([args.ffmpeg,'-v','error','-nostdin',*options],input=data,
        stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout
def decode(data):
    return np.frombuffer(ffmpeg(['-i','pipe:0','-ac','1','-ar',str(sr),'-f','f32le','pipe:1'],data),'<f4').copy()
def encode(x):
    options=['-f','f32le','-ar',str(sr),'-ac','1','-i','pipe:0','-map_metadata','-1',
        '-f','ogg','-c:a','libvorbis','-q:a','6','pipe:1']
    for _ in range(4):
        ogg=ffmpeg(options,x.astype('<f4').tobytes())
        peak=float(np.max(np.abs(decode(ogg))))
        if peak<=.96:return ogg
        x=x*(.94/peak)
    raise ValueError("Post-Vorbis true sample peak remained above ceiling")
def measure(x):
    block=sr//50
    rms=np.sqrt((np.pad(x,(0,(-len(x))%block)).reshape(-1,block)**2).mean(axis=1))
    active=rms[rms>max(.002,rms.max()*.10)]
    return float(np.percentile(active,70)) if len(active) else 0
def master(x,target):
    x=x.copy();x-=x.mean()
    gain=np.clip(target/max(.001,measure(x)),1.45,6)
    y=x*gain
    # Soft knee only at the top of the waveform; preserve dynamics below it.
    over=abs(y)>.64
    y[over]=np.sign(y[over])*(.64+.24*np.tanh((abs(y[over])-.64)/.24))
    return y
with zipfile.ZipFile(args.baseline) as z:
    original=json.loads(z.read(prefix+'sounds.json'))
    if 'consume.grip' in original:raise ValueError('Use the original pre-remix JAR as the input bank')
    paths={(entry if isinstance(entry,str) else entry['name']).split(':')[1]
           for key,event in original.items() if key.startswith('weapon.')
           for entry in event['sounds'] if isinstance(entry,str) or entry.get('type','sound')=='sound'}
    payload={p:z.read(prefix+'sounds/'+p+'.ogg') for p in paths}
def process(name):
    old=decode(payload[name])
    target=.17 if 'swing' in name else .22 if any(x in name for x in ('heavy','ground')) else .19
    new=encode(master(old,target));p=out/'sounds'/(name+'.ogg');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(new)
    y=decode(new)
    return name,old,y
with ThreadPoolExecutor(max_workers=6) as pool:
    rendered=list(pool.map(process,sorted(paths)))
old={n:x for n,x,y in rendered};current={n:y for n,x,y in rendered}
# Stem, playback ratio, level, offset seconds. Pitch/rhythm differentiate
# puncture, traction, snap, and the softer intake tail between contacts.
recipes={
 'consume.grip':[('whipfist_morph',1.6,.80,0),('none_hit',1.4,.45,.015)],
 'consume.pierce':[('claws_hit',1.3,1,0),('whipfist_morph',1.7,.66,.022)],
 'consume.cleave':[('blade_hit',.95,.95,0),('claws_morph',1.22,.68,.04)],
 'consume.tear':[('whipfist_morph',1.2,1,0),('claws_hit',1.25,.69,.045)],
 'consume.dent':[('none_hit',.95,1,0),('muscle_mass_morph',1.8,.49,.035)],
 'consume.crush':[('hammerfists_heavy',1,1,0),('whipfist_morph',1.42,.72,.05)],
 'consume.slash':[('blade_swing',1.25,.7,0),('claws_hit',1.25,1,.024)],
 'consume.absorb':[('whipfist_morph',.87,.85,0),('muscle_mass_morph',1.2,.38,.13)],
 'consume.finish':[('none_morph',1.5,.95,0),('muscle_mass_hit',1.12,.6,.055)],
 'skill.charge':[('muscle_mass_morph',.78,.9,0),('whipfist_swing',.88,.36,.12)],
 'skill.groundspike':[('hammerfists_ground',.86,1,0),('claws_ground',1.12,.72,.095)],
 'skill.tendril':[('whipfist_swing',.84,.8,0),('whipfist_heavy',.84,1,.055),('claws_morph',.93,.55,.12)],
 'skill.pain':[('blade_swing',.91,.70,0),('muscle_mass_heavy',.87,1,.045)],
 'skill.dive':[('hammerfists_ground',.75,1,0),('muscle_mass_ground',1,.64,.105)]}
events=json.loads((out/'sounds.json').read_bytes())
labels=['Closes grip','Piercing impact','Cleaving impact','Tissue tears','Blunt impact','Crushing impact','Slashing impact','Biomass draws inward','Absorption completes','Power gathers','Groundspikes erupt','Tendrils burst','Focused strike releases','Dive impacts']
chinese=['抓住目标','刺入','劈开','组织撕裂','钝击','重压碎裂','刃口划过','组织回收','吞噬完成','蓄力','地刺爆发','触手爆发','集中打击释放','俯冲撞地']
for index,(event,layers) in enumerate(recipes.items()):
    names=[]
    for v in [1,2]:
        mix=np.zeros(sr*2,np.float32);end=0
        for stem,rate,level,delay in layers:
            x=old[f'weapons/{stem}_{v}'];active=np.nonzero(abs(x)>.008)[0]
            if len(active):x=x[max(0,active[0]-120):active[-1]+128]
            x=np.interp(np.arange(0,len(x),rate),np.arange(len(x)),x)
            start=round(sr*delay);n=min(len(x),len(mix)-start)
            mix[start:start+n]+=level*x[:n];end=max(end,start+n)
        clip=master(mix[:end],.19 if event.endswith(('grip','absorb','charge')) else .23)
        fade=min(len(clip),sr//65);clip[-fade:]*=np.linspace(1,0,fade)
        key=event.replace('.','/')+'_'+str(v);p=out/'sounds'/(key+'.ogg');p.parent.mkdir(parents=True,exist_ok=True)
        p.write_bytes(encode(clip));current[key]=decode(p.read_bytes())
        names.append({'name':'blacklight:'+key,'attenuation_distance':28 if event.startswith('consume.') else 38})
    events[event]={'subtitle':'subtitle.blacklight.'+event,'sounds':names}
for cue,source in [('hit','pierce'),('ground','crush')]:
    events['weapon.backspines.'+cue]={'subtitle':'subtitle.blacklight.weapon.backspines.'+cue,
        'sounds':[{'name':'blacklight:consume.'+source,'type':'event'}]}
for lang,strings in [('en_us',labels),('zh_cn',chinese)]:
    p=out/'lang'/(lang+'.json');data=json.loads(p.read_bytes())
    data['subtitle.blacklight.weapon.backspines.hit']='Quill pierces' if lang=='en_us' else '肩刺刺入'
    data['subtitle.blacklight.weapon.backspines.ground']='Quill strikes ground' if lang=='en_us' else '肩刺撞地'
    for event,label in zip(recipes,strings):data['subtitle.blacklight.'+event]=label
    p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
(out/'sounds.json').write_text(json.dumps(events,indent=2)+'\n',encoding='utf8')
def rmsdb(v):return 20*np.log10(max(1e-8,v))
stats={name:{'beforeActiveDb':round(rmsdb(measure(x)),2),'afterActiveDb':round(rmsdb(measure(y)),2),
             'afterPeak':round(float(abs(y).max()),5)} for name,x,y in rendered}
stats.update({name:{'afterActiveDb':round(rmsdb(measure(y)),2),'afterPeak':round(float(abs(y).max()),5)}
              for name,y in current.items() if name not in old})
assert max(row['afterPeak'] for row in stats.values())<1.0
(evidence/'audio-levels.json').write_text(json.dumps(stats,indent=2),encoding='utf8')
def preview(name,seq):
    blocks=[]
    for x in seq:blocks.extend([np.zeros(sr//4),x])
    with wave.open(str(evidence/(name+'.wav')),'wb') as w:
        w.setparams((1,2,sr,0,'NONE','not compressed'))
        w.writeframes((np.clip(np.concatenate(blocks),-1,1)*32767).astype('<i2').tobytes())
selection=['weapons/none_swing_1','weapons/none_hit_1','weapons/claws_hit_1','weapons/hammerfists_heavy_1']
preview('weapon-before',[old[x] for x in selection])
preview('weapon-after',[current[x] for x in selection])
preview('consume-contacts',[current['consume/'+s+'_1'] for s in ['grip','pierce','tear','crush','absorb','finish']])
print(len(rendered),'existing variations;',len(current)-len(old),'new variations; median change:',
      np.median([d['afterActiveDb']-d['beforeActiveDb'] for d in stats.values() if 'beforeActiveDb' in d]))
