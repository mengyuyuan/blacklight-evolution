"""Preview actual Java equipment cuboids and generated NBT layouts; no game extracts.

Writes an offline HTML/SVG viewer. Pillow, if available, also writes a PNG contact
sheet for visual review. Geometry and approximate material colours are illustrative;
Minecraft UV sampling, lighting and animation remain subject to client playtesting.
"""
from pathlib import Path
import gzip
import base64
import io
import json
import math
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/previews'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = (ROOT / 'src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java').read_text(encoding='utf8')
PATTERN = re.compile(r'box\(root,"([^"]+)","([^"]+)",([^;]+)\);')
PIVOTS = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0),
          'left_arm': (5, 2, 0), 'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
BASE = {'head': (-4, -8, -4, 8, 8, 8), 'body': (-4, 0, -2, 8, 12, 4),
        'right_arm': (-3, -2, -2, 4, 12, 4), 'left_arm': (-1, -2, -2, 4, 12, 4),
        'right_leg': (-2, 0, -2, 4, 12, 4), 'left_leg': (-2, 0, -2, 4, 12, 4)}


def extract_block(text, marker):
    start = text.index(marker)
    brace = text.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[brace + 1:end - 1], text[:start] + text[end:]


def equipment(heavy, military=False):
    extra, common = extract_block(SOURCE, 'if(heavy){')
    army, common = extract_block(common, 'if(military){')
    army_heavy, army = extract_block(army, 'if(heavy){')
    parts = {(p, 'base'): (BASE[p], '#3b4648') for p in BASE}
    sections = [common] + ([extra] if heavy else []) + ([army] if military else []) + ([army_heavy] if military and heavy else [])
    for section in sections:
        for parent, name, numbers in PATTERN.findall(section):
            values = [float(v.strip().rstrip('Ff')) for v in numbers.split(',')]
            if len(values) != 8:
                raise ValueError(f'Cannot preview {parent}/{name}: {numbers}')
            u, v, *box = values
            colour = '#728879' if v == 40 else '#222c31' if v == 32 else '#4a5149' if v == 48 else '#252c30'
            parts[parent, name] = (box, colour)
    if military:
        for key in (('head', 'left_filter'), ('head', 'right_filter')):
            parts.pop(key)
    if len(parts) < 30:
        raise ValueError('Model source changed: update the preview reader')
    return parts


def turn(point, x=0, y=0, z=0):
    a, b, c = point
    b, c = b * math.cos(x) - c * math.sin(x), b * math.sin(x) + c * math.cos(x)
    a, c = a * math.cos(y) + c * math.sin(y), -a * math.sin(y) + c * math.cos(y)
    a, b = a * math.cos(z) - b * math.sin(z), a * math.sin(z) + b * math.cos(z)
    return a, b, c


def polygons(heavy, pose, rear=False, military=False):
    faces = []
    yaw = math.radians(150 if rear else 30)
    # HumanoidModel part pivots and the simple zero-look poses from setupAnim.
    angles = {'right_arm': (-.25, 0, 0), 'left_arm': (0, 0, 0)}
    if pose != 'idle':
        reload = pose == 'reload'
        angles = {'right_arm': (-1.35 + .45 * reload, -.12, 0),
                  'left_arm': (-1.25 - .4 * reload, .85, .06 + .12 * reload)}
    for (parent, name), (box, colour) in equipment(heavy, military).items():
        x, y, z, w, h, d = box
        points = [(x + i * w, y + j * h, z + k * d) for i, j, k in
                  ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))]
        transformed = []
        for p in points:
            p = turn(p, *angles.get(parent, (0, 0, 0)))
            p = tuple(p[i] + PIVOTS[parent][i] for i in range(3))
            transformed.append(turn(p, y=yaw))
        for indices, shade, normal in (((0, 1, 2, 3), 1.12, (0, 0, -1)), ((4, 7, 6, 5), .8, (0, 0, 1)),
                                      ((0, 4, 5, 1), 1.25, (0, -1, 0)), ((3, 2, 6, 7), .65, (0, 1, 0)),
                                      ((0, 3, 7, 4), .9, (-1, 0, 0)), ((1, 5, 6, 2), 1.0, (1, 0, 0))):
            normal = turn(turn(normal, *angles.get(parent, (0, 0, 0))), y=yaw)
            if normal[2] + .24 * normal[1] >= 0:
                continue
            vertices = [transformed[i] for i in indices]
            xy = [(a * 7, (b - c * .24) * 7) for a, b, c in vertices]
            rgb = [min(255, round(int(colour[i:i + 2], 16) * shade)) for i in (1, 3, 5)]
            if military:
                rgb[0] = round(rgb[0] * .82)
                rgb[2] = round(rgb[2] * .73)
            depths = [p[2] + p[1] * .24 for p in vertices]
            faces.append((sum(depths) / 4, xy, tuple(rgb), depths))
    return sorted(faces, reverse=True, key=lambda f: f[0])


def model_svg(heavy, pose, rear=False, military=False):
    shapes = ['<ellipse cx="0" cy="173" rx="58" ry="12" fill="#d4d8d2"/>']
    for _, points, colour, _ in polygons(heavy, pose, rear, military):
        xy = ' '.join(f'{x:.1f},{y:.1f}' for x, y in points)
        shapes.append(f'<polygon points="{xy}" fill="rgb{colour}" stroke="#202b2c" stroke-width=".45"/>')
    return '<svg viewBox="-130 -90 260 285" role="img" aria-label="Source equipment geometry">' + ''.join(shapes) + '</svg>'


def model_png(heavy, pose, rear=False, military=False):
    """Orthographic triangle depth buffer: intersecting equipment must occlude correctly."""
    from PIL import Image, ImageDraw
    width, height = 340, 560
    image = Image.new('RGB', (width, height), '#edf0e9')
    ImageDraw.Draw(image).ellipse((85, 462, 255, 484), fill='#d5dbd1')
    pixels = image.load()
    depth_buffer = [float('inf')] * (width * height)
    for _, points, colour, depths in polygons(heavy, pose, rear, military):
        vertices = [(170 + x * 1.7, 180 + y * 1.7, depth) for (x, y), depth in zip(points, depths)]
        for indices in ((0, 1, 2), (0, 2, 3)):
            a, b, c = [vertices[i] for i in indices]
            area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if abs(area) < .0001:
                continue
            for y in range(max(0, math.floor(min(a[1], b[1], c[1]))), min(height, math.ceil(max(a[1], b[1], c[1])))):
                for x in range(max(0, math.floor(min(a[0], b[0], c[0]))), min(width, math.ceil(max(a[0], b[0], c[0])))):
                    px, py = x + .5, y + .5
                    wa = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area
                    wb = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area
                    wc = 1 - wa - wb
                    if min(wa, wb, wc) < -.0001:
                        continue
                    depth = wa * a[2] + wb * b[2] + wc * c[2]
                    offset = y * width + x
                    if depth < depth_buffer[offset]:
                        depth_buffer[offset] = depth
                        pixels[x, y] = colour
    return image


def model_frame(heavy, pose, rear, military=False):
    try:
        output = io.BytesIO()
        model_png(heavy, pose, rear, military).save(output, format='PNG')
        return '<img alt="Source equipment geometry" style="width:100%;height:430px;object-fit:contain" src="data:image/png;base64,' + base64.b64encode(output.getvalue()).decode('ascii') + '">'
    except ImportError:
        return model_svg(heavy, pose, rear, military)


class NbtReader:
    def __init__(self, path):
        self.stream = io.BytesIO(gzip.decompress(path.read_bytes()))

    def number(self, fmt):
        return struct.unpack('>' + fmt, self.stream.read(struct.calcsize('>' + fmt)))[0]

    def text(self):
        return self.stream.read(self.number('H')).decode('utf8')

    def value(self, kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
        if kind == 8:
            return self.text()
        if kind == 9:
            element, count = self.number('B'), self.number('i')
            return [self.value(element) for _ in range(count)]
        if kind == 10:
            result = {}
            while (element := self.number('B')) != 0:
                name = self.text()
                result[name] = self.value(element)
            return result
        raise ValueError(f'Unsupported NBT tag {kind}')

    def read(self):
        if self.number('B') != 10:
            raise ValueError('Structure is not a compound')
        self.text()
        return self.value(10)


def layout_svg(name, roof):
    directory = 'structures' if 'minecraft_version=1.20.1' in (ROOT / 'gradle.properties').read_text() else 'structure'
    path = ROOT / f'src/main/resources/data/blacklight/{directory}/{name}.nbt'
    data = NbtReader(path).read()
    width = data['size'][0]
    cells = {}
    for block in data['blocks']:
        x, y, z = block['pos']
        state = data['palette'][block['state']]['Name'].split(':')[1]
        if state == 'air' or (not roof and y > 2):
            continue
        if (x, z) not in cells or cells[x, z][0] <= y:
            cells[x, z] = (y, state, block.get('nbt', {}))
    shapes = []
    colours = {'gray_concrete': '#727b7a', 'black_concrete': '#303b3e', 'iron_bars': '#a2aaa4',
               'yellow_concrete': '#d5b560', 'red_concrete': '#a44740', 'sandstone': '#bdb697',
               'gray_bed': '#79948b', 'chest': '#ba8d4c', 'sea_lantern': '#c7e9c4', 'ladder': '#977f56'}
    for (x, z), (_, state, nbt) in cells.items():
        colour = colours.get(state, '#5c6866' if 'blackstone' in state else '#d5dbd4')
        shapes.append(f'<rect x="{x * 12}" y="{z * 12}" width="12" height="12" fill="{colour}" stroke="#f3f4ed" stroke-width=".35"/>')
        if state == 'chest':
            label = 'S' if nbt.get('LootTable', '').endswith('supplies') else 'T'
            shapes.append(f'<text x="{x * 12 + 6}" y="{z * 12 + 9}" text-anchor="middle" font-size="9">{label}</text>')
    for entity in data['entities']:
        x, y, z = entity['pos']
        heavy = entity['nbt']['id'].endswith('heavy')
        shapes.append(f'<circle cx="{x * 12}" cy="{z * 12}" r="4.5" fill="{"#a62e34" if heavy else "#245b76"}" stroke="white"/>')
    return f'<svg viewBox="-6 -6 {width * 12 + 12} {width * 12 + 12}" role="img" aria-label="Actual structure layout">' + ''.join(shapes) + '</svg>'


frames = {f'{faction}|{kind}|{pose}|{view}': model_frame(kind == 'heavy', pose, view == 'back', faction == 'military')
          for faction in ('blackwatch', 'military') for kind in ('regular', 'heavy') for pose in ('idle', 'aim', 'reload') for view in ('front', 'back')}
layouts = {f'{kind}|{view}': layout_svg(name, view == 'roof')
           for kind,name in (('outpost','blackwatch_outpost'),('checkpoint','blackwatch_checkpoint'),('camp','military_camp'),('army_checkpoint','military_checkpoint')) for view in ('floor', 'roof')}
document = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width">
<title>黑色守望 · 几何与设施预览</title><style>
body{margin:0;background:#f0f1eb;color:#263631;font:16px/1.7 system-ui,sans-serif}main{max-width:1120px;margin:auto;padding:36px 24px}
h1{font-size:32px;line-height:1.3}h2{margin:36px 0 12px}p{max-width:850px}.muted{color:#5d6c63}.pair{display:grid;grid-template-columns:1fr 1fr;gap:24px}
article{background:#fafbf6;border-top:3px solid #334b42;padding:16px 24px}article h3{margin:0;font-size:21px}svg{display:block;width:100%;max-height:430px;margin:12px auto}
select{font:inherit;padding:6px 12px;border:1px solid #87978a;background:#fafbf6;margin:4px 12px 8px 0}label{display:inline-block}code{font-size:13px}footer{margin-top:32px;border-top:1px solid #b8c2b7;padding-top:16px}
@media(max-width:650px){.pair{grid-template-columns:1fr}main{padding:20px}}
</style><main><p class="muted">BLACKWATCH / DEVELOPMENT PREVIEW · 2026-10-23</p>
<h1>军方与黑色守望 · 装备轮廓与驻防设施</h1>
<p>从实际 Java 立方体和已生成 NBT 读取的几何示意。颜色为近似材质分区，游戏中的 UV、照明与动作观感仍需客户端实测。</p>
<h2>01 / 普通兵与重装兵</h2><label>阵营 <select id="faction"><option value="blackwatch">黑色守望</option><option value="military">常规军方</option></select></label><label>姿态 <select id="pose"><option value="idle">待机持枪</option><option value="aim">简单瞄准</option><option value="reload">简单换弹</option></select></label>
<label>视角 <select id="view"><option value="front">前侧</option><option value="back">后侧 / 背包</option></select></label>
<div class="pair"><article><h3>普通步兵</h3><div id="regular"></div><p>双滤罐面具、护目镜、胸挂、护膝、通信背包与步枪。</p></article>
<article><h3>重装步兵</h3><div id="heavy"></div><p>厚胸甲、加宽肩甲和膝甲、供弹背包与加粗枪械轮廓。军方使用较轻护甲与灰绿色调；默认列兵胸章无额外图形。</p></article></div>
<h2>02 / 两方自然生成设施</h2><label>布局切片 <select id="slice"><option value="floor">地面与家具</option><option value="roof">含屋顶与塔台</option></select></label>
<p class="muted">蓝点：普通兵；红点：重装兵；T：科技箱；S：补给箱。地面切片保留 y≤2 方块，塔台士兵仍按实际位置标出。</p>
<div class="pair"><article><h3>哨站 · 25×25</h3><div id="outpost"></div><p>4 普通兵 + 1 重装兵；营房、床铺、瞭望塔、2 科技箱、1 补给箱。</p></article>
<article><h3>检查站 · 17×17</h3><div id="checkpoint"></div><p>2 普通兵 + 1 重装兵；通行车道、掩体、警戒岗、科技箱与补给箱。</p></article></div>
<div class="pair"><article><h3>军方营地 · 21×21</h3><div id="camp"></div><p>7 普通兵 + 1 重装兵；营房、指挥岗、塔台、科技箱与补给箱。</p></article><article><h3>军方检查站 · 17×17</h3><div id="army_checkpoint"></div><p>3 普通兵 + 1 重装兵；独立驻军编组及军衔。</p></article></div>
<footer><p>黑色守望两模板复用 <code>blacklight:blackwatch_outpost</code> 结构池，权重 2:1。原生成间距、群系、电路与扫描仪配方保留。军方另用40/16间距，避让8区块内黑色守望候选点。四模板驻军按位置生成独立单位，带1名中士和1名下士重装兵。已有区块不会自动改建。</p>
<p class="muted">此页可离线打开。不是 Minecraft 实机截图；不代表客户端或广域地形生成验收。</p></footer></main>
<script>const frames=__FRAMES__,layouts=__LAYOUTS__;
function update(){for(const k of ['regular','heavy'])document.getElementById(k).innerHTML=frames[faction.value+'|'+k+'|'+pose.value+'|'+view.value];for(const k of ['outpost','checkpoint','camp','army_checkpoint'])document.getElementById(k).innerHTML=layouts[k+'|'+slice.value]}
for(const id of ['faction','pose','view','slice'])document.getElementById(id).addEventListener('change',update);update();</script></html>'''
(OUT / 'blackwatch-model-facilities.html').write_text(document.replace('__FRAMES__', json.dumps(frames)).replace('__LAYOUTS__', json.dumps(layouts)), encoding='utf8')

try:
    from PIL import Image, ImageDraw, ImageFont
    image = Image.new('RGB', (1400, 1500), '#edf0e9')
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 26) if Path('C:/Windows/Fonts/arial.ttf').exists() else ImageFont.load_default(size=26)
    small = ImageFont.load_default(size=17)
    draw.text((40, 24), 'MILITARY + BLACKWATCH / SOURCE GEOMETRY', fill='#24392f', font=font)
    for row, military in enumerate((False, True)):
        for column, (heavy, pose) in enumerate(((False, 'idle'), (True, 'idle'), (False, 'aim'), (True, 'aim'))):
            cx = column * 340 + 180
            label = ('MILITARY ' if military else 'BLACKWATCH ') + ('HEAVY' if heavy else 'REGULAR') + ' / ' + pose.upper()
            draw.text((cx - 145, 100 + row * 650), label, fill='#374b3d', font=small)
            image.paste(model_png(heavy, pose, military=military), (column * 340 + 10, 150 + row * 650))
    draw.text((40, 1460), 'Approximate colours. Actual UV, lighting and animation still require Minecraft client review.', fill='#556b59', font=small)
    image.save(OUT / 'blackwatch-equipment.png')
except ImportError:
    pass
print(OUT / 'blackwatch-model-facilities.html')
