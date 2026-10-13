"""Asset checks for the independently widened Minecraft Devastator spikes.

Runs with Python only; it neither launches Minecraft nor reads private assets.
"""
import hashlib
import json
import math
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from author_block_spikes import build


def points(mesh):
    values = mesh['positions']
    return [tuple(values[i:i + 3]) for i in range(0, len(values), 3)]


def part_points(mesh, name):
    part = next(p for p in mesh['parts'] if p['name'] == name)
    start = part['firstTriangle'] * 3
    ids = sorted(set(mesh['indices'][start:start + part['triangleCount'] * 3]))
    vertices = points(mesh)
    return [vertices[i] for i in ids]


def core_width(mesh, cube=0):
    # The first cuboid face retains source order; the edge is the actual local
    # X cross section after bone rotation, measured in exported world vertices.
    part = next(p for p in mesh['parts'] if p['name'] == f'spire_0_cube_{cube}')
    offset = part['firstTriangle'] * 3
    vertices = points(mesh)
    a, b = mesh['indices'][offset], mesh['indices'][offset + 5]
    return math.dist(vertices[a], vertices[b])


def eruption_width(mesh):
    vertices = [point for part in mesh['parts'] if part['name'].startswith('eruption_')
                for point in part_points(mesh, part['name'])]
    return max(max(p[a] for p in vertices) - min(p[a] for p in vertices) for a in (0, 2))


class BlockSpikeProportionsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.recipe = json.loads((ROOT / 'art/devastators/spikes.model.json').read_text())
        cls.geometry = json.loads((ROOT / cls.recipe['sourceGeometry']).read_text())['minecraft:geometry'][0]
        cls.meshes = json.loads((ROOT / 'src/main/resources/assets/blacklight/models/devastators/spikes.json').read_text())['meshes']

    def test_requested_dimensions_and_taper_are_measured_in_vertices(self):
        for name, config in self.recipe['variants'].items():
            with self.subTest(name=name):
                mesh = self.meshes[name]
                self.assertAlmostEqual(mesh['bounds']['max'][1], config['height'], places=7)
                self.assertAlmostEqual(core_width(mesh), config['coreWidth'], places=5)
                self.assertAlmostEqual(eruption_width(mesh), config['rootFootprint'], places=5)
                self.assertLess(core_width(mesh, 5), core_width(mesh) * .1)
                self.assertEqual(len(mesh['indices']) // 3, 576 if config['cluster'] else 144)
                self.assertGreaterEqual(config['height'] / 1.8, 1.4)

    def test_height_change_does_not_rescale_the_cross_section_or_root(self):
        config = dict(self.recipe['variants']['groundspike_small'], height=3.25)
        taller = build(self.geometry, config, 'independence_check')
        self.assertAlmostEqual(taller['bounds']['max'][1], 3.25, places=7)
        self.assertAlmostEqual(core_width(taller), .60, places=5)
        self.assertAlmostEqual(eruption_width(taller), .85, places=5)

    def test_cube_uv_topology_and_colours_remain_the_pre_dev10_values(self):
        # Measured from the checked-in dev9 cuboid bake before editing. These
        # guard the texture mapping and topology, rather than new dimensions.
        golden = {
            False: ('87e4e1e3aad1d5930fc4f37555e6f665a0c5b7a1f3975b0652b74ddbdfdadf36',
                    '1f1c32c407a0894fbf0b65aec3f590516dc9194dadd5c485101e5dffb6fb3d90',
                    'a78657dd7203ccb3a61d7db84976291b28c1ce6a76511d1b5c284141f352f9ec'),
            True: ('f20b43e2690a083297548a1ee4de257de004ef329adb54da3b855df8213555d2',
                   'e2c19947a11a7055bfe4256a62796d9ca19f016fe9e491ad044f139187a6c8e4',
                   '2e6f81bcd6960e767ff8b1aaecf1a51891935a50b6d60d11d6ff556925d09823')}
        for name, config in self.recipe['variants'].items():
            for field, expected in zip(('uvs', 'indices', 'colors'), golden[config['cluster']]):
                with self.subTest(name=name, field=field):
                    raw = json.dumps(self.meshes[name][field], separators=(',', ':')).encode()
                    self.assertEqual(hashlib.sha256(raw).hexdigest(), expected)

    def test_closed_cuboids_keep_finite_unit_outward_normals_and_flat_faces(self):
        for name, mesh in self.meshes.items():
            with self.subTest(name=name):
                vertices = points(mesh)
                self.assertTrue(all(math.isfinite(v) for field in ('positions', 'normals', 'uvs') for v in mesh[field]))
                self.assertGreaterEqual(min(mesh['indices']), 0)
                self.assertLess(max(mesh['indices']), len(vertices))
                for start in range(0, len(mesh['indices']), 6):
                    ids = mesh['indices'][start:start + 6]
                    a, b, c, d = [vertices[ids[i]] for i in (0, 1, 2, 5)]
                    u, v = [b[i] - a[i] for i in range(3)], [c[i] - a[i] for i in range(3)]
                    face = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
                    normal = mesh['normals'][ids[0] * 3:ids[0] * 3 + 3]
                    self.assertAlmostEqual(sum(x * x for x in normal), 1, places=5)
                    self.assertGreater(sum(face[i] * normal[i] for i in range(3)), 0)
                    self.assertLess(abs(sum((d[i] - a[i]) * normal[i] for i in range(3))), 2e-6)

    def test_generation_is_reproducible_and_lod_does_not_change_the_silhouette(self):
        for name, config in self.recipe['variants'].items():
            with self.subTest(name=name):
                self.assertEqual(build(self.geometry, config, name), self.meshes[name])
                for field in ('positions', 'indices', 'uvs', 'bounds'):
                    self.assertEqual(self.meshes[name][field], self.meshes[name + '_lod'][field])


if __name__ == '__main__':
    unittest.main()
