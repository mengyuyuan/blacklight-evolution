"""The goddess and spider reuse palette indices but have different skeletons."""
import gzip
import struct
import unittest
from pathlib import Path


class GreeneMoltArmsTest(unittest.TestCase):
    def test_spider_wrist_and_lower_arm_follow_their_parents_until_the_swap(self):
        root = Path(__file__).resolve().parents[2]
        scene = root / "src/main/resources/assets/blacklight/greene/molt/scene.bin.gz"
        with gzip.open(scene, "rb") as stream:
            magic, people, lines, frames = struct.unpack("<4I", stream.read(16))
            self.assertEqual(magic, 0x474D4C32)
            stream.seek((36 + 4 * lines + 18) * 4, 1)
            for _ in range(2):
                vertices, = struct.unpack("<I", stream.read(4))
                stream.seek(vertices * 48, 1)
            frame_data = stream.read()
        stride, remainder = divmod(len(frame_data), frames)
        self.assertEqual(remainder, 0)
        self.assertEqual(stride - (people * 12 + lines * 48 + 68) * 4, 791 * 64)
        starts = []
        for frame in range(132):
            offset = frame * stride
            # This clip rotates at the shoulders. Each wrist inherits its elbow,
            # and the lowered forearm inherits the lowered upper arm.
            for parent, child in [(34, 35), (36, 37), (37, 38)]:
                self.assertEqual(frame_data[offset+parent*64:offset+(parent+1)*64],
                                 frame_data[offset+child*64:offset+(child+1)*64], frame)
            starts.append(frame_data[offset+36*64:offset+37*64])
        self.assertGreater(len(set(starts)), 50, "lower arm remained in bind pose")
