"""Author the shared 16 px Blacklight tissue material vocabulary.

Geometry supplies volume and lighting. These broad pixel clusters supply fibres,
chitin grain, tendon and cavities without photographed noise or baked shadows.
"""
from pathlib import Path
from PIL import Image, ImageDraw


def build_texture(destination=None):
    atlas = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
    bases = [(132, 34, 44), (90, 26, 36), (60, 50, 62), (190, 176, 150),
             (150, 78, 84), (46, 22, 32), (70, 60, 70)]
    for tile, base in enumerate(bases):
        image = Image.new('RGBA', (16, 16), base + (255,))
        d = ImageDraw.Draw(image)
        if tile in (0, 1, 4):
            # Long folded muscle bundles, with intermittent rather than gridded seams.
            light = [(168, 56, 62), (120, 40, 50), (184, 110, 108)][(0, 1, 4).index(tile)]
            dark = [(86, 20, 32), (60, 16, 28), (104, 50, 58)][(0, 1, 4).index(tile)]
            d.polygon([(0, 2), (6, 2), (9, 4), (15, 4), (15, 7), (8, 6), (5, 4), (0, 4)], fill=light)
            d.polygon([(0, 10), (5, 9), (10, 11), (15, 10), (15, 13), (9, 14), (5, 12), (0, 13)], fill=light)
            d.line([(0, 8), (5, 7), (10, 9), (15, 8)], fill=dark, width=1)
            d.line([(2, 1), (8, 1), (12, 3)], fill=dark, width=1)
        elif tile == 2:
            # Large continuous shell facets. A single thin red cleft per tile.
            d.polygon([(0, 0), (9, 0), (7, 6), (0, 11)], fill=(84, 72, 88))
            d.polygon([(10, 0), (15, 0), (15, 10), (8, 15), (6, 12)], fill=(44, 38, 50))
            d.line([(2, 15), (5, 10), (6, 7), (9, 3)], fill=(104, 92, 106), width=1)
            d.line([(7, 15), (10, 10), (10, 7), (12, 5)], fill=(176, 36, 48), width=1)
        elif tile == 3:
            d.polygon([(0, 0), (4, 0), (6, 15), (1, 15)], fill=(214, 202, 176))
            d.polygon([(10, 0), (15, 0), (15, 15), (12, 15)], fill=(150, 136, 116))
            d.line([(7, 0), (8, 5), (8, 11), (9, 15)], fill=(168, 152, 128))
        elif tile == 5:
            d.polygon([(0, 0), (4, 0), (3, 9), (0, 15)], fill=(70, 26, 40))
            d.polygon([(13, 0), (15, 0), (15, 15), (12, 12)], fill=(150, 30, 44))
        else:
            d.polygon([(0, 0), (5, 0), (8, 15), (3, 15)], fill=(96, 84, 96))
            d.polygon([(11, 0), (15, 0), (15, 15), (13, 15)], fill=(48, 40, 52))
        atlas.paste(image, (tile * 16, 0))
    target = destination or Path(__file__).resolve().parents[1] / 'src/main/resources/assets/blacklight/textures/entity/tissue_anatomy.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target)
    return target


if __name__ == '__main__':
    print(build_texture())
