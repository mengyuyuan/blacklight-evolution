# -*- coding: utf-8 -*-
# 手绘 16x16 物品贴图：逐格网格 + 少量对角线辅助
import os, math
from PIL import Image
OUT = r'E:\jtmp\tex_hand\item'; os.makedirs(OUT, exist_ok=True)
P = {
 '.':None,
 # 玻璃
 'W':(238,246,252),'g':(178,208,234),'G':(94,138,188),'q':(56,84,128),
 # 钢
 'S':(212,218,224),'s':(156,164,174),'t':(102,108,120),'T':(54,56,66),
 # 黑光外壳
 'K':(20,13,24),'k':(44,30,48),'p':(78,54,80),'P':(122,90,120),
 # 血肉
 'R':(255,96,88),'r':(220,36,44),'m':(154,20,36),'M':(92,12,30),'n':(50,6,22),
 # 黄铜
 'Y':(255,234,150),'y':(232,186,76),'b':(178,126,42),'B':(112,72,26),'O':(62,38,14),
 # 铜弹头
 'C':(244,176,124),'c':(198,112,64),'u':(128,62,36),
 # 橄榄绿
 'Q':(200,208,140),'L':(160,170,100),'l':(118,128,68),'o':(82,90,46),'d':(42,48,24),
 # 青绿药剂
 'A':(150,252,224),'a':(58,204,172),'e':(30,130,122),'E':(16,72,74),
 # 橙
 'F':(255,206,96),'f':(240,132,40),'h':(170,70,24),
 # 屏幕绿
 'X':(176,255,150),'x':(72,204,84),'v':(26,98,42),'V':(10,40,20),
 # 电路板
 'Z':(72,146,84),'z':(42,102,58),'w':(26,66,42),'j':(12,34,22),
 'J':(250,212,62),'I':(248,248,244),'N':(14,14,18),
}
def grid(rows):
    assert len(rows) == 16, len(rows)
    im = Image.new('RGBA', (16, 16), (0,0,0,0)); px = im.load()
    for y, r in enumerate(rows):
        assert len(r) == 16, (y, r, len(r))
        for x, ch in enumerate(r):
            c = P[ch]
            if c: px[x, y] = c + (255,)
    return im
ITEMS = {}

ITEMS['tissue_regeneration_serum'] = [
 "................",
 "......TTTT......",
 ".....TSSstT.....",
 ".....TtttTT.....",
 "......GWgG......",
 "......GWmG......",
 ".....GWrrmG.....",
 "....GWrRrrmG....",
 "...GWrRRrrmMG...",
 "...GgrRrrmmMG...",
 "...GrrrrmmmMG...",
 "...GrrmmmmMMG...",
 "...GmmmmMMMnG...",
 "....GMMMMMnG....",
 ".....GGGGGG.....",
 "................"]

ITEMS['adaptation_serum'] = [
 "................",
 "......TTTT......",
 ".....TSSstT.....",
 ".....TtttTT.....",
 "......GWgG......",
 "....GGGWgGGG....",
 "....GWAaaaeG....",
 "....GWaaaeeG....",
 "....GIIIIIIG....",
 "....GIEaEaIG....",
 "....GgaaaeEG....",
 "....GaaaeeEG....",
 "....GaeeeEEG....",
 "....GeeeEEEG....",
 ".....GGGGGG.....",
 "................"]

def cart(top):  # 一发步枪弹 3 宽
    return [".C.", "Ccu", "ccu", "YyB", "Yyb", "Yyb", "Yyb", "Yyb", "Yyb", "ybB", "BBO"], top
def rifle():
    rows = [list("." * 16) for _ in range(16)]
    for x0, top in ((2, 2), (6, 1), (10, 2)):
        c, t = cart(top)
        for i, r in enumerate(c):
            for j, ch in enumerate(r):
                if ch != '.': rows[t + i][x0 + j] = ch
    clip = ["..TSSSSSSSSSsT..", "..TsssssssssttT.", "...TTTTTTTTTTT.."]
    for i, r in enumerate(clip):
        for x, ch in enumerate(r):
            if ch != '.': rows[11 + i][x] = ch
    return ["".join(r) for r in rows]
ITEMS['rifle_round'] = rifle()

ITEMS['machine_gun_round'] = [
 "................",
 "................",
 "......dddd......",
 "......d..d......",
 "..ddddddddddd...",
 "..dQLLLLLLLld...",
 "..dLllllllood...",
 "..dddddddddddtyC",
 "..dLlllllllod...",
 "..dlJJJJJllodtyC",
 "..dlllllllood...",
 "..dLllllllloddyC",
 "..dlllllllood.t.",
 "..doooooooood...",
 "...ddddddddd....",
 "................"]

def grenade(head, band, case):
    h1, h2, h3, h4, ho = head  # 高光 亮 中 暗 描边
    b1, b2 = band
    c1, c2, c3, c4, co = case
    return [
 "................",
 "................",
 "......%s%s%s%s......" % (ho, ho, ho, ho),
 ".....%s%s%s%s%s%s....." % (ho, h1, h2, h2, h3, ho),
 "....%s%s%s%s%s%s%s%s...." % (ho, h2, h1, h2, h2, h3, h4, ho),
 "....%s%s%s%s%s%s%s%s...." % (ho, h2, h2, h2, h3, h3, h4, ho),
 "....%s%s%s%s%s%s%s%s...." % (ho, h2, h2, h3, h3, h4, h4, ho),
 "....%s%s%s%s%s%s%s%s...." % (ho, h3, h3, h3, h4, h4, h4, ho),
 "....%s%s%s%s%s%s%s%s...." % (co, b1, b1, b1, b2, b2, b2, co),
 "....%s%s%s%s%s%s%s%s...." % (co, c1, c2, c2, c3, c3, c4, co),
 "....%s%s%s%s%s%s%s%s...." % (co, c1, c2, c2, c3, c3, c4, co),
 "....%s%s%s%s%s%s%s%s...." % (co, c1, c2, c3, c3, c3, c4, co),
 "....%s%s%s%s%s%s%s%s...." % (co, c2, c2, c3, c3, c4, c4, co),
 "...%s%s%s%s%s%s%s%s%s%s..." % (co, c1, c2, c2, c3, c3, c3, c4, c4, co),
 "...%s%s%s%s%s%s%s%s%s%s..." % (co, co, co, co, co, co, co, co, co, co),
 "................"]
ITEMS['grenade_round'] = grenade(("Q","L","l","o","d"), ("J","b"), ("Y","y","b","B","O"))
inf = grenade(("P","p","k","K","N"), ("r","m"), ("s","t","t","T","N"))
inf[4] = inf[4][:6] + "R" + inf[4][7:]
inf[5] = inf[5][:7] + "r" + inf[5][8:]
inf[6] = inf[6][:5] + "r" + inf[6][6:9] + "r" + inf[6][10:]
inf[7] = inf[7][:8] + "m" + inf[7][9:]
ITEMS['infection_round'] = inf
th = grenade(("S","s","t","t","T"), ("f","h"), ("Y","y","b","B","O"))
th[5] = "....T" + "FFff" + "hhT...."[:0] + "fh" + "T...."
th[6] = "....T" + "JJJJ" + "bb" + "T...."
ITEMS['thermobaric_round'] = th

# 对角线物品：沿 (0,15)->(15,0)，v=沿轴 0..15，u=法向（负=左上受光）
def diag(prof):
    rows = [list("." * 16) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            u = x + y - 15; v = (x - y + 15) / 2
            r = prof(v, u)
            if r: rows[y][x] = r
    return ["".join(r) for r in rows]
def shade(u, w, ramp):  # ramp: 描边暗, 高光, 中, 暗, 描边
    if w == 0: return ramp[2]
    if u == -w: return ramp[1] if w == 1 else ramp[0]
    if u == w: return ramp[4]
    if u == -w + 1 and w > 1: return ramp[1]
    if u == w - 1 and w > 1: return ramp[3]
    return ramp[2]


def band(u, w, ramp):  # ramp: 亮侧描边, 高光, 中, 暗, 暗侧描边
    if u <= -w: return ramp[0]
    if u >= w: return ramp[4]
    if u == -w + 1: return ramp[1]
    if u == w - 1: return ramp[3]
    return ramp[2]
def syringe(v, u):
    a = abs(u)
    if v <= 0.5 and a <= 3: return band(u, 3, "TSstT")            # 推杆按钮
    if 0.5 < v <= 2.5 and a <= 1: return band(u, 1, "tSsTT")       # 推杆
    if 2.5 < v <= 3.5 and a <= 4: return band(u, 4, "TSstT")       # 指托
    if 3.5 < v <= 11.0 and a <= 2:                                 # 玻璃针筒
        if u == -2: return "G"
        if u == 2: return "q"
        if v <= 5.0: return "s" if u < 1 else "t"                  # 胶塞
        if u == -1: return "W" if 9 <= v <= 10.5 else "R"
        if u == 1: return "M" if v < 9.5 else "m"
        return "n" if v in (6.5, 8.0) else "r"
    if 11.0 < v <= 12.0 and a <= 1: return band(u, 1, "tSstT")     # 针座
    if 12.0 < v <= 15.0 and a == 0: return "S" if v >= 14 else "s"  # 针
    return None
ITEMS['supreme_parasite_syringe'] = diag(syringe)

def rocket(v, u):
    a = abs(u)
    if v <= 1.0 and a in (3, 4): return "o" if u < 0 else "d"       # 尾翼
    if v <= 3.5 and a <= 1: return band(u, 1, "tSttT")              # 尾管
    if 1.0 < v <= 3.0 and a in (3, 4): return "o" if u < 0 else "d"
    if 3.5 < v <= 8.0 and a <= 1: return band(u, 1, "oLlod")        # 杆身
    if 8.0 < v <= 13.0 and a <= 2:                                  # 弹头
        if 8.5 <= v <= 9.0: return "J" if u < 2 else "b"
        return band(u, 2, "oQLod")
    if 13.0 < v <= 14.5 and a <= 1: return band(u, 1, "oQlod")
    if 14.5 < v <= 15 and a == 0: return "t"
    return None
ITEMS['rocket_round'] = diag(rocket)

def missile(v, u):
    a = abs(u)
    if v <= 2.5 and 2 < a <= 4: return "t" if u < 0 else "T"       # 尾翼
    if v <= 0.5 and a <= 2: return band(u, 2, "TttTN")              # 喷口
    if 0.5 < v <= 12.0 and a <= 2:
        if 4.5 <= v <= 5.5: return "r" if u < 1 else "m"            # 红色识别带
        return band(u, 2, "sISsT")
    if 7.5 <= v <= 9.0 and 2 < a <= 3: return "s" if u < 0 else "T" # 弹翼
    if 12.0 < v <= 13.5 and a <= 1: return band(u, 1, "TsTTN")       # 导引头
    if 13.5 < v <= 15 and a == 0: return "T"
    return None
ITEMS['missile_round'] = diag(missile)

ITEMS['machine_gun_round'] = [
 "................",
 "...C..C..C..C...",
 "..Ccu.Cu.Cu.Ccu.",
 "..YyB.yB.yB.YyB.",
 "..YyBTyBTyBTYyB.",
 "..ybBtbBtbBtybB.",
 "..ddddddddddddd.",
 "..dQLLLLLLLLLod.",
 "..dLlllllllllod.",
 "..ddddddddddddd.",
 "..dLlllllllllod.",
 "..dlJJJJJJllood.",
 "..dlllllllllood.",
 "..doooooooooood.",
 "...ddddddddddd..",
 "................"]
ITEMS['field_scanner'] = [
 "...........R....",
 "...........t....",
 "...........t....",
 "..dddddddddddd..",
 "..dQLLLLLLLLod..",
 "..dLVVVVVVVVod..",
 "..dLVvvxvvvVod..",
 "..dLVvxXxvRVod..",
 "..dLVxvxvvvVod..",
 "..dLVvvvvvvVod..",
 "..dLVVVVVVVVod..",
 "..dlrlJllttlod..",
 "..dlllllllllod..",
 "..dooooooooood..",
 "...dddddddddd...",
 "................"]

ITEMS['military_circuit'] = [
 "................",
 "..jjjjjjjjjjjj..",
 "..jZZZZZZZZZzj..",
 "..jZyyyZZZRzzj..",
 "..jZysNNNNszzj..",
 "..jzZNStttNZzj..",
 "..jyyNtTTTNyyj..",
 "..jzZNtTTTNZzj..",
 "..jZZsNNNNszzj..",
 "..jZyZZZZyZzzj..",
 "..jzyyyzzyzzwj..",
 "..jzzzzzzzzwwj..",
 "..jyjyjyjyjyjj..",
 "..jbjbjbjbjbjj..",
 "..jjjjjjjjjjjj..",
 "................"]

def orb():
    rows = [list("." * 16) for _ in range(16)]
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy; d = math.hypot(dx, dy)
            if d > 5.9: continue
            lit = (-dx - dy) / (d + 1e-6)
            if d > 4.9: ch = "K"
            elif d > 3.4: ch = "P" if lit > 0.75 and d < 4.6 else ("p" if lit > 0.2 else "k")
            elif d > 2.6: ch = "r"
            else:
                ch = "N" if abs(dx) < 0.6 and abs(dy) < 2.4 else ("R" if d < 1.9 else "m")
            rows[y][x] = ch
    for (x, y) in ((2, 2), (3, 3), (13, 2), (12, 3), (2, 13), (3, 12), (13, 13), (12, 12)):
        rows[y][x] = "K" if x in (2, 13) else "k"
    rows[3][3] = rows[3][12] = "p"
    return ["".join(r) for r in rows]
ITEMS['city_transit'] = orb()

ITEMS['living_hive_tissue'] = [
 "................",
 "................",
 "......nnnn......",
 "....nnmrrmnn....",
 "...nmrRrmmMKK...",
 "..nmrRrmmMKpPK..",
 "..nmrrmmMMKkpK..",
 ".nMmmrmmMMnKKn..",
 ".nmmmmmMMMMMMn..",
 ".nMKKmmmmMMMMn..",
 ".nKpPKmmrmMMn...",
 "..KkpKmrRrMMn...",
 "..nKKmmmrMMnn...",
 "...nnMMMMMnn....",
 ".....nnnnn......",
 "................"]

def runs(spec):
    rows = [list("." * 16) for _ in range(16)]
    for y, (x, s) in spec.items():
        for i, ch in enumerate(s):
            if ch != '.': rows[y][x + i] = ch
    return ["".join(r) for r in rows]
ITEMS['supreme_parasite_syringe'] = runs({
 0: (15, "S"), 1: (14, "S"), 2: (13, "s"),
 3: (11, "TsT"),
 4: (9, "GWWgq"),
 5: (8, "GWRrq"),
 6: (7, "GRrmq"),
 7: (6, "GrnMq"),
 8: (5, "GRrMq"),
 9: (4, "GrmMq"),
 10: (3, "GsstT"),
 11: (1, "TSSSstT"),
 12: (2, "sT"),
 13: (1, "sT"),
 14: (0, "TSsT"),
 15: (0, "TT"),
})
ITEMS['missile_round'] = [
 "................",
 ".......NN.......",
 "......TttN......",
 "......TssN......",
 "......ISsT......",
 "......ISsT......",
 ".....tISsTT.....",
 "......ISsT......",
 "......rrmn......",
 "......ISsT......",
 "......ISsT......",
 ".....tISsTT.....",
 "....tsISsTsT....",
 "...tsTttTTTsT...",
 "...TT.TTTN.TT...",
 "................"]
ITEMS['rocket_round'] = [
 "................",
 ".......dd.......",
 "......dQLd......",
 ".....dQLlod.....",
 "....dQLLlood....",
 "....dLLlllod....",
 "....dJJJJbbd....",
 ".....dllood.....",
 "......dlod......",
 "......tSsT......",
 "......tSsT......",
 ".....otSsTd.....",
 "....oQtSsTod....",
 "...oQl.tT.lod...",
 "...ooo....ddd...",
 "................"]

for k, rows in ITEMS.items():
    grid(rows).save(os.path.join(OUT, k + '.png'))
print('ok', len(ITEMS))
