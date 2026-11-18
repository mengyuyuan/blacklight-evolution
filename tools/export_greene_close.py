"""Append-only source export for MotherCore's two Slug close attacks.

Reads the accepted native-helper rig; never rewrites its geometry/base clips.
Only original ROT/TRAN/SCL curves are exported, no original mesh or texture.
"""
from pathlib import Path
import copy, hashlib, importlib.util, json, struct, sys
sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('greene_export', ROOT/'tools/export_encounter_source_motion.py')
m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
np = m.np
assets = ROOT/'src/main/resources/assets/blacklight/models'
base_path = assets/'encounter_elizabeth_greene_source.json'
base_bytes = base_path.read_bytes(); base = json.loads(base_bytes)
source, rig_name, _, _ = m.PROFILES['elizabeth_greene']
nodes = m.nodes(source); rig = m.skeleton(nodes, rig_name)
anims = {m.decoder.text8(n['payload'], 4)[0]: n for n in nodes if n['type'] == 0x121000}
order = base['bones']; parents = base['parents']; pivots = np.asarray(base['pivots'])
scale = base['pixelsPerSourceUnit']; K = np.diag([scale, scale, scale, 1.]); Ki = np.linalg.inv(K)
skel = next(n for n in nodes if n['type'] == 0x23000 and m.decoder.text8(n['payload'])[0] == rig_name)
compensate = {}
for node in skel['children']:
    if node['type'] != 0x23001: continue
    name, at = m.decoder.text8(node['payload']); extra = node['payload'][at+68:]
    if len(extra) != 54: raise ValueError('Unexpected native joint metadata')
    matrix_type, flag = struct.unpack_from('<HI', extra, 48)
    compensate[name] = matrix_type == 1 and flag != 0

def row_for(mat, pivot):
    sizes = np.linalg.norm(mat[:3, :3], axis=0); rotation = mat[:3, :3]/sizes
    if np.max(np.abs(rotation.T@rotation-np.eye(3))) > 2e-5: raise ValueError('Local shear')
    return [round(float(v), 7) for v in m.euler(rotation)+list(mat[:3, 3]-pivot+rotation@np.diag(sizes)@pivot)+list(sizes)]

def native(clip, frame):
    tracks = {}
    for t in clip['tracks']: tracks.setdefault(t['bone'], {})[t['tag']] = t
    local, world, scales = [], [], []
    for i, (name, parent, bind) in enumerate(rig):
        mat = bind.copy(); channels = tracks.get(name, {}) if i else {}
        if 'ROT' in channels: mat[:3, :3] = m.quat_matrix(m.track_at(channels['ROT'], frame))
        if 'TRAN' in channels: mat[:3, 3] = m.track_at(channels['TRAN'], frame)
        size = np.asarray(m.track_at(channels['SCL'], frame)) if 'SCL' in channels else np.ones(3)
        mat[:3, :3] = mat[:3, :3]@np.diag(size); scales.append(size); local.append(mat)
        effective = mat.copy()
        if i and compensate[name]: effective[:3, :3] = np.diag(1/scales[parent])@effective[:3, :3]
        world.append(effective if i == 0 else world[parent]@effective)
    return local, world, scales

result = {k: copy.deepcopy(base[k]) for k in ('bones', 'parents', 'pivots', 'pixelsPerSourceUnit')}
result.update(schema=1, source=str(source), sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
              baseRigSha256=hashlib.sha256(base_bytes).hexdigest(), clips={})
fixtures = []
for key, original in [('close_left', 'mother_slug_hit_left'), ('close_right', 'mother_slug_hit_right')]:
    clip = m.decoder.decode(anims[original]); frames = []
    if clip['fps'] != 24 or int(clip['frames']) != 76: raise ValueError('Changed source clip')
    for frame in range(int(clip['frames'])):
        local, world, scales = native(clip, frame)
        by_name = dict(zip(order, copy.deepcopy(base['clips']['idle']['frames'][0])))
        for i, (name, parent, _) in enumerate(rig):
            target = 'native_'+name
            if target not in by_name: continue
            mat = K@local[i]@Ki
            if target+'_parent_scale' in by_name:
                offset = np.eye(4); offset[:3, 3] = mat[:3, 3]
                by_name[target+'_offset'] = row_for(offset, np.zeros(3)); mat[:3, 3] = 0
                inverse = np.eye(4); inverse[:3, :3] = np.diag(1/scales[parent])
                by_name[target+'_parent_scale'] = row_for(inverse, np.zeros(3))
            elif i and compensate[name] and np.max(np.abs(scales[parent]-1)) > 1e-6:
                raise ValueError('Existing rig lacks required compensation helper '+name)
            by_name[target] = row_for(mat, np.zeros(3))
        frames.append([by_name[name] for name in order])
        if frame in (0, 15, 30, 34, 39, 50, 75):
            joint = 'Slug_02_Spine_5' if key == 'close_left' else 'Slug_01_Spine_5'
            index = next(i for i, row in enumerate(rig) if row[0] == joint)
            fixtures.append({'clip':key, 'frame':frame, 'joint':joint,
                             'localOffset':[-2,0,0], 'pointModelBlocks':((K@world[index]@np.array([-2.,0,0,1]))[:3]/16).tolist()})
    result['clips'][key] = {'source':original, 'fps':clip['fps'], 'seconds':(clip['frames']-1)/clip['fps'], 'frames':frames}
(assets/'greene_close_source.json').write_text(json.dumps(result, separators=(',',':')), encoding='utf-8')
(ROOT/'src/test/resources/greene-close-reference.json').write_text(json.dumps({'sourceSha256':result['sourceSha256'],
    'method':'Direct native source matrices including parent scale compensation; before presentation multiplier.', 'fixtures':fixtures}, indent=2)+'\n', encoding='utf-8')
if base_path.read_bytes() != base_bytes: raise ValueError('Base source resource changed')
print('Exported close_left/right: 76 frames @24fps; base rig/geometry unchanged')
