"""Original pixel-art civilian atlases; standard 64px skin UVs + a separate tissue swatch.
No external game texture is sampled. Deterministic, nearest-neighbour Minecraft pixels.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import random

OUT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight/textures/entity'
OUT.mkdir(parents=True,exist_ok=True)
PALETTES=[
    ((163,130,108),(42,52,66),(27,32,43),(45,33,28)), # charcoal city jacket
    ((181,147,118),(151,79,35),(39,47,54),(53,41,29)), # road worker
    ((141,112,94),(137,143,137),(48,49,57),(27,24,24)), # open-collar shirt
    ((176,143,119),(55,92,95),(31,48,55),(59,39,31)), # clinic scrubs
]

def make(index):
    skin,shirt,pants,hair=PALETTES[index];rng=random.Random(90613+index)
    im=Image.new('RGBA',(128,64));d=ImageDraw.Draw(im)
    def fill(box,color,grain=3):
        x,y,w,h=box
        for py in range(y,y+h):
            for px in range(x,x+w):
                shade=rng.randint(-grain,grain)
                im.putpixel((px,py),tuple(max(0,min(255,c+shade)) for c in color)+(255,))
    def rect(box,c):d.rectangle(box,fill=c)
    # Head. Each face is deliberately shaded; the face remains recognisably human.
    fill((0,0,32,16),skin);fill((0,8,8,8),tuple(c-12 for c in skin));fill((24,8,8,8),tuple(c-18 for c in skin))
    fill((8,0,16,8),hair);fill((16,8,8,8),hair)
    rect((8,8,15,9),hair);rect((8,10,8,12),hair);rect((15,10,15,12),hair)
    rect((9,11,10,11),(48,37,34));rect((13,11,14,11),(48,37,34))
    rect((9,12,10,12),(183,177,156));rect((13,12,14,12),(121,114,101))
    rect((10,12,10,12),(34,26,26));rect((13,12,13,12),(49,21,22))
    rect((11,13,12,14),tuple(c-17 for c in skin));rect((10,15,13,15),(94,64,60))
    # Hair is a sparse outer layer, with actual transparency around the face.
    fill((40,0,16,8),hair);fill((32,8,8,3),hair);fill((48,8,8,4),hair);fill((56,8,8,3),hair)
    rect((40,8,47,8),hair);rect((40,9,40,10),hair);rect((47,9,47,9),hair)
    # Continuous upper/lower limb UVs: split geometry adds elbows/knees, no separate skin scale.
    fill((16,16,24,16),shirt)
    fill((0,16,16,16),pants);fill((16,48,16,16),pants)
    fill((40,16,16,16),shirt);fill((32,48,16,16),shirt)
    # Hands and worn boots, kept on the correct UV sides.
    fill((40,28,16,4),skin);fill((32,60,16,4),skin)
    for x,y in ((0,16),(16,48)):
        fill((x,y+13,16,3),(30,28,29));rect((x+4,y+15,x+7,y+15),(58,52,48))
        rect((x+4,y+5,x+4,y+10),tuple(max(0,c-9) for c in pants))
    # Open collar, seams, hems and shirt-specific silhouette cues.
    fill((20,20,8,12),shirt)
    rect((22,20,25,20),skin);rect((23,21,24,22),skin)
    seam=tuple(max(0,c-24) for c in shirt);rect((23,23,23,30),seam)
    rect((20,30,27,31),seam);rect((20,22,21,24),tuple(min(255,c+12) for c in shirt))
    if index==0:
        rect((21,21,22,27),(69,76,85));rect((25,21,26,27),(69,76,85));rect((24,24,24,29),(110,108,94))
    elif index==1:
        for x in (20,26):rect((x,21,x,29),(194,180,132))
        rect((20,27,27,28),(193,184,148));rect((25,23,26,25),(91,64,39))
    elif index==2:
        rect((20,21,22,21),(181,181,166));rect((25,21,27,21),(181,181,166));rect((25,24,26,26),(102,110,108))
    else:
        rect((20,21,22,21),(76,119,117));rect((25,23,26,25),(80,119,116));rect((26,24,26,24),(194,189,168))
    # Torn shoulder/collar follows a continuous surface, not unrelated spikes.
    tissue=(29,23,27);red=(105,29,34);vein=(141,43,40)
    for points in ([(27,20),(26,21),(27,23),(26,25),(27,28)],[(14,10),(15,11),(14,13),(15,15)],[(36,53),(37,56),(36,58),(38,61)]):
        d.line(points,fill=tissue,width=2);d.line(points,fill=red,width=1)
    for x,y in ((27,24),(26,27),(14,14),(36,58),(37,60)):
        rect((x,y,x+1,y+1),tissue)
    # Separate material atlas for low-profile flesh plates. Enough local value variation for vanilla lighting.
    fill((64,0,48,40),tissue,4)
    for x in range(65,111,7):
        d.line([(x,2),(x+2,9),(x,15),(x+3,23),(x+1,36)],fill=(52,39,43),width=2)
        d.line([(x+1,3),(x+3,10),(x+1,15),(x+4,23),(x+2,36)],fill=red,width=1)
    for x,y in ((68,6),(84,7),(87,23),(68,28)):
        rect((x,y,x+2,y),(146,43,42));rect((x,y+1,x+1,y+1),(83,24,30))
    im.save(OUT/f'infected_civilian_{index}.png',optimize=True)
    return im

if __name__=='__main__':
    images=[make(i) for i in range(4)]
    print('Authored four original 128x64 civilian atlases in',OUT)
