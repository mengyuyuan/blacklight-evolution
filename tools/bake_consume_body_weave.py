"""Bake the P1 consume material for Minecraft's regular, non-PBR entity pass.

Run with Pillow installed:
    python tools/bake_consume_body_weave.py /path/to/startup_effects.p3d.rz
The source alpha is preserved byte for byte. The simulated reflection is an
adaptation for entity rendering and is calculated from the packed source normal.
"""
import argparse
import hashlib
import io
import json
import math
import struct
from pathlib import Path
from PIL import Image
import export_devastator_fx as p3d


def dds_bytes(nodes, name, compression):
    matches = [n for n in p3d.walk(nodes) if n.kind == 0x19000 and p3d.string(n.payload)[0] == name]
    if len(matches) != 1:
        raise ValueError(f"Expected one P3D texture {name}, found {len(matches)}")
    payloads = []
    for node in p3d.walk(matches):
        blob = bytes(node.payload)
        offset = blob.find(b"DDS ")
        if offset in (0, 4):
            payloads.append(blob[offset:])
    if len(payloads) != 1 or payloads[0][84:88] != compression:
        raise ValueError(f"Unexpected DDS layout for {name}")
    return payloads[0]


def dds(nodes, name):
    buf = dds_bytes(nodes, name, b"DXT5")
    return Image.open(io.BytesIO(buf)).convert("RGBA"), hashlib.sha256(buf).hexdigest()


def cube_atlas(buf):
    # The reviewed DXT1 cube has all six faces at one mip level, in Direct3D order.
    size = 64
    face_bytes = size * size // 2
    if (len(buf) != 128 + 6 * face_bytes or struct.unpack_from("<II", buf, 12) != (size, size)
            or struct.unpack_from("<I", buf, 28)[0] not in (0, 1)
            or struct.unpack_from("<I", buf, 112)[0] != 0xfe00):
        raise ValueError("Expected the original six-face 64px reflection cube")
    header = bytearray(buf[:128])
    struct.pack_into("<II", header, 108, 0x1000, 0)
    atlas = Image.new("RGB", (3 * size, 2 * size))
    for i in range(6):
        start = 128 + i * face_bytes
        face = Image.open(io.BytesIO(header + buf[start:start + face_bytes])).convert("RGB")
        atlas.paste(face, (i % 3 * size, i // 3 * size))
    return atlas


def bake(albedo, normal):
    if albedo.size != (256, 256) or normal.size != (512, 512):
        raise ValueError("Unexpected consume texture sizes")
    # The packed normal stores tangent X in alpha and Y in green. Shade at
    # native normal resolution, then filter the light to the albedo texels.
    light = []
    for green, alpha in zip(normal.getchannel("G").tobytes(), normal.getchannel("A").tobytes()):
        x, y = alpha / 127.5 - 1, green / 127.5 - 1
        z = math.sqrt(max(0, 1 - x*x - y*y))
        diffuse = max(0, -.60*x - .64*y + .48*z)
        half = max(0, -.48*x - .39*y + .785*z)
        light.append(round(13 * diffuse + 37 * min(1, half)**11))
    lighting = Image.new("L", normal.size)
    lighting.putdata(light)
    lighting = lighting.resize(albedo.size, Image.Resampling.BOX)
    result = Image.new("RGBA", albedo.size)
    pixels = []
    channels = [band.tobytes() for band in albedo.split()]
    for r, g, b, a, glint in zip(*channels, lighting.tobytes()):
        value = 7 + .26*(.2126*r + .7152*g + .0722*b) + glint
        # Neutral charcoal shadow and a slightly cooler reflected highlight.
        pixels.append((round(value), round(value + .025*glint), round(value + .076*glint), a))
    result.putdata(pixels)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("--shader-textures", type=Path, metavar="DIRECTORY",
                        help="Also write the original albedo, packed normal and six-face reflection atlas")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] /
                        "src/main/resources/assets/blacklight/textures/entity/consume_body_weave.png")
    args = parser.parse_args()
    nodes = p3d.chunks(memoryview(p3d.decompress(args.package)))
    bound = p3d.shaders(nodes)["SHAD_consume"]
    albedo, diffuse_hash = dds(nodes, bound["consume_color"])
    normal, normal_hash = dds(nodes, bound["consume_normal"])
    atlas = bake(albedo, normal)
    if atlas.getchannel("A").tobytes() != albedo.getchannel("A").tobytes():
        raise ValueError("Original consume apertures changed")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(args.output, format="PNG", compress_level=9)
    if args.shader_textures:
        reflection = cube_atlas(dds_bytes(nodes, bound["consume_refl"], b"DXT1"))
        args.shader_textures.mkdir(parents=True, exist_ok=True)
        for name, texture in (("diffuse", albedo), ("normal", normal), ("reflection", reflection)):
            texture.save(args.shader_textures / f"consume_body_{name}.png", compress_level=9)
    print(json.dumps({"size":atlas.size, "diffuseDDS":diffuse_hash, "normalDDS":normal_hash,
                      "png":hashlib.sha256(args.output.read_bytes()).hexdigest()}))


if __name__ == "__main__":
    main()
