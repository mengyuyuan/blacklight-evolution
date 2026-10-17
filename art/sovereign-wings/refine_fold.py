"""Re-solve folded joints against the installed mesh (Blender background Python).

The former fold quaternions belonged to an older, wider rig. Reusing them after
moving the hinges sent two blade tips through the actor's front. Only rest-fold
rotations are authored here; flight geometry, skin weights and textures stay intact.
"""
import json, math
from pathlib import Path
from mathutils import Vector, Quaternion

ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / 'src/main/resources/assets/blacklight/wing_models/sovereign_wings.json'

def solve(a, b, c, target, pole):
    l1, l2 = (b-a).length, (c-b).length
    delta = target-a
    distance = delta.length
    if not abs(l1-l2)+.001 < distance < l1+l2-.001:
        raise ValueError(f'Unreachable folded endpoint: {target}, lengths {l1}, {l2}')
    direction = delta.normalized()
    normal = (pole-direction*pole.dot(direction)).normalized()
    along = (l1*l1-l2*l2+distance*distance)/(2*distance)
    middle = a+direction*along+normal*math.sqrt(max(0, l1*l1-along*along))
    return (b-a).rotation_difference(middle-a), (c-b).rotation_difference(target-middle)

def refine(data):
    pivots = {b['name']: Vector(b['pivot']) for b in data['bones']}
    for variant in ('left_over', 'right_over'):
        outer = 1 if variant == 'left_over' else -1
        folds = {name: Quaternion() for name in pivots}
        for side, label in ((1, 'left'), (-1, 'right')):
            layer = 2.4 if side == outer else 0
            for i in range(1, 5):
                rib, arch, finger, tip = [f'{label}_{part}_{i}' for part in ('rib','arch','finger','tip')]
                apex = Vector((side*[8.5,8,7.5,8][i-1], [35,35,34,32][i-1], 6.5+layer+(i-1)*.7))
                end = Vector((-side*[4,5,6,3][i-1], [3,3,3,4][i-1], 7+layer+(i-1)*.7))
                # Fold in the back plane. An aft pole makes a hollow basket;
                # an outboard pole nests the spars beside each other instead.
                q1,q2 = solve(pivots[rib],pivots[arch],pivots[finger],apex,Vector((side,0,.06)))
                shift = apex-pivots[finger]
                # Distal blades cross the spine in separate depth layers. The
                # server's existing random variant selects the outer wing.
                q3,q4 = solve(apex,pivots[tip]+shift,Vector(data['skin_paths'][tip][-1])+shift,end,Vector((-side,0,.02)))
                folds[rib]=q1;folds[arch]=q1.conjugated()@q2
                folds[finger]=q2.conjugated()@q3;folds[tip]=q3.conjugated()@q4
        data['fold_poses'][variant] = {name:[q.x,q.y,q.z,q.w] for name,q in folds.items()}
    return data

if __name__ == '__main__':
    data = refine(json.loads(ASSET.read_text(encoding='utf8')))
    ASSET.write_text(json.dumps(data,separators=(',',':')),encoding='utf8')
    print('Rebuilt both folds from current rest joints:', ASSET)
