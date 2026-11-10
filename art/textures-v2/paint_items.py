import numpy as np, math, os
from paint_blocks import P, save, rng, FLESH, CARA, GLOW, SAC, CRIM, STEEL, OLIVE
N = 16
BRASS = P('5a3a10','8a5c1c','b8862e','dcae48','f4d67a'); BRASS_O = P('2e1c06')[0]
COPP  = P('5a2410','8a3a1c','b4562c','d47a48','eea070'); COPP_O = P('2a1006')[0]
ST5   = P('2c3137','3b4249','4f575f','6a737c','939ba3'); ST_O = P('121518')[0]
OL5   = P('2a3220','3a4430','4e5a40','66745a','8a9878'); OL_O = P('12160c')[0]
YEL5  = P('6a5410','9a7c18','c8a428','e8c844','f8e690'); YEL_O = P('2a2006')[0]
RED5  = P('4a0a0a','7a1212','a81c18','d03828','f06a50'); RED_O = P('200404')[0]
WHT5  = P('6a6e70','8e9296','b4b8ba','d4d8d8','f2f4f2'); WHT_O = P('26282a')[0]
TAN5  = P('5a4a30','7a6644','9a8458','b8a274','d4c094'); TAN_O = P('241c10')[0]
ORA5  = P('6a2a08','a24410','d46a18','f49230','ffc070'); ORA_O = P('2a1004')[0]
CAR5  = CARA[[1,2,3,4,6]]; CAR_O = CARA[0]
GLS5  = P('5a6e78','7e94a0','a4bcc6','c8dce2','eef8fa'); GLS_O = P('1a2428')[0]
BONE5 = P('7a6c58','9a8a70','bcac90','d8ccb0','f2ead6'); BONE_O = P('2a2418')[0]
TEAL5 = P('0a4a40','108070','20b098','50dcc0','b0fff0')
BLOOD5= P('3a0408','6a0a12','a0141c','d42a2a','ff6a50')
LT = np.array([-.62, -.62, .48]); LT /= np.linalg.norm(LT)

class Canvas:
    def __init__(s):
        s.c = np.zeros((N, N, 4)); s.o = np.zeros((N, N, 3)); s.f = np.zeros((N, N), bool)
    def put(s, x, y, col, oc=None, a=255):
        if 0 <= x < N and 0 <= y < N:
            s.c[y, x, :3] = col; s.c[y, x, 3] = a; s.f[y, x] = True
            if oc is not None: s.o[y, x] = oc
    def outline(s, skip=None):
        add = []
        for y in range(N):
            for x in range(N):
                if s.f[y, x]: continue
                for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
                    X, Y = x+dx, y+dy
                    if 0 <= X < N and 0 <= Y < N and s.f[Y, X] and not (skip is not None and skip[Y, X]):
                        add.append((x, y, s.o[Y, X])); break
        for x, y, c in add: s.c[y, x, :3] = c; s.c[y, x, 3] = 255
    def save(s, name): save(s.c, 'item\\' + name + '.png')

def lathe(cv, p0, p1, prof, tag=None):
    p0 = np.array(p0, float); p1 = np.array(p1, float); ax = p1 - p0; L = np.linalg.norm(ax); ax /= L
    pe = np.array([-ax[1], ax[0]])
    for y in range(N):
        for x in range(N):
            v = np.array([x+.5, y+.5]) - p0; t = v @ ax / L; s = v @ pe
            if t < 0 or t > 1: continue
            r = prof(t)
            if r is None: continue
            rad, shader, oc = r
            if rad <= 0 or abs(s) > rad: continue
            u = s/rad; nz = math.sqrt(max(0, 1-u*u))
            n3 = np.array([pe[0]*u, pe[1]*u, nz]); b = float(n3 @ LT)
            res = shader(b, t, s, rad, x, y)
            if res is None: continue
            col, a = (res, 255) if not isinstance(res, tuple) else res
            cv.put(x, y, col, oc, a)
            if tag is not None: tag[y, x] = True

def solid(pal):
    def f(b, t, s, r, x, y):
        return pal[int(np.clip(math.floor((b + .15)/1.0*5), 0, 4))]
    return f

def seg(table):
    def prof(t):
        for t0, t1, rf, sh, oc in table:
            if t0 <= t < t1:
                return (rf(t) if callable(rf) else rf), sh, oc
        return None
    return prof

def ogive(t0, t1, r0, pw=.6):
    return lambda t: r0*max(0, 1-((t-t0)/(t1-t0)))**pw

# ---------- rifle round ----------
def rifle_round():
    cv = Canvas()
    lathe(cv, (3.2, 13.8), (13.2, 2.8), seg([
        (0, .08, 2.1, solid(BRASS[[0,1,1,2,3]]), BRASS_O),
        (.08, .12, 1.5, solid(BRASS[[0,0,1,1,2]]), BRASS_O),
        (.12, .55, 2.1, solid(BRASS), BRASS_O),
        (.55, .63, lambda t: 2.1-(t-.55)/.08*.7, solid(BRASS), BRASS_O),
        (.63, .70, 1.4, solid(BRASS), BRASS_O),
        (.70, 1.0, ogive(.70, 1.0, 1.45, .55), solid(COPP), COPP_O)]))
    cv.outline(); cv.save('rifle_round')

def mg_round():
    cv = Canvas()
    for i, (cx, top) in enumerate(((3.5, 1.5), (8.0, 2.5), (12.5, 1.5))):
        lathe(cv, (cx, 13.6), (cx, top), seg([
            (0, .08, 1.6, solid(BRASS[[0,1,1,2,3]]), BRASS_O),
            (.08, .58, 1.6, solid(BRASS), BRASS_O),
            (.58, .66, 1.1, solid(BRASS), BRASS_O),
            (.66, 1.0, ogive(.66, 1.0, 1.15, .5), solid(COPP), COPP_O)]))
    for x in range(1, 15):
        for y in (9, 10):
            col = ST5[3] if y == 9 else ST5[1]
            if x in (2, 3, 4, 7, 8, 9, 11, 12, 13): col = ST5[4] if y == 9 else ST5[2]
            cv.put(x, y, col, ST_O)
    cv.outline(); cv.save('machine_gun_round')

def grenade_like(name, body, band, nose, band2=None, veins=False):
    cv = Canvas()
    tbl = [(0, .07, 4.6, solid(ST5[[0,1,1,2,3]]), ST_O),
           (.07, .34, 3.6, solid(ST5), ST_O),
           (.34, .42, 4.4, solid(COPP), COPP_O),
           (.42, .52, 4.4, solid(body), OL_O if body is OL5 else body[0]*.4),
           (.52, .60, 4.4, solid(band), band[0]*.4),
           (.60, .68, 4.4, solid(band2 if band2 is not None else body), body[0]*.4),
           (.68, 1.0, ogive(.68, 1.0, 4.4, .55), solid(nose), nose[0]*.4)]
    tag = np.zeros((N, N), bool)
    lathe(cv, (8.0, 15.0), (8.0, 1.0), seg(tbl), tag)
    if veins:
        r = rng(7)
        for (x, y) in ((6, 6), (7, 5), (9, 7), (10, 8), (5, 8), (11, 5)):
            if cv.f[y, x]: cv.put(x, y, GLOW[3] if r.random() > .4 else GLOW[2])
    cv.outline(); cv.save(name)

def rocket_like(name, missile=False):
    cv = Canvas()
    if not missile:
        tbl = [(0, .05, 1.2, solid(ST5[[0,0,1,1,2]]), ST_O),
               (.05, .24, lambda t: 3.3 - (t-.05)/.19*1.8, solid(ST5), ST_O),
               (.24, .52, 1.6, solid(OL5), OL_O),
               (.52, .58, 1.9, solid(YEL5), YEL_O),
               (.58, .82, lambda t: 1.9 + math.sin((t-.58)/.24*math.pi*.8)*.9, solid(OL5), OL_O),
               (.82, 1.0, ogive(.82, 1.0, 2.6, .75), solid(OL5[[0,1,2,3,4]]), OL_O)]
        p0, p1 = (2.0, 14.0), (14.0, 2.0)
    else:
        tbl = [(0, .04, 1.1, solid(ST5[[0,0,1,1,2]]), ST_O),
               (.04, .20, lambda t: 3.0 - (t-.04)/.16*1.7, solid(ST5), ST_O),
               (.20, .48, 1.5, solid(WHT5), WHT_O),
               (.48, .58, lambda t: 2.6 - abs(t-.53)*20, solid(ST5), ST_O),
               (.58, .66, 1.5, solid(WHT5), WHT_O),
               (.66, .72, 1.5, solid(RED5), RED_O),
               (.72, .86, 1.5, solid(WHT5), WHT_O),
               (.86, 1.0, ogive(.86, 1.0, 1.5, .45), solid(P('101418','1e2a34','30485a','5a7a90','a8c8e0')), ST_O)]
        p0, p1 = (1.6, 14.4), (14.4, 1.6)
    lathe(cv, p0, p1, seg(tbl))
    cv.outline(); cv.save(name)

def syringe():
    cv = Canvas()
    def fluid(b, t, s, r, x, y):
        if abs(s) > r - .75:
            return GLS5[int(np.clip((b+.15)*5, 0, 4))], 210
        c = (t - .25)/.53
        wy = math.sin(c*math.pi*3)*.55
        if abs(s - wy) < .45: return P('1a0610')[0]
        if c > .78 and abs(s) < .6: return GLOW[3]
        return BLOOD5[int(np.clip((b+.25)*4, 0, 4))]
    tbl = [(0, .06, 2.6, solid(ST5), ST_O),
           (.06, .20, 1.0, solid(ST5), ST_O),
           (.20, .25, 3.2, solid(ST5), ST_O),
           (.25, .78, 2.7, fluid, GLS_O),
           (.78, .88, lambda t: 2.0 - (t-.78)/.10*1.2, solid(ST5), ST_O)]
    lathe(cv, (1.4, 14.6), (12.4, 3.6), seg(tbl))
    cv.outline()
    for x, y in ((11, 4), (12, 3), (13, 2), (14, 1)):
        cv.c[y, x] = [*ST5[4], 255]
    cv.c[0, 15] = [*ST5[3], 255]
    cv.save('supreme_parasite_syringe')

def vial(name, prof, liquid, fill_y, extra=None):
    cv = Canvas()
    def mk(kind):
        def f(b, t, s, r, x, y):
            if kind == 'cap': return kind_cap(b, t, s, r, x, y)
            if abs(s) > r - 1.0:
                return GLS5[int(np.clip((b+.1)*5, 0, 4))], 230
            if y >= fill_y:
                if extra:
                    e = extra(x, y)
                    if e is not None: return e
                return liquid[int(np.clip((b+.35)*4, 0, 4))]
            return P('3c4c54')[0], 150
        return f
    kind_cap = prof['cap']
    def pf(t):
        for t0, t1, rf, kind in prof['tbl']:
            if t0 <= t < t1:
                return (rf(t) if callable(rf) else rf), mk(kind), (GLS_O if kind != 'cap' else ST_O)
        return None
    lathe(cv, (8.0, 15.4), (8.0, .6), pf)
    for y in range(N):
        x = 5 if cv.f[y, 5] else None
    cv.outline()
    return cv

def adaptation():
    def cap(b, t, s, r, x, y):
        if t < .91: return TEAL5[int(np.clip((b+.15)*5, 0, 4))]
        return ST5[int(np.clip((b+.15)*5, 0, 4))]
    tbl = [(0, .56, lambda t: 5.6*math.sqrt(max(0, 1-((t-.30)/.30)**4)), 'g'),
           (.56, .66, lambda t: 5.6*max(.38, 1-(t-.56)/.10*.62), 'g'),
           (.66, .78, 2.2, 'g'),
           (.78, .83, 2.7, 'g'),
           (.83, 1.0, 2.6, 'cap')]
    def bub(x, y):
        if (x, y) in ((6, 9), (9, 11), (7, 12), (10, 8)): return TEAL5[4]
        return None
    cv = vial('adaptation_serum', {'tbl': tbl, 'cap': cap}, TEAL5, 7, bub)
    for y in (8, 9, 10, 11): 
        if cv.f[y, 4]: cv.c[y, 4] = [*GLS5[4], 255]
    cv.save('adaptation_serum')

def regen():
    def cap(b, t, s, r, x, y):
        if t < .90: return RED5[int(np.clip((b+.15)*5, 0, 4))]
        return BONE5[int(np.clip((b+.15)*5, 0, 4))]
    tbl = [(0, .10, lambda t: 3.6*math.sqrt(max(0, 1-((.10-t)/.10)**2)), 'g'),
           (.10, .62, 3.6, 'g'),
           (.62, .70, lambda t: 3.6 - (t-.62)/.08*1.6, 'g'),
           (.70, .80, 2.0, 'g'),
           (.80, .84, 2.6, 'g'),
           (.84, 1.0, 2.6, 'cap')]
    def tissue(x, y):
        d = math.hypot((x+.5-8.2)/1.6, (y+.5-9.5)/2.2)
        if d < 1: return FLESH[6] if d < .45 else FLESH[5]
        if (x, y) in ((7, 13), (9, 6)): return BLOOD5[4]
        return None
    cv = vial('tissue_regeneration_serum', {'tbl': tbl, 'cap': cap}, BLOOD5, 5, tissue)
    for y in range(6, 12):
        if cv.f[y, 5]: cv.c[y, 5] = [*GLS5[4], 255]
    cv.save('tissue_regeneration_serum')

def scanner():
    cv = Canvas()
    O = OL_O
    for y in range(0, 4): cv.put(11, y, ST5[3] if y else RED5[3], ST_O)
    cv.put(11, 0, RED5[4], RED_O)
    for y in range(3, 15):
        for x in range(3, 13):
            if (x in (3, 12)) and (y in (3, 14)): continue
            v = OL5[2]
            if x == 3 or y == 3: v = OL5[4]
            elif x == 12 or y == 14: v = OL5[0]
            elif x == 4 or y == 4: v = OL5[3]
            cv.put(x, y, v, O)
    SC = P('061a0c','0c3a1a','1a7a34','40d060','b0ffb8')
    for y in range(5, 11):
        for x in range(5, 11):
            cv.put(x, y, SC[0] if (x == 5 or y == 5) else SC[1], O)
    c = 8.0
    for y in range(5, 11):
        for x in range(5, 11):
            dx, dy = x+.5-c, y+.5-c; d = math.hypot(dx, dy)
            if 2.1 < d < 2.9: cv.put(x, y, SC[2])
            a = math.atan2(dy, dx)
            if d < 3 and abs(math.atan2(math.sin(a+.8), math.cos(a+.8))) < .35: cv.put(x, y, SC[3])
    cv.put(9, 6, SC[4]); cv.put(7, 8, SC[3]); cv.put(6, 9, RED5[4])
    cv.put(5, 12, RED5[3]); cv.put(6, 12, RED5[2]); cv.put(9, 12, YEL5[3]); cv.put(10, 12, YEL5[2])
    cv.put(5, 13, OL5[1]); cv.put(6, 13, OL5[1]); cv.put(9, 13, OL5[1]); cv.put(10, 13, OL5[1])
    cv.outline(); cv.save('field_scanner')

def circuit():
    cv = Canvas()
    PCB = P('0a2a18','124028','1c5a38','2a7a4c','46a066'); PO = P('041008')[0]
    GOLD = BRASS
    for y in range(1, 15):
        for x in range(1, 15):
            v = PCB[2]
            if x == 1 or y == 1: v = PCB[3]
            if x == 14 or y == 14: v = PCB[0]
            cv.put(x, y, v, PO)
    for x in range(2, 14, 2):
        cv.put(x, 14, GOLD[3], BRASS_O); cv.put(x, 13, GOLD[2], BRASS_O)
    for y in range(4, 10):
        for x in range(4, 10):
            cv.put(x, y, P('15161a')[0] if not (x == 4 or y == 4) else P('2c2e34')[0])
    for k in range(5, 10, 2):
        cv.put(3, k, ST5[4]); cv.put(10, k, ST5[3]); cv.put(k, 3, ST5[4]); cv.put(k, 10, ST5[3])
    cv.put(5, 5, P('5c606a')[0])
    for y in range(11, 13): cv.put(6, y, GOLD[1]); cv.put(8, y, GOLD[1])
    for x in range(10, 13): cv.put(x, 6, GOLD[1])
    cv.put(12, 3, RED5[4]); cv.put(12, 4, RED5[2]); cv.put(12, 9, ST5[4]); cv.put(12, 10, ST5[2])
    for x in (2, 3): cv.put(x, 11, GOLD[1])
    cv.outline(); cv.save('military_circuit')

def transit():
    cv = Canvas(); c = 8.0
    for k, (sx, sy) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        for i in range(12):
            t = i/11; d = 4.0 + t*3.6; x = int(c + sx*d*.707); y = int(c + sy*d*.707)
            if t < .7 or i % 1 == 0:
                cv.put(x, y, BONE5[3] if sx + sy < 0 else BONE5[1], BONE_O)
                if t < .45: cv.put(x + (1 if sx < 0 else -1), y, BONE5[2], BONE_O)
    for y in range(N):
        for x in range(N):
            dx, dy = x+.5-c, y+.5-c; d = math.hypot(dx, dy)
            if 3.0 < d <= 5.3:
                b = (-dx - dy)/d*.5 + .5
                v = CAR5[int(np.clip(b*4.4, 0, 4))]
                if (x*7 + y*3) % 11 == 0: v = CRIM[4]
                cv.put(x, y, v, CAR_O)
            elif d <= 3.0:
                v = GLOW[int(np.clip(5 - d*1.4, 1, 5))]
                if abs(dx) < .6 and abs(dy) < 2.2: v = P('1a0204')[0]
                cv.put(x, y, v, GLOW[0]*.4)
    cv.put(6, 6, GLOW[5])
    cv.outline(); cv.save('city_transit')

def tissue():
    cv = Canvas(); cx, cy = 8.0, 8.4
    from paint_blocks import vnoise
    nz = vnoise(16, 4, 5)
    for y in range(N):
        for x in range(N):
            dx, dy = x+.5-cx, y+.5-cy; a = math.atan2(dy, dx); d = math.hypot(dx, dy)
            R = 5.6 + math.sin(a*3 + 1)*.9 + math.sin(a*5 + 2)*.5
            if d > R: continue
            u = d/R; nz3 = math.sqrt(max(0, 1-u*u))
            b = -dx/R*.55 - dy/R*.55 + nz3*.5
            col = FLESH[int(np.clip(2 + b*4.5, 1, 7))]
            if nz[y, x] > .62: col = CAR5[int(np.clip(1 + b*3.5, 0, 4))]
            if .55 < nz[y, x] <= .62: col = GLOW[2]
            cv.put(x, y, col, FLESH[0])
    cv.put(8, 14, FLESH[3], FLESH[0]); cv.put(8, 15, GLOW[1], FLESH[0])
    cv.put(6, 5, FLESH[7])
    cv.outline(); cv.save('living_hive_tissue')

rifle_round(); mg_round()
grenade_like('grenade_round', OL5, YEL5, OL5)
grenade_like('infection_round', CAR5, P('5a0a0a','9a1010','d42418','ff4a2a','ff8a5a'), CRIM[[1,2,3,4,6]], veins=True)
grenade_like('thermobaric_round', TAN5, ORA5, P('2a2a2a','3c3c3c','545454','6c6c6c','8a8a8a'), band2=ORA5)
rocket_like('rocket_round'); rocket_like('missile_round', True)
syringe(); adaptation(); regen(); scanner(); circuit(); transit(); tissue()
print('items done')
