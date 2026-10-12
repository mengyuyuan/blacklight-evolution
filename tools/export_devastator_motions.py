"""Export reviewed Prototype devastator/Bulletdive body tracks for private use.

Inputs and outputs stay outside the repository. This script contains only source
names, FIG owner references and scheduling metadata, never game curves or meshes.
The shared numeric retarget engine and its private skeleton calibration are local
inputs; no old game executable or Blender process is launched.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import shutil
import struct
import sys
import zlib

os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
os.environ.setdefault("OMP_NUM_THREADS", "1")
sys.dont_write_bytecode = True
FPS = 60
TRANSITION = 0.05


def stage(source, first, last, seconds, owner, track=None, note="", blend=0.033333):
    return dict(sourceName=source, start=first, end=last, seconds=round(seconds * FPS) / FPS,
                owner=owner, track=track, note=note, blend=blend)


def freeze(source, frame, seconds, owner, note=""):
    return stage(source, frame, frame, seconds, owner, note=note)


def recipes():
    pain = "alex_dev_critical_pain"
    pound = "alex_dev_groundpound"
    clap = "alex_dev_clap"
    whip = "alex_dev_whip"
    charge = "alex_dev_charge"
    jump = "alex_loco_jump_long_D"
    idle = "alex_amb_fight_A"
    configs = []
    for air in (False, True):
        prefix = "dev_air_" if air else "dev_"
        for kind in ("groundspike", "tendril", "critical_pain"):
            key = prefix + kind
            if kind == "groundspike":
                initial = stage(pound, 0, 8 if air else 7, 7 / 30, 71916 if air else 16420)
                held = stage(pound, 8 if air else 7, 18, 23 / 30 if air else 25 / 30,
                             71916 if air else 16928)
                antic = stage(charge, 20 if air else 0, 70 if air else 90,
                              1 if air else 1.5, 81992 if air else 18420)
                impact = initial["seconds"] + antic["seconds"] + 8 / 30
                main = [initial, antic,
                        stage(pound, 18, 26, 8 / 30, 73940 if air else 19476),
                        freeze(pound, 26, 3, 20164,
                               "Keep the source end pose through the target controller/late hit at 2.8s; 3s lease is a Minecraft scheduling adaptation."),
                        stage(pound, 26, 25, 2 / 3, 31348),
                        stage(idle, 15, 20, 4 / 3, 31348)]
                effect_start = antic["seconds"] + 8 / 30
                extra = dict(effectStart=effect_start, effectEnd=effect_start + 3,
                             damageStart=effect_start, damageEnd=effect_start + 3,
                             targetBeginStart=effect_start + 1 / 3,
                             targetBeginStop=effect_start + 1,
                             radiusSource=40, damageSource=500, targetCountSource=40,
                             randomDamageSource=25, minimumTargetCountSource=50,
                             sourceEffectOwner=20164, sourceControllerTrack=20500,
                             notes=["Air activation uses the source common air antic. Its conditional return to the ground devastator and physics remain Minecraft adaptations.",
                                    "Ground end-pose animation declares 2s, but a sibling hit is scheduled at 2.8s; the selected 3s lease preserves that record and does not claim full native sequencer reconstruction."])
                if air:
                    extra.update(travelPose=impact - 1 / FPS)
                landing = dict(holdTime=impact - 1 / FPS, impactTime=impact, takeoff=False) if air else None
            elif kind == "tendril":
                initial = stage(clap, 17 if air else 15, 24 if air else 19,
                                1 if air else .2, 75228 if air else 32412)
                held = freeze(clap, 24, 1 / 20, 75228, "Air Input charge occupies the windup's 1s; final frame remains available while keys are held.") if air else stage(clap, 19, 24, 25 / 30, 32920)
                antic = stage(charge, 20 if air else 0, 70 if air else 90,
                              1 if air else 1.5, 81992 if air else 34608)
                attack_owner = 77184 if air else 35696
                last = 25 if air else 40
                attack_seconds = (last - 15) / 30
                main = [initial, antic,
                        stage(whip, 15, last, attack_seconds, attack_owner),
                        freeze(whip, last, 3 - attack_seconds, attack_owner,
                               "Source Hold End Frame, retained until DevastatorTrack's explicit end at 3s.")]
                if air:
                    main += [stage("alex_loco_jump_fall_cycle", 0, 18, .6, 135572,
                                   note="Native fall source body used for bounded recovery; its .6s lease is an adaptation.")]
                else:
                    main += [freeze(whip, 25, 1 / 3, 46508), stage(idle, 15, 20, .5, 46508)]
                extra = dict(effectStart=antic["seconds"] + .4, effectEnd=antic["seconds"] + 3,
                             damageStart=antic["seconds"] + (5 / 12 if air else .433333),
                             damageEnd=antic["seconds"] + 3,
                             targetBeginStart=antic["seconds"] + .666,
                             targetBeginStop=antic["seconds"] + 1.5,
                             radiusSource=30, damageSource=400, targetCountSource=50,
                             randomDamageSource=20, minimumTargetCountSource=60,
                             sourceEffectOwner=attack_owner, sourceControllerTrack=77248 if air else 35932,
                             notes=["Source labels this ordinary chord sonic_clap, but the critical branch uses alex_dev_whip and a dedicated DevastatorTrack.",
                                    "The air node co-owns alex_loco_jump_long_D; target leg partition compositing is a retarget adaptation, not a recovered native partition runtime."])
                landing = None
            else:
                initial = stage(pain, 0, 10 if air else 5, 1 if air else 1 / 6,
                                50496 if air else 376)
                held = freeze(pain, 10, 1 / 20, 50496, "Air source charge is already in its 1s windup.") if air else stage(pain, 6, 10, 25 / 30, 1188)
                antic = stage(charge, 0, 70, 1.5, 52104 if air else 2544)
                owner = 53552 if air else 3600
                neutral = "alex_dev_critical_pain_forward_air" if air else pain
                main = [initial, antic,
                        stage(pain, 0, 15, .5, owner,
                              note="First .5s of the source 0..25/.833333 body track, before the vertical aim driver takes over."),
                        stage(neutral, 31, 65, 34 / 30, owner, 53916 if air else 3836,
                              note="Decoded AnimationBlendVerticalAim neutral branch at speed 1; angle blending is handled separately."),
                        freeze(neutral, 65, 3 - .5 - 34 / 30, owner,
                               "Hold the neutral aim end pose through the source TendrilDevastatorTrack's 3s end.")]
                if air:
                    main += [stage(pain, 65, 85, 2 / 3, 71068)]
                else:
                    main += [stage("alex_dev_critical_pain_step_back", 0, 64, 64 / 30, 12632)]
                extra = dict(effectStart=1.5, effectEnd=4.5, damageStart=1.5 + 2 / 3,
                             damageEnd=4.5, targetBeginStart=1.5 + 2 / 3 + (1 / 3 if air else 0),
                             targetBeginStop=4.5, radiusSource=200, damageSource=1250,
                             targetCountSource=10, sourceEffectOwner=owner,
                             sourceControllerTrack=54208 if air else 4128,
                             sourceAimTrack=53916 if air else 3836,
                             notes=["radiusSource is TendrilDevastatorTrack.Distance, not a circular explosion radius.",
                                    "Two same-time hit records have different source conditions and do not prove two hits per target.",
                                    "Attack FX has a -0.033333 end sentinel; its teardown semantics remain unknown."])
                landing = None
            cfg = dict(id=key, stages=main, chargeStages=[initial, held],
                       releaseTime=initial["seconds"], holdTicks=max(1, math.ceil(held["seconds"] * 20 - 1e-5)),
                       devastator=extra, landing=landing, air=air, kind=kind)
            configs.append(cfg)
            if kind == "critical_pain":
                for direction in ("up", "down"):
                    variant = dict(cfg, id=key + "_aim_" + direction)
                    aim = "alex_dev_critical_pain_" + (direction + "_air" if air else "aim_" + direction)
                    variant["stages"] = [dict(s, sourceName=aim) if s.get("track") in (3836, 53916) or (s["sourceName"] == neutral and s["start"] == s["end"] == 65) else dict(s) for s in main]
                    variant["chargeKey"] = key + "_charge"
                    configs.append(variant)

    bullet = "alex_bullet_smash"
    initial = [stage(bullet, 35, 43, 8 / 30, 116832),
               stage(bullet, 43, 46, .4, 116832, blend=0)]
    flight = [stage(bullet, 46, 49, 1 / 6, 127116),
              stage(bullet, 50, 57, 7 / 30, 128496),
              freeze(bullet, 57, 1.1, 128496, "Hold End Frame until actual supporting-surface impact.")]
    impact = sum(s["seconds"] for s in initial + flight)
    # Native track 126244 has blendInTime=0. The impact sample must enter the
    # real braced landing pose immediately, not keep inverted fall frame 57.
    land = [stage("alex_loco_jump_heavy_all", 110, 115, 1 / 6, 125588, blend=0),
            freeze("alex_loco_jump_heavy_all", 115, 1 / 6, 125588),
            stage("alex_loco_jump_heavy_all", 115, 140, 25 / 30, 125588)]
    configs.append(dict(id="bulletdive_drop", stages=initial + flight + land,
                        chargeStages=initial + [freeze(bullet, 46, 1 / 20, 116832)],
                        releaseTime=2 / 3, holdTicks=1, kind="bulletdive", air=True,
                        landing=dict(holdTime=impact - 1 / FPS, impactTime=impact, takeoff=False),
                        devastator=dict(effectStart=impact - 2 / 3, effectEnd=impact - 2 / 3 + .5,
                                        damageStart=impact - 2 / 3, damageEnd=impact - 2 / 3 + .1,
                                        targetBeginStart=impact - 2 / 3, targetBeginStop=impact - 2 / 3 + .1,
                                        radiusSource=0, damageSource=0, targetCountSource=0,
                                        travelPose=impact - 1 / FPS, sourceEffectOwner=125588,
                                        notes=["Source impact is SupportingSurface-driven, not the nominal flight duration.",
                                               "Selected low-height landing branch; other source height branches have different recovery leases.",
                                               "Radius/damage/count 0 mark unknown controller values and must not be interpreted as original zero damage."])))
    configs.append(dict(id="bulletdive_drop_land", stages=land, visualOnly=True))
    configs.append(dict(id="bulletdive_drop_flight", stages=flight[1:], visualOnly=True))
    # Effect teardown and body recovery use independent clocks. Metadata retains
    # the original hit/controller lease; a later visual return must not prolong
    # damage. Negative native reverse sentinels are never converted to seconds.
    recoveries = []
    for cfg in configs:
        if cfg.get("visualOnly") or "_aim_" in cfg["id"]:
            continue
        body = cfg["stages"]
        if cfg["kind"] == "bulletdive":
            recovery = land
            recovery_first = cfg["landing"]["impactTime"]
        else:
            count = 2 if cfg["kind"] in ("groundspike", "tendril") and not (cfg["kind"] == "tendril" and cfg["air"]) else 1
            recovery = body[-count:]
            recovery_first = sum(s["seconds"] for s in body[:-count])
        extra = cfg["devastator"]
        extra.update(recoveryStart=recovery_first-cfg["releaseTime"],
                     recoveryEnd=sum(s["seconds"] for s in body)-cfg["releaseTime"],
                     recoveryAnimation=cfg["id"]+"_recovery",
                     recoverySourceConfirmed=not (cfg["kind"] == "tendril" and cfg["air"]))
        if cfg["kind"] == "tendril":
            attack = extra["effectStart"]-.4
            extra.update(attackStart=attack, retractStart=attack+7/3,
                         retractEnd=attack+10/3, reverseVelocitySource=350,
                         radialBlur=[dict(begin=attack+.4,duration=.5,intensityStart=100,intensityEnd=0,sizeStart=0,sizeEnd=2,falloffStart=0,falloffEnd=10,sourceTrack=138880),
                                     dict(begin=attack+7/3,duration=1,intensityStart=30,intensityEnd=30,sizeStart=2,sizeEnd=0,falloffStart=10,falloffEnd=0,sourceTrack=147684)])
        elif cfg["kind"] == "groundspike":
            attack=extra["effectStart"]
            extra.update(attackStart=attack, retractDurationSource=.2,
                         retractTriggerConfirmed=False,
                         radialBlur=[dict(begin=attack,duration=.5,intensityStart=100,intensityEnd=0,sizeStart=0,sizeEnd=2,falloffStart=0,falloffEnd=10,sourceTrack=155644),
                                     dict(begin=attack+1/6,duration=.5,intensityStart=20,intensityEnd=0,sizeStart=0,sizeEnd=2,falloffStart=0,falloffEnd=10,sourceTrack=155816)])
        elif cfg["kind"] == "critical_pain":
            extra.update(attackStart=1.5,reverseVelocitySource=200,
                         retractTriggerConfirmed=False,
                         notes=extra["notes"]+["TendrilTrack.TimeReverse is 0/-1, TimeEnd is -1; runtime contact-to-return scheduling is an adaptation until the native controller callback is decoded."])
        recoveries.append(dict(id=cfg["id"]+"_recovery", stages=[dict(s) for s in recovery], visualOnly=True))
    configs.extend(recoveries)
    return configs


def health_settings(path):
    raw = path.read_bytes()
    data = zlib.decompress(raw[16:]) if path.suffix == ".rz" else raw
    marker = b"proto::PrototypeHealthBehaviour"
    pos = data.index(marker) + len(marker)
    # Verified local component version/flags, inherited scalar sequence and six
    # health-unlockable entries. Fail on another schema rather than guess floats.
    if data[pos:pos + 4] != bytes.fromhex("01000100"):
        raise ValueError("Unknown PrototypeHealthBehaviour component envelope")
    start = pos + 232
    if struct.unpack_from("<I", data, start)[0] != 6:
        raise ValueError("Expected six health unlockable entries")
    unlockables = [dict(unlockable=struct.unpack_from("<I", data, start + 4 + i * 12)[0],
                        healthAdded=struct.unpack_from("<f", data, start + 8 + i * 12)[0],
                        criticalMassHealthAdded=struct.unpack_from("<f", data, start + 12 + i * 12)[0]) for i in range(6)]
    fields = [("devastatorHealthCost", "f"), ("defaultHealthPercentage", "f"),
              ("criticalMassHealthGainFactor", "f"), ("criticalMassExitHealthLoss", "f"),
              ("regenRateMax", "f"), ("regenRateUnlockableFirst", "I"), ("regenRateUnlockableLast", "I"),
              ("regenTimeWithNoDamageMax", "f"), ("regenTimeWithNoDamageUnlockableFirst", "I"),
              ("regenTimeWithNoDamageUnlockableLast", "I"), ("consumeHealthMultiplier", "f"),
              ("consumeHealthMultiplierUnlockableFirst", "I"), ("consumeHealthMultiplierUnlockableLast", "I"),
              ("adrenalineSurgeThresholdHealth", "f"), ("adrenalineSurgeHealthOnEntry", "f"),
              ("adrenalineSurgeUndamageableTime", "f"), ("minTimeBetweenAdrenalineDevastators", "f"),
              ("lowHealthThreshold", "f"), ("adrenalineSurgeUnlockable", "I")]
    at = start + 4 + 6 * 12
    values = {name: struct.unpack_from("<" + typ, data, at + i * 4)[0] for i, (name, typ) in enumerate(fields)}
    if values["devastatorHealthCost"] != 400 or values["adrenalineSurgeUnlockable"] != 21:
        raise ValueError("Health settings do not match the reviewed local schema")
    return dict(sourceSha256=hashlib.sha256(data).hexdigest(), componentOffset=hex(pos),
                registrationVA="0x102c40ac..0x102c43c6", unlockables=unlockables, fields=values,
                boundary="Configuration fields only; native health callback/control flow and unit conversion are not established.")


def validate(payload):
    meta = payload["blacklight"]
    end = max(t for track in payload["animation"] for t in track["time"])
    assert 4 <= meta["durationTicks"] <= 200
    assert meta["durationTicks"] == math.ceil((end + TRANSITION) * 20 - .00001)
    for track in payload["animation"]:
        assert len(track["time"]) == len(track["transform"])
        assert all(a < b for a, b in zip(track["time"], track["time"][1:]))
        assert all(len(m) == 16 and all(math.isfinite(v) for v in m) for m in track["transform"])
    if "contactStartTick" in meta:
        start, stop = meta["contactStartTick"], meta["contactEndTick"]
        assert 2 <= start < stop < meta["durationTicks"]
        charge = meta["charge"]
        assert charge["windupTicks"] == math.ceil(charge["releaseTime"] * 20 - .0001)
        assert start / 20 + .001 >= charge["releaseTime"] + TRANSITION
        if "landing" in meta:
            land = meta["landing"]
            assert 0 <= land["holdTime"] < land["impactTime"] < end
            assert start >= math.ceil((land["impactTime"] + TRANSITION) * 20 - .001)


def validate_bullet_landing(engine, rt, frames, roots, coords, times, impact):
    """Compare the actual impact sample to the independently decoded source pose.

    A valid clock alone cannot detect an inverted fall pose blended into landing.
    Check all 67 source world matrices, then the emitted native biped's grounded
    feet and head order after Root/Coord separation and the native render scale.
    Numeric pose diagnostics are returned only to the private audit.
    """
    np = engine.np
    landing = next(s for s in rt.stages
                   if s["sourceName"] == "alex_loco_jump_heavy_all" and s["start"] == 110)
    if landing["blend"] != 0 or abs(landing["begin"] - impact) > 1e-8:
        raise ValueError("Bulletdive impact must hard-cut to the native zero-blend landing track")
    index = round(impact * FPS)
    if abs(times[index] - impact) > 1e-8:
        raise ValueError("Bulletdive landing impact does not align with its authored sample")
    expected = landing["sourceObject"].world(110 / 30)
    for matrix in expected.values():
        matrix[:3, 3] += landing["offset"]
    actual = rt.staged_world(times[index])
    source_error = max(float(np.max(np.abs(actual[j] - expected[j]))) for j in expected)
    if source_error > 1e-7:
        raise ValueError("Bulletdive impact retained a previous pose instead of source heavy frame 110")
    native = {}
    for name in rt.target.names:
        local = roots[index] if name == "Root" else rt.target.rest[name] @ engine.matrix(frames[index][name])
        parent = rt.target.parents[name]
        native[name] = (native[parent] if parent else np.eye(4)) @ local
    render_scale = .9375
    feet = [(native["Leg_" + side] @ np.array([0., .37472, 0., 1.]))[2] * render_scale
            for side in ("R", "L")]
    head = native["Head"][2, 3] * render_scale
    if min(feet) < -.02 or min(feet) > .03 or head - min(feet) < .25:
        raise ValueError("Bulletdive landing is not a grounded, upright head-above-feet pose")
    return dict(sourceAnimation="alex_loco_jump_heavy_all", sourceFrame=110,
                sourceTrack=126244, sourceBlendInTime=0, impactSample=times[index],
                sourceWorldMatrixMaxError=source_error,
                sourceHeadZ=float(expected["Head"][2, 3] - landing["offset"][2]),
                sourceAnkleZ=[float(expected["Ankle_" + side][2, 3] - landing["offset"][2])
                              for side in ("R", "L")],
                nativeRootZ=float(native["Root"][2, 3] * render_scale),
                nativeHeadZ=float(head), nativeFeetZ=[float(v) for v in feet],
                diagnosticCoordZ=float(coords[index][2, 3] * render_scale),
                boundary="Source and exported pose validation; actual GPU/onGround playback remains a separate game check.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-source", type=Path, required=True)
    parser.add_argument("--retarget", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit-output", type=Path, required=True)
    parser.add_argument("--health-source", type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    for path in (args.output, args.audit_output):
        if path.resolve() == repo or repo in path.resolve().parents:
            parser.error("Private output must be outside the repository")
    if args.output.resolve() == args.baseline.resolve():
        parser.error("Use a separate profile; never overwrite the baseline")
    args.output.mkdir(parents=True, exist_ok=True)
    before = {}
    for path in args.baseline.iterdir():
        if path.is_file():
            before[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
            shutil.copy2(path, args.output / path.name)
    spec = importlib.util.spec_from_file_location("private_retarget", args.retarget / "numpy_retarget.py")
    engine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(engine)
    engine.PRIVATE = args.private_source
    records = {r["name"]: r for r in json.loads((args.private_source / "decoded-all-r25/index_PRIVATE.json").read_text())["records"]}
    decoded = {}

    def source(name):
        if name not in decoded:
            record = records[name]
            raw = (args.private_source / "decoded-all-r25" / record["file"]).read_bytes()
            if hashlib.sha256(raw).hexdigest() != record["fileSha256"]:
                raise ValueError("Source snapshot changed: " + name)
            data = json.loads(raw)
            if data["frame_rate"] != 30 or not record["allChannelsDecoded"]:
                raise ValueError("Incomplete or unsupported source clip: " + name)
            decoded[name] = data
        return decoded[name]

    audit = []
    written = set()
    for cfg in recipes():
        jobs = [(cfg["id"], cfg["stages"], bool(cfg.get("visualOnly")))]
        if "chargeStages" in cfg and not cfg.get("chargeKey"):
            jobs.append((cfg["id"] + "_charge", cfg["chargeStages"], True))
        for key, stages, visual in jobs:
            if key in written:
                continue
            written.add(key)
            segments = [dict(s, source=source(s["sourceName"])) for s in stages]
            rt = engine.Retarget(segments[0]["source"], skeleton="alex")
            if len(rt.source.joints) != 67:
                raise ValueError("Expected the reviewed 67-joint Alex source skeleton")
            frames, report = rt.generate(segments)
            times = [i / FPS for i in range(len(frames))]
            motion_origin = rt.staged_world(0)["Motion_Root"][:3, 3]
            roots, coords = [], []
            recompose = 0
            for t, frame in zip(times, frames):
                full = rt.target.rest["Root"] @ engine.matrix(frame["Root"])
                delta = (rt.staged_world(t)["Motion_Root"][:3, 3] - motion_origin) * rt.scale
                residual = full.copy()
                residual[:3, 3] -= delta
                residual[2, 3] -= (motion_origin[2] - rt.source.bind["Motion_Root"][2, 3]) * rt.scale
                coord = engine.np.eye(4)
                coord[:3, 3] = delta
                base = full.copy()
                base[2, 3] -= (motion_origin[2] - rt.source.bind["Motion_Root"][2, 3]) * rt.scale
                recompose = max(recompose, float(engine.np.max(engine.np.abs(coord @ residual - base))))
                roots.append(residual)
                coords.append(coord)
            landing_quality = None
            if key == "bulletdive_drop":
                landing_quality = validate_bullet_landing(
                    engine, rt, frames, roots, coords, times, cfg["landing"]["impactTime"])
            # In the native air node the same owner co-owns jump leg animation.
            # Preserve its decoded lower-body curves without applying another
            # Motion_Root displacement or inventing new arm/shoulder rotations.
            air_legs = bool(cfg.get("air") and cfg.get("kind") in ("groundspike", "tendril"))
            if air_legs:
                jump_source = source("alex_loco_jump_long_D")
                legs = engine.Retarget(jump_source, skeleton="alex")
                start = cfg["releaseTime"]
                end = cfg["releaseTime"] + (1 if cfg["kind"] in ("groundspike", "tendril") else 1.5)
                leg_segments = [stage("alex_loco_jump_long_D", 10, 20, start, 71916 if cfg["kind"] == "groundspike" else 75228),
                                stage("alex_loco_jump_long_D", 20, 30, end - start, 81992),
                                freeze("alex_loco_jump_long_D", 30, max(1 / FPS, times[-1] - end), 77184)]
                leg_frames, _ = legs.generate(leg_segments)
                through = cfg["landing"]["holdTime"] if cfg.get("landing") else cfg["releaseTime"] + cfg["devastator"]["effectEnd"]
                for i, t in enumerate(times):
                    if t <= through + 1e-6:
                        leg = leg_frames[min(i, len(leg_frames) - 1)]
                        for bone in ("Thigh_R", "Leg_R", "Knee_R", "Thigh_L", "Leg_L", "Knee_L"):
                            frames[i][bone] = leg[bone]
            tracks = [dict(name=bone, time=times, transform=[(roots[i] if bone == "Root" else rt.target.rest[bone] @ engine.matrix(f[bone])).reshape(-1).tolist() for i, f in enumerate(frames)]) for bone in rt.target.names]
            tracks.append(dict(name="Coord", time=times, transform=[m.reshape(-1).tolist() for m in coords]))
            meta = dict(durationTicks=math.ceil((times[-1] + TRANSITION) * 20 - .00001), transitionSeconds=TRANSITION)
            if not visual:
                extra = cfg["devastator"]
                contact = cfg["releaseTime"] + extra["damageStart"] + TRANSITION
                stop = cfg["releaseTime"] + extra["damageEnd"] + TRANSITION
                meta.update(contactStartTick=max(2, math.ceil(contact * 20 - .001)),
                            contactEndTick=math.ceil(stop * 20 - .001),
                            charge=dict(windupTicks=math.ceil(cfg["releaseTime"] * 20 - .0001),
                                        holdTicks=cfg["holdTicks"], releaseTime=cfg["releaseTime"],
                                        animation=cfg.get("chargeKey", cfg["id"] + "_charge")),
                            devastator=extra)
                meta["contactWindows"] = [dict(startTick=meta["contactStartTick"], endTick=meta["contactEndTick"])]
                if cfg.get("landing"):
                    meta["landing"] = cfg["landing"]
            payload = dict(format="MATRIX", animation=tracks, blacklight=meta,
                           source=[dict(animation=s["sourceName"], sha256=records[s["sourceName"]]["fileSha256"], start=s["start"], end=s["end"], seconds=s["seconds"], owner=s["owner"], track=s.get("track"), note=s.get("note", "")) for s in stages],
                           privateNotice="Original-derived local-only. Source 67-joint curves retargeted to the shared native biped. Coord is diagnostic; authoritative movement must not apply it twice.")
            validate(payload)
            encoded = json.dumps(payload, separators=(",", ":"), allow_nan=False).encode()
            out = args.output / (key + ".json")
            out.write_bytes(encoded)
            assert json.loads(out.read_bytes())["blacklight"] == meta
            audit.append(dict(id=key, path=str(out), sha256=hashlib.sha256(encoded).hexdigest(),
                              seconds=times[-1], meta=meta, stages=payload["source"],
                              footEndpointMaxError=report["footEndpointMaxError"],
                              rootCoordRecomposeMaxError=recompose, sourceJoints=67,
                              landingPoseQuality=landing_quality,
                              airLegPartitionAdapted=air_legs, inGameAccepted=False))
            print(key, "seconds", round(times[-1], 6), "frames", len(frames), "meta", json.dumps(meta, ensure_ascii=False), flush=True)
    for name, digest in before.items():
        assert hashlib.sha256((args.baseline / name).read_bytes()).hexdigest() == digest
        assert hashlib.sha256((args.output / name).read_bytes()).hexdigest() == digest
    summary = dict(baselineCount=len(before), addedCount=len(written), original224Unchanged=len(before) == 224,
                   profiles=audit, resourceBoundary="Private curves never written into the repository.")
    if args.health_source:
        summary["healthSettings"] = health_settings(args.health_source)
    args.audit_output.parent.mkdir(parents=True, exist_ok=True)
    args.audit_output.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf8")
    print("COMPLETE", len(written), "new motions; baseline", len(before), "files unchanged", flush=True)


if __name__ == "__main__":
    main()
