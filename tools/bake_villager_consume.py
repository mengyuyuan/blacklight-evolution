"""Bake the location and orientation of the captive in the giver's grapple socket."""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from read_consume_fig import read_pairs, hash_name

import export_encounter_source_motion as motion
from export_context_actions import quaternion

ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "art/motion-profile"
# Source crop starts and receiver holds match the shipped paired clips.
WEAPONS = {
    "normal": ("alex", "alex_grap_punch_chest", 0, 90),
    "claws": ("alex_claws", "alex_claw_grap_footsweep", 30, 75),
    "claws_head": ("alex_claws", "alex_claw_grap_crush_head", 45, 135),
    "hammer": ("alex_hammerfist", "alex_hammer_grap_headdrop", 0, 55),
    "blade": ("alex_blades", "alex_grap_blade_spin_and_slice", 0, None),
    "hammer_smush": ("alex_hammerfist", "alex_hammer_grap_headsmush", 0, None),
    "muscle_heart": ("alex_musclemass", "alex_grap_muscle_heart_to_head", 0, None),
    "muscle": ("alex_musclemass", "alex_grap_muscle_arm_rip", 0, 30),
    "whip": ("alex_whipfist", "alex_whipfist_grap_come_here", 0, None),
    "whip_lash": ("alex_whipfist", "alex_whipfist_grap_lash", 0, None),
}


def selected_rig(rig, clip, names):
    required = set(names)
    for name, parent, _ in reversed(rig[1:]):
        if name in required:
            required.add(rig[parent][0])
    tracks = {}
    for track in clip["tracks"]:
        if track["bone"] in required:
            tracks.setdefault(track["bone"], {})[track["tag"]] = track
    return [(name, rig[parent][0] if i else None, bind, tracks.get(name, {}))
            for i, (name, parent, bind) in enumerate(rig) if name in required]


def sample(rig, frame):
    pose = {}
    for name, parent, bind, tracks in rig:
        m = bind.copy()
        if "ROT" in tracks:
            m[:3, :3] = motion.quat_matrix(motion.track_at(tracks["ROT"], frame))
        if "SCL" in tracks:
            m[:3, :3] = m[:3, :3] @ np.diag(motion.track_at(tracks["SCL"], frame))
        if "TRAN" in tracks:
            m[:3, 3] = motion.track_at(tracks["TRAN"], frame)
        pose[name] = pose[parent] @ m if parent else m
    return pose


def rigid(matrix):
    u, _, v = np.linalg.svd(matrix[:3, :3])
    u[:, -1] *= np.linalg.det(u @ v)
    out = matrix.copy()
    out[:3, :3] = u @ v
    return out


# Vertices of the vanilla villager, in model pixels relative to each socket.
# The hat rim and crossed arms extend substantially outside the standing AABB.
def corners(lo, hi):
    from itertools import product
    return np.array(list(product(*zip(lo, hi)))).T


def pitch(angle):
    c, s = np.cos(angle), np.sin(angle)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


PARTS = [
    (0, (0, 0, 0), corners((-4.51, -10.51, -4.51), (4.51, .51, 4.51))),
    (0, (0, 0, 0), pitch(-np.pi/2) @ corners((-8, -8, -6), (8, 8, -5))),
    (0, (0, 0, 0), corners((-1, -3, -6), (1, 1, -4))),
    (1, (0, 0, 0), corners((-4.5, -.5, -3.5), (4.5, 20.5, 3.5))),
    (2, (0, 3, -1), pitch(-.75) @ corners((-8, -2, -2), (8, 6, 2))),
    (4, (-2, 12, 0), corners((-2, 0, -2), (2, 12, 2))),
    (5, (2, 12, 0), corners((-2, 0, -2), (2, 12, 2))),
]


def supported_rotation(rotation, key, seconds):
    # The solid robe cannot flex like the source pelvis. Let it lie against the
    # street after a takedown, or fold the captive toward the rising knee.
    if key == "normal_head_stomp":
        u = np.clip((seconds-.21)/.25, 0, 1)
    elif key == "normal_beatdown":
        u = np.clip((seconds-.86)/.32, 0, 1)
    elif key == "normal_knee_head":
        u = np.clip(1-abs(seconds-.6)/.38, 0, 1)
    else:
        return rotation
    up = rotation[:, 1]
    tangent = up * [1, 0, 1]
    if np.linalg.norm(tangent) < .01:
        return rotation
    if key == "normal_knee_head":
        tangent = motion.unit(tangent)*.96 + np.array([0., .28, 0.])
    change = quaternion(motion.rotation_between(up, motion.unit(tangent)))
    return motion.quat_matrix(motion.slerp([0., 0., 0., 1.], change, u*u*(3-2*u))) @ rotation


def neck_above_ground(parts, rotation):
    # Match ReceiverModelPose: world chest rotation lives on the rigid torso,
    # each part gets its rotation relative to that chest, in model axes.
    chest_inverse = motion.quat_matrix(parts[11:15]).T
    pixels = (.9375/16)*np.diag([-1., -1., 1.])
    model_origin = np.array([0., 1.501*.9375-1.95*.84, 0.])[:, None]
    lowest = min(np.min(rotation[1] @ (model_origin + pixels @
                 (np.asarray(pivot)[:, None] + chest_inverse @
                  motion.quat_matrix(parts[7+4*joint:11+4*joint]) @ vertices)))
                 for joint, pivot, vertices in PARTS)
    return -lowest


# P1 endpoints in metres. Ball and ankle markers sit inside the shoes.
# Include an inverted head and the hands for prone/tumbling receivers.
SUPPORT_JOINTS = {"Ball_L": .055, "Ball_R": .055, "Ankle_L": .08, "Ankle_R": .08,
                  "Head": .12, "Wrist_L": .045, "Wrist_R": .045,
                  "Knee_L": .085, "Knee_R": .085, "Pelvis": .12}


def playback(path, speed, changed):
    clip = json.loads(path.read_bytes())
    if speed != 1:
        # Re-runnable even on a previously baked checkout.
        metadata = clip["blacklight"]
        factor = clip.get("provenance", {}).get("p1PlaybackSpeed", 1) / speed
        if abs(factor-1) > 1e-7:
            for track in clip["animation"]:
                track["time"] = [t*factor for t in track["time"]]
            metadata["durationTicks"] = int(np.ceil(clip["animation"][0]["time"][-1]*20-1e-6))+1
            clip.setdefault("provenance", {})["p1PlaybackSpeed"] = speed
            path.write_text(json.dumps(clip,separators=(",", ":")), encoding="utf8")
        changed.add(path)
    return clip


def bake(archive, powers, only=None):
    path = PROFILE/"consume-placement.data"
    data = json.loads(path.read_bytes())
    specs = WEAPONS.copy()
    for p in sorted(PROFILE.glob("consume_*_victim.json")):
        key = p.stem[len("consume_"):-len("_victim")]
        if key.startswith(("normal_", "stealth_")):
            src = json.loads(p.read_bytes())["source"]
            specs[key] = "alex", src["animation"][:-4], 0, src["sourceFrames"][-1]
    if only:
        specs = {k:specs[k] for k in only}
    human = motion.skeleton(motion.nodes(archive.with_name("startup.p3d.rz")), "ped_m_sold_aa_master_skeleton_skeleton")
    fig = read_pairs(archive.with_name("alex_fig.p3d.rz"), [spec[1] for spec in specs.values()])
    events_path = PROFILE / "consume-receiver-events.data"
    events = json.loads(events_path.read_bytes())
    changed = {events_path, path}
    result = dict(data.get("villagerPairs", {})) if only else {}
    mirror = np.array([-1., 1., 1.])
    for package in sorted({spec[0] for spec in specs.values()}):
        nodes = motion.nodes(archive if package == "alex" else powers / (package + ".p3d.rz"))
        rig_name = next(motion.decoder.text8(n["payload"])[0] for n in nodes if n["type"] == 0x23000)
        actor_rig = motion.skeleton(nodes, rig_name)
        clips = {motion.decoder.text8(n["payload"], 4)[0]: n for n in nodes if n["type"] == 0x121000}
        for key, (pkg, name, start, hold) in specs.items():
            if pkg != package:
                continue
            giver = motion.decoder.decode(clips[name + "_gv"])
            receiver = motion.decoder.decode(clips[name + "_rcv"])
            gv, rv = fig[name+"_gv"], fig[name+"_rcv"]
            # A grab slot names a coordinate system, not a replacement for the
            # receiver's authored world travel. The wrist grapple locator and
            # Motion_Root at entry register both puppets into one scene.
            explicit_wrist = gv.get("socket") == hash_name("R_Wrist_Grapple")
            socket = "R_Wrist_Grapple" if explicit_wrist or key in (
                "claws", "blade", "whip", "whip_lash", "hammer", "muscle",
                "normal_beatdown", "normal_knee_head", "hammer_smush", "muscle_heart") else "Root_Grapple"
            ar = selected_rig(actor_rig, giver, ["Pelvis", socket])
            profile = {"deformSeconds": 5/6} if key == "blade" else events["profiles"][key]
            markers = rv["events"]
            if "consumeByParent" in markers:
                profile["absorbSeconds"] = round(markers["consumeByParent"], 6)
            if key in ("hammer_smush", "muscle_heart"):
                profile["effectSeconds"] = round(markers["effectConsume"], 6)
                profile["timingNote"] = "Effect and absorption are receiver FIG events. Skin contact uses the paired wrist approach. Finish is adapted to Minecraft."
            if key.startswith(("normal_", "stealth_")):
                profile["effectSeconds"] = round(markers["effectConsume"], 6)
                if key.startswith("stealth_"):
                    profile["finishSeconds"] = round(markers.get("setVisibility",markers["death"]),6)
                    profile["absorbSeconds"] = profile["effectSeconds"]
                elif key == "normal_knee_head":
                    profile["finishSeconds"] = 1.55
                profile["timingNote"] = "P1 receiver FIG effect and absorption/visibility markers. Hit timing and post-absorption settlement adapted to Minecraft."
                if key == "normal_beatdown":
                    profile["timingNote"] = 'P1 paired wrist contacts at frames 50, 60, 70, 82 and 99; FIG tendrils at 88, ConsumeByParent at 115. Both original clips continue through 196.'
            if rv["gibs"]:
                hand, free = rv["gibs"]
                assert (hand["joint"], hand["slot"]) == (hash_name("R_Wrist_Grapple"), hash_name("Temporary"))
                assert (free["joint"], free["slot"]) == (hash_name("Motion_Root"), 0)
                assert abs(hand["seconds"]-free["seconds"]) < 1e-5
                assert abs(profile["deformSeconds"]-hand["seconds"]) < 1e-4
                if key == "muscle":
                    assert hand["template"] == hash_name("ConsumeMaleGenericVert01LeftGib")
                    assert free["template"] == hash_name("ConsumeMaleGenericVert01RightGib")

            vr = selected_rig(human, receiver, ["Motion_Root", "Shoulder_L", "Shoulder_R", *SUPPORT_JOINTS])
            actor_clip = playback(PROFILE / f"consume_{key}_actor.json", gv["speed"], changed)
            playback(PROFILE / f"consume_{key}_victim.json", gv["speed"], changed)
            root = next(track for track in actor_clip["animation"] if track["name"] == "Root")
            entry = rigid(sample(ar, start)[socket]) @ np.linalg.inv(rigid(sample(vr, start)["Motion_Root"]))
            rows = []
            for i, (time, transform) in enumerate(zip(root["time"], root["transform"], strict=True)):
                frame = time * gv["speed"] * giver["fps"] + start
                victim_frame = time * rv["speed"] * receiver["fps"] + start
                a = sample(ar, min(frame, giver["frames"]-1))
                v = sample(vr, min(victim_frame, receiver["frames"]-1, hold if hold is not None else victim_frame))
                slot = (rigid(a[socket]) @ np.linalg.inv(rigid(v["Motion_Root"]))
                        if explicit_wrist else entry)
                def position(joint):
                    return mirror * ((slot @ v[joint])[:3, 3] - a["Pelvis"][:3, 3])
                neck = position("Head")
                up = motion.unit(neck - position("Pelvis"))
                across = position("Shoulder_R") - position("Shoulder_L")
                back = motion.unit(np.cross(across, up))
                rotation = supported_rotation(np.column_stack((np.cross(up, back), up, back)), key, time)
                support = neck_above_ground(data["projections"][key][i], rotation)
                # The shipped Root matrix is in armature space (Z up); use the
                # actual planted player's pelvis to rebase the P1 pair once.
                tx, ty, tz = np.asarray(transform).reshape(4, 4)[:3, 3]
                point = (np.array([tx, tz, -ty]) + neck) * .9375
                floor = min((slot @ v[n])[1,3]-radius for n,radius in SUPPORT_JOINTS.items())
                rows.append(np.round([*point, *quaternion(rotation), support, max(0.,floor)*.9375], 7).tolist())
            result[key] = rows
    events_path.write_bytes(json.dumps(events,separators=(",", ":")).encode())
    data["villagerPairs"] = result
    raw = json.dumps(data, separators=(",", ":"), allow_nan=False).encode()
    path.write_bytes(raw)
    manifest_path = PROFILE.parent/"motion-profile-manifest.json"
    manifest = json.loads(manifest_path.read_bytes())
    for asset in changed:
        payload = asset.read_bytes()
        manifest["files"][asset.name] = {"bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
    manifest_path.write_bytes((json.dumps(manifest, ensure_ascii=False, indent=2)+"\n").encode())


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("powers", type=Path)
    parser.add_argument("--only", nargs="+")
    args = parser.parse_args()
    bake(args.archive, args.powers, args.only)
