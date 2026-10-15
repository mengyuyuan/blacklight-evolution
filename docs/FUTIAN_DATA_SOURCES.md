# Futian Civic Centre scenery

Map data © OpenStreetMap contributors, licensed under ODbL 1.0:
https://www.openstreetmap.org/copyright
https://opendatacommons.org/licenses/odbl/1-0/

The bundled `data/blacklight/city/futian/` database is derived from that map
data and is provided under ODbL 1.0. The mod's code license does not replace
the map database license. It contains block states only, with no copied
Minecraft textures, entities, player data, command blocks or game code.

Generation tool: Arnis 3.3.0, Apache-2.0.
https://github.com/louis-e/arnis/releases/tag/v3.3.0
Verified Windows release SHA-256:
`e4f6f8c9e07e7b8b99a7ec7f5e7c9f8790a79ff9edc2b3003b18e2dd37855a78`

OSM extract retrieved 2026-10-15:
https://api.openstreetmap.org/api/0.6/map?bbox=114.043,22.533,114.065,22.555

The generated area uses WGS84 bounding box
`22.541,114.048,22.552,114.062` (south, west, north, east), 1 block per metre.
X points east, Z points south. Output covers 1440 × 1232 blocks after chunk
padding. Ground is Y=63; maximum imported block Y=314. Real elevations are
not imported in this version. OSM building heights/parts are used where
present; missing heights and interiors are generated, not surveyed.

Reproduce with the pinned release:

```text
arnis-windows.exe --file futian.osm --bbox 22.541,114.048,22.552,114.062
  --output-dir futian-world --mode geo-only --ground-level 63 --scale 1
  --interior --overture=false --no-3d --signage none --map-item=false
  --map-preview --world-type flat --spawn-lat 22.5445 --spawn-lng 114.0545
python tools/import_futian_world.py --world "futian-world/Arnis World 1"
  --output src/main/resources/data/blacklight/city/futian
```

Commands are shown across lines for readability. The import requires Python,
numpy and nbtlib. The small `hives.json` gameplay overlay records hand-selected
real building bounding boxes and seven collision-checked facade positions per
building; infection selection does not change the underlying street geometry.

Architectural reference for the Civic Centre's connected east/central/west
wings and its relationship to the book mall, Lianhuashan and southern axis:
Shenzhen Planning and Natural Resources Bureau, Shenzhen Place Names Gazetteer:
https://pnr.sz.gov.cn/attachment/1/1285/1285348/10537364.pdf

The Civic Centre is separately adapted in `FutianCivicCentre.java`: a thin
486-block wing roof, yellow cylindrical tower, red square tower, open atrium,
raised pedestrian deck and accessible office wings replace Arnis's extruded
roof volumes. Colours and silhouette also reference the Shenzhen Archives
photograph: https://www.szdag.gov.cn/gzdt/gzdt/content/post_1590841.html
This remains a block-scale interpretation, not a survey-accurate architectural
model. The library and concert hall east entrances now have a separate curtain-wall
and tree-support detail pass in `FutianPublicSpace.java`. The south pedestrian
axis includes paving, benches and lighting. Other building volumes and most
interiors retain the OSM/Arnis base; the furniture is a playable adaptation.
Reference: Shenzhen Housing and Construction Bureau architectural archive:
https://zjj.sz.gov.cn/gkmlpt/content/12/12270/post_12270322.html
