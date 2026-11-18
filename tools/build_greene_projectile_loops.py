"""Create seamless loop adapters from existing credited project audio; never play.

No Prototype waveforms are read. Circular overlap-add and resampling are authored
presentation choices, not a reconstruction of the native loop's timbre.
"""
from array import array
from pathlib import Path
import hashlib
import json
import math
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
FORGE=Path('D:/blacklight-evolution-forge-1.20.1')
ASSETS=Path('src/main/resources/assets/blacklight')
FFMPEG=Path('D:/AI/VideoTools/node_modules/ffmpeg-static/ffmpeg.exe')
RATE=44100


def decode(path):
    pcm=array('f',subprocess.check_output([str(FFMPEG),'-v','error','-nostdin','-i',str(path),'-ac','1','-ar',str(RATE),'-f','f32le','pipe:1']))
    if sys.byteorder!='little':pcm.byteswap()
    return pcm


def sample(pcm,phase):
    position=(phase%1)*len(pcm);at=int(position);fraction=position-at
    return pcm[at]*(1-fraction)+pcm[(at+1)%len(pcm)]*fraction


roles={
    'biomass':(2.8753333333333333,['infection/resonance_charge_1.ogg','infection/mend_2.ogg','infection/tension_3.ogg']),
    'fire':(5.788299319727892,['vehicles/helicopter_jet.ogg'])}
rows=[]
for role,(seconds,names) in roles.items():
    sources=[decode(ROOT/ASSETS/'sounds'/name) for name in names]
    frames=round(seconds*RATE);out=array('f',[0])*frames
    for layer,pcm in enumerate(sources):
        cycles=max(1,round(seconds*RATE/len(pcm)))
        for i in range(frames):
            phase=(i*cycles/frames+layer/len(sources))%1
            opposite=(phase+.5)%1
            # Two periodic overlapping Hann windows: no endpoint silence gap.
            out[i]+=(sample(pcm,phase)*math.sin(math.pi*phase)**2+sample(pcm,opposite)*math.sin(math.pi*opposite)**2)/len(sources)
    mean=sum(out)/len(out);peak=max(abs(x-mean) for x in out)
    for i in range(len(out)):out[i]=(out[i]-mean)*.5/max(peak,.001)
    # A 5 ms cosine bridge meets at the same endpoint value and zero slope;
    # unlike fading to silence it does not insert a silent loop gap.
    edge=round(.005*RATE);join=(out[0]+out[-1])*.5
    for i in range(edge):
        weight=.5-.5*math.cos(math.pi*i/(edge-1))
        out[i]=join+(out[i]-join)*weight
        out[-1-i]=join+(out[-1-i]-join)*weight
    target=ASSETS/'sounds/greene'/f'projectile_{role}_loop.ogg'
    (ROOT/target).parent.mkdir(parents=True,exist_ok=True)
    raw=out.tobytes() if sys.byteorder=='little' else b''.join(__import__('struct').pack('<f',x) for x in out)
    subprocess.run([str(FFMPEG),'-v','error','-nostdin','-y','-f','f32le','-ac','1','-ar',str(RATE),'-i','pipe:0','-c:a','libvorbis','-q:a','5',str(ROOT/target)],input=raw,check=True)
    encoded=(ROOT/target).read_bytes();(FORGE/target).parent.mkdir(parents=True,exist_ok=True);(FORGE/target).write_bytes(encoded)
    decoded=decode(ROOT/target)
    rows.append(dict(role=role,file=target.as_posix(),sha256=hashlib.sha256(encoded).hexdigest(),
        sources=[dict(file=name,sha256=hashlib.sha256((ROOT/ASSETS/'sounds'/name).read_bytes()).hexdigest()) for name in names],
        license='CC BY-SA 3.0 derived Socapex Foley' if role=='biomass' else 'Existing project-authored synthesis',
        sampleRate=RATE,channels=1,frames=len(decoded),seconds=len(decoded)/RATE,
        authoredBoundaryStep=abs(out[-1]-out[0]),encodedBoundaryStep=abs(decoded[-1]-decoded[0]),
        encodedPeak=max(abs(x) for x in decoded),encodedRms=math.sqrt(sum(x*x for x in decoded)/len(decoded))))
record=dict(date='2026-11-17',generator='tools/build_greene_projectile_loops.py',
    method='Existing project sources, circular two-window overlap-add, integer cycle resampling, mean removal, .5 authored peak, 5 ms cosine join bridge and mono Vorbis encoding. Original durations inform loop lengths only.',
    attribution='Biomass derivatives retain Philippe Groarke / Socapex CC BY-SA 3.0; see art/weapon-audio/README.md and Socapex-LICENSES.txt. Fire derives exclusively from project-authored helicopter_jet synthesis.',
    validation='Static PCM/encoded metadata and boundary samples only. No playback, subjective listening, tests, Java, game or native engine execution.',loops=rows)
for repo in (ROOT,FORGE):(repo/'art/encounters/greene-projectile-loop-foley.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
print(json.dumps(rows))
