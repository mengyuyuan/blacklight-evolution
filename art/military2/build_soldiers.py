"""Original layered soldiers. Reproducible native Java + independent 2x PNG atlases.

No original-game assets, network requests, Gradle or Minecraft processes are used.
Run without --install to stage only; --install writes the four owned production files.
The preview reader separately extracts piece(...) calls from the resulting Java.
"""
from pathlib import Path
import argparse
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
STAGE = Path(__file__).resolve().parent
ATLAS, SCALE = 256, 2
PIECES = []
cursor = [2, 2, 0]


def part(group, parent, name, box, material, detail=""):
    x, y, z, w, h, d = box
    tw, th = math.ceil(2 * (w + d)), math.ceil(h + d)
    if cursor[0] + tw + 2 > ATLAS:
        cursor[0] = 2
        cursor[1] += cursor[2] + 2
        cursor[2] = 0
    u, v = cursor[:2]
    cursor[0] += tw + 2
    cursor[2] = max(cursor[2], th)
    assert v + th + 2 < ATLAS, (name, cursor)
    PIECES.append(dict(group=group, parent=parent, name=name, uv=[u, v], box=box,
                       material=material, detail=detail))


def geometry():
    # Empty vanilla HumanoidModel parts remain the animation / grab-compatible rig.
    part("all", "head", "uniform_head", [-4, -8, -4, 8, 8, 8], "skin", "face")
    part("all", "body", "uniform_torso", [-4, 0, -2, 8, 12, 4], "cloth", "jacket")
    for side, x in [("right", -3), ("left", -1)]:
        part("all", side + "_arm", "uniform_sleeve", [x, -2, -2, 4, 12, 4], "cloth", "sleeve")
        part("all", side + "_leg", "uniform_trouser", [-2, 0, -2, 4, 12, 4], "cloth", "trouser")
        part("all", side + "_leg", "uniform_boot", [-2.06, 8.2, -2.4, 4.12, 3.8, 4.55], "boot", "laces")
        part("all", side + "_leg", "uniform_sole", [-2.1, 11.45, -2.55, 4.2, .6, 4.75], "rubber")
    # Military: compact helmet, open face and asymmetric comms headset.
    part("military", "head", "gear_helmet_crown", [-4.075, -8.65, -4.075, 8.15, 1.55, 8.15], "helmet", "helmet")
    part("military", "head", "gear_helmet_rim", [-4.25, -7.1, -4.2, 8.5, 1.4, 8.4], "helmet", "helmet")
    part("military", "head", "gear_helmet_rear", [-4.2, -5.7, 3.7, 8.4, 2.4, .65], "helmet")
    part("military", "head", "gear_helmet_right", [-4.3, -5.7, -3, .6, 1.9, 6.7], "helmet")
    part("military", "head", "gear_helmet_left", [3.7, -5.7, -3, .6, 1.9, 6.7], "helmet")
    part("military", "head", "gear_helmet_brim", [-4.3, -5.95, -4.65, 8.6, .45, 1.2], "helmet")
    part("military", "head", "gear_headset", [-4.65, -4.8, -.9, .7, 2.25, 2.1], "rubber", "headset")
    part("military", "head", "gear_microphone", [-4.45, -2.8, -3.6, .35, .4, 3.5], "metal")
    part("military", "head", "gear_stowed_goggles", [-2.9, -7.35, -4.52, 5.8, 1.3, .55], "lens", "goggles")
    # BW: hood panels, sealed mask and separate lenses; no full cube over the face.
    part("bw", "head", "gear_hood_crown", [-4.075, -8.6, -4.075, 8.15, 1.55, 8.15], "hood")
    part("bw", "head", "gear_hood_rim", [-4.3, -7.05, -4.25, 8.6, 1.25, 8.6], "hood")
    part("bw", "head", "gear_hood_rear", [-4.25, -5.8, 3.8, 8.5, 5.9, .65], "hood", "hood")
    part("bw", "head", "gear_hood_right", [-4.35, -5.8, -4.2, .65, 5.7, 8.6], "hood")
    part("bw", "head", "gear_hood_left", [3.7, -5.8, -4.2, .65, 5.7, 8.6], "hood")
    part("bw", "head", "gear_mask_seal", [-3.75, -5.9, -4.48, 7.5, 6.1, .65], "rubber", "seal")
    for side, x in [("right", -3.3), ("left", .35)]:
        part("bw", "head", "gear_lens_" + side, [x, -5.25, -4.88, 2.95, 1.45, .5], "lens", "lens")
    part("bw", "head", "gear_respirator", [-2.6, -3.35, -5.2, 5.2, 2.85, 1.15], "mask", "vents")
    for side, x in [("right", -4.05), ("left", 2.35)]:
        part("bw", "head", "gear_filter_" + side, [x, -2.85, -5.4, 1.7, 2.1, 1.65], "filter", "filter")
    # Main carrier is a front/back plate and straps rather than a box around the torso.
    part("all", "body", "gear_front_carrier", [-3.8, .8, -2.7, 7.6, 7.55, .8], "carrier", "molle")
    part("all", "body", "gear_rear_carrier", [-3.8, .8, 1.95, 7.6, 7.55, .8], "carrier", "molle")
    for side, x in [("right", -3.65), ("left", 2.2)]:
        part("all", "body", "gear_strap_" + side, [x, -.15, -2.65, 1.45, 2, 5.3], "webbing", "strap")
    for i, x in enumerate([-3.15, -.9, 1.35]):
        part("all", "body", "gear_magpouch_" + str(i), [x, 4.65, -4.05, 1.8, 3.2, 1.45], "pouch", "pouch")
    part("all", "body", "gear_belt", [-4.12, 9.25, -2.2, 8.24, 1.1, 4.4], "webbing", "belt")
    part("all", "body", "gear_utility_pouch", [3.6, 8.7, -.1, 1.45, 2.8, 2.1], "pouch", "pouch")
    part("all", "body", "gear_rank_patch", [-3.25, 1.8, -3.62, 2.15, 2.1, .3], "patch")
    part("all", "body", "gear_faction_tab", [.8, 1.85, -3.62, 2.35, 1, .3], "identity", "identity")
    # Radio / patrol pack and heavy supply pack are mutually exclusive.
    part("regular", "body", "gear_patrol_pack", [-2.7, 2, 2.75, 5.4, 6.1, 2.3], "pack", "pack")
    part("regular", "body", "gear_radio", [-4.15, 1.15, 2.75, 1.65, 3.75, 1.7], "radio", "radio")
    part("heavy", "body", "gear_supply_pack", [-3.25, 1, 2.75, 6.5, 8.6, 3.5], "pack", "pack")
    part("heavy", "body", "gear_supply_lid", [-3.4, .65, 2.65, 6.8, 1.65, 3.7], "webbing", "strap")
    part("all", "body", "gear_aerial", [-3.5, -2.3, 3.75, .3, 4, .3], "metal")
    part("heavy", "body", "gear_sternum_plate", [-3.55, .7, -3.25, 7.1, 3.5, .65], "plate", "plate")
    part("heavy", "body", "gear_abdomen_plate", [-2.7, 8, -2.75, 5.4, 2.25, .65], "plate", "plate")
    part("bwHeavy", "body", "gear_neck_guard", [-3.5, -.65, -2.45, 7, 1.45, 4.9], "plate", "plate")
    for side, arm_x in [("right", -3), ("left", -1)]:
        part("all", side + "_arm", "gear_glove", [arm_x - .06, 6.85, -2.06, 4.12, 3.3, 4.12], "glove", "glove")
        part("heavy", side + "_arm", "gear_shoulder", [arm_x - .15, -2.25, -2.35, 4.3, 2.9, 4.7], "plate", "plate")
        part("heavy", side + "_arm", "gear_forearm", [arm_x - .08, 4.4, -2.35, 4.16, 2.45, .55], "plate", "plate")
        part("all", side + "_leg", "gear_kneepad", [-1.55, 4.75, -2.5, 3.1, 2.65, .7], "knee", "knee")
        part("heavy", side + "_leg", "gear_shin", [-1.55, 7.45, -2.55, 3.1, 1.8, .65], "plate", "plate")
        part("bwHeavy", side + "_leg", "gear_thigh", [-1.6, 1.1, -2.35, 3.2, 2.8, .55], "plate", "plate")
    # Weapon-long axis is local +Y: the posed right arm takes it along world -Z.
    # Four actual-item families, each independently hidden when the hand is empty.
    for group in ["weapon_rifle", "weapon_machine", "weapon_grenade"]:
        machine, grenade = group == "weapon_machine", group == "weapon_grenade"
        part(group, "right_arm", "stock", [-1.8, 3.2, -1.8, 1.6, 5, 2.45], "gunstock", "stock")
        part(group, "right_arm", "receiver", [-1.95, 8, -2.25, 1.9, 5.4, 2.45], "gunmetal", "receiver")
        part(group, "right_arm", "grip", [-1.65, 9.1, .15, 1.3, 1.4, 2.6], "rubber")
        part(group, "right_arm", "handguard", [-1.8, 13.4, -2.05, 1.6, 5.7 if machine else 4.6, 1.95], "gunstock", "rail")
        part(group, "right_arm", "barrel", [-1.43, 19.1 if machine else 18, -1.45, .85, 6 if machine else 3.8, .85], "metal")
        part(group, "right_arm", "muzzle", [-1.57, 24.7 if machine else 21.5, -1.59, 1.14, 1.15, 1.14], "rubber", "muzzle")
        part(group, "right_arm", "sight", [-1.4, 10, -3.15, .8, 2.4, .9], "metal", "sight")
        if grenade:
            part(group, "right_arm", "drum", [-2.65, 10.65, .1, 3.3, 3.2, 3.1], "gunmetal", "drum")
            part(group, "right_arm", "wide_bore", [-2, 16.1, -2.25, 2, 4.3, 2.2], "metal", "muzzle")
        elif machine:
            part(group, "right_arm", "ammo_box", [-2.7, 11, .1, 3.4, 3.6, 3.1], "pack", "ammobox")
            part(group, "right_arm", "bipod", [-1.65, 19.5, -.5, 1.3, 4.3, .5], "metal")
        else:
            part(group, "right_arm", "magazine", [-1.65, 11.7, .1, 1.3, 2.5, 3.25], "gunmetal", "magazine")
    part("weapon_launcher", "right_arm", "tube", [-3.25, -.6, -4, 3.5, 19.8, 3.5], "launcher", "launcher")
    part("weapon_launcher", "right_arm", "rear_ring", [-3.5, -.7, -4.25, 4, 1.15, 4], "rubber", "muzzle")
    part("weapon_launcher", "right_arm", "front_ring", [-3.5, 18.2, -4.25, 4, 1.2, 4], "rubber", "muzzle")
    part("weapon_launcher", "right_arm", "grip", [-2, 8.1, -.5, 1.4, 1.6, 3.3], "rubber")
    part("weapon_launcher", "right_arm", "trigger_mount", [-2.25, 6.8, -1, 1.9, 4, .8], "gunmetal")
    part("weapon_launcher", "right_arm", "optic", [.25, 9.7, -3.7, 1.2, 3.7, 1.35], "lens", "sight")
    # Eight project-specific insignia. PRIVATE intentionally has a single small bar.
    for rank in range(8):
        count = [1, 2, 3, 1, 2, 1, 3, 4][rank]
        for i in range(count):
            square = rank >= 5
            part("rank_" + str(rank), "body", "mark_" + str(i),
                 [-2.95 + (i % 2) * .8 if square else -3.02,
                  2.25 + (i // 2) * .75 if square else 2.15 + i * .47,
                  -3.82, .52 if square else 1.65, .52 if square else .22, .14], "rank")
    command_geometry()


def command_geometry():
    """Append only: the military.2 UV islands and authored pixel colours stay put."""
    for faction in ["military", "bw"]:
        g=faction+"Role_team_leader"
        # Battlefield leader: helmet/hood band, arm recognition bars and extra radio kit.
        part(g,"head","role_leader_crown_stripe",[-.7,-8.77,-4.12,1.4,.18,8.24],"recognition")
        part(g,"head","role_leader_front_stripe",[-3.9,-6.55,-4.8,7.8,.45,.2],"recognition")
        part(g,"head","role_leader_rear_stripe",[-.7,-7.05,4.4,1.4,2.3,.18],"recognition")
        for side,x in [("right",-3.12),("left",-1.12)]:
            for i in range(2):
                part(g,side+"_arm","role_leader_arm_bar_"+str(i),[x,1.1+i*.75,-2.15,4.24,.42,.26],"recognition")
        part(g,"body","role_leader_radio_panel",[2.45,2.7,-3.6,1.6,1.5,.75],"radio","radio")
        part(g,"body","role_leader_radio_battery",[-2.4,1.2,5.1,4.8,3.6,1.1],"radio","radio")
        part(g,"body","role_leader_long_aerial",[1.6,-4.1,5.5,.28,5.6,.28],"metal")
        part(g,"head","role_leader_comms",[4.32,-4.9,-.75,.75,2.4,2],"radio","radio")
        for role in ["officer","commander"]:
            g=faction+"Role_"+role
            commander=role=="commander"
            # Officers replace their standard patrol backpack instead of stacking boxes.
            part(g,"body","role_command_pack",[-2.8,1.6,2.8,5.6,5.5,1.8],"command_fabric","pack")
            part(g,"body","role_command_pack_frame",[-3,1.35,2.7,6,.65,2],"webbing","strap")
            part(g,"body","role_command_aerial",[-2.6,-2.8,3.5,.28,4.3,.28],"metal")
            part(g,"body","role_map_case",[3.7,6.25,-.8,1.3,5.1,3.7],"command_leather","mapcase")
            part(g,"left_arm","role_field_terminal",[-.25,3.8,-2.65,2.5,2,.65],"radio","terminal")
            for side,x in [("right",-3.15),("left",-1.15)]:
                part(g,side+"_arm","role_shoulder_board",[x,-2.65,-1.75,4.3,.65,3.5],"command_trim","epaulette")
                part(g,side+"_arm","role_shoulder_core",[x+.4,-2.81,-1.25,3.5,.2,2.5],"command_fabric","epaulette")
            # Stepped diagonal strap: original field-command carrying harness.
            for i in range(4):
                part(g,"body","role_cross_strap_"+str(i),[2.2-i*1.2,3.9+i*.7,-4.3,.85,.78,.35],"command_leather","strap")
            if faction=="military":
                part(g,"head","role_cap_crown",[-4.22,-8.8,-4.15,8.44,2.5,8.3],"command_fabric","cap")
                part(g,"head","role_cap_top",[-4.08,-9.3,-4.02,8.16,.5,8.04],"command_fabric")
                part(g,"head","role_cap_band",[-4.3,-6.3,-4.25,8.6,.75,8.5],"command_trim" if commander else "command_leather","capband")
                part(g,"head","role_cap_visor",[-4.35,-5.75,-6.05,8.7,.42,3.35],"command_leather","visor")
                part(g,"head","role_cap_badge",[-.7,-8,-4.4,1.4,1.35,.3],"command_trim","badge")
                part(g,"head","role_officer_headset",[-4.7,-4.8,-.9,.7,2.2,2],"rubber","headset")
                part(g,"head","role_officer_boom",[-4.5,-2.8,-3.5,.35,.35,3.2],"metal")
            else:
                # Command hood/hard-cap surrounds the existing gas-mask and lenses.
                part(g,"head","role_hood_crown",[-4.12,-9.05,-4.12,8.24,1.8,8.24],"command_fabric")
                part(g,"head","role_hood_band",[-4.45,-7.25,-4.4,8.9,1.5,8.8],"command_fabric")
                part(g,"head","role_hood_rear",[-4.4,-5.75,3.85,8.8,5.9,.65],"command_fabric","hood")
                part(g,"head","role_hood_right",[-4.5,-5.75,-4.3,.75,5.9,8.7],"command_fabric")
                part(g,"head","role_hood_left",[3.75,-5.75,-4.3,.75,5.9,8.7],"command_fabric")
                part(g,"head","role_visor_bridge",[-3.7,-5.85,-4.9,7.4,.42,.55],"command_trim")
                part(g,"head","role_hood_badge",[-1,-7.1,-4.66,2,.9,.28],"recognition","badge")
                part(g,"head","role_hood_rear_band",[-4.5,-6.9,4.42,9,.55,.2],"recognition")
            if commander:
                # Flat epaulettes, high split collar and short coat panels read as command dress.
                for side,x in [("right",-4.45),("left",3.6)]:
                    part(g,"body","role_command_collar_"+side,[x,-.8,-2.6,.85,2.65,5.15],"command_fabric")
                for side,x in [("right",-4.6),("left",2.5)]:
                    part(g,"body","role_collar_lapel_"+side,[x,-.3,-3.15,2.1,2.15,.65],"command_trim")
                part(g,"body","role_collar_rear",[-4.45,-.8,2.05,8.9,1.6,.6],"command_trim")
                part(g,"body","role_command_mantle",[-4.8,.05,2.25,9.6,.75,1.05],"command_trim")
                for side,x in [("right",-3.25),("left",-1.25)]:
                    part(g,side+"_arm","role_command_board_edge",[x,-2.45,-2.15,4.5,.9,.35],"recognition")
                if faction=="bw":
                    for side,x in [("right",-4.15),("left",1.55)]:
                        part(g,"head","role_commander_visor_flash_"+side,[x,-6.85,-4.82,2.6,.48,.2],"recognition")
                for side,x in [("right",-4.1),("left",.45)]:
                    part(g,"body","role_coat_front_"+side,[x,10.25,-2.65,3.65,3.7,.6],"command_fabric","coat")
                part(g,"body","role_coat_rear",[-4.1,9.8,2.25,8.2,4.15,.65],"command_fabric","coat")
                part(g,"body","role_belt_buckle",[-1,9.25,-2.75,2,1.05,.65],"command_trim","badge")
                part(g,"body","role_command_back_tab",[-1.65,3.25,4.62,3.3,2,.25],"recognition","badge")


PALETTES = {
    "military": dict(cloth=(111, 116, 83), camo=(69, 80, 60), camo2=(155, 147, 111),
                     helmet=(118, 126, 87), carrier=(83, 93, 65), webbing=(71, 80, 55),
                     pouch=(98, 107, 72), pack=(99, 109, 76), plate=(141, 145, 107),
                     hood=(88, 96, 68), mask=(54, 66, 57), knee=(75, 85, 61),
                     skin=(183, 140, 111), lens=(88, 126, 116), identity=(184, 171, 113),
                     recognition=(210,174,75),command_fabric=(71,87,62),
                     command_leather=(55,54,43),command_trim=(196,180,115)),
    "blackwatch": dict(cloth=(136, 141, 143), camo=(109, 117, 121), camo2=(158, 162, 158),
                       helmet=(51, 60, 64), carrier=(48, 58, 63), webbing=(41, 49, 54),
                       pouch=(62, 72, 76), pack=(70, 81, 86), plate=(169, 174, 169),
                       hood=(76, 86, 91), mask=(55, 67, 72), knee=(61, 72, 77),
                       skin=(171, 133, 112), lens=(124, 161, 151), identity=(195, 78, 62),
                       recognition=(187,65,50),command_fabric=(174,181,175),
                       command_leather=(45,58,64),command_trim=(84,102,106))}
COMMON = dict(boot=(52, 49, 43), rubber=(30, 36, 39), glove=(51, 57, 55),
              metal=(85, 97, 100), gunmetal=(56, 69, 75), gunstock=(43, 53, 55),
              filter=(91, 102, 106), radio=(44, 57, 59), patch=(38, 48, 49),
              rank=(223, 210, 158), launcher=(95, 109, 76))


def faces(p):
    u, v = p["uv"]
    _, _, _, w, h, d = p["box"]
    return {"top": (u+d, v, w, d), "bottom": (u+d+w, v, w, d),
            "left": (u, v+d, d, h), "front": (u+d, v+d, w, h),
            "right": (u+d+w, v+d, d, h), "back": (u+2*d+w, v+d, w, h)}


def atlas(faction):
    image = Image.new("RGBA", (ATLAS*SCALE, ATLAS*SCALE), (0, 0, 0, 0))
    palette = COMMON | PALETTES[faction]
    for index, p in enumerate(PIECES):
        for face, (u, v, w, h) in faces(p).items():
            px,py=math.floor(u*SCALE),math.floor(v*SCALE)
            pw=max(1,math.ceil((u+w)*SCALE)-px)
            ph=max(1,math.ceil((v+h)*SCALE)-py)
            rgb = palette[p["material"]]
            tile = Image.new("RGBA", (pw, ph), (*rgb, 255))
            draw = ImageDraw.Draw(tile)
            rng = random.Random(index*49 + sum(map(ord, face)))
            material, detail = p["material"], p["detail"]
            for y in range(ph):
                for x in range(pw):
                    n = rng.randint(-5, 5)
                    tile.putpixel((x, y), tuple(max(0, min(255, c+n)) for c in rgb)+(255,))
            def rect(box, color):
                x0,y0,x1,y1 = box
                draw.rectangle((int(x0),int(y0),max(int(x0),int(x1)),max(int(y0),int(y1))), fill=color)
            light = tuple(min(255, c+22) for c in rgb)
            dark = tuple(max(0, c-25) for c in rgb)
            if material == "cloth":
                for _ in range(max(2, pw*ph//15)):
                    x,y = rng.randrange(pw),rng.randrange(ph)
                    rect((x,y,x+rng.randint(1,4),y+rng.randint(0,2)), palette[rng.choice(["camo","camo2"])])
                draw.line((0,0,0,ph-1), fill=dark)
                draw.line((pw-1,0,pw-1,ph-1), fill=light)
            if material == "skin" and face == "top":
                rect((0,0,pw-1,ph-1),(62,52,45))
            if detail == "sleeve" and face == "bottom":
                rect((0,0,pw-1,ph-1),palette["skin"])
            elif material in ("plate", "carrier", "pouch", "pack", "gunmetal", "launcher", "helmet", "mask", "knee", "command_fabric", "command_leather", "command_trim"):
                draw.line((1,0,pw-2,0), fill=light)
                draw.line((0,ph-1,pw-1,ph-1), fill=dark)
                if pw>4 and ph>4:
                    draw.point((1,1), fill=light)
                    draw.point((pw-2,ph-2), fill=dark)
            if detail == "epaulette" and face == "top":
                draw.rectangle((0,0,pw-1,ph-1),outline=light)
                for x in range(2,pw-1,3):draw.line((x,1,x,ph-2),fill=dark)
            if face in ("front", "back", "left", "right"):
                if detail in ("molle", "pack"):
                    for y in range(4,ph-1,4):
                        draw.line((1,y,pw-2,y), fill=dark, width=1)
                        for x in range(2,pw,4): draw.point((x,y-1),fill=light)
                if detail in ("pouch", "pack", "ammobox"):
                    draw.line((0,ph*.28,pw-1,ph*.28),fill=dark)
                    rect((pw*.42,ph*.22,pw*.58,ph*.47), palette["webbing"])
                    if ph>8: draw.line((2,ph-2,pw-3,ph-2),fill=light)
                if detail in ("rail", "receiver", "magazine", "vents", "filter", "drum", "radio"):
                    for y in range(2,ph-1,3): draw.line((1,y,pw-2,y),fill=dark)
                if detail == "plate" and pw>5 and ph>4:
                    draw.line((2,2,pw-3,2,pw-3,ph-3),fill=light)
                if detail == "terminal" and face == "front":
                    rect((1,1,pw-2,ph-2),(68,117,108))
                    draw.line((1,ph//2,pw-2,ph//2),fill=(146,177,151))
                if detail == "mapcase":
                    draw.line((1,ph*.28,pw-2,ph*.28),fill=dark)
                    rect((pw*.4,ph*.2,pw*.6,ph*.5),palette["command_trim"])
                if detail == "coat":
                    draw.line((1,0,1,ph-1),fill=light)
                    draw.line((1,ph-2,pw-2,ph-2),fill=dark)
                if detail == "laces" and face == "front":
                    for y in range(1,ph-3,2): draw.line((pw*.35,y,pw*.65,y),fill=(119,110,89))
                if detail == "launcher":
                    rect((0,ph*.14,pw,ph*.18),(192,174,87))
                    rect((0,ph*.73,pw,ph*.77),(192,174,87))
                if detail == "identity" and face == "front":
                    rect((1,1,pw-2,ph-2),palette["identity"])
                    if faction == "blackwatch": draw.line((pw*.5,0,pw*.5,ph),fill=(30,40,43))
                if material == "lens":
                    rect((1,1,pw-2,max(1,ph//3)), tuple(min(255,c+47) for c in rgb))
                    draw.line((0,ph-1,pw-1,ph-1),fill=(32,61,62))
                if material == "skin":
                    if face == "front":
                        # Fully authored face also remains underneath sealed BW protection.
                        rect((0,0,pw-1,3),(57,47,41))
                        rect((0,3,1,9),(72,56,45)); rect((pw-2,3,pw-1,9),(72,56,45))
                        rect((3,6,6,6),(70,50,42)); rect((9,6,12,6),(70,50,42))
                        rect((3,7,6,8),(213,208,179)); rect((9,7,12,8),(213,208,179))
                        rect((5,7,6,8),(46,56,49)); rect((9,7,10,8),(46,56,49))
                        rect((7,8,8,11),(158,112,88)); rect((6,13,10,13),(108,71,61))
                        draw.line((3,14,5,15,10,15,12,14),fill=(150,112,91))
                    elif face in ("top","back"):
                        rect((0,0,pw-1,ph-1),(62,52,45))
                        if face=="back": rect((0,ph-3,pw-1,ph-1),palette["skin"])
                    else:
                        rect((0,0,pw-1,4),(62,52,45))
                if detail == "sleeve":
                    # Last 3 model units are uncovered hands when gear is off.
                    rect((0,ph-6,pw-1,ph-1),palette["skin"])
                    draw.line((0,ph-7,pw-1,ph-7),fill=palette["webbing"])
                if detail == "jacket":
                    if face == "front":
                        draw.line((pw//2,0,pw//2,ph-1),fill=dark)
                        rect((1,5,5,9),palette["camo"]);rect((pw-6,5,pw-2,9),palette["camo"])
                        for y in (3,10,16,21):draw.point((pw//2,y),fill=light)
                    if face=="back":draw.line((1,6,pw-2,6),fill=dark)
            image.alpha_composite(tile, (px,py))
    path = STAGE / (faction + "_soldier_layered.png")
    image.save(path)
    return path


JAVA_HEAD = '''package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Original P2-inspired layering, not extracted assets. Generated by art/military2/build_soldiers.py.
 * 256 UV units, independent 512px faction atlases. Vanilla humanoid pivots preserve grabbing/locomotion. */
public final class BlackwatchModel extends HumanoidModel<MilitarySoldier> {
    private final boolean military;
    private final ModelPart[] equipment;
    private final ModelPart[] ranks = new ModelPart[8];
    private final ModelPart[] weapons = new ModelPart[4];
    private final ModelPart[][] commandParts = new ModelPart[3][6];
    private final ModelPart[] hoodShell;
    private final ModelPart[] patrolPacks = new ModelPart[5];
    public BlackwatchModel(ModelPart root) { this(root, false); }
    public BlackwatchModel(ModelPart root, boolean military) {
        super(root);
        this.military=military;
        hat.visible = false;
        equipment = new ModelPart[]{head.getChild("equipment"), body.getChild("equipment"),
            rightArm.getChild("equipment"), leftArm.getChild("equipment"),
            rightLeg.getChild("equipment"), leftLeg.getChild("equipment")};
        for (int i=0; i<8; i++) ranks[i] = body.getChild("rank_"+i);
        String[] kinds={"weapon_rifle","weapon_machine","weapon_grenade","weapon_launcher"};
        for (int i=0; i<4; i++) weapons[i]=rightArm.getChild(kinds[i]);
        String[] parents={"head","body","right_arm","left_arm","right_leg","left_leg"};
        String[] roles={"team_leader","officer","commander"};
        for(int i=0;i<roles.length;i++) for(int j=0;j<parents.length;j++)
            commandParts[i][j]=root.getChild(parents[j]).getChild("role_"+roles[i]);
        String[] shellNames=military?new String[0]:new String[]{"gear_hood_crown","gear_hood_rim",
            "gear_hood_rear","gear_hood_right","gear_hood_left"};
        hoodShell=new ModelPart[shellNames.length];
        for(int i=0;i<shellNames.length;i++) hoodShell[i]=equipment[0].getChild(shellNames[i]);
        String[] packNames={"gear_patrol_pack","gear_radio","gear_aerial","gear_supply_pack","gear_supply_lid"};
        for(int i=0;i<packNames.length;i++)
            patrolPacks[i]=equipment[1].hasChild(packNames[i])?equipment[1].getChild(packNames[i]):null;
    }
    public static LayerDefinition layer(boolean heavy) { return layer(heavy,false); }
    public static LayerDefinition layer(boolean heavy,boolean military) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("body",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("right_arm",CubeListBuilder.create(),PartPose.offset(-5,2,0));
        root.addOrReplaceChild("left_arm",CubeListBuilder.create(),PartPose.offset(5,2,0));
        root.addOrReplaceChild("right_leg",CubeListBuilder.create(),PartPose.offset(-1.9F,12,0));
        root.addOrReplaceChild("left_leg",CubeListBuilder.create(),PartPose.offset(1.9F,12,0));
        for(String parent:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"})
            root.getChild(parent).addOrReplaceChild("equipment",CubeListBuilder.create(),PartPose.ZERO);
        for(String parent:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"})
            for(String role:new String[]{"team_leader","officer","commander"})
                root.getChild(parent).addOrReplaceChild("role_"+role,CubeListBuilder.create(),PartPose.ZERO);
        for(int i=0;i<8;i++) root.getChild("body").addOrReplaceChild("rank_"+i,CubeListBuilder.create(),PartPose.ZERO);
        for(String kind:new String[]{"weapon_rifle","weapon_machine","weapon_grenade","weapon_launcher"})
            root.getChild("right_arm").addOrReplaceChild(kind,CubeListBuilder.create(),PartPose.ZERO);
'''
JAVA_TAIL = '''        return LayerDefinition.create(mesh,256,256);
    }
    /** The declarative calls above are also the offline preview's geometry input. */
    private static void piece(PartDefinition root,boolean military,boolean heavy,String group,
                              String parent,String name,int u,int v,
                              float x,float y,float z,float w,float h,float d) {
        boolean include = switch(group) {
            case "military" -> military;
            case "bw" -> !military;
            case "regular" -> !heavy;
            case "heavy" -> heavy;
            case "bwHeavy" -> !military && heavy;
            default -> true;
        };
        if(group.startsWith("militaryRole_")&&!military || group.startsWith("bwRole_")&&military) include=false;
        if(!include) return;
        PartDefinition target=root.getChild(parent);
        if(group.contains("Role_")) target=target.getChild("role_"+group.substring(group.indexOf("Role_")+5));
        else if(group.startsWith("weapon_")||group.startsWith("rank_")) target=target.getChild(group);
        else if(name.startsWith("gear_")) target=target.getChild("equipment");
        target.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).addBox(x,y,z,w,h,d),PartPose.ZERO);
    }
    private int heldWeapon(MilitarySoldier entity) {
        if(!entity.equipped() || !(entity.getMainHandItem().getItem() instanceof FirearmItem gun)) return -1;
        return switch(gun.definition().kind()) {
            case BULLET -> gun.definition().id().equals("military_machine_gun")?1:0;
            case GRENADE -> 2;
            default -> 3;
        };
    }
    @Override public void setupAnim(MilitarySoldier entity,float swing,float amount,float age,float yaw,float pitch) {
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        hat.visible=false;
        for(ModelPart part:equipment) part.visible=entity.equipped();
        int role=switch(entity.commandRole()) {
            case TEAM_LEADER -> 0;
            case OFFICER -> 1;
            case COMMANDER -> 2;
            default -> -1;
        };
        for(int i=0;i<commandParts.length;i++) for(ModelPart part:commandParts[i])
            part.visible=entity.equipped() && i==role;
        boolean commandHead=role>=1;
        if(military && commandHead) equipment[0].visible=false;
        for(ModelPart part:hoodShell) part.visible=!commandHead;
        for(ModelPart part:patrolPacks) if(part!=null) part.visible=!commandHead;
        for(int i=0;i<ranks.length;i++) ranks[i].visible=entity.equipped() && i==entity.rank().ordinal();
        int held=heldWeapon(entity);
        for(int i=0;i<weapons.length;i++) weapons[i].visible=i==held;
        // Reset every frame: renderers/models are shared across entities and equipment states.
        rightArm.zRot=0; leftArm.zRot=0;
        if(held<0) return;
        boolean reloading=entity.phase()==3;
        boolean aiming=entity.phase()!=0;
        rightArm.xRot=(aiming?-1.48F:-1.10F)+head.xRot*.7F;
        rightArm.yRot=(held==3?-.08F:-.38F)+head.yRot*.6F;
        leftArm.xRot=(aiming?-1.42F:-1.16F)+head.xRot*.7F;
        leftArm.yRot=(held==3?.95F:.72F)+head.yRot*.6F;
        leftArm.zRot=-.06F;
        if(reloading) {
            rightArm.xRot+=.22F;
            leftArm.xRot=-1.1F+(float)Math.sin(age*.25F)*.2F;
            leftArm.yRot=.35F;
        }
        if(entity.phase()==2) rightArm.xRot+=(float)Math.sin(age*2)*.025F;
    }
}
'''


def java_source():
    lines = []
    for p in PIECES:
        nums=",".join(f"{n:g}F" for n in p["box"])
        lines.append(f'        piece(root,military,heavy,"{p["group"]}","{p["parent"]}","{p["name"]}",{p["uv"][0]},{p["uv"][1]},{nums});')
    return JAVA_HEAD + "\n".join(lines) + "\n" + JAVA_TAIL


def renderer(forge):
    loc = 'new ResourceLocation("blacklight",path)' if forge else 'ResourceLocation.fromNamespaceAndPath("blacklight",path)'
    return '''package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlackwatchModel;
import com.codex.blacklight.entity.MilitarySoldier;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Separate faction textures; no colour multiplier or permanently attached held-item layer. */
public final class BlackwatchRenderer extends MobRenderer<MilitarySoldier,BlackwatchModel> {
    private final ResourceLocation texture;
    private static ResourceLocation texture(String path) { return ''' + loc + '''; }
    public BlackwatchRenderer(EntityRendererProvider.Context context,boolean heavy) { this(context,heavy,false); }
    public BlackwatchRenderer(EntityRendererProvider.Context context,boolean heavy,boolean military) {
        super(context,new BlackwatchModel(BlackwatchModel.layer(heavy,military).bakeRoot(),military),.5F);
        texture=texture("textures/entity/"+(military?"military":"blackwatch")+"_soldier_layered.png");
    }
    @Override public ResourceLocation getTextureLocation(MilitarySoldier entity) { return texture; }
}
'''


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--install",action="store_true")
    parser.add_argument("--platform",choices=["all","neoforge","forge"],default="all")
    args=parser.parse_args()
    geometry()
    (STAGE / "BlackwatchModel.java").write_text(java_source(),encoding="utf8")
    (STAGE / "BlackwatchRenderer-neoforge.java").write_text(renderer(False),encoding="utf8")
    (STAGE / "BlackwatchRenderer-forge.java").write_text(renderer(True),encoding="utf8")
    textures=[atlas(f) for f in PALETTES]
    (STAGE / "material-map.json").write_text(json.dumps(PIECES,ensure_ascii=False,indent=2),encoding="utf8")
    if args.install:
        for target in [ROOT.parent / "blacklight-evolution",ROOT.parent / "blacklight-evolution-forge-1.20.1"]:
            forge="forge-1.20.1" in target.name
            if args.platform!="all" and forge!=(args.platform=="forge"):continue
            code=target / "src/main/java/com/codex/blacklight/client"
            (code / "model/BlackwatchModel.java").write_text(java_source(),encoding="utf8")
            (code / "renderer/BlackwatchRenderer.java").write_text(renderer("forge-1.20.1" in target.name),encoding="utf8")
            resources=target / "src/main/resources/assets/blacklight/textures/entity"
            resources.mkdir(parents=True,exist_ok=True)
            for texture in textures:(resources / texture.name).write_bytes(texture.read_bytes())
    print(json.dumps(dict(pieces=len(PIECES),uv_units=ATLAS,texture_pixels=ATLAS*SCALE,
                          atlas_used_height=cursor[1]+cursor[2],installed=args.install)))


if __name__=="__main__":main()
