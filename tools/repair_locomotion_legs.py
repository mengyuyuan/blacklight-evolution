"""Reconstruct leg bend planes from decoded source hip orientation.

Requires the local numeric retarget module plus numpy/scipy. Original decoded
assets remain outside the repository. Existing foot endpoints, clip timing,
root/upper-body tracks and gameplay movement are preserved.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import sys

import numpy as np


def solve_leg(hip, foot, reference, upper_length, lower_length):
    """A straight knee cannot define roll; the authored hip basis can."""
    axis = foot - hip
    distance = float(np.linalg.norm(axis))
    if distance < 1e-8:
        raise ValueError("Degenerate hip/foot endpoint")
    axis /= distance
    normal = reference[:, 0] - axis * (reference[:, 0] @ axis)
    if np.linalg.norm(normal) < 1e-5:
        raise ValueError("Hip reference perpendicular axis is singular")
    normal /= np.linalg.norm(normal)
    plane = np.cross(normal, axis)
    along = (upper_length**2 - lower_length**2 + distance**2) / (2 * distance)
    height = np.sqrt(max(0, upper_length**2 - along**2))
    knee = hip + axis * along + plane * height
    result = []
    for position, tip in [(hip, knee), (knee, foot)]:
        y = tip - position
        y /= np.linalg.norm(y)
        transform = np.eye(4)
        transform[:3, :3] = np.column_stack([normal, y, np.cross(normal, y)])
        transform[:3, 3] = position
        result.append(transform)
    return result


def max_step(matrices):
    rotations = np.array(matrices)[:, :3, :3]
    rotations = rotations / np.linalg.norm(rotations, axis=1)[:, None, :]
    cosines = (np.einsum('nij,nij->n', rotations[:-1], rotations[1:]) - 1) / 2
    return float(np.degrees(np.arccos(np.clip(cosines, -1, 1))).max())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--retarget', type=Path, required=True)
    parser.add_argument('--decoded', type=Path, required=True)
    parser.add_argument('--source-audit', type=Path, required=True)
    parser.add_argument('--profile', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--clips', nargs='+', default=[
        'basic_idle_whip', 'basic_walk', 'basic_sprint', 'basic_jump_running'])
    args = parser.parse_args()
    sys.path.insert(0, str(args.retarget))
    from numpy_retarget import Retarget, rot, quat
    records = {r['name']: r for r in json.loads(
        (args.decoded / 'index_PRIVATE.json').read_bytes())['records'] if r['animation_type'] == 'PTRN'}
    audits = {r['id']: r for r in json.loads(args.source_audit.read_bytes())}
    reports = []
    # Validate every candidate before replacing any files.
    replacements = {}
    for name in args.clips:
        path = args.profile / (name + '.json')
        original = path.read_bytes()
        data = json.loads(original)
        tracks = {track['name']: track for track in data['animation']}
        evidence = audits[name]
        source_raw = (args.decoded / records[evidence['source']]['file']).read_bytes()
        digest = hashlib.sha256(source_raw).hexdigest()
        assert digest == evidence['sourceHash'], f'Source changed: {name}'
        rt = Retarget(json.loads(source_raw), skeleton='alex')
        rt.generate([dict(start=a, end=b, seconds=c, blend=0) for a, b, c in evidence['segments']])
        times = tracks['Root']['time']
        assert abs(times[-1] - evidence['seconds']) < 1e-8
        names = ['Thigh_R', 'Leg_R', 'Knee_R', 'Thigh_L', 'Leg_L', 'Knee_L']
        corrected = {n: [] for n in names}
        endpoint_error = 0.0
        for i, time in enumerate(times):
            world = {}
            for bone in rt.target.names:
                parent = rt.target.parents[bone]
                local = np.array(tracks[bone]['transform'][i]).reshape(4, 4)
                world[bone] = (world[parent] if parent else np.eye(4)) @ local
            old = copy.deepcopy(world)
            source = rt.staged_world(time)
            for side in ['R', 'L']:
                thigh, leg, knee = 'Thigh_' + side, 'Leg_' + side, 'Knee_' + side
                reference = (rot(quat(source['Hip_' + side]))
                             @ rot(quat(rt.source.bind['Hip_' + side])).T @ rt.calibrated[thigh])
                foot = (old[leg] @ np.array([0, .37472, 0, 1]))[:3]
                world[thigh], world[leg] = solve_leg(old[thigh][:3, 3], foot, reference,
                                                    np.linalg.norm(rt.target.rest[leg][:3, 3]), .37472)
                world[knee][:3, 3] = world[leg][:3, 3]
                world[knee][:3, :3] = (world[thigh][:3, :3] @ old[thigh][:3, :3].T
                                      @ old[knee][:3, :3])
                actual = (world[leg] @ np.array([0, .37472, 0, 1]))[:3]
                endpoint_error = max(endpoint_error, float(np.linalg.norm(actual - foot)))
            for bone in names:
                local = np.linalg.inv(world[rt.target.parents[bone]]) @ world[bone]
                corrected[bone].append(local)
        assert endpoint_error < 1e-7, f'Foot moved: {name}'
        steps = {}
        for bone in names:
            before = np.array(tracks[bone]['transform']).reshape(-1, 4, 4)
            # Keep explicitly closed loops closed, without changing their length.
            if evidence['loop'] and np.allclose(before[0], before[-1], atol=1e-8):
                corrected[bone][-1] = corrected[bone][0].copy()
            steps[bone] = dict(before=max_step(before), after=max_step(corrected[bone]))
            assert steps[bone]['after'] < 60, f'Needs separate source inspection: {name}/{bone}'
            tracks[bone]['transform'] = [m.reshape(-1).tolist() for m in corrected[bone]]
        data['provenance']['legPlaneRepair'] = dict(
            method='Source hip orientation defines knee bend plane; fixed-length IK retains baked foot endpoints.',
            source=evidence['source'], sourceSha256=digest,
            baselineSha256=hashlib.sha256(original).hexdigest())
        replacements[path] = json.dumps(data, separators=(',', ':'), ensure_ascii=False, allow_nan=False).encode()
        reports.append(dict(clip=name, source=evidence['source'], maxFootEndpointError=endpoint_error,
                            steps=steps, baselineSha256=hashlib.sha256(original).hexdigest()))
        print(name, steps['Thigh_R'], steps['Thigh_L'], flush=True)
    manifest_path = args.profile.parent / 'motion-profile-manifest.json'
    manifest = json.loads(manifest_path.read_bytes())
    for path, encoded in replacements.items():
        path.write_bytes(encoded)
        manifest['files'][path.name] = dict(bytes=len(encoded), sha256=hashlib.sha256(encoded).hexdigest())
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    args.report.write_text(json.dumps(reports, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
