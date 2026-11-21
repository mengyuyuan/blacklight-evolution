"""Build Minecraft's consume score from credited project Foley and deterministic synthesis.

Requires Python 3, NumPy, SciPy and FFmpeg with libvorbis.
Run from a checkout: python tools/build_consume_branch_audio.py --project . --evidence build/consume-audio
Optional --mirror writes the same OGG bytes and merges the same descriptors into a second checkout.
"""
from argparse import ArgumentParser
from concurrent.futures import ThreadPoolExecutor
from functools import lru_cache
from hashlib import sha256
from pathlib import Path
import json
import subprocess
import wave
import numpy as np
from scipy.signal import butter, sosfilt

SR = 44100

# Length, dominant frequencies, and decay of individual physical elements.
# Pitched mass is mixed with band-limited noise. Fibres and breath are primarily noise.
# type: duration, noise low/high, resonance, drop, pulse density, resonant energy.
MATERIAL = {
    'mass':   (.22,    65,  980, 128, .59,   0, .68),
    'chest':  (.13,    95, 1900, 202, .63,   0, .53),
    'skin':   (.070,  360, 4100, 260, .62,   0, .16),
    'knock':  (.059,  270, 2800, 520, .91,   0, .53),
    'snap':   (.041,  920, 5400, 870, .54,   0, .25),
    'grain':  (.135,  500, 3700, 590, .51,  63, .07),
    'cloth':  (.185,  680, 3600, 480, .86,  28, .04),
    'air':    (.215,  340, 4800, 360, .50,   0, .00),
    'steel':  (.138, 1200, 6800,1880, .94,   0, .39),
    'cord':   (.175,  380, 2400, 330, .40,  19, .35),
    'strain': (.245,  170, 1850, 235, .74,  33, .30),
    'wet':    (.107,  255, 2500, 432, .33,  26, .30),
    'reed':   (.192,  280, 1900, 420, .41,  49, .30),
    'break':  (.170,  370, 5000, 560, .42,  71, .17),
}

# Each hit has a different stroke or sequence of physical contacts. Entries are
# (time in seconds, material, level). Adjacent beats in a combo have their own recording.
def marks(*entries):
    return entries

HITS = {
    'chest_drive':('穿胸刺入','Chest strike penetrates','claws_hit',1.17,marks((0,'chest',1.0),(.023,'wet',.90),(.088,'cord',.42))),
    'heel_stamp':('脚跟砸下','Heel stamps down','hammerfists_ground',1.05,marks((0,'mass',1),(.012,'break',.77),(.095,'knock',.41))),
    'knee_bump':('膝撞抬击','Knee strikes upward','none_heavy',1.30,marks((0,'skin',.87),(.009,'chest',.98),(.058,'air',.51))),
    'beat_jab':('短拳击中','Quick jab lands','none_hit',1.40,marks((0,'skin',1),(.012,'knock',.74))),
    'beat_hook':('勾拳击中','Hook lands','none_heavy',1.27,marks((0,'air',.64),(.014,'chest',.93),(.039,'skin',.55))),
    'beat_cross':('直拳击中','Cross lands','muscle_mass_hit',1.38,marks((0,'knock',1),(.009,'skin',.67),(.035,'grain',.45))),
    'beat_drive':('重拳压入','Driving punch lands','none_ground',1.21,marks((0,'chest',.95),(.017,'wet',.8),(.038,'mass',.52))),
    'beat_crush':('末拳击碎','Finishing punch cracks','hammerfists_hit',.96,marks((0,'mass',1),(.014,'snap',.96),(.049,'break',.93),(.11,'knock',.47))),
    'neck_snap':('颈部短促折响','Neck snaps','none_morph',1.62,marks((0,'cloth',.43),(.016,'snap',1),(.031,'knock',.76))),
    'rib_squeeze':('抱紧时胸背挤压','Bear hug compresses','muscle_mass_morph',.86,marks((0,'strain',.9),(.041,'chest',.62),(.12,'reed',1),(.205,'air',.68))),
    'joint_lock':('双臂绞锁','Arms lock into a hold','none_hit',1.60,marks((0,'knock',.89),(.043,'strain',.67),(.093,'snap',1),(.14,'knock',.67))),
    'choke_press':('扼喉收紧','Choke hold tightens','whipfist_morph',.83,marks((0,'skin',.42),(.018,'air',.97),(.082,'reed',1),(.20,'cloth',.54))),
    'throat_rip':('咽喉撕开','Throat tears','claws_morph',1.07,marks((0,'snap',.73),(.019,'wet',.83),(.055,'break',1),(.14,'wet',.73))),
    'claw_shear':('利爪交错剪切','Claws shear across','claws_hit',1.07,marks((0,'steel',.88),(.027,'snap',.87),(.074,'grain',.92))),
    'skull_click':('爪尖合拢压碎','Claws close and crack','claws_heavy',1.18,marks((0,'knock',.82),(.038,'snap',1),(.077,'snap',.70),(.10,'break',.71))),
    'blade_split':('刀锋斩开','Blade splits through','blade_hit',.89,marks((0,'steel',1),(.018,'wet',.75),(.086,'air',.69))),
    'head_drop':('锤拳向下砸击','Hammer drops','hammerfists_heavy',.84,marks((0,'mass',1),(.026,'knock',.85),(.098,'grain',.6))),
    'hammer_press':('锤拳碾压','Hammer presses down','hammerfists_morph',.88,marks((0,'chest',.88),(.048,'strain',.85),(.131,'break',1),(.22,'mass',.64))),
    'hook_rend':('鞭拳钩住扯开','Whip hook wrenches','whipfist_heavy',1.03,marks((0,'cord',1),(.031,'knock',.85),(.075,'break',.86))),
    'lash_bite':('鞭梢抽中','Whip lash strikes','whipfist_swing',1.28,marks((0,'air',.72),(.016,'snap',1),(.030,'skin',.68))),
    'lash_recoil':('鞭梢回抽','Whip snaps back','whipfist_hit',1.16,marks((0,'skin',.74),(.026,'cord',1),(.073,'wet',.69))),
    'arm_wrench':('肌肉拧扯手臂','Arm twists and pulls free','muscle_mass_heavy',.88,marks((0,'strain',.87),(.039,'knock',.63),(.105,'snap',.88),(.134,'wet',.99))),
    'heart_grab':('抓取组织','Hand grips tissue','muscle_mass_morph',1.31,marks((0,'wet',1),(.022,'skin',.73),(.080,'reed',.55))),
    'heart_slap':('组织拍击','Tissue slaps on impact','muscle_mass_hit',1.04,marks((0,'skin',1),(.012,'chest',.85),(.044,'wet',.60))),
    'mount_fist':('骑乘重拳','Mounted fist strikes','none_ground',.82,marks((0,'mass',1),(.019,'chest',.92),(.092,'knock',.48))),
    'mount_claw':('骑乘爪击','Mounted claws rake','claws_ground',.89,marks((0,'mass',.72),(.014,'steel',.8),(.05,'grain',.87),(.113,'break',.68))),
    'mount_blade':('骑乘重斩','Mounted blade cleaves','blade_heavy',.80,marks((0,'steel',1),(.026,'mass',.91),(.092,'wet',.72))),
    'mount_maul':('骑乘锤拳撞击','Mounted hammer impacts','hammerfists_ground',.73,marks((0,'mass',1),(.021,'break',.90),(.1,'mass',.58),(.14,'grain',.49))),
    'mount_cable':('重型鞭索绞拉','Heavy whip pulls taut','whipfist_morph',.75,marks((0,'cord',1),(.048,'strain',.97),(.137,'snap',.7),(.176,'mass',.62))),
    'mount_heave':('重型肌束拖拽','Heavy muscle bundle tears','muscle_mass_ground',.79,marks((0,'chest',.89),(.036,'strain',1),(.13,'wet',.86),(.20,'break',.63))),
    'hunter_jaw':('猎手下颚被撑开','Hunter jaw opens','hammerfists_morph',.83,marks((0,'knock',.88),(.024,'strain',.96),(.088,'snap',1),(.15,'reed',.62))),
    'hunter_rend':('猎手组织撕离','Hunter tissue tears free','whipfist_ground',.82,marks((0,'mass',.78),(.022,'break',1),(.096,'wet',.91),(.187,'air',.56))),
    'quill_extract':('肩刺汲取组织','Quill extracts tissue','claws_morph',1.26,marks((0,'snap',.81),(.021,'reed',.89),(.096,'wet',1))),
    'quill_return':('肩刺携组织飞回','Loaded quill returns','whipfist_swing',1.15,marks((0,'steel',.43),(.023,'air',.93),(.075,'cord',.81))),
    'quill_merge':('肩刺组织并入身躯','Quill tissue merges','muscle_mass_morph',1.19,marks((0,'wet',.90),(.065,'chest',.91),(.126,'knock',.54))),
    'weave':('触手贴身交织','Tendrils weave over body','claws_morph',1.55,marks((0,'cloth',.69),(.027,'cord',.91),(.105,'grain',.74),(.177,'cloth',.54))),
}
# Families colour the acquisition, sustained inward pull and short closing tail.
# Contact timbres above remain specific to the actual move within each family.
FAMILIES = {
    'bare':('徒手','Unarmed','none_morph',1.12,'skin','wet'),
    'quiet':('潜行','Stealth','none_morph',1.43,'cloth','air'),
    'claw':('利爪','Claw','claws_morph',1.23,'snap','grain'),
    'blade':('刀锋','Blade','blade_morph',1.02,'steel','wet'),
    'hammer':('锤拳','Hammer','hammerfists_morph',.88,'mass','break'),
    'lash':('鞭拳','Whip','whipfist_morph',1.12,'cord','reed'),
    'muscle':('肌肉','Muscle','muscle_mass_morph',.95,'strain','skin'),
    'mount':('重型','Heavy','hammerfists_heavy',.77,'chest','strain'),
    'hunter':('猎手','Hunter','muscle_mass_heavy',.84,'knock','break'),
}

# Each palette uses its own impulse or friction model. Attack and release times are in seconds.
# A punch excites a damped body; a blade sweeps a short edge; a tear breaks fibres in clusters.
def material_voice(kind,rng,pitch):
    if kind in ('palm','rib','body','drive','fist_end'):
        dur,hz,fall,decay,smack={
            'palm':(.104,242,108,.022,.94),'rib':(.205,174,66,.053,.40),
            'body':(.235,122,47,.068,.24),'drive':(.237,158,48,.061,.52),
            'fist_end':(.264,109,39,.077,.52)}[kind]
        t=np.arange(round(SR*dur))/SR
        phase=2*np.pi*np.cumsum((fall+(hz-fall)*np.exp(-t/.016))*pitch)/SR
        pressure=(np.sin(phase)+.20*np.sin(phase*2.08+.32))*np.exp(-t/decay)
        slap=band(rng.normal(size=len(t)),420,4400)*np.exp(-t/.012)
        low=band(rng.normal(size=len(t)),95,640)*np.exp(-t/(decay*.77))
        return (.86*pressure+smack*slap+.47*low)*np.minimum(1,t/.0013)
    if kind in ('slice','rake','piercing'):
        dur,hi,lo={'slice':(.174,7200,1900),'rake':(.123,5400,2300),'piercing':(.086,5300,900)}[kind]
        t=np.arange(round(SR*dur))/SR
        phase=2*np.pi*np.cumsum((lo+(hi-lo)*(1-t/dur)**2)*pitch)/SR
        shear=band(rng.normal(size=len(t)),1600,8500)
        edge=.34*np.sin(phase)+.19*np.sin(phase*1.43+.5)
        envelope=np.minimum(1,t/.004)*(1-t/dur)**2
        if kind=='rake':envelope*=.32+.68*np.abs(np.cos(2*np.pi*t*9))
        return (edge+.78*shear)*envelope
    if kind in ('tug','rip','fibre','grit'):
        dur,gap,low,high={'tug':(.176,.031,180,1250),'rip':(.290,.017,350,3500),
            'fibre':(.196,.012,650,5400),'grit':(.153,.014,950,6700)}[kind]
        t=np.arange(round(SR*dur))/SR
        noise=band(rng.normal(size=len(t)),low*pitch,high*pitch)
        env=np.zeros(len(t));at=rng.uniform(.001,.008)
        while at<dur-.01:
            span=rng.uniform(.004,.018) if kind!='tug' else rng.uniform(.018,.036)
            u=np.maximum(0,t-at)
            env+=rng.uniform(.40,1.2)*np.minimum(1,u/.0017)*np.exp(-u/span)
            at+=gap*rng.uniform(.47,1.6)
        if kind=='tug':
            carrier=np.sin(2*np.pi*(92*t+760*t*t)*pitch)
            return (.78*carrier+.33*noise)*env*np.sin(np.pi*t/dur)**.7
        return noise*env*np.maximum(0,np.sin(np.pi*t/dur))**.5
    if kind in ('stone_floor','wood_floor','soil_floor','metal_floor'):
        dur=.58 if kind=='metal_floor' else .43
        t=np.arange(round(SR*dur))/SR
        phase=2*np.pi*np.cumsum((49+47*np.exp(-t/.02))*pitch)/SR
        low=.82*np.sin(phase)*np.exp(-t/.068)
        noise=rng.normal(size=len(t))
        if kind=='stone_floor':
            surface=.65*band(noise,220,3800)*np.exp(-t/.025)+.42*np.sin(2*np.pi*291*pitch*t)*np.exp(-t/.027)
        elif kind=='wood_floor':
            surface=sum(a*np.sin(2*np.pi*f*pitch*t)*np.exp(-t/d) for a,f,d in ((1,177,.085),(.63,359,.062),(.45,644,.050)))
            surface+=.32*band(noise,530,2400)*np.exp(-t/.019)
        elif kind=='metal_floor':
            surface=sum(a*np.sin(2*np.pi*f*pitch*t)*np.exp(-t/d) for a,f,d in ((.65,377,.11),(1,893,.13),(.53,1447,.18),(.29,2189,.16)))
            surface+=.34*band(noise,960,6500)*np.exp(-t/.011)
        else:
            surface=band(noise,110,1040)*np.exp(-t/.095)+.35*band(noise,690,2800)*np.exp(-((t-.075)/.055)**2)
        return (low+surface)*np.minimum(1,t/.0028)*np.minimum(1,(dur-t)/.025)
    return None

# Retain the event-to-animation contract while making its acoustic contact explicit.
def strokes(cue,original):
    flesh={
        'chest_drive':((0,'palm',.85),(.011,'piercing',.57),(.035,'body',1),(.085,'wet',.24)),
        'heel_stamp':((0,'body',1),(.013,'skin',.56),(.047,'knock',.34)),
        'knee_bump':((0,'rib',1),(.009,'palm',.46),(.065,'air',.45)),
        'beat_jab':((0,'palm',1),(.016,'cloth',.10)),
        'beat_hook':((0,'rib',1),(.026,'skin',.61),(.058,'air',.26)),
        'beat_cross':((0,'skin',.94),(.008,'knock',.80),(.026,'rib',.58)),
        'beat_drive':((0,'drive',1),(.037,'palm',.45),(.071,'cloth',.30)),
        'beat_crush':((0,'fist_end',1),(.014,'snap',.78),(.063,'rib',.70),(.103,'break',.48)),
        'head_drop':((0,'body',1),(.015,'palm',.58),(.069,'knock',.48)),
        'mount_fist':((0,'drive',1),(.028,'palm',.65)), 'mount_maul':((0,'body',1),(.020,'rib',.65),(.081,'break',.41)),
        'blade_split':((0,'slice',1),(.034,'skin',.38),(.072,'fibre',.38)),
        'claw_shear':((0,'rake',.92),(.026,'rake',.68),(.057,'rake',.48),(.10,'fibre',.48)),
        'mount_blade':((0,'slice',1),(.026,'rib',.57),(.085,'fibre',.63)),
        'mount_claw':((0,'rake',1),(.024,'rake',.61),(.062,'fibre',.87)),
        'hook_rend':((0,'cord',.70),(.039,'tug',.91),(.128,'rip',1)),
        'lash_bite':((0,'air',.35),(.012,'snap',1),(.025,'palm',.64)),
        'lash_recoil':((0,'tug',1),(.059,'fibre',.89),(.146,'skin',.31)),
        'arm_wrench':((0,'tug',1),(.106,'rip',.95),(.215,'snap',.61)),
        'hunter_rend':((0,'tug',.9),(.063,'rip',1),(.132,'fibre',.66),(.21,'wet',.36)),
        'throat_rip':((0,'skin',.47),(.028,'fibre',.83),(.080,'rip',1)),
        'mount_heave':((0,'body',.56),(.033,'tug',.88),(.142,'rip',1)),
        'mount_cable':((0,'cord',.78),(.055,'tug',1),(.150,'fibre',.80)),
    }
    return flesh.get(cue,original)

GROUND={'stone':('石面','stone'),'wood':('木板','timber'),'soil':('松软地面','earth'),'metal':('金属表面','metal')}
for surface,(zh,en) in GROUND.items():
    layers=[(0,surface+'_floor',1.)]
    if surface=='stone':layers.append((.076,'grit',.31))
    if surface=='soil':layers.append((.052,'cloth',.26))
    HITS['body_land_'+surface]=('身体撞上'+zh,'Body lands on '+en,'none_hit',1.,tuple(layers))

def scores():
    result=dict(HITS)
    for family,(zh,en,stem,rate,bite,body) in FAMILIES.items():
        result['grip_'+family]=(zh+'抓牢',en+' grip closes',stem,rate*1.37,
                marks((0,bite,1),(.032,'cloth',.67),(.064,'skin',.64)))
        result['absorb_'+family]=(zh+'组织回流',en+' tissue draws inward',stem,rate*.92,
                marks((0,'wet',.70),(.034,body,.65),(.093,'intake',1),(.25,'reed',.69)))
        result['finish_'+family]=(zh+'吞噬收口',en+' absorption seals',stem,rate*1.18,
                marks((0,body,.79),(.016,'seal',1),(.123,bite,.45)))
    return result

def ffmpeg(exe,options,data):
    result=subprocess.run([exe,'-v','error','-nostdin',*options],input=data,capture_output=True)
    if result.returncode:raise RuntimeError(result.stderr.decode(errors='replace'))
    return result.stdout

def decode(exe,ogg):
    return np.frombuffer(ffmpeg(exe,['-i','pipe:0','-ar',str(SR),'-ac','1','-f','f32le','pipe:1'],ogg),'<f4').copy()

def band(noise,lo,hi):
    return sosfilt(butter(2,[lo,hi],btype='bandpass',fs=SR,output='sos'),noise)

def texture(kind,rng,pitch):
    special=material_voice(kind,rng,pitch)
    if special is not None:return special
    if kind=='intake':
        t=np.arange(round(.34*SR))/SR
        noise=band(rng.normal(size=len(t)),350,3600)
        env=(.15+np.sin(np.pi*t/.39)**1.4)*np.minimum(1,t/.012)*np.clip((.34-t)/.09,0,1)
        suction=.66+.34*np.cos(2*np.pi*(10*t+20*t*t))
        return noise*env*suction
    if kind=='seal':
        t=np.arange(round(.16*SR))/SR
        phase=np.cumsum(122+470*np.exp(-t/.028))*2*np.pi/SR
        return (np.sin(phase)*.60+band(rng.normal(size=len(t)),190,1900)*.58)*np.minimum(1,t/.003)*np.exp(-t/.040)
    dur,lo,hi,freq,drop,density,tone=MATERIAL[kind]
    dur*=rng.uniform(.94,1.06)
    t=np.arange(round(dur*SR))/SR
    rand=rng.normal(size=len(t))
    n=band(rand,lo*pitch,min(10000,hi*pitch))
    n/=np.percentile(abs(n),98)+1e-8
    wave=2*np.pi*np.cumsum(freq*pitch*(drop+(1-drop)*np.exp(-t/(dur*.19))))/SR
    body=(np.sin(wave)+.31*np.sin(wave*1.72+.4)+.13*np.sin(wave*2.64+.9))*.78
    soft=kind in ('air','cloth','reed','strain','cord')
    rise=dur*(.21 if soft else .021)
    decay=dur*(.42 if soft else .24)
    env=np.minimum(1,t/rise)*np.exp(-np.maximum(0,t-rise)/decay)*np.minimum(1,(dur-t)/.013)
    if density:
        env*=.32+.68*(.5+.5*np.sin(2*np.pi*density*t+1.1*np.sin(t*43)))**2
    return (body*tone+n*(1-tone))*env

def active(x):
    block=SR//50
    rms=np.sqrt(np.mean(np.pad(x,(0,(-len(x))%block)).reshape(-1,block)**2,axis=1))
    good=rms[rms>max(.002,.12*rms.max())]
    return float(np.percentile(good,70))

def build(args):
    resource=Path('src/main/resources/assets/blacklight')
    src=args.project/resource
    destinations=[src]+([args.mirror/resource] if args.mirror else [])
    evidence=args.evidence;evidence.mkdir(parents=True,exist_ok=True)
    profiles=scores()
    @lru_cache(maxsize=None)
    def foley(stem,variation):
        x=decode(args.ffmpeg,(src/'sounds/weapons'/f'{stem}_{variation}.ogg').read_bytes())
        indices=np.flatnonzero(abs(x)>.012)
        return x[max(0,indices[0]-100):indices[-1]+100]
    def render(job):
        cue,v=job;zh,en,stem,rate,layers=profiles[cue]
        rng=np.random.default_rng(int.from_bytes(sha256(f'{cue}/{v}'.encode()).digest()[:8],'little'))
        contact=cue in HITS
        floor=cue.startswith('body_land_')
        layers=strokes(cue,layers)
        source_take=1+(v-1)%2
        x=foley(stem,source_take)
        phase=cue.split('_')[0]
        # The close and seal are short. The "intake" deliberately swells over time.
        cap=.32 if phase=='absorb' else .23 if phase=='grip' else .20 if phase=='finish' else .30
        rate*=rng.uniform(.96,1.04)
        source=np.interp(np.arange(0,len(x),rate),np.arange(len(x)),x)[:round(SR*cap)]
        if phase=='absorb':
            source=source[::-1].copy()
            source*=np.linspace(.30,1,len(source))
        source*=np.minimum(1,np.arange(len(source))[::-1]/(SR*.033))
        bed=0 if floor else .125 if contact else .53
        mix=np.zeros(SR);mix[:len(source)]=source*(bed/max(.03,float(np.percentile(abs(source),98))))
        end=len(source);pitch=rng.uniform(.94,1.06)
        if contact:pitch*=(1.,.85,1.17)[v-1]
        for layer,(delay,kind,gain) in enumerate(layers):
            y=texture(kind,rng,pitch)
            if contact:
                # An alternate performance both redistributes pressure and changes internal timing.
                time_scale=(1.,1.22,.84)[v-1]
                delay*=time_scale
                if layer>0:gain*=((1.,.68,1.22) if layer%2 else (1.,1.28,.80))[v-1]
                if v!=1:
                    y=np.interp(np.arange(0,len(y),1/time_scale),np.arange(len(y)),y)
            y*=gain*.84/max(.01,np.percentile(abs(y),97))
            start=round(delay*SR*rng.uniform(.96,1.04))
            mix[start:start+len(y)]+=y;end=max(end,start+len(y))
        x=mix[:end];x-=x.mean()
        target=.225 if phase in ('grip','finish') else .245 if phase=='absorb' else .255
        z=x*target/max(.001,active(x))
        mask=abs(z)>.64
        z[mask]=np.sign(z[mask])*(.64+.28*np.tanh((abs(z[mask])-.64)/.28))
        z[:30]*=np.linspace(0,1,30);z[-300:]*=np.linspace(1,0,300)
        for _ in range(6):
            data=ffmpeg(args.ffmpeg,['-f','f32le','-ar',str(SR),'-ac','1','-i','pipe:0','-map_metadata','-1',
                  '-fflags','+bitexact','-flags:a','+bitexact','-c:a','libvorbis','-q:a','6','-f','ogg','pipe:1'],z.astype('<f4').tobytes())
            out=decode(args.ffmpeg,data);peak=float(abs(out).max())
            if peak<=.956:break
            z*=.90/peak
        if peak>.96:raise ValueError((cue,peak))
        name=f'consume/score/{cue}_{v}'
        for folder in destinations:
            path=folder/'sounds'/(name+'.ogg');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        onset=np.flatnonzero(abs(out)>.04)[0]/SR
        return cue,v,out,dict(sha256=sha256(data).hexdigest(),duration=round(len(out)/SR,4),
                  onsetMs=round(1000*onset,2),activeDbfs=round(20*np.log10(active(out)),2),peak=round(peak,5),
                  source=f'weapons/{stem}_{source_take}' if bed else None,syntheticLayers=[m[1] for m in layers])
    with ThreadPoolExecutor(max_workers=6) as pool:
        rendered=list(pool.map(render,[(key,v) for key in profiles for v in ((1,2,3) if key in HITS else (1,2))]))
    for folder in destinations:
        path=folder/'sounds.json';events=json.loads(path.read_bytes())
        languages={lang:json.loads((folder/'lang'/f'{lang}.json').read_bytes()) for lang in ('en_us','zh_cn')}
        for cue,(zh,en,_,_,_) in profiles.items():
            key='consume.'+cue;subtitle='subtitle.blacklight.'+key
            events[key]={'subtitle':subtitle,'sounds':[{'name':f'blacklight:consume/score/{cue}_{v}',
                      'attenuation_distance':28} for v in ((1,2,3) if cue in HITS else (1,2))]}
            languages['en_us'][subtitle]=en;languages['zh_cn'][subtitle]=zh
        events['consume.body_land']={'subtitle':'subtitle.blacklight.consume.body_land',
                 'sounds':[{'name':'blacklight:consume.body_land_stone','type':'event'}]}
        languages['en_us']['subtitle.blacklight.consume.body_land']='Body hits the ground'
        languages['zh_cn']['subtitle.blacklight.consume.body_land']='身体撞地'
        path.write_text(json.dumps(events,indent=2)+'\n',encoding='utf8')
        for lang,labels in languages.items():
            (folder/'lang'/f'{lang}.json').write_text(json.dumps(labels,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    report={f'{cue}_{v}':row for cue,v,x,row in rendered}
    (evidence/'score-levels.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    waves={cue:x for cue,v,x,row in rendered if v==1}
    for name,selection in [('stealth-five',['neck_snap','rib_squeeze','joint_lock','choke_press','throat_rip']),
        ('normal-and-combo',['chest_drive','heel_stamp','knee_bump','beat_jab','beat_hook','beat_cross','beat_drive','beat_crush']),
        ('weapon-and-heavy',['claw_shear','blade_split','head_drop','hook_rend','arm_wrench','mount_maul','hunter_jaw','hunter_rend']),
        ('phases-and-quills',['grip_bare','chest_drive','weave','absorb_bare','finish_bare','quill_extract','quill_return','quill_merge'])]:
        with wave.open(str(evidence/(name+'.wav')),'wb') as w:
            w.setparams((1,2,SR,0,'NONE','not compressed'))
            for cue in selection:
                w.writeframes((np.clip(waves[cue],-1,1)*32767).astype('<i2').tobytes())
                w.writeframes(bytes(SR//3*2))
    print(f'{len(profiles)} events, {len(rendered)} OGG, written to {len(destinations)} checkouts')

if __name__=='__main__':
    opts=ArgumentParser()
    opts.add_argument('--project',type=Path,required=True)
    opts.add_argument('--mirror',type=Path)
    opts.add_argument('--evidence',type=Path,required=True)
    opts.add_argument('--ffmpeg',default='ffmpeg')
    build(opts.parse_args())
