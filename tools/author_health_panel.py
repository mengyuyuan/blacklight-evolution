"""Paint the health badge, connector and lower rail for the mod's health HUD."""
from pathlib import Path
import hashlib,json
from PIL import Image,ImageDraw
repo=Path(__file__).resolve().parents[1]
out=repo/'src/main/resources/assets/blacklight/textures/gui/health'
out.mkdir(parents=True,exist_ok=True)
dims={}
def image(key,size,paint):
    im=Image.new('RGBA',size)
    paint(ImageDraw.Draw(im))
    im.save(out/(key+'.png'))
    dims[key]={'width':size[0],'height':size[1]}
def emblem(d):
    d.polygon([(2,8),(8,2),(25,2),(30,7),(30,23),(23,30),(8,30),(2,24)],fill=(20,21,26,236))
    d.line([(3,13),(3,8),(9,3),(19,3)],fill=(155,160,155,255),width=2)
    d.line([(12,28),(23,28),(28,23),(28,13)],fill=(82,75,77,255),width=2)
    d.polygon([(7,12),(10,9),(13,9),(16,12),(19,9),(22,9),(25,12),(25,15),(16,24),(7,15)],fill=(160,50,59,255))
    d.line([(7,16),(12,16),(14,13),(16,19),(19,15),(25,15)],fill=(239,195,172,255))
def joint(d):
    d.line([(0,3),(5,3),(12,10),(23,10)],fill=(87,83,82,232),width=2)
    d.line([(0,6),(5,6),(12,13),(24,13)],fill=(151,84,83,246))
def rail(d):
    d.rectangle((0,0,31,1),fill=(86,65,67,250))
    d.line([(0,3),(19,3),(23,1)],fill=(157,125,116,210))
image('heart',(32,32),emblem)
image('junction',(24,16),joint)
image('underline',(32,4),rail)
profile={'schema':2,'width':336,'height':36,'bar':[39,13,293,7],'heart':[1,1,32,32],
         'junction':[24,11,24,16],'underline':[39,21,293,4],'fillColor':[163,53,61],
         'edgeColor':[235,166,143],'damageColor':[119,83,78],'textures':dims}
path=repo/'art/motion-profile/health-hud.data'
path.write_text(json.dumps(profile,separators=(',',':')),encoding='utf8')
manifest=repo/'art/motion-profile-manifest.json'
d=json.loads(manifest.read_bytes())
entry=d['files']['health-hud.data']
entry['bytes']=path.stat().st_size
if 'sha256' in entry:entry['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
manifest.write_text(json.dumps(d,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
