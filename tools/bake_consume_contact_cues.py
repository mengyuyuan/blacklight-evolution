"""Bake the impact sockets of the shipped player rig at the encounter's event times."""
import hashlib
import json
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "art/motion-profile"
BIND = ROOT / "src/main/resources/assets/blacklight/animation/player_bind.json"
AXIS = np.array([[1, 0, 0, 0], [0, 0, 1, 0], [0, -1, 0, 0], [0, 0, 0, 1]])
# Approach/release are in seconds. Between repeated punches the offset is blended
# between successive impacts, preserving the independent receiver root animation.
STRIKES = {
    "normal": (["Tool_L"], .38, .36),
    "normal_head_stomp": (["Sole_L"], .45, .55),
    "normal_beatdown": (["Tool_L", "Tool_R", "Tool_L", "Tool_R", "Tool_R"], .21, .22),
    "muscle_heart": (["Tool_L", "Tool_L"], .22, .22),
    "hammer_smush": (["Tool_R"], .33, .24),
    "normal_knee_head": (["Leg_R"], .39, .30),
    "stealth_guillotine": (["Hand_R"], .50, .52),
    "muscle": (["Tool_R"], .42, .30),
}


def joints(node, parent=None):
    yield node["name"], parent, np.array(node["transform"]).reshape(4, 4)
    for child in node["children"]:
        yield from joints(child, node["name"])


def bake():
    placement_path = PROFILE / "consume-placement.data"
    placement = json.loads(placement_path.read_bytes())
    events = json.loads((PROFILE / "consume-receiver-events.data").read_bytes())["profiles"]
    hierarchy = list(joints(json.loads(BIND.read_bytes())["hierarchy"][0]))
    cues = {}
    for key, (sockets, approach, release) in STRIKES.items():
        tracks = json.loads((PROFILE / f"consume_{key}_actor.json").read_bytes())["animation"]
        tracks = {row["name"]: row for row in tracks}
        times = np.array(tracks["Root"]["time"])
        rows = []
        for time, socket in zip(events[key]["hitSeconds"], sockets, strict=True):
            index = int(np.argmin(abs(times - time)))
            pose, bind = {}, {}
            for name, parent, rest in hierarchy:
                transforms = tracks.get(name, {}).get("transform")
                local = np.array(transforms[min(index, len(transforms)-1)]).reshape(4, 4) if transforms else rest
                pose[name] = (pose[parent] if parent else AXIS) @ local
                bind[name] = (bind[parent] if parent else AXIS) @ rest
            if key == "muscle":
                mesh = json.loads((ROOT / "src/main/resources/assets/blacklight/models/form_muscle_mass.json").read_bytes())
                palms = [(pose["Hand_"+side] @ [*mesh["anchors"]["palm_"+side], 1])[:3] for side in ("L", "R")]
                point = np.mean(palms, axis=0)
            elif socket == "Sole_L":
                side = "Leg_L"
                point = (pose[side] @ np.linalg.inv(bind[side]) @ [-.119, .001, 0, 1])[:3]
            else:
                point = pose[socket][:3, 3].copy()
            point[2] *= -1  # Runtime local Z is the actor's forward direction.
            rows.append([round(time / times[-1], 8), round(approach / times[-1], 8),
                         round(release / times[-1], 8), *np.round(point, 8).tolist()])
            if key == "muscle":
                # The left palm is released by SpawnGib. Sample its authored
                # follow-through two frames later to transfer the opening impulse.
                future = {}
                for name, parent, rest in hierarchy:
                    frames = tracks.get(name, {}).get("transform")
                    local = np.array(frames[min(index+2, len(frames)-1)]).reshape(4, 4) if frames else rest
                    future[name] = (future[parent] if parent else AXIS) @ local
                release = []
                for side in ("L", "R"):
                    endpoint = (future["Hand_"+side] @ [*mesh["anchors"]["palm_"+side], 1])[:3]
                    release.append(np.round(endpoint * [1,1,-1], 7).tolist())
                placement["releasePalms"] = {key: release}
        cues[key] = rows
    placement["contactCues"] = cues
    raw = json.dumps(placement, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf8")
    placement_path.write_bytes(raw)
    path = ROOT / "art/motion-profile-manifest.json"
    manifest = json.loads(path.read_bytes())
    manifest["files"][placement_path.name] = {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    path.write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf8"))


if __name__ == "__main__":
    bake()
