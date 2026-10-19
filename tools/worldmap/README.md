# Offline Shenzhen atlas build

Python 3.12+, dependencies: osmium 4.3.1, shapely 2.2.0, numpy, Pillow, requests.
Use an isolated environment; these libraries are not game dependencies.

1. Set `BLACKLIGHT_MAP_SOURCE` to a working directory on a disk with sufficient space.
2. Download `guangdong-261014.osm.pbf` into it and verify the SHA-256 in
   `docs/worldgen/sources/osm-source.json`.
3. Run `extract_boundaries.py`, then `extract_features.py`.
4. Run `terrain.py` to retrieve/check cached Terrarium tiles. This is the only network step
   in these scripts; never invoke it from the game.
5. Run `build_atlas.py`. It writes compressed geographic tiles and a hash index under
   `src/main/resources/data/blacklight/worldmap/shenzhen/`.

All paths for inputs use `BLACKLIGHT_MAP_SOURCE`. Source downloads remain outside the
repository. Heights without OSM tags and building materials are procedural; see the
accuracy limitations in `docs/worldgen/SHENZHEN_SOURCES.md`.

The pinned DEM hash manifest is in `docs/worldgen/sources/terrain-source.json`.
Compare downloaded tiles with that manifest before claiming a byte-for-byte rebuild;
the upstream tile service itself is not versioned.
