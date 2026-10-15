import os
import sys,math,json,gzip,time,collections
from pathlib import Path

import osmium,shapely,shapely.geometry as G,shapely.wkb,shapely.ops
root=Path(os.environ['BLACKLIGHT_MAP_SOURCE']).resolve()
fc=json.loads((root/'boundaries.geojson').read_text(encoding='utf8'))
city=G.shape(next(f['geometry'] for f in fc['features'] if f['properties']['id']==3464353));box=city.bounds
shapely.prepare(city)
# World projection is fixed at the existing Futian sample, with longitude wrap for future continents.
SX=111319.49079327358*math.cos(math.radians(22.5465));SZ=111319.49079327358
project=lambda x,y,z=None:(((x-114.048+180)%360-180)*SX,(22.552-y)*SZ)
factory=osmium.geom.WKBFactory();counts=collections.Counter();invalid=collections.Counter()
selected_districts=[]
for f in fc['features']:
 g=G.shape(f['geometry'])
 if f['properties'].get('name','') in ['福田区','罗湖区','南山区','盐田区','宝安区','龙岗区','龙华区','坪山区','光明区','大鹏新区','深汕特别合作区']:
  selected_districts.append({'name':f['properties']['name'],'id':f['properties']['id'],'geometry':G.mapping(g)})
(root/'shenzhen-boundary.geojson').write_text(json.dumps(G.mapping(city)),encoding='utf8')
(root/'districts.geojson').write_text(json.dumps(selected_districts,ensure_ascii=False),encoding='utf8')
keep_tags={'name','name:en','building','building:part','building:levels','height','min_height','building:min_level','roof:shape','roof:height','building:material','building:colour','highway','width','lanes','layer','bridge','tunnel','water','waterway','natural','landuse','leisure','railway','surface','amenity'}
start=time.monotonic()
with gzip.open(root/'shenzhen-features.jsonl.gz','wt',encoding='utf8',compresslevel=6) as out:
 class Extract(osmium.SimpleHandler):
  def emit(self,kind,oid,tags,g):
   if g.is_empty:return
   a,b,c,d=g.bounds
   if c<box[0] or a>box[2] or d<box[1] or b>box[3] or not city.intersects(g):return
   if not g.is_valid:g=shapely.make_valid(g)
   # Retain entire buildings crossing the city boundary; clip long landscape and road geometry.
   if kind!='building':g=g.intersection(city)
   if g.is_empty:return
   g=shapely.ops.transform(project,g).simplify(.35,preserve_topology=True)
   out.write(json.dumps({'kind':kind,'id':oid,'tags':{k:v for k,v in tags.items() if k in keep_tags},'geometry':G.mapping(g)},ensure_ascii=False,separators=(',',':'))+'\n');counts[kind]+=1
   if sum(counts.values())%10000==0:print(dict(counts),'seconds',round(time.monotonic()-start,1),flush=True)
  def way(self,w):
   t=dict(w.tags)
   kind='road' if 'highway' in t else 'rail' if t.get('railway') in ('rail','light_rail','tram') else 'river' if 'waterway' in t else None
   if not kind or t.get('area')=='yes' or t.get('highway') in ('proposed','construction') or t.get('tunnel')=='yes':return
   try:self.emit(kind,'w'+str(w.id),t,shapely.wkb.loads(factory.create_linestring(w),hex=True))
   except Exception as e:invalid[type(e).__name__]+=1
  def area(self,a):
   t=dict(a.tags)
   kind='building' if t.get('building','no')!='no' or t.get('building:part','no')!='no' else 'water' if t.get('natural')=='water' or t.get('waterway')=='riverbank' or t.get('landuse') in ('reservoir','basin') else 'forest' if t.get('natural') in ('wood','scrub') or t.get('landuse')=='forest' else 'park' if t.get('leisure') in ('park','garden','golf_course','playground','recreation_ground') or t.get('landuse') in ('grass','meadow','recreation_ground') else 'paved' if t.get('amenity')=='parking' or t.get('highway')=='pedestrian' else None
   if not kind:return
   try:self.emit(kind,('w' if a.from_way() else 'r')+str(a.orig_id()),t,shapely.wkb.loads(factory.create_multipolygon(a),hex=True))
   except Exception as e:invalid[type(e).__name__]+=1
 h=Extract();h.apply_file(str(root/'guangdong-261014.osm.pbf'),locations=True,idx='flex_mem')
report={'counts':dict(counts),'invalid':dict(invalid),'boundary_bbox':box,'projection':{'originLon':114.048,'originLat':22.552,'metresPerLongitudeDegree':SX,'metresPerLatitudeDegree':SZ,'wrapLongitude':True},'districts':[d['name'] for d in selected_districts],'seconds':time.monotonic()-start}
(root/'feature-extraction.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8');print(report,flush=True)
