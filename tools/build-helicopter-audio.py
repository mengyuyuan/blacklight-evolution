"""Deterministic original rotor, turbine, door and warning cues; explicit local FFmpeg."""
from pathlib import Path
import json, hashlib, subprocess
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
FFMPEG=Path('D:/AI/VideoTools/node_modules/ffmpeg-static/ffmpeg.exe')
RATE=44100
rng=np.random.default_rng(91726)
report=[]
for name,seconds in [('rotor',4),('turbine',4),('door',1.2),('alarm',2)]:
    t=np.arange(round(RATE*seconds))/RATE
    if name=='rotor':
        pulse=np.maximum(0,np.sin(2*np.pi*18*t))**4
        wave=.45*np.sin(2*np.pi*36*t)+.18*np.sin(2*np.pi*72*t)+pulse*(.25*np.sin(2*np.pi*180*t)+.08*rng.normal(size=len(t)))
    elif name=='turbine':
        wave=.2*np.sin(2*np.pi*420*t)+.15*np.sin(2*np.pi*840*t)+.12*np.sin(2*np.pi*1260*t)+.05*rng.normal(size=len(t))
    elif name=='door':
        noise=rng.normal(size=len(t));wave=.16*noise*np.exp(-t*3)+.3*np.sin(2*np.pi*100*t)*np.exp(-t*7)+.22*noise*np.exp(-np.abs(t-.95)*45)
    else:wave=(.4*np.sin(2*np.pi*660*t)+.2*np.sin(2*np.pi*880*t))*(np.sin(2*np.pi*2*t)>0)
    if name in ('rotor','turbine'):
        # Blend the noisy endpoints around the already periodic harmonic layers.
        count=round(RATE*.02);ramp=np.linspace(0,1,count);blend=wave[-count:]*(1-ramp)+wave[:count]*ramp;wave[:count]=blend;wave[-count:]=blend
    else:wave*=np.minimum(1,t/.015)*np.minimum(1,(seconds-t)/.03)
    wave-=wave.mean();wave=wave/max(abs(wave))*.65
    path=ROOT/f'src/main/resources/assets/blacklight/sounds/vehicles/helicopter_{name}.ogg'
    subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-y','-f','f32le','-ar',str(RATE),'-ac','1','-i','pipe:0','-c:a','libvorbis','-q:a','5',str(path)],input=wave.astype('<f4').tobytes(),check=True)
    decoded=subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-i',str(path),'-f','f32le','-ac','1','-ar',str(RATE),'pipe:1'],capture_output=True,check=True).stdout
    samples=np.frombuffer(decoded,dtype='<f4');assert np.max(abs(samples))<1
    report.append({'cue':name,'seconds':seconds,'mono':True,'decodedPeak':float(np.max(abs(samples))),'rms':float(np.sqrt(np.mean(samples*samples))),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    dest=Path('D:/blacklight-evolution-forge-1.20.1')/path.relative_to(ROOT);dest.write_bytes(path.read_bytes())
for root in (ROOT,Path('D:/blacklight-evolution-forge-1.20.1')):
    path=root/'src/main/resources/assets/blacklight/sounds.json';data=json.loads(path.read_text('utf8'))
    for entry in report:
        name=entry['cue']
        # The sound manifest owns the playback mix. Rebuilding a sample keeps its gain and range.
        sample=data.setdefault('vehicle.helicopter_'+name,{'sounds':[{}]})['sounds'][0]
        sample.update(name='blacklight:vehicles/helicopter_'+name,stream=False)
        sample.setdefault('attenuation_distance',96 if name in ('rotor','turbine') else 32)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf8')
    out=root/'art/military-vehicles/helicopter-audio.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps({'origin':'Project-authored procedural synthesis; no commercial audio','cues':report},indent=2)+'\n','utf8')
print(json.dumps(report))
