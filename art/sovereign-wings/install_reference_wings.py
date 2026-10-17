"""Validate the two authored folds and install the matching runtime cuboids."""
from pathlib import Path
import json, math, shutil

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'model-v12'
RES = ROOT.parents[1] / 'src/main/resources/assets/blacklight'
data = json.loads((OUT / 'sovereign_wings.asset.json').read_text(encoding='utf8'))
names = {b['name'] for b in data['bones']}
folds = {}
for variant in ('left_over', 'right_over'):
    validation = json.loads((OUT / f'fold_validation_{variant}.json').read_text())
    assert not validation['unreachable_targets'], variant
    assert all(p['head_vertex_samples'] == 0 for p in validation['head_samples']), variant
    assert validation['bounds'][-1]['min'][1] > .8, variant
    fold = json.loads((OUT / f'fold_pose_{variant}.json').read_text())
    assert set(fold) == names
    assert all(len(q) == 4 and all(math.isfinite(v) for v in q)
               and abs(sum(v*v for v in q)-1) < .0001 for q in fold.values())
    folds[variant] = fold
assert folds['left_over'] != folds['right_over']
data['fold_poses'] = folds
(OUT / 'sovereign_wings.asset.json').write_text(json.dumps(data, separators=(',', ':')), encoding='utf8')
(RES / 'wing_models').mkdir(exist_ok=True)
shutil.copy2(OUT / 'sovereign_wings.asset.json', RES / 'wing_models/sovereign_wings.json')
for filename in ('sovereign_wings.png', 'sovereign_wings_emissive.png'):
    shutil.copy2(OUT / filename, RES / 'textures' / filename)
print(f'Installed {len(data["elements"])} cuboids, {len(names)} bones, both folded poses.')
