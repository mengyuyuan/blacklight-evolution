"""Deferred asset regressions; these do not launch or render Minecraft."""
from pathlib import Path
import json
import math
import unittest


ROOT = Path(__file__).resolve().parents[1]
ITEMS = tuple(f"{faction}_{kind}_chassis"
              for faction in ("military", "blackwatch")
              for kind in ("truck", "apc", "tank"))


def transform_vector(vector, transform, left_hand=False):
    """ItemTransform's rotationXYZ after scale; translations do not affect normals."""
    x, y, z = (value * scale for value, scale in zip(vector, transform["scale"]))
    rx, ry, rz = map(math.radians, transform["rotation"])
    if left_hand:
        ry, rz = -ry, -rz
    # rotationXYZ is Rx * Ry * Rz: apply the rightmost rotation first.
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return x, y, z


class VehicleChassisItemPresentationTest(unittest.TestCase):
    def models(self):
        for item in ITEMS:
            path = ROOT / f"src/main/resources/assets/blacklight/models/item/{item}.json"
            yield item, json.loads(path.read_text(encoding="utf-8"))

    def test_registered_chassis_front_faces_the_gui_camera(self):
        # README/vehicle-models coordinates: +Z is the cab/glacis/cannon front.
        # GuiGraphics applies scale(16,-16,16), preserving the view-depth sign.
        for item, model in self.models():
            with self.subTest(item=item):
                front = transform_vector((0, 0, 1), model["display"]["gui"])
                roof = transform_vector((0, 1, 0), model["display"]["gui"])
                self.assertGreater(front[2], 0, "the front must face the viewer, not the tail")
                self.assertGreater(roof[2], 0, "the upper silhouette must remain visible")

    def test_first_person_hands_present_mirrored_views_of_the_same_vehicle(self):
        for item, model in self.models():
            with self.subTest(item=item):
                right = model["display"]["firstperson_righthand"]
                left = model["display"]["firstperson_lefthand"]
                for axis in ((0, 0, 1), (0, 1, 0)):
                    a = transform_vector(axis, right)
                    b = transform_vector(axis, left, left_hand=True)
                    self.assertAlmostEqual(a[0], -b[0], places=7)
                    self.assertAlmostEqual(a[1], b[1], places=7)
                    self.assertAlmostEqual(a[2], b[2], places=7)
                # Vanilla also mirrors the authored left translation's X.
                self.assertEqual(right["translation"], left["translation"])


if __name__ == "__main__":
    unittest.main()
