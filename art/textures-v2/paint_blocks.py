import numpy as np, os, math
from PIL import Image
OUT = r'E:\jtmp\tex_new'
os.makedirs(OUT + r'\item', exist_ok=True); os.makedirs(OUT + r'\block', exist_ok=True)

def P(*hexes):
    return np.array([[int(h[i:i+2], 16) for i in (0, 2, 4)] for h in hexes], dtype=np.float64)

FLESH = P('1a0508','2e0a0f','4a1016','6b1a1f','8e2a2a','b04238','cf6a55','e8968a')
CARA  = P('0b080c','151017','211823','2e2232','3d2e42','4f3d55','6a5571')
GLOW  = P('5a0a0a','9a1010','d42418','ff4a2a','ff8a5a','ffc8a0')
SAC   = P('2a0608','551014','8a1a1a','bb3020','e05a2a','f58a45','ffc070','fff0c8')
PUS   = P('2b1806','5a3a0e','8a6418','b89026','dcb848','f2dc80','fff6c0')
SHELL = P('120809','1f0e10','2e1518','40201f','552c28','6e3c33','8a5040')
NECRO = P('100c0b','1d1715','2c2420','3d322b','4f4237','655446')
CONC  = P('3a3b3a','4c4d4b','5f605d','72736f','868783','9a9b96','afb0aa')
STEEL = P('1e2226','2c3137','3b4249','4c545c','5f6871','767f88','939ba3')
RUST  = P('3a1a0c','5c2a12','7c3e1a','9a5426')
BURL  = P('3e3220','554531','6e5b40','857150','9c8761','b39e75','c9b68e')
OLIVE = P('1c2214','2a3220','3a4430','4b5740','5e6b50','758363')
WOOD  = P('201a10','33291a','463924','5a4a30','6d5b3d')

def rng(s): return np.random.default_rng(s)

def vnoise(n, cells, seed):
    r = rng(seed); g = r.random((cells, cells))
    t = np.arange(n) * cells / n
    i0 = np.floor(t).astype(int); f = t - i0; f = f*f*(3-2*f); i1 = (i0+1) % cells
    a = g[np.ix_(i0, i0)]; b = g[np.ix_(i0, i1)]; c = g[np.ix_(i1, i0)]; d = g[np.ix_(i1, i1)]
    fy = f[:, None]; fx = f[None, :]
    return (a*(1-fx)+b*fx)*(1-fy) + (c*(1-fx)+d*fx)*fy

def fbm(n, seed, base=4, oct=3):
    s = 0; w = 1; tot = 0
    for o in range(oct):
        c = base * 2**o
        if c > n: break
        s = s + w*vnoise(n, c, seed+o*31); tot += w; w *= .5
    return s/tot

def jgrid(k, n, seed, j=.8):
    r = rng(seed); pts = []; s = n/k
    for gy in range(k):
        for gx in range(k):
            pts.append(((gx+.5+(r.random()-.5)*j)*s, (gy+.5+(r.random()-.5)*j)*s))
    return np.array(pts)

def worley(pts, n):
    yy, xx = np.mgrid[0:n, 0:n] + .5
    dx = xx[..., None] - pts[None, None, :, 0]; dy = yy[..., None] - pts[None, None, :, 1]
    dx = (dx + n/2) % n - n/2; dy = (dy + n/2) % n - n/2
    d = np.hypot(dx, dy); o = np.argsort(d, axis=2)
    f1 = np.take_along_axis(d, o[..., :1], 2)[..., 0]; f2 = np.take_along_axis(d, o[..., 1:2], 2)[..., 0]
    id1 = o[..., 0]
    ddx = np.take_along_axis(dx, o[..., :1], 2)[..., 0]; ddy = np.take_along_axis(dy, o[..., :1], 2)[..., 0]
    return f1, f2, id1, ddx, ddy

def light(h):
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1))/2; gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0))/2
    return gx + gy

def q(v, pal, dith=0, seed=0):
    v = np.clip(v, 0, 1)
    if dith:
        n = v.shape[0]; v = v + (rng(seed).random(v.shape)-.5)*dith
    idx = np.clip(np.rint(v*(len(pal)-1)), 0, len(pal)-1).astype(int)
    return pal[idx]

def save(img, rel):
    a = np.clip(np.rint(img), 0, 255).astype(np.uint8)
    if a.shape[2] == 3: a = np.dstack([a, np.full(a.shape[:2], 255, np.uint8)])
    Image.fromarray(a, 'RGBA').save(os.path.join(OUT, rel))

N = 32
yy, xx = np.mgrid[0:N, 0:N] + .5

def flesh(seed, pal=FLESH, k=3, glow=True, glowpal=GLOW):
    f1, f2, idc, _, _ = worley(jgrid(k, N, seed), N)
    e = f2 - f1
    h = np.clip(e/4.5, 0, 1)**.6 + fbm(N, seed+5, 8, 2)*.25
    L = light(h)
    tone = rng(seed+9).random(k*k)[idc]*.12
    v = .06 + h*.34 + L*.62 + tone*.8
    img = q(v, pal, .12, seed)
    crev = e < 1.0
    img[crev] = pal[0]
    if glow:
        gm = crev & (fbm(N, seed+77, 4, 2) > .56)
        img[gm] = glowpal[1]
        img[gm & (fbm(N, seed+78, 8, 1) > .6)] = glowpal[2]
    spec = (L > .26) & (h > .62) & (rng(seed+3).random((N, N)) > .75)
    img[spec] = pal[-1]
    return img

def carapace(seed):
    f1, f2, idc, _, _ = worley(jgrid(3, N, seed, .9), N)
    e = f2 - f1
    h = np.clip(e/3.0, 0, 1)**.5 - f1*.012
    L = light(h)
    tone = rng(seed+2).random(9)[idc]*.18
    v = .30 + L*1.6 + tone + fbm(N, seed+8, 8, 2)*.12 - .06
    img = q(v, CARA, .1, seed)
    seam = e < 1.1
    img[seam] = CARA[0]
    lit = rng(seed+4).random(9) > .45
    gm = seam & lit[idc] & (fbm(N, seed+11, 4, 1) > .45)
    img[gm] = GLOW[1]
    img[gm & (rng(seed+12).random((N, N)) > .6)] = GLOW[2]
    halo = (e >= 1.1) & (e < 2.0) & lit[idc] & (fbm(N, seed+11, 4, 1) > .5)
    img[halo] = img[halo]*.6 + GLOW[0]*.4
    sc = (rng(seed+13).random((N, N)) > .985) & ~seam
    img[sc] = CARA[-1]
    return img

def sacs(bg, pts, radii, pal, seed, rimc):
    img = bg.copy()
    for (cx, cy), r in zip(pts, radii):
        dx = (xx - cx + N/2) % N - N/2; dy = (yy - cy + N/2) % N - N/2
        d = np.hypot(dx, dy)
        m = d < r
        nz = np.sqrt(np.clip(1 - (d/r)**2, 0, 1))
        lam = (-dx/r*.55 - dy/r*.55 + nz*.6)
        inner = 1 - d/r
        v = .02 + inner*.38 + lam*.38 + fbm(N, seed+int(cx*7), 8, 1)*.1
        col = q(v, pal, .08, seed+int(cy))
        img[m] = col[m]
        rim = m & (d > r - 1.0)
        img[rim] = rimc
        sp = m & (np.hypot(dx + r*.38, dy + r*.38) < max(.8, r*.16))
        img[sp] = pal[-1]
        vein = m & (d < r-1.5) & (np.abs(np.sin((np.arctan2(dy, dx)*3 + d*.7 + seed))) < .12)
        img[vein] = img[vein]*.6 + pal[1]*.4
    return img

def hive_sac():
    bg = flesh(41, k=4, glow=False)*.7
    r = rng(42); pts = jgrid(2, N, 43, .6); radii = 6.0 + r.random(4)*1.8
    return sacs(bg, pts, radii, SAC, 44, SAC[0])

def vein_walks(img, seed, count, col, colhi, w=1):
    r = rng(seed); m = np.zeros((N, N), bool)
    for _ in range(count):
        x, y = r.random()*N, r.random()*N; a = r.random()*6.28
        for _s in range(40):
            m[int(y) % N, int(x) % N] = True
            if w > 1: m[int(y) % N, int(x+1) % N] = True
            a += (r.random()-.5)*.9; x += math.cos(a); y += math.sin(a)
    sh = np.roll(np.roll(m, 1, 0), 1, 1) & ~m
    img[sh] = img[sh]*.55
    img[m] = col
    hi = m & ~np.roll(np.roll(m, -1, 0), -1, 1)
    img[hi] = colhi
    return img

CRIM = P('22060c','3c0b15','5c1220','7e1c2a','a12a36','c44450','df7070','f2a0a0')

def city_flesh():
    img = flesh(51, pal=CRIM, k=5)
    return vein_walks(img, 52, 3, CRIM[3], CRIM[5])

def city_sac():
    bg = city_flesh()*.85
    r = rng(61); pts = []; radii = []
    while len(pts) < 4:
        p = (r.random()*N, r.random()*N); rr = 2.6 + r.random()*2.2
        ok = all(math.hypot(((p[0]-q2[0]+16) % 32)-16, ((p[1]-q2[1]+16) % 32)-16) > rr+r2+1 for q2, r2 in zip(pts, radii))
        if ok: pts.append(p); radii.append(rr)
    return sacs(bg, pts, radii, SAC, 62, CRIM[0])

def city_shell():
    img = np.zeros((N, N, 3)); W = 8; H = 8; R = 5.6
    best = np.full((N, N), -1.0); bx = np.zeros((N, N)); by = np.zeros((N, N)); bid = np.zeros((N, N), int)
    rs = rng(71).random(64)
    for row in range(4):
        cy = row*H + 1.0; off = (W/2) if row % 2 else 0
        for col in range(5):
            cx = col*W + off
            dx = (xx - cx + N/2) % N - N/2; dy = (yy - cy) % N
            dy = np.where(dy > N/2, dy - N, dy)
            inside = np.hypot(dx, dy) < R
            take = inside & (dy > best)
            best[take] = dy[take]; bx[take] = dx[take]; by[take] = dy[take]; bid[take] = row*5+col
    d = np.hypot(bx, by)
    t = d/R
    h = np.sqrt(np.clip(1-t*t, 0, 1))
    v = .25 + h*.35 - bx/R*.18 - (by/R)*.12 + rs[bid]*.12 + fbm(N, 73, 8, 1)*.1
    img = q(v, SHELL, .1, 74)
    rim = t > .82
    img[rim] = SHELL[0]
    hl = (t > .55) & (t < .72) & (bx < 0) & (by < 2)
    img[hl] = SHELL[5]
    gl = rim & (rs[bid] > .65) & (by > 2)
    img[gl] = CRIM[3]
    return img

def hatch(open_):
    img = city_shell()*.9
    cx = cy = 16.0
    dx = xx - cx; dy = yy - cy; r = np.hypot(dx, dy); a = np.arctan2(dy, dx)
    ring = (r > 10.2) & (r < 15.0)
    u = (r - 10.2)/4.8; hz = np.sqrt(np.clip(1-(2*u-1)**2, 0, 1))
    nx = dx/np.maximum(r, .1)*(2*u-1); ny = dy/np.maximum(r, .1)*(2*u-1)
    lam = -nx*.6 - ny*.6 + hz*.5
    seg = np.floor((a + math.pi)/(2*math.pi)*14).astype(int)
    frac = ((a + math.pi)/(2*math.pi)*14) % 1
    v = .15 + lam*.55 + rng(81).random(15)[seg]*.12
    rc = q(v, CARA, .08, 82)
    img[ring] = rc[ring]
    gap = ring & ((frac < .09) | (frac > .93))
    img[gap] = CARA[0]
    img[(r >= 9.6) & (r <= 10.2)] = CARA[0]
    img[(r >= 15.0) & (r < 15.7)] = CARA[0]
    inner = r < 9.6
    if not open_:
        fold = np.sin(a*7 + r*.55)
        v2 = .25 + (r/9.6)*.35 + fold*.22
        ic = q(v2, CRIM, .1, 83)
        img[inner] = ic[inner]
        ridge = inner & (fold > .82)
        img[ridge] = CRIM[6]
        valley = inner & (fold < -.8)
        img[valley] = CRIM[0]
        c = inner & (r < 2.2)
        img[c] = GLOW[2]; img[inner & (r < 1.2)] = GLOW[4]
    else:
        v2 = (r/9.6)**1.8
        HOLE = P('000000','070203','12040a','240810','3c0c16','5c1220')
        ic = q(v2 + np.sin(a*9)*.06, HOLE, .06, 84)
        img[inner] = ic[inner]
        for k in range(8):
            ak = k*math.pi/4 + .2
            da = np.angle(np.exp(1j*(a - ak)))
            wid = (r - 4.5)/5.5*.30
            sp = inner & (r > 4.5) & (np.abs(da) < wid)
            col = np.where((da < 0)[..., None], P('e0d4b8')[0], P('a89878')[0])
            img[sp] = col[sp]
        rr = inner & (r > 8.2)
        img[rr] = CRIM[3]
        wet = inner & (r > 7.6) & (r <= 8.2) & (np.sin(a*11) > .3)
        img[wet] = GLOW[1]
    return img

def wounded():
    v = fbm(N, 91, 4, 3)
    img = q(.15 + v*.75, NECRO, .14, 92)
    br = (fbm(N, 93, 4, 2) > .58) & ((xx.astype(int) + yy.astype(int)) % 2 == 0)
    img[br] = img[br]*.5 + CRIM[2]*.5
    f1, f2, _, _, _ = worley(jgrid(4, N, 94, .9), N)
    cr = ((f2 - f1) < .7) & (fbm(N, 95, 8, 1) > .45)
    img[cr] = NECRO[0]
    r = rng(96)
    for i in range(4):
        cx, cy, rr = r.random()*N, r.random()*N, 2.2 + r.random()*1.8
        dx = (xx - cx + 16) % 32 - 16; dy = (yy - cy + 16) % 32 - 16; d = np.hypot(dx, dy)
        img[(d < rr+1.2) & (d >= rr)] = PUS[3]*.8
        img[(d < rr+1.2) & (d >= rr) & (dx + dy > 0)] = PUS[2]*.8
        m = d < rr
        col = q(.2 + (d/rr)*.6 + (dx + dy)/rr*.12, CRIM, .1, 97+i)
        img[m] = col[m]
        img[d < rr*.4] = P('080203')[0]
        img[(d < rr*.4) & (np.hypot(dx+.6, dy+.6) < .7)] = GLOW[3]
    return img

def city_vein():
    img = flesh(101, pal=CRIM, k=5, glow=False)*.6
    VE = P('2a0408','4a0810','701018','981820','c02428','e84a38','ff9070')
    r = rng(102)
    for i, (x0, wd, fr, am) in enumerate([(7, 2.6, 1, 2.5), (22, 3.2, 2, 2.0), (15, 1.4, 1, 3.5)]):
        ph = r.random()*6.28
        cxl = x0 + am*np.sin(yy/N*2*math.pi*fr + ph)
        w = wd + .5*np.sin(yy/N*4*math.pi + ph)
        sd = (xx - cxl)/w
        m = np.abs(sd) < 1
        hz = np.sqrt(np.clip(1-sd*sd, 0, 1))
        v = .05 + hz*.5 - sd*.22 + (np.sin(yy*1.1 + ph + i) > .7)*.12
        col = q(v, VE, .05, 103+i)
        img[m] = col[m]
        edge = (np.abs(sd) >= 1) & (np.abs(sd) < 1 + .6/w)
        img[edge] = VE[0]*.6
    return img

def concrete():
    v = .45 + (fbm(N, 111, 4, 3)-.5)*.35
    img = q(v, CONC, .14, 112)
    ag = rng(113).random((N, N))
    img[ag > .955] = CONC[1]; img[ag < .03] = CONC[5]
    st = fbm(N, 114, 2, 2) > .62
    img[st] = img[st]*.85
    img[0, :] = CONC[5]; img[:, 0] = CONC[5]; img[N-1, :] = CONC[0]; img[:, N-1] = CONC[0]
    img[16, 1:N-1] = CONC[1]; img[15, 1:N-1] = img[15, 1:N-1]*.5 + CONC[5]*.5
    for hx, hy in ((8, 8), (24, 8), (8, 24), (24, 24)):
        img[hy-1:hy+1, hx-1:hx+1] = CONC[0]; img[hy-1, hx-1:hx+1] = P('2a2b2a')[0]
        img[hy+1, hx-1:hx+1] = CONC[5]
        for k in range(2, 6):
            if hy + k < N-1 and k % 2 == 0: img[hy+k, hx] = img[hy+k, hx]*.82
    return img

def metal():
    v = .42 + (fbm(N, 121, 8, 2)-.5)*.2
    img = q(v, STEEL, .08, 122)
    for y in range(2, N-2):
        for x in range(2, N-2):
            cx, cy = x // 4, y // 4; lx, ly = x % 4, y % 4
            if (cx + cy) % 2 == 0:
                if lx == ly and 0 < lx < 3: img[y, x] = STEEL[5] if lx == 1 else STEEL[2]
            else:
                if lx + ly == 3 and 0 < lx < 3: img[y, x] = STEEL[5] if lx == 2 else STEEL[2]
    img[0, :] = STEEL[6]; img[:, 0] = STEEL[6]; img[1, 1:] = STEEL[5]; img[1:, 1] = STEEL[5]
    img[N-1, :] = STEEL[0]; img[:, N-1] = STEEL[0]; img[N-2, 1:] = STEEL[1]; img[1:N-1, N-2] = STEEL[1]
    for rx, ry in ((3, 3), (28, 3), (3, 28), (28, 28), (16, 3), (16, 28), (3, 16), (28, 16)):
        img[ry, rx] = STEEL[6]; img[ry+1, rx+1] = STEEL[0]; img[ry, rx+1] = STEEL[4]; img[ry+1, rx] = STEEL[3]
    ru = (fbm(N, 123, 4, 2) > .74)
    rc = q(fbm(N, 124, 8, 1), RUST, .2, 125)
    m = ru & (rng(126).random((N, N)) > .62)
    img[m] = rc[m]
    return img

def sandbag():
    img = np.zeros((N, N, 3)) + BURL[0]
    rows = [(0, 8, 0), (8, 8, 8), (16, 8, 0), (24, 8, 8)]
    r = rng(131)
    for ry, rh, off in rows:
        for b in range(3):
            x0 = b*16 - 16 + off
            for y in range(ry, ry+rh):
                for xr in range(x0, x0+16):
                    x = xr % N
                    u = (xr - x0 + .5)/16*2 - 1; w = (y - ry + .5)/rh*2 - 1
                    s = abs(u)**3 + abs(w)**2.2
                    if s > 1: continue
                    hz = math.sqrt(max(0, 1 - s))
                    lam = -u*.25 - w*.55 + hz*.55
                    weave = .06 if (x + y) % 2 == 0 else -.03
                    v = .1 + lam*.75 + weave + (r.random()-.5)*.1
                    i = int(np.clip(round(v*6), 0, 6))
                    img[y, x] = BURL[i]
                    if abs(u) > .86 and (y % 2 == 0): img[y, x] = BURL[1]
                    if w < -.35 and w > -.55 and abs(u) < .7 and x % 3 == 0: img[y, x] = BURL[5]
    d = (fbm(N, 132, 4, 2) > .63)
    img[d] = img[d]*.8
    return img

def crate():
    img = np.zeros((N, N, 3))
    planks = [(3, 11), (11, 20), (20, 29)]
    r = rng(141)
    for i, (y0, y1) in enumerate(planks):
        base = .35 + r.random()*.2
        for y in range(y0, y1):
            for x in range(N):
                g = math.sin(x*.35 + y*1.7 + i*3 + math.sin(x*.12 + i)*2)
                v = base + g*.12 + (r.random()-.5)*.1 - (y - y0)/(y1 - y0)*.15
                img[y, x] = OLIVE[int(np.clip(round(v*5), 0, 5))]
        img[y0, :] = OLIVE[5]; img[y1-1, :] = OLIVE[0]
    for y in range(N):
        for x in range(N):
            if x < 3 or x > N-4 or y < 3 or y > N-4:
                e = min(x, y, N-1-x, N-1-y)
                v = 4 if (x <= y and x < 3) or (y < x and y < 3) else 2
                if x > N-4 or y > N-4: v = 1 if e == 0 else 2
                if e == 0 and (x == 0 or y == 0): v = 5
                img[y, x] = WOOD[min(v, 4)] if v < 5 else WOOD[4]*1.1
    for cx, cy in ((0, 0), (N-6, 0), (0, N-6), (N-6, N-6)):
        img[cy:cy+6, cx:cx+6] = STEEL[3]
        img[cy, cx:cx+6] = STEEL[5]; img[cy:cy+6, cx] = STEEL[5]
        img[cy+5, cx:cx+6] = STEEL[1]; img[cy:cy+6, cx+5] = STEEL[1]
        img[cy+2, cx+2] = STEEL[6]; img[cy+3, cx+3] = STEEL[0]
    ST = P('c8c2a0')[0]
    for i, x0 in enumerate((8, 14, 20)):
        for k in range(4):
            for xx_ in (x0 + k, x0 + 6 - k - 1):
                pass
        for k in range(3):
            y = 14 + k; img[y, x0 + k] = img[y, x0 + k]*.3 + ST*.7; img[y, x0 + 5 - k] = img[y, x0 + 5 - k]*.3 + ST*.7
    return img

BLOCKS = {
    'hive_flesh': lambda: flesh(11),
    'hive_carapace': lambda: carapace(21),
    'hive_sac': hive_sac,
    'city_flesh': city_flesh,
    'city_sac': city_sac,
    'city_shell': city_shell,
    'city_hatch': lambda: hatch(False),
    'city_hatch_open': lambda: hatch(True),
    'city_wounded': wounded,
    'city_vein': city_vein,
    'facility_concrete': concrete,
    'facility_metal': metal,
    'facility_sandbag': sandbag,
    'facility_crate': crate,
}
if __name__ == '__main__':
    for k, f in BLOCKS.items(): save(f(), 'block\\' + k + '.png')
    print('blocks', len(BLOCKS))
