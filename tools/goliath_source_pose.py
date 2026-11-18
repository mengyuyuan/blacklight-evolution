"""P2 Goliath TRS sampling, including the original nonuniform SCL channels.

Kept separate from the shared reader while other encounters are being audited.
The verified P2 local-pose/parent/palette pipeline is recorded separately.
"""
import struct
import numpy as np


def rig_metadata(m, nodes, name):
    skeleton = next(n for n in nodes if n['type'] == 0x23000 and m.decoder.text8(n['payload'])[0] == name)
    _, p = m.decoder.text8(skeleton['payload'])
    version, count = struct.unpack_from('<II', skeleton['payload'], p)
    assert version == 1
    rows = []
    for joint in skeleton['children']:
        if joint['type'] != 0x23001:
            continue
        bone, p = m.decoder.text8(joint['payload'])
        auxiliary = list(struct.unpack_from('<12f', joint['payload'], p + 68))
        matrix_type, flag = struct.unpack_from('<HI', joint['payload'], p + 116)
        # SkeletonLoader 0x1086c740 and local-pose update 0x1088a2a0.
        # Do not silently ignore a future nontrivial pivot/shear configuration.
        assert auxiliary == [0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0], bone
        assert matrix_type == 1 and flag == (0 if bone == 'Middle_Base_L' else 1), (bone, matrix_type, flag)
        rows.append(dict(name=bone, offset=joint['offset'], auxiliary=auxiliary,
                         matrixType=matrix_type, flag=flag))
    assert len(rows) == count
    return rows


def locals(m, rig, clip, frame):
    tracks = {}
    for track in clip['tracks']:
        assert track['tag'] in ('ROT', 'TRAN', 'SCL'), track['tag']
        tracks.setdefault(track['bone'], {})[track['tag']] = track
    result = []
    for index, (name, parent, bind) in enumerate(rig):
        local = bind.copy()
        channels = tracks.get(name, {})
        if 'ROT' in channels:
            local[:3, :3] = m.quat_matrix(m.track_at(channels['ROT'], frame))
        if 'TRAN' in channels:
            local[:3, 3] = m.track_at(channels['TRAN'], frame)
        if 'SCL' in channels:
            scale = np.asarray(m.track_at(channels['SCL'], frame))
            assert np.all(scale > 0), (name, frame, scale)
            local[:3, :3] = local[:3, :3] @ np.diag(scale)
        result.append(local)
    return result


def world(m, rig, clip, frame):
    sampled = locals(m, rig, clip, frame)
    matrices, result = [], {}
    for index, (name, parent, _) in enumerate(rig):
        local = sampled[index].copy()
        # P2 0x1088a663..0x1088a6ac applies reciprocal parent channel scale
        # to the LINEAR part only. The offset still stretches with the parent.
        # Loader flag is zero only for unweighted Middle_Base_L in this rig.
        if index > 0 and name != 'Middle_Base_L':
            parent_scale = np.linalg.norm(sampled[parent][:3, :3], axis=0)
            local[:3, :3] = np.diag(1 / parent_scale) @ local[:3, :3]
        value = local if index == 0 else matrices[parent] @ local
        matrices.append(value)
        result[name] = value
    return result
