"""Attach the approved species geometry to native bodies without exporting vanilla skins.

Keeps cut structural faces separate from growths. At runtime structural faces
receive the actual host cube UVs, while original authored growths keep their colors.
"""
import argparse, hashlib, json, math
from collections import defaultdict
from pathlib import Path
from import_infected_models import normalized_bone, primary_parts, coordinates, bounds, palette

INFECTION_MATERIALS={"chitin","facet","tendon","tissue","edge","scar","recess"}

def mesh(parts, colors, growth=False):
    vertices, triangles = [], []
    for part in parts:
        start = len(vertices) // 3
        for v in part['vertices']: vertices.extend(coordinates(v))
        for polygon, material in zip(part['faces'], part['materials']):
            if growth and material not in INFECTION_MATERIALS: continue
            polygon = list(reversed(polygon))
            for i in range(1, len(polygon)-1):
                triangles.extend([start+polygon[0], start+polygon[i], start+polygon[i+1], colors[material]])
    return {'vertices': vertices, 'triangles': triangles}

def export(source, output):
    output.mkdir(parents=True, exist_ok=True)
    colors = palette(source/'materials.mtl')
    manifest = {'format': 1, 'species': {}}
    for species in sorted(json.loads((source/'coverage.json').read_text(encoding='utf8'))):
        path = source/(species+'-geometry.json')
        parts = json.loads(path.read_text(encoding='utf8'))
        groups = defaultdict(list)
        for part in parts:
            if part['faces']: groups[normalized_bone(part)].append(part)
        bones = []
        for name, items in groups.items():
            base = [p for p in primary_parts(items) if p['name'].startswith('Base /')]
            if base:
                base=[max(base,key=lambda p: math.prod(b-a for a,b in zip(bounds(p['vertices'])['min'],bounds(p['vertices'])['max'])))]
            graft = [p for p in items if not p['name'].startswith('Base /')]
            # Original ears, wool, snout, nose and patches remain the native model's job.
            if not graft and not base: continue
            fleece=[p for p in items if p['name'].startswith('Fleece /')]
            reference = base or items
            bones.append({'name': name,
                'reference': bounds(coordinates(v) for p in reference for v in p['vertices']),
                'skin': mesh(base, colors), 'growth': mesh(graft, colors, True),
                'furSkin':mesh(fleece,colors),
                'furReference':bounds(coordinates(v) for p in fleece for v in p['vertices']) if fleece else None,
                'parts': [p['name'] for p in graft]})
        data = {'format': 1, 'species': 'minecraft:'+species, 'bones': bones}
        (output/(species+'.json')).write_text(json.dumps(data,separators=(',',':'))+'\n',encoding='utf8')
        manifest['species'][species] = {'sourceSha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'bones': len(bones), 'growthParts': sum(len(b['parts']) for b in bones)}
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf8')
    print('Exported',len(manifest['species']),'approved species grafts')

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,default=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight/infection_parts')
    args=p.parse_args();export(args.source,args.output)
