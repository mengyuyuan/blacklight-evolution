from PIL import Image, ImageDraw
import os, sys
O = r'E:\jtmp\tex_old'; Nn = r'E:\jtmp\tex_new'
kind = sys.argv[1]; out = sys.argv[2]
names = sorted(f[:-4] for f in os.listdir(os.path.join(Nn, kind)) if f.endswith('.png'))
S = 160 if kind == 'block' else 128
cols = 4
rows = (len(names)+cols-1)//cols
W = cols*(2*S+30); H = rows*(S+22)
sh = Image.new('RGBA', (W, H), (36, 36, 40, 255)); d = ImageDraw.Draw(sh)
def prep(p):
    if not os.path.exists(p): return None
    im = Image.open(p).convert('RGBA'); w, h = im.size
    if h > w: im = im.crop((0, 0, w, w))
    if kind == 'block':
        t = Image.new('RGBA', (w*2, w*2))
        for i in range(2):
            for j in range(2): t.paste(im, (i*w, j*w))
        im = t
    return im.resize((S, S), Image.NEAREST)
for i, n in enumerate(names):
    x = (i % cols)*(2*S+30); y = (i//cols)*(S+22)
    a = prep(os.path.join(O, kind, n+'.png')); b = prep(os.path.join(Nn, kind, n+'.png'))
    if a: sh.alpha_composite(a, (x+4, y+2))
    else: d.text((x+40, y+70), '(none)', fill=(160, 160, 160, 255))
    sh.alpha_composite(b, (x+S+10, y+2))
    d.text((x+4, y+S+5), n + '   old | new', fill=(230, 230, 230, 255))
sh.save(out); print(out, sh.size)
