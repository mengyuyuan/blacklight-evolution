"""Author licensed Foley for source-defined Hydra death roles. No playback.
Original game audio is read only as previously extracted duration/patch metadata;
no original waveform enters the runtime resources.
"""
from pathlib import Path
import hashlib,json,subprocess,zipfile,wave

ROOT=Path(__file__).resolve().parents[1]
WORK=Path('D:/CodexCache/work/authored-hydra-death-audio');WORK.mkdir(exist_ok=True)
FFMPEG=Path('D:/AI/VideoTools/node_modules/ffmpeg-static/ffmpeg.exe')
ASSETS=ROOT/'src/main/resources/assets/blacklight'
DEST=ASSETS/'sounds/hydra';DEST.mkdir(exist_ok=True)
archive=ROOT/'art/weapon-audio/combat-0.zip'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
sources={}
with zipfile.ZipFile(archive) as zipped:
    for stem in ['Monster_Hurt','slow squish']:
        name=next(n for n in zipped.namelist() if '__MACOSX' not in n and n.endswith('/Socapex - '+stem+'.wav'))
        path=WORK/(stem.replace(' ','_')+'.wav');path.write_bytes(zipped.read(name))
        with wave.open(str(path)) as w:duration=w.getnframes()/w.getframerate()
        sources[stem]={'path':path,'archiveEntry':name,'seconds':duration,'sha256':sha(path)}
def tempo(value):
    parts=[]
    while value<.5:parts.append('atempo=0.5');value*=2
    while value>2:parts.append('atempo=2');value/=2
    return ','.join(parts+[f'atempo={value:.12f}'])
assets=[]
for index,(rate,duration) in enumerate(zip([.48,.51,.46],[2.798,3.275333333333333,2.8726666666666665]),1):
    path=DEST/f'death_roar_{index}.ogg'
    graph=f'[0:a]aformat=channel_layouts=mono,asetrate={round(44100*rate)},aresample=44100,{tempo(sources["Monster_Hurt"]["seconds"]/rate/duration)},apad,volume=0.68[v];'
    graph+='[1:a]aformat=channel_layouts=mono,aresample=44100,volume=0.19[w];'
    graph+=f'[v][w]amix=inputs=2:normalize=0,atrim=duration={duration},highpass=f=50,lowpass=f=6500,afade=t=in:d=0.015,afade=t=out:st={duration-.17}:d=0.17,alimiter=limit=0.8:level=false[out]'
    subprocess.run([str(FFMPEG),'-v','error','-nostdin','-y','-i',str(sources['Monster_Hurt']['path']),'-stream_loop','-1','-i',str(sources['slow squish']['path']),'-filter_complex',graph,'-map','[out]','-t',str(duration),'-ac','1','-ar','44100','-c:a','libvorbis','-q:a','5',str(path)],check=True)
    assets.append({'file':path.relative_to(ASSETS).as_posix(),'seconds':duration,'voiceRate':rate,'sha256':sha(path),'bytes':path.stat().st_size})
duration=1.944671201814059;path=DEST/'death_debris_loop.ogg';ground=ASSETS/'sounds/infection/ground_1.ogg'
subprocess.run([str(FFMPEG),'-v','error','-nostdin','-y','-stream_loop','-1','-i',str(ground),'-stream_loop','-1','-i',str(sources['slow squish']['path']),'-filter_complex',f'[0:a]aformat=channel_layouts=mono,volume=0.65[a];[1:a]aformat=channel_layouts=mono,volume=0.25[b];[a][b]amix=inputs=2:normalize=0,atrim=duration={duration},highpass=f=70,lowpass=f=8500,afade=t=in:d=0.02,afade=t=out:st={duration-.02}:d=0.02,alimiter=limit=0.8:level=false[out]','-map','[out]','-t',str(duration),'-ac','1','-ar','44100','-c:a','libvorbis','-q:a','5',str(path)],check=True)
assets.append({'file':path.relative_to(ASSETS).as_posix(),'seconds':duration,'groundSource':ground.relative_to(ASSETS).as_posix(),'groundSha256':sha(ground),'sha256':sha(path),'bytes':path.stat().st_size})
events={
    'hydra.death_roar':{'subtitle':'subtitle.blacklight.hydra.death_roar','sounds':[f'blacklight:hydra/death_roar_{i}' for i in range(1,4)]},
    'hydra.death_whoosh':{'subtitle':'subtitle.blacklight.hydra.death_whoosh','sounds':[f'blacklight:infection/swing_{i}' for i in range(1,4)]},
    'hydra.death_debris':{'subtitle':'subtitle.blacklight.hydra.death_debris','sounds':['blacklight:hydra/death_debris_loop']},
}
p=ASSETS/'sounds.json';document=json.loads(p.read_text(encoding='utf8'));document.update(events);p.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
for language,values in [('zh_cn',['九头蛇：死亡嘶吼','九头蛇：身体倒落','九头蛇：碎石下落']),('en_us',['Hydra death roar','Hydra body falls','Hydra debris falls'])]:
    p=ASSETS/'lang'/f'{language}.json';document=json.loads(p.read_text(encoding='utf8'))
    for event,text in zip(events,values):document[events[event]['subtitle']]=text
    p.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
record={'schema':1,'date':'2026-11-17','scope':'Authored Foley, not original Prototype waveforms; duration metadata only is referenced.',
    'license':'CC BY-SA 3.0, existing Socapex attribution in art/weapon-audio/README.md and Socapex-LICENSES.txt applies to these derived audio files.',
    'sourceArchiveSha256':sha(archive),'sources':{name:{k:str(v) if isinstance(v,Path) else v for k,v in row.items() if k!='path'} for name,row in sources.items()},
    'assets':assets,'events':events,'changes':'Recorded voice resampling/time stretching; recorded wet/ground Foley layers; mono44100Hz, filters, bounded fades, limiting and Vorbis. No playback, no timbre-match claim.',
    'pending':'Listening, real client SoundManager lifecycle and player loudness/identity acceptance.'}
(ROOT/'art/creature-audio/hydra-death-authored-20261117.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print('Authored four Hydra death audio files without playback.')
