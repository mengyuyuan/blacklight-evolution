"""Author the shared 16 px Blacklight tissue material vocabulary.

Geometry supplies volume and lighting. These broad pixel clusters supply fibres,
chitin grain, tendon and cavities without photographed noise or baked shadows.
"""
from pathlib import Path
from PIL import Image, ImageDraw


def build_texture(destination=None):
    atlas = Image.new('RGBA', (128, 32), (0, 0, 0, 0))
    bases = [(91, 30, 37), (49, 23, 30), (40, 39, 45), (161, 151, 125),
             (122, 70, 72), (22, 11, 18), (77, 70, 73)]
    for tile, base in enumerate(bases):
        image = Image.new('RGBA', (16, 16), base + (255,))
        d = ImageDraw.Draw(image)
        if tile in (0, 1, 4):
            # Long folded muscle bundles, with intermittent rather than gridded seams.
            light = [(127, 44, 49), (70, 32, 40), (158, 96, 93)][(0, 1, 4).index(tile)]
            dark = [(61, 18, 27), (32, 14, 23), (81, 40, 47)][(0, 1, 4).index(tile)]
            d.polygon([(0, 2), (6, 2), (9, 4), (15, 4), (15, 7), (8, 6), (5, 4), (0, 4)], fill=light)
            d.polygon([(0, 10), (5, 9), (10, 11), (15, 10), (15, 13), (9, 14), (5, 12), (0, 13)], fill=light)
            d.line([(0, 8), (5, 7), (10, 9), (15, 8)], fill=dark, width=1)
            d.line([(2, 1), (8, 1), (12, 3)], fill=dark, width=1)
        elif tile == 2:
            # Large continuous shell facets. A single thin red cleft per tile.
            d.polygon([(0, 0), (9, 0), (7, 6), (0, 11)], fill=(51, 50, 57))
            d.polygon([(10, 0), (15, 0), (15, 10), (8, 15), (6, 12)], fill=(31, 30, 37))
            d.line([(2, 15), (5, 10), (6, 7), (9, 3)], fill=(65, 60, 65), width=1)
            d.line([(7, 15), (10, 10), (10, 7), (12, 5)], fill=(91, 27, 37), width=1)
        elif tile == 3:
            d.polygon([(0, 0), (4, 0), (6, 15), (1, 15)], fill=(192, 179, 143))
            d.polygon([(10, 0), (15, 0), (15, 15), (12, 15)], fill=(118, 111, 98))
            d.line([(7, 0), (8, 5), (8, 11), (9, 15)], fill=(142, 130, 107))
        elif tile == 5:
            d.polygon([(0, 0), (4, 0), (3, 9), (0, 15)], fill=(39, 14, 24))
            d.polygon([(13, 0), (15, 0), (15, 15), (12, 12)], fill=(46, 16, 25))
        else:
            d.polygon([(0, 0), (5, 0), (8, 15), (3, 15)], fill=(99, 88, 85))
            d.polygon([(11, 0), (15, 0), (15, 15), (13, 15)], fill=(53, 48, 54))
        atlas.paste(image, (tile * 16, 0))
    target = destination or Path(__file__).resolve().parents[1] / 'src/main/resources/assets/blacklight/textures/entity/tissue_anatomy.png'
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target)
    return target


if __name__ == '__main__':
    print(build_texture())
