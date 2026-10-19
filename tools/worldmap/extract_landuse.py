"""Supplement frozen map geometry with OSM urban land use (offline, same pinned PBF)."""
import os, json, gzip, collections, time
from pathlib import Path
import osmium, shapely, shapely.geometry as G, shapely.ops, shapely.wkb
from terrain import SX, SZ

root = Path(os.environ['BLACKLIGHT_MAP_SOURCE']).resolve()
city = G.shape(json.loads((root / 'shenzhen-boundary.geojson').read_text()))
shapely.prepare(city)
factory = osmium.geom.WKBFactory()
counts = collections.Counter()
invalid = collections.Counter()
project = lambda x,y,z=None: ((x-114.048)*SX,(22.552-y)*SZ)
urban = {'residential','commercial','retail','industrial','institutional','railway',
         'construction','brownfield','garages','education','religious','civic_admin'}
started = time.monotonic()
with gzip.open(root/'shenzhen-landuse.jsonl.gz','wt',encoding='utf8') as out:
    class Extract(osmium.SimpleHandler):
        def area(self, area):
            tags = dict(area.tags)
            if tags.get('landuse') not in urban: return
            try:
                geometry = shapely.wkb.loads(factory.create_multipolygon(area), hex=True)
            except RuntimeError:
                invalid['invalid_area'] += 1
                return
            if not geometry.is_valid: geometry = shapely.make_valid(geometry)
            if not geometry.intersects(city): return
            geometry = shapely.ops.transform(project,geometry.intersection(city)).simplify(.35,preserve_topology=True)
            if geometry.is_empty: return
            out.write(json.dumps({'kind':'urban','id':('w' if area.from_way() else 'r')+str(area.orig_id()),
                'tags':tags,'geometry':G.mapping(geometry)},ensure_ascii=False,separators=(',',':'))+'\n')
            counts[tags['landuse']] += 1
    Extract().apply_file(str(root/'guangdong-261014.osm.pbf'),locations=True,idx='flex_mem')
report = {'counts':dict(counts),'invalid':dict(invalid),'source':'guangdong-261014.osm.pbf','seconds':time.monotonic()-started}
(root/'landuse-extraction.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print(report,flush=True)
