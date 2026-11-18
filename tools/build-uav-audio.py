"""Authored UAV sound design using measured P1 cue roles, duration and broad frequency bands."""
from pathlib import Path
import hashlib,json,subprocess
import numpy as np
ROOT=Path(__file__).resolve().parents[1];FORGE=Path('D:/blacklight-evolution-forge-1.20.1');FFMPEG=Path('D:/AI/VideoTools/node_modules/ffmpeg-static/ffmpeg.exe');RATE=48000
roles={'rotor':('UAV_idle','uav_idle_01',1.5473333333333332,True),'seeing':('UAV_seeing','uav_seeing_01',1.0246666666666666,True),'scan':('UAV_servotalk','uav_start_scan',.53,False),'alert':('UAV_alert','uav_alerted',2.466,False)}
report=[]
for cue,(patch,source,seconds,loop) in roles.items():
 rng=np.random.default_rng(int.from_bytes(hashlib.sha256(cue.encode()).digest()[:4],'little'));t=np.arange(round(RATE*seconds))/RATE
 if cue=='rotor':
  signal=sum(g*np.sin(2*np.pi*round(f*seconds)/seconds*t) for f,g in [(93.4,.32),(67.9,.14),(186.8,.10),(280.2,.05)])
  noise=rng.normal(size=len(t));freq=np.fft.rfftfreq(len(t),1/RATE);noise=np.fft.irfft(np.fft.rfft(noise)*np.exp(-(freq/1500)**2),n=len(t));signal=(signal+.08*noise)*(.88+.12*np.sin(2*np.pi*round(16.8*seconds)/seconds*t))
 elif cue=='seeing':
  rate=5/seconds;phase=2*np.pi*(1684*t+.9*np.sin(2*np.pi*rate*t));envelope=.2+.8*(.5+.5*np.cos(2*np.pi*rate*t))**.7
  signal=(.62*np.sin(phase)+.11*np.sin(phase*2)+.05*np.sin(2*np.pi*93*t))*envelope
 elif cue=='scan':
  phase=2*np.pi*(1164*t+42*t*t+.55*np.sin(2*np.pi*26*t));signal=.42*np.sin(phase)+.17*np.sin(2*np.pi*4547*t)*np.sin(2*np.pi*43*t)+.06*np.sin(2*np.pi*10477*t)+.06*rng.normal(size=len(t))
 else:
  phase=2*np.pi*(1678.6*t+1.5*np.sin(2*np.pi*(3/seconds)*t));envelope=.76+.24*np.cos(2*np.pi*(3/seconds)*t)
  signal=(.5*np.sin(phase)+.22*np.sin(2*np.pi*568*t)+.14*np.sin(2*np.pi*2815*t))*envelope
 # Short smooth edge taper prevents Vorbis restart clicks; not source waveform copying.
 edge=.003 if loop else .012;signal*=np.sin(np.minimum(1,t/edge)*np.pi/2)**2*np.sin(np.minimum(1,(seconds-t)/edge)*np.pi/2)**2
 signal-=signal.mean();signal=signal/np.max(abs(signal))*.68
 path=ROOT/f'src/main/resources/assets/blacklight/sounds/vehicles/uav_{cue}.ogg'
 subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-y','-f','f32le','-ar',str(RATE),'-ac','1','-i','pipe:0','-c:a','libvorbis','-q:a','6',str(path)],input=signal.astype('<f4').tobytes(),check=True)
 raw=subprocess.run([str(FFMPEG),'-hide_banner','-loglevel','error','-i',str(path),'-f','f32le','-ac','1','-ar',str(RATE),'pipe:1'],capture_output=True,check=True).stdout
 samples=np.frombuffer(raw,dtype='<f4');assert abs(len(samples)/RATE-seconds)<.003 and np.max(abs(samples))<.9
 report.append({'cue':cue,'sourcePatch':patch,'sourceSample':source,'loop':loop,'seconds':seconds,'decodedSamples':len(samples),'decodedPeak':float(np.max(abs(samples))),'rms':float(np.sqrt(np.mean(samples*samples))),'loopBoundaryJump':float(abs(samples[-1]-samples[0])) if loop else None,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
 (FORGE/path.relative_to(ROOT)).write_bytes(path.read_bytes())
labels={'rotor':('UAV idles','无人机待机嗡鸣'),'seeing':('UAV scans a target','无人机正在扫描目标'),'scan':('UAV starts scanning','无人机启动扫描'),'alert':('UAV raises an alert','无人机发出警报')}
for root in [ROOT,FORGE]:
 path=root/'src/main/resources/assets/blacklight/sounds.json';data=json.loads(path.read_text(encoding='utf8'))
 for cue in labels:data['vehicle.uav_'+cue]={'subtitle':'subtitles.blacklight.uav_'+cue,'sounds':[{'name':'blacklight:vehicles/uav_'+cue,'stream':False,'attenuation_distance':40}]}
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 for lang,index in [('en_us',0),('zh_cn',1)]:
  path=root/f'src/main/resources/assets/blacklight/lang/{lang}.json';data=json.loads(path.read_text(encoding='utf8'))
  for cue,texts in labels.items():data['subtitles.blacklight.uav_'+cue]=texts[index]
  path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
 path=root/'art/military2/uav-audio.json';path.write_text(json.dumps({'origin':'Project-authored synthesis referenced to source roles, measured durations and broad dominant frequency bands; not original recordings or a waveform reconstruction','sourceAudioPackageSha256':'6c1a48ddb61fe61242ee9c5f308f6cc870e0a85a5e46da7e700a78b90a062493','sampleRate':RATE,'cues':report,'additional':'Existing uav_break retained for accepted destruction. Legacy transmit/confirm event IDs and files retained but no longer triggered by UAV; original state cues replace the invented recurring transmission/acknowledgement beeps.','limits':['Original recording timbre is not claimed identical','Minecraft range, volume, maximum simultaneous voices and recognition clock are adaptations']},ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(report))
