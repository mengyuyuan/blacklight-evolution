import os, sys
from PIL import Image, ImageDraw
D = sys.argv[1]; out = sys.argv[2]
names = sorted(f[:-4] for f in os.listdir(D) if f.endswith('.png'))
S = 10; cell = 16*S + 16
cols = 5; rows = (len(names)+cols-1)//cols
sh = Image.new('RGBA', (cols*cell, rows*(cell+14) + 60), (48, 48, 54, 255)); d = ImageDraw.Draw(sh)
for i, n in enumerate(names):
    im = Image.open(os.path.join(D, n+'.png')).convert('RGBA')
    x = (i % cols)*cell; y = (i//cols)*(cell+14)
    sh.alpha_composite(im.resize((16*S, 16*S), Image.NEAREST), (x+8, y+4))
    d.text((x+8, y+16*S+6), n, fill=(220, 220, 220, 255))
# 物品栏实际大小 (2x GUI = 32px)
y0 = rows*(cell+14) + 10
for i, n in enumerate(names):
    im = Image.open(os.path.join(D, n+'.png')).convert('RGBA')
    x = 8 + i*40
    d.rectangle((x-2, y0-2, x+33, y0+33), fill=(139, 139, 139, 255))
    sh.alpha_composite(im.resize((32, 32), Image.NEAREST), (x, y0))
sh.save(out)
