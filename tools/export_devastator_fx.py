"""Export reviewed Prototype devastator geometry and its bound DDS textures.

Requires local, legally obtained game files and Pillow. Source meshes and PNGs
must stay in a private profile outside this repository. This exporter preserves
the source Y-up vertex coordinates, normals, UVs and triangle indices; it does
not invent spike/tendril geometry or copy an original shader implementation.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import io
import json
import math
from pathlib import Path
import re
import struct
import sys
import zlib

sys.dont_write_bytecode = True

# Shared actor retarget scale times the existing native model render scale.
# This is a consistent actor-to-world calibration, not proof of a game unit name.
WORLD_SCALE = 0.8721314614129095 * 0.9375
POSITION = 0x2C929929
NORMAL = 0xC206BCE7
UV0 = 0x00364509
MESHES = {
    "alex/alex.p3d.rz": {
        "groundspike_single_small_Groundspike_Single_SmallShape": "groundspike_small",
        "groundspike_single_mid_Groundspike_Single_MediumShape": "groundspike_mid",
        "groundspike_single_large_Groundspike_Single_LargeShape": "groundspike_large",
        "groundspike_small_Groundspike_SmallShape": "groundspike_cluster_small",
        "groundspike_mid_Groundspike_MediumShape": "groundspike_cluster_mid",
        "groundspike_large_Groundspike_LargeShape": "groundspike_cluster_large",
    },
    "startup_effects.p3d.rz": {
        "Tendril_SegmentShape": "tendril_segment",
        "Tendril_SpikeShape": "tendril_spike",
        "Whipfist_Segment_AShape": "whipfist_segment",
    },
}


@dataclass
class Chunk:
    kind: int
    payload: memoryview
    children: list["Chunk"]
    offset: int


def string(payload, offset=0):
    if offset >= len(payload):
        raise ValueError("Missing P3D string")
    size = payload[offset]
    end = offset + 1 + size
    if end > len(payload):
        raise ValueError("Truncated P3D string")
    return bytes(payload[offset + 1:end]).rstrip(b"\0").decode("ascii"), end


def chunks(raw, start=0, end=None):
    end = len(raw) if end is None else end
    result = []
    while start < end:
        if start + 12 > end:
            raise ValueError(f"Truncated chunk header at {start:#x}")
        kind, header, size = struct.unpack_from("<III", raw, start)
        if not 12 <= header <= size or start + size > end:
            raise ValueError(f"Invalid chunk boundary at {start:#x}")
        result.append(Chunk(kind, raw[start + 12:start + header],
                            chunks(raw, start + header, start + size), start))
        start += size
    if start != end:
        raise ValueError("Unaligned chunk end")
    return result


def walk(nodes):
    for node in nodes:
        yield node
        yield from walk(node.children)


def decompress(path):
    encoded = path.read_bytes()
    if len(encoded) < 16:
        raise ValueError(f"Truncated RZ {path}")
    raw = zlib.decompress(encoded[16:])
    declared = struct.unpack_from("<I", encoded, 8)[0]
    if len(raw) != declared:
        raise ValueError(f"RZ size mismatch {path}: {len(raw)} != {declared}")
    return raw


def shaders(nodes):
    result = {}
    for node in walk(nodes):
        if node.kind != 0x11015:
            continue
        name, _ = string(node.payload)
        parameters = {}
        for child in node.children:
            if child.kind == 0x11016:
                key, offset = string(child.payload)
                value, _ = string(child.payload, offset) if offset < len(child.payload) else ("", offset)
                parameters[key] = value
        if name in result and result[name] != parameters:
            raise ValueError(f"Conflicting shader {name}")
        result[name] = parameters
    return result


def skeletons(nodes):
    result = {}
    for node in walk(nodes):
        if node.kind != 0x23000:
            continue
        name, offset = string(node.payload)
        _, count, _, _ = struct.unpack_from("<4I", node.payload, offset)
        joints = []
        for child in node.children:
            if child.kind == 0x23001:
                joint_name, offset = string(child.payload)
                parent = struct.unpack_from("<I", child.payload, offset)[0]
                matrix = list(struct.unpack_from("<16f", child.payload, offset + 4))
                joints.append(dict(name=joint_name, parent=parent, bindMatrix=matrix))
        if len(joints) != count:
            raise ValueError(f"Skeleton count mismatch: {name}")
        result[name] = joints
    return result


def attributes(parts, count):
    streams = {}
    descriptions = []
    for child in parts:
        if child.kind == 0x10012:
            version, field, byte_count = struct.unpack_from("<3I", child.payload)
            if version != 0x20001 or byte_count != len(child.payload) - 12:
                raise ValueError("Vertex buffer schema mismatch")
            if field in streams:
                raise ValueError("Duplicate vertex stream")
            streams[field] = child.payload[12:]
        elif child.kind == 0x10014:
            version, field, reference_bytes, descriptor_bytes = struct.unpack_from("<4I", child.payload)
            if version != 0x20001 or descriptor_bytes != len(child.payload) - 16 or descriptor_bytes % 17:
                raise ValueError("Vertex description schema mismatch")
            for offset in range(16, len(child.payload), 17):
                semantic, encoding, byte_offset, stride, element, components, flags = struct.unpack_from(
                    "<IIIBBBH", child.payload, offset)
                descriptions.append((semantic, field, reference_bytes, encoding,
                                     byte_offset, stride, element, components, flags))
    result = {}
    for semantic, field, reference_bytes, encoding, byte_offset, stride, element, components, _ in descriptions:
        if semantic not in (POSITION, NORMAL, UV0):
            continue
        if encoding != 0 or element != 0:
            raise ValueError("Only declared float32 position/normal/UV attributes are supported")
        stream = streams[field]
        if len(stream) != reference_bytes or len(stream) != count * stride:
            raise ValueError("Vertex stream/description count mismatch")
        used = 2 if semantic == UV0 else 3
        if components < used or byte_offset + used * 4 > stride:
            raise ValueError("Attribute does not fit its stream")
        if semantic in result:
            raise ValueError("Duplicate vertex semantic")
        values = [x for i in range(count) for x in struct.unpack_from(
            "<" + str(used) + "f", stream, i * stride + byte_offset)]
        if not all(math.isfinite(v) for v in values):
            raise ValueError("Non-finite vertex attribute")
        result[semantic] = values
    if set(result) != {POSITION, NORMAL, UV0}:
        raise ValueError("Missing declared position/normal/UV")
    return result


def texture_id(source):
    stem = Path(source).stem
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", stem):
        raise ValueError(f"Unsafe texture name {source}")
    return "devfx_" + stem.lower() + ".png"


def export_texture(nodes, source_name, output):
    from PIL import Image
    candidates = [n for n in walk(nodes) if n.kind == 0x19000 and string(n.payload)[0] == source_name]
    if len(candidates) != 1:
        raise ValueError(f"Texture must resolve exactly once: {source_name} ({len(candidates)})")
    encoded = []
    for node in walk(candidates):
        payload = bytes(node.payload)
        offset = payload.find(b"DDS ")
        if offset in (0, 4):
            encoded.append(payload[offset:])
    if len(encoded) != 1:
        raise ValueError(f"DDS must resolve exactly once: {source_name}")
    image = Image.open(io.BytesIO(encoded[0])).convert("RGBA")
    filename = texture_id(source_name)
    image.save(output / filename, format="PNG")
    png = (output / filename).read_bytes()
    return dict(sourceName=source_name, filename=filename,
                resource="blacklight:textures/private_devfx/" + filename,
                width=image.width, height=image.height,
                sourceDdsSha256=hashlib.sha256(encoded[0]).hexdigest(),
                pngSha256=hashlib.sha256(png).hexdigest())


def export_mesh(node, mesh_id, shader_map, rigs):
    source_name, offset = string(node.payload)
    rig = string(node.payload, offset + 4)[0] if node.kind == 0x10001 else ""
    if rig:
        joints = rigs[rig]
        # Reviewed devastator spike skins are rigid, single-root identity rigs.
        identity = [1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.]
        if len(joints) != 1 or joints[0]["bindMatrix"] != identity:
            raise ValueError(f"Unreviewed non-rigid/bind transform: {source_name}")
    positions, normals, uvs, indices = [], [], [], []
    texture_name = normal_name = None
    shader_names = []
    primitive_count = 0
    for child in node.children:
        if child.kind != 0x10020:
            continue
        shader, offset = string(child.payload, 4)
        primitive, vertex_format, count, index_count, palette_count = struct.unpack_from(
            "<5I", child.payload, offset)
        if primitive != 0:
            raise ValueError(f"Only source triangle lists supported: {source_name}")
        parameters = shader_map[shader]
        diffuse = parameters.get("color") or parameters.get("diffuseTexture")
        normal = parameters.get("normal") or parameters.get("normalTexture") or ""
        if not diffuse:
            raise ValueError(f"No unambiguous source diffuse binding: {shader}")
        if texture_name is not None and (texture_name != diffuse or normal_name != normal):
            raise ValueError("Multi-material mesh needs explicit groups; do not collapse materials")
        texture_name, normal_name = diffuse, normal
        a = attributes(child.children, count)
        index_buffers = [p.payload for p in child.children if p.kind == 0x10013]
        if len(index_buffers) != 1:
            raise ValueError("Unexpected index buffer count")
        ib = index_buffers[0]
        if len(ib) != 12 + index_count * 2 or index_count % 3:
            raise ValueError("Index buffer triangle count mismatch")
        local_indices = list(struct.unpack_from("<" + str(index_count) + "H", ib, 12))
        if max(local_indices, default=-1) >= count:
            raise ValueError("Index out of range")
        if rig:
            palettes = [p.payload for p in child.children if p.kind == 0x1000D]
            if len(palettes) != 1 or palette_count != 1 or struct.unpack("<2I", palettes[0]) != (1, 0):
                raise ValueError("Unexpected single-root skin palette")
            main_buffers = [p.payload[12:] for p in child.children if p.kind == 0x10012
                            and struct.unpack_from("<I", p.payload, 4)[0] == 2]
            if len(main_buffers) != 1 or len(main_buffers[0]) != count * 56:
                raise ValueError("Unreviewed spike skin weight stream")
            for vertex in range(count):
                first = struct.unpack_from("<3f", main_buffers[0], vertex * 56 + 40)
                weights = (*first, 1.0 - sum(first))
                joints = struct.unpack_from("<4B", main_buffers[0], vertex * 56 + 52)
                if not all(math.isfinite(w) and -1e-5 <= w <= 1.00001 for w in weights):
                    raise ValueError("Invalid source spike weights")
                if any(joint != 0 and weight > 1e-6 for joint, weight in zip(joints, weights)):
                    raise ValueError("Non-root source spike influence would need full skinning")
        elif palette_count:
            raise ValueError("Unexpected skin data in static mesh")
        base = len(positions) // 3
        positions.extend(a[POSITION]); normals.extend(a[NORMAL]); uvs.extend(a[UV0])
        indices.extend(base + i for i in local_indices)
        shader_names.append(shader); primitive_count += 1
    if not primitive_count:
        raise ValueError(f"Missing geometry {source_name}")
    low = [min(positions[axis::3]) for axis in range(3)]
    high = [max(positions[axis::3]) for axis in range(3)]
    axis = max(range(3), key=lambda a: high[a] - low[a])
    if not all(.5 < math.sqrt(sum(v * v for v in normals[i:i + 3])) < 1.5
               for i in range(0, len(normals), 3)):
        raise ValueError(f"Unexpected source normals: {source_name}")
    return dict(id=mesh_id, sourceName=source_name, skeletonName=rig,
                skeleton=rigs[rig] if rig else [], shaderNames=shader_names,
                sourceDiffuse=texture_name, sourceNormal=normal_name,
                textureName="blacklight:textures/private_devfx/" + texture_id(texture_name),
                textureFile=texture_id(texture_name),
                normalTextureName=("blacklight:textures/private_devfx/" + texture_id(normal_name)) if normal_name else "",
                positions=positions, normals=normals, uvs=uvs, indices=indices,
                bounds=dict(min=low, max=high), longitudinalAxis=axis,
                sourceScale=WORLD_SCALE, primitiveCount=primitive_count)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--game-art", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--audit-output", required=True, type=Path)
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[1]
    for destination in (args.output, args.audit_output):
        if destination.resolve().is_relative_to(repository):
            raise ValueError("Private derived geometry/textures cannot be exported inside the public repository")
    args.output.mkdir(parents=True, exist_ok=True)
    args.audit_output.mkdir(parents=True, exist_ok=True)
    result = dict(version=1, worldScale=WORLD_SCALE, coordinateSystem="source_Y_up",
                  meshes={}, textures={}, sourceFiles={},
                  notes=["Rigid original bind geometry and source UV/basecolor; native shader and native sequencer are not copied.",
                         "Source spike skins have one identity root. No baked ground-spike growth clip has been confirmed.",
                         "Runtime growth/retraction/bending remain explicit Minecraft driver adaptations."])
    audit = {}
    for package, wanted in MESHES.items():
        raw = decompress(args.game_art / package)
        parsed = chunks(memoryview(raw)); material_map = shaders(parsed); rigs = skeletons(parsed)
        result["sourceFiles"][package] = hashlib.sha256(raw).hexdigest()
        found = {}
        texture_names = set()
        for node in walk(parsed):
            if node.kind not in (0x10000, 0x10001):
                continue
            source_name, _ = string(node.payload)
            if source_name not in wanted:
                continue
            mesh_id = wanted[source_name]
            if mesh_id in found:
                raise ValueError("Duplicate requested mesh")
            mesh = export_mesh(node, mesh_id, material_map, rigs)
            found[mesh_id] = mesh
            texture_names.add(mesh["sourceDiffuse"])
            if mesh["sourceNormal"]:
                texture_names.add(mesh["sourceNormal"])
            audit[mesh_id] = dict(vertices=len(mesh["positions"]) // 3,
                                  triangles=len(mesh["indices"]) // 3,
                                  bounds=mesh["bounds"], longitudinalAxis=mesh["longitudinalAxis"],
                                  skeleton=mesh["skeletonName"], texture=mesh["sourceDiffuse"],
                                  offset=node.offset, sourcePackage=package)
        if set(found) != set(wanted.values()):
            raise ValueError(f"Missing requested source geometry: {set(wanted.values()) - set(found)}")
        result["meshes"].update(found)
        for texture_name in sorted(texture_names):
            info = export_texture(parsed, texture_name, args.output)
            if texture_name in result["textures"] and result["textures"][texture_name] != info:
                raise ValueError("Conflicting source texture data")
            result["textures"][texture_name] = info
    target = args.output / "devastator-fx.data"
    target.write_text(json.dumps(result, separators=(",", ":"), allow_nan=False), encoding="utf-8")
    size = target.stat().st_size
    if size >= 8 * 1024 * 1024:
        raise ValueError("Derived geometry exceeds the loader budget")
    audit_report = dict(worldScale=WORLD_SCALE, meshes=audit, textures=result["textures"],
                        sourceFiles=result["sourceFiles"], dataBytes=size,
                        dataSha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                        sourceGrowthAnimationConfirmed=False, inGameAcceptance=False)
    (args.audit_output / "devastator-fx-audit_PRIVATE.json").write_text(
        json.dumps(audit_report, indent=2, allow_nan=False), encoding="utf-8")
    print(json.dumps(audit_report, indent=2))


if __name__ == "__main__":
    main()
