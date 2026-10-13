"""Encode native tutorial captures; no labels are baked into the remappable demos.

Usage: python tools/encode_tutorial_gifs.py POSE_CAPTURE SKILL_CAPTURE
Requires Pillow. Both inputs are produced by the opt-in disposable-world fixtures.
"""
from pathlib import Path
import hashlib,json,sys
from PIL import Image

poses,skills=map(Path,sys.argv[1:3])
target=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight/tutorials'
target.mkdir(parents=True,exist_ok=True)
overrides={'graveyard':'ground_graveyard','barrage':'ground_barrage','pain':'ground_pain','dive':'bulletdive_80'}
manifest={}
for folder in sorted(poses.iterdir()):
    if not folder.is_dir():continue
    family=folder.name
    source=skills/'tutorials'/overrides[family] if family in overrides else folder
    paths=sorted(source.glob('*.png'));assert 2<=len(paths)<=180,(family,len(paths))
    frames=[Image.open(p).convert('RGB').resize((320,180),Image.Resampling.LANCZOS) for p in paths]
    contact=Image.new('RGB',(320*10,180*((len(frames)+9)//10)))
    for n,f in enumerate(frames):contact.paste(f,(n%10*320,n//10*180))
    palette=contact.quantize(colors=192,method=Image.Quantize.MEDIANCUT)
    frames=[f.quantize(palette=palette,dither=Image.Dither.NONE) for f in frames]
    duration=80 if family in overrides else 70
    out=target/(family+'.gif')
    frames[0].save(out,save_all=True,append_images=frames[1:],loop=0,duration=duration,optimize=False,disposal=1)
    with Image.open(out) as im:
        assert im.n_frames>1 and im.size==(320,180)
        count=im.n_frames
    manifest[family]={'file':out.name,'frames':count,'duration_ms':duration*len(frames),'source':'native game effect' if family in overrides else 'native model motion / mechanic diagram','sha256':hashlib.sha256(out.read_bytes()).hexdigest()}
(target/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(f'{len(manifest)} GIFs, {sum(p.stat().st_size for p in target.glob("*.gif"))/1024/1024:.2f} MiB')
