"""Author finite mono fallback voices from the project's credited Socapex recordings.

P1 FIG supplies event identity/timing, not these waveforms. No original game audio
is read, embedded or played by this tool. It only converts existing source assets.
"""
from pathlib import Path
import argparse,hashlib,json,subprocess,zipfile,wave

p=argparse.ArgumentParser()
p.add_argument('--ffmpeg',required=True)
p.add_argument('--work',type=Path,required=True)
args=p.parse_args()
repo=Path(__file__).resolve().parents[1]
archive=repo/'art/weapon-audio/combat-0.zip'
assets=repo/'src/main/resources/assets/blacklight'
folder=assets/'sounds/infection';folder.mkdir(parents=True,exist_ok=True)
args.work.mkdir(parents=True,exist_ok=True)
sources={}
with zipfile.ZipFile(archive) as zipped:
    for stem in ['hurt','Monster_Hurt','blub_hurt2','slow squish']:
        matches=[n for n in zipped.namelist() if '__MACOSX' not in n and n.endswith('/Socapex - '+stem+'.wav')]
        if len(matches)!=1:raise ValueError('Missing unique credited source '+stem)
        data=zipped.read(matches[0]);path=args.work/(stem.replace(' ','_')+'.wav');path.write_bytes(data)
        with wave.open(str(path)) as wav:duration=wav.getnframes()/wav.getframerate()
        sources[stem]={'archive_entry':matches[0],'sha256':hashlib.sha256(data).hexdigest(),'seconds':duration,'path':path}

families={
    'common_attack':('hurt',[.96,1,.91],.6,None),
    'common_death':('hurt',[.8,.84,.77],1.15,'blub_hurt2'),
    'hunter_grunt':('Monster_Hurt',[.85,.9,.8],.68,None),
    'hunter_roar':('Monster_Hurt',[.58,.62,.66],1.2,None),
    'hunter_death':('Monster_Hurt',[.45,.48,.5],1.5,'slow squish'),
}
report=[]
for family,(stem,rates,seconds,secondary) in families.items():
    for index,rate in enumerate(rates,1):
        dest=folder/(family+'_'+str(index)+'.ogg')
        duration=min(seconds,max(sources[stem]['seconds']/rate,(sources[secondary]['seconds']+.16) if secondary else 0))
        command=[args.ffmpeg,'-hide_banner','-loglevel','error','-y','-i',str(sources[stem]['path'])]
        primary=f'[0:a]aformat=channel_layouts=mono,asetrate={round(44100*rate)},aresample=44100,volume=0.7[p]'
        if secondary:
            command+=['-i',str(sources[secondary]['path'])]
            graph=primary+';[1:a]aformat=channel_layouts=mono,aresample=44100,volume=0.28,adelay=160[s];[p][s]amix=inputs=2:normalize=0[m];'
            source='[m]'
        else:
            graph=primary+';';source='[p]'
        graph+=source+f'atrim=duration={duration},highpass=f=60,lowpass=f=10500,afade=t=in:d=0.006,afade=t=out:st={duration-.14}:d=0.14,alimiter=limit=0.8:level=false[out]'
        command+=['-filter_complex',graph,'-map','[out]','-ac','1','-ar','44100','-c:a','libvorbis','-q:a','4',str(dest)]
        subprocess.run(command,check=True)
        report.append({'file':dest.relative_to(assets).as_posix(),'source':stem,'secondary':secondary,
                       'source_rate_factor':rate,'maximum_seconds':seconds,'authored_seconds':duration,'channels':1,'sample_rate':44100,
                       'sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'bytes':dest.stat().st_size})

events={
    'common_attack':('common_attack',.9), 'common_death':('common_death',.7),
    'hunter_grunt':('hunter_grunt',.9), 'hunter_roar':('hunter_roar',.85),
    'hunter_hurt':('hunter_grunt',.75), 'hunter_death':('hunter_death',.85),
    'hunter_whoosh':('swing',1), 'common_land':('ground',.65),
}
path=assets/'sounds.json';document=json.loads(path.read_text(encoding='utf-8'))
for event,(family,volume) in events.items():
    document['infection.'+event]={'subtitle':'subtitle.blacklight.infection.'+event,
        'sounds':[{'name':f'blacklight:infection/{family}_{v}','volume':volume,'attenuation_distance':24} for v in range(1,4)]}
path.write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
record={'schema':1,'date':'2026-11-17','kind':'authored-fallback-not-P1-waveforms',
        'source_archive':str(archive.relative_to(repo)),'source_archive_sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),
        'credits':'Socapex - Evol Online SFX; existing project CC BY-SA 3.0 attribution and upstream contributors retained in art/weapon-audio/README.md and Socapex-LICENSES.txt.',
        'sources':{stem:{k:v for k,v in value.items() if k!='path'} for stem,value in sources.items()},
        'method':'Recorded voices only; mono downmix, bounded resampling, optional existing wet Foley layer, filters, short fade, limiter. No synthetic oscillator/noise and no original-game waveform.',
        'assets':report,'events':events,
        'acceptance':'Resource production only; not auditioned, no game/client/unit/GameTest execution. Subjective mix and species identity remain pending.'}
target=repo/'art/creature-audio';target.mkdir(exist_ok=True)
(target/'authored-voice-fallbacks-20261117.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'produced':len(report),'bytes':sum(r['bytes'] for r in report),'events':len(events),'played':False}))
