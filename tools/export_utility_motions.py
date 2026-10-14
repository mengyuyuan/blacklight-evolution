"""Export Blacklight motion data with separate initial-source and project-adaptation metadata.

Reads decoded source inputs and adapts them to Minecraft. This script does not
claim the resulting motion was wholly authored from scratch, and does not upload files.
"""

from motion_provenance import motion_notice, motion_provenance
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import sys

os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
os.environ.setdefault("OMP_NUM_THREADS", "1")
parser = argparse.ArgumentParser()
parser.add_argument("--decoded", type=Path, required=True)
parser.add_argument("--retarget", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
sys.path.insert(0, str(args.retarget))
from numpy_retarget import Retarget, matrix, decompose, np

# Full decoded body clips. Source transition slicing is not established here.
specs = [
    ("utility_shield_deploy", "alex_shield", "alex_power__transform_shield_come_around_left_003_Brian_1"),
    ("utility_shield_hit", "alex_shield", "alex_shield_impact"),
    ("utility_shield_run", "alex_shield", "alex_shield_run_n"),
    ("utility_shield_sprint", "alex_shield", "alex_shield_sprint_n"),
    ("utility_armor_transform", "alex_armour", "alex_act_armor_transformation"),
    ("utility_armor_idle", "alex_armour", "alex_armor_amb_fight"),
]
args.output.mkdir(parents=True, exist_ok=True)
for name, folder, source in specs:
    path = args.decoded / folder / (source + ".json")
    raw = path.read_bytes()
    decoded = json.loads(raw)
    if name == "utility_armor_transform":
        # Original skin disappearance belongs to surface replacement, not to the
        # Minecraft skeleton. Keep source rotation/translation without inherited
        # body shrinking moving the knees, shoulders or root during retargeting.
        for group in decoded["groups"]:
            group["channels"] = [c for c in group["channels"] if c["semantic"] != "SCL"]
    end = decoded["num_frames"]
    seconds = end / decoded["frame_rate"]
    rt = Retarget(decoded, skeleton="alex")
    frames, audit = rt.generate([dict(start=0, end=end, seconds=seconds, blend=0)])
    target = rt.target
    times = [i / 60 for i in range(len(frames))]
    origin = rt.staged_world(0)["Motion_Root"][:3, 3]
    roots, coords = [], []
    for i, frame in enumerate(frames):
        root = target.rest["Root"] @ matrix(frame["Root"])
        delta = (rt.staged_world(times[i])["Motion_Root"][:3, 3] - origin) * rt.scale
        root[:3, 3] -= delta
        root[2, 3] -= (origin[2] - rt.source.bind["Motion_Root"][2, 3]) * rt.scale
        roots.append(root)
        coord = np.eye(4)
        coord[:3, 3] = delta
        coords.append(coord)
    tracks = []
    for bone in target.names:
        matrices = roots if bone == "Root" else [target.rest[bone] @ matrix(f[bone]) for f in frames]
        tracks.append(dict(name=bone, time=times, transform=[m.reshape(-1).tolist() for m in matrices]))
    tracks.append(dict(name="Coord", time=times, transform=[m.reshape(-1).tolist() for m in coords]))
    result = dict(format="MATRIX", animation=tracks,
                  blacklight=dict(durationTicks=math.ceil((times[-1] + .05) * 20 - .00001), transitionSeconds=.05),
                  privateNotice=motion_notice('Original-derived, local only. Gameplay never applies diagnostic Coord.'), provenance=motion_provenance(),
                  source=dict(animation=source, sha256=hashlib.sha256(raw).hexdigest()))
    if name.startswith("utility_shield_"):
        bind = rt.source.bind
        basis = np.eye(4)
        basis[:3, :3] = rt.calibrated["Tool_L"].T @ bind["Wrist_L"][:3, :3] * rt.scale
        grip = []
        for t in times:
            world = rt.source.world(t)
            delta = (basis @ np.linalg.inv(world["Wrist_L"]) @ world["L_Wrist_Grapple"]
                     @ np.linalg.inv(bind["L_Wrist_Grapple"]) @ bind["Wrist_L"] @ np.linalg.inv(basis))
            grip.append(decompose(delta))
        result["shieldGrip"] = dict(fps=60, frames=grip, sourceJoint="L_Wrist_Grapple")
    if name == "utility_armor_transform":
        result["source"]["adaptation"] = "Body SCL excluded; armor growth handled by private armor-growth.data."
    (args.output / (name + ".json")).write_text(json.dumps(result, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    print(name, "seconds", times[-1], "foot error", audit["footEndpointMaxError"])
