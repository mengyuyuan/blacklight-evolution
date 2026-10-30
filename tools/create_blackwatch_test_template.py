"""Generate the empty, original 16x8x16 arena used only by Blackwatch GameTests."""
import gzip
import struct
from pathlib import Path


def utf(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, value):
    return bytes([kind]) + utf(name) + value


root = Path(__file__).resolve().parents[1]
directory = "structure" if "net.neoforged.moddev" in (root / "build.gradle").read_text(encoding="utf-8") else "structures"
for name, size in (("blackwatch_arena", (16, 8, 16)), ("blackwatch_facility_arena", (32, 12, 32)), ("military_march_arena", (96, 12, 96)), ("military_stress_arena", (128, 12, 128)), ("military_slope_arena", (48, 40, 48))):
    target = root / "src/blackwatchTest/resources/data/blacklight" / directory / (name + ".nbt")
    data = bytes([10]) + utf("")
    data += tag(3, "DataVersion", struct.pack(">i", 3465))
    data += tag(9, "size", bytes([3]) + struct.pack(">iiii", 3, *size))
    data += tag(9, "palette", bytes([10]) + struct.pack(">i", 1) + tag(8, "Name", utf("minecraft:air")) + bytes([0]))
    data += tag(9, "blocks", bytes([10]) + struct.pack(">i", 0))
    data += tag(9, "entities", bytes([10]) + struct.pack(">i", 0))
    data += bytes([0])
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(gzip.compress(data, mtime=0))
    print(target)
