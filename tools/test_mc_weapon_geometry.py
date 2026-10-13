"""Regression checks against the approved sculpture, not exporter math."""
from pathlib import Path
import json
import unittest
import numpy as np

ROOT = Path(__file__).resolve().parents[1]


class WeaponProportions(unittest.TestCase):
    def check_shape(self, key, names, minimum_scale):
        source = 'art/blade-mc-v6/layout.json' if key == 'blade' else 'art/weapons-mc-v4/layout.json'
        original = json.loads((ROOT / source).read_text())['models'][key]
        runtime = json.loads((ROOT / f'src/main/resources/assets/blacklight/models/form_{key}.json').read_text())
        for name in names:
            with self.subTest(part=name):
                before = np.unique(np.array(next(p for p in original if p['name'] == name)['vertices']) / 16, axis=0)
                part = runtime['parts'][name + ' / Hand_R']
                faces = runtime['joints']['Hand_R'][part['start']:part['start']+part['count']]
                after = np.unique(np.array([v for f in faces for v in f['p']]), axis=0)
                self.assertEqual(len(before), len(after))
                # All pairwise distances characterize the shape independently of
                # vertex order, winding, translation, rotation and reflection.
                def distances(v):
                    d = np.linalg.norm(v[:, None] - v[None, :], axis=2)
                    return np.sort(d[np.triu_indices(len(v), 1)])
                a, b = distances(before), distances(after)
                ratio = np.median(b / a)
                self.assertGreaterEqual(ratio, minimum_scale)
                np.testing.assert_allclose(b, a * ratio, atol=3e-7, rtol=2e-6)

    def test_hammer_keeps_fist_volume_and_finger_proportions(self):
        self.check_shape('hammerfists', ['Heavy fist mass', 'Broad outer knuckle plate', 'Folded finger 1'], 1.8)

    def test_whip_keeps_hook_shape_at_visible_size(self):
        self.check_shape('whipfist', ['Forward main hook', 'Forward inner hook', 'Hook root cuff'], 1.7)


if __name__ == '__main__':
    unittest.main()
