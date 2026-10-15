import os
import sys

import osmium,shapely,shapely.wkb,shapely.geometry,json
from pathlib import Path
root=Path(os.environ['BLACKLIGHT_MAP_SOURCE']).resolve()
class Boundaries(osmium.SimpleHandler):
 def __init__(self):super().__init__();self.factory=osmium.geom.WKBFactory();self.items=[]
 def area(self,a):
  tags=dict(a.tags)
  if a.from_way() or tags.get('boundary')!='administrative':return
  if a.orig_id()==3464353 or tags.get('admin_level')=='6':
   try:g=shapely.wkb.loads(self.factory.create_multipolygon(a),hex=True)
   except Exception:return
   if a.orig_id()==3464353 or (g.bounds[0]<115.6 and g.bounds[2]>113.7 and g.bounds[1]<23.2 and g.bounds[3]>22.3):
    self.items.append({'type':'Feature','properties':{'id':a.orig_id(),**tags},'geometry':shapely.geometry.mapping(g)})
    print(a.orig_id(),tags.get('name'),g.bounds,flush=True)
h=Boundaries();h.apply_file(str(root/'guangdong-261014.osm.pbf'),locations=True,idx='flex_mem')
assert any(f['properties']['id']==3464353 for f in h.items),'Shenzhen boundary missing'
(root/'boundaries.geojson').write_text(json.dumps({'type':'FeatureCollection','features':h.items},ensure_ascii=False),encoding='utf8')
print('boundary complete',len(h.items),flush=True)
