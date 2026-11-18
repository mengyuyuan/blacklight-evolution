"""Authored Goliath neck, fitted to the exported source joint chain."""
import math


def add_neck(geometry, motion):
    bones = geometry["minecraft:geometry"][0]["bones"]
    names = [bone["name"] for bone in bones]
    for name in ("neck_base", "neck_middle", "neck_top"):
        if name in names:
            raise ValueError("Neck geometry is already present")
    torso = bones[names.index("torso")]
    faces = torso["cubes"][0]["uv"]
    reference = motion["clips"]["idle"]["frames"][0]

    for name, parent, endpoint, width, depth, start, end in (
        ("neck_base", "Spine_3", "Neck", 18, 16, .24, 1.14),
        ("neck_middle", "Neck", "Neck_2", 15, 13, -.28, 1.28),
        ("neck_top", "Neck_2", "Head", 11, 10, -.36, 1.28),
    ):
        row = reference[motion["bones"].index("source_parent_scale_" + endpoint)]
        x, y, z = row[3:6]
        distance = math.sqrt(x*x + y*y + z*z)
        midpoint = (start + end) / 2
        # Convert the local axis into Gecko's cube pivot and Euler convention.
        pivot = [-x*midpoint, y*midpoint, z*midpoint]
        size = [width, (end - start)*distance, depth]
        pitch = -math.degrees(math.asin(z/distance))
        roll = math.degrees(math.atan2(-x, y))
        uv = {
            face: {
                "uv": [round(origin + span*.32, 3) for origin, span in zip(data["uv"], data["uv_size"])],
                "uv_size": [round(span*.36, 3) for span in data["uv_size"]],
            }
            for face, data in faces.items()
        }
        cube = {
            "origin": [round(center - span/2, 6) for center, span in zip(pivot, size)],
            "size": [round(value, 6) for value in size],
            "pivot": [round(value, 6) for value in pivot],
            "rotation": [round(pitch, 6), 0, round(roll, 6)],
            "uv": uv,
        }
        bones.append({"name": name, "parent": "source_" + parent, "pivot": [0, 0, 0], "cubes": [cube]})
