"""Fit small bind-local terrain probes to the custom scalp, fringe and their attached actors."""
import gzip
import json
import struct
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation

models = Path(__file__).resolve().parents[1] / "src/main/resources/assets/blacklight/models"
bones = (0, 33, 34)
tracks, vertices = [], []
with gzip.open(models / "greene_outbreak_fullbody.mesh.gz", "rb") as mesh:
    magic, groups, frames, rate = struct.unpack(">4i", mesh.read(16))
    assert magic == 0x47464D31 and groups == 791
    for _ in range(groups):
        count, = struct.unpack(">i", mesh.read(4))
        tracks.append(np.frombuffer(mesh.read(frames * 28), ">f4").reshape(-1, 7))
        vertices.append(np.unique(np.frombuffer(mesh.read(count * 36), ">f4").reshape(-1, 9)[:, :3], axis=0))
owners = gzip.decompress((models / "greene_goddess_receivers.bin.gz").read_bytes())[-63:]
actors = json.loads((models / "greene_colonies.json").read_bytes())
points = {bone: [vertices[bone]] for bone in bones}
for frame in range(0, frames, 6):
    locations = [track[frame, :3] for track in tracks]
    rotations = [Rotation.from_quat(track[frame, 3:]).as_matrix() for track in tracks]
    posed = [v @ r.T + p for v, r, p in zip(vertices, rotations, locations)]
    for group in range(35, groups):
        bone = owners[(group - 35) // 12]
        if bone in points:
            points[bone].append((posed[group] - locations[bone]) @ rotations[bone])
    order = [0, 0]
    for actor in actors:
        bone = actor["bone"]
        if bone not in points:
            continue
        donor = 35 + 12 * actor["actor"]
        body = np.concatenate(posed[donor:donor + 12]) - locations[donor]
        row = int(bone == 34)
        fringe = actor["region"] == "hair"
        for fold in ((0, .5, 1, -.22) if fringe else (0,)):
            turn = Rotation.from_euler("YZX", [(order[row] % 3 - 1) * .22 * fold,
                                                (.33 if row else -.28) * fold, -1.23 * fold])
            points[bone].append(turn.apply(body) @ rotations[bone] + actor["point"])
        if fringe:
            order[row] += 1
data = bytearray()
for bone in bones:
    cloud = np.concatenate(points[bone])
    # Each cell stores only occupied bounds. Empty air between long locks stays empty.
    bins, membership = np.unique(np.floor(cloud / 1.1).astype(int), axis=0, return_inverse=True)
    lo = np.full((len(bins), 3), np.inf)
    hi = -lo.copy()
    np.minimum.at(lo, membership, cloud)
    np.maximum.at(hi, membership, cloud)
    data += struct.pack(">i", len(bins))
    data += np.concatenate(((lo + hi) / 2, (hi - lo) / 2), axis=1).astype(">f4").tobytes()
(models / "greene_hover_probes.bin").write_bytes(data)
