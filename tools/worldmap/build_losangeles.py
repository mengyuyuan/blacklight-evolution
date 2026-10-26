"""Frozen Los Angeles municipal OSM/DEM -> existing Blacklight atlas schema.

Run with Python 3.12 and the project's GIS dependencies. All downloads occur here
at build time. The game reads only the resulting bounded geographic tiles.
"""
import argparse, collections, concurrent.futures, gzip, hashlib, json, math, sqlite3, sys, time
from pathlib import Path
import numpy as np
import osmium, requests, shapely, shapely.geometry as G, shapely.ops, shapely.wkb
from PIL import Image

SX=102811.19191866614; SZ=111319.49079327358; SIZE=1024; STEP=32
REGION='los_angeles'; RELATION=207359; ZOOM=12; N=2**ZOOM
KINDS={'urban':0,'forest':1,'park':2,'paved':3,'water':4,'river':5,'road':6,'rail':7,'building':8}
KEEP={'name','name:en','building','building:part','building:levels','height','min_height','building:min_level','roof:shape','roof:height','highway','width','lanes','layer','bridge','tunnel','water','waterway','natural','landuse','leisure','railway','surface','amenity'}

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists() and path.read_bytes()==data:return
    tmp=path.with_suffix(path.suffix+'.tmp');tmp.write_bytes(data);tmp.replace(path)
def record(path,value):write(path,json.dumps(value,ensure_ascii=False,indent=2).encode())
def project(x,y,z=None):return (((x-114.048+180)%360-180)*SX,(22.552-y)*SZ)
def boundary(root):
    path=root/'los-angeles-boundary.osm'
    url=f'https://api.openstreetmap.org/api/0.6/relation/{RELATION}/full'
    if not path.exists():
        r=requests.get(url,timeout=90);r.raise_for_status();write(path,r.content)
    class Boundary(osmium.SimpleHandler):
        def __init__(self):super().__init__();self.geometry=None;self.tags=None
        def area(self,a):
            if not a.from_way() and a.orig_id()==RELATION:
                self.geometry=shapely.wkb.loads(osmium.geom.WKBFactory().create_multipolygon(a),hex=True);self.tags=dict(a.tags)
    h=Boundary();h.apply_file(str(path),locations=True,idx='flex_mem')
    assert h.geometry is not None and h.geometry.is_valid
    assert h.tags['name']=='Los Angeles' and h.tags['admin_level']=='8'
    record(root/'boundary-source.json',{'url':url,'retrieved':'2026-10-25','sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'tags':h.tags,'bbox':h.geometry.bounds})
    record(root/'los-angeles-boundary.geojson',G.mapping(h.geometry))
    print('Boundary ready',h.geometry.bounds,flush=True)
    return h.geometry

def extract(root,city):
    counts=collections.Counter();invalid=collections.Counter();started=time.monotonic();factory=osmium.geom.WKBFactory()
    shapely.prepare(city);bbox=city.bounds;path=root/'los-angeles-features.jsonl.gz'
    if path.exists():return json.loads((root/'feature-extraction.json').read_text())
    temporary=path.with_suffix('.tmp')
    with gzip.open(temporary,'wt',encoding='utf8',compresslevel=6) as out:
        class Extract(osmium.SimpleHandler):
            def emit(self,kind,oid,tags,g):
                if g.is_empty:return
                a,b,c,d=g.bounds
                if c<bbox[0] or a>bbox[2] or d<bbox[1] or b>bbox[3] or not city.intersects(g):return
                if not g.is_valid:g=shapely.make_valid(g)
                if kind!='building':g=g.intersection(city)
                if g.is_empty:return
                g=shapely.ops.transform(project,g).simplify(.35,preserve_topology=True)
                out.write(json.dumps({'kind':kind,'id':oid,'tags':{k:v for k,v in tags.items() if k in KEEP},'geometry':G.mapping(g)},ensure_ascii=False,separators=(',',':'))+'\n')
                counts[kind]+=1
                if sum(counts.values())%25000==0:print('Extracted',dict(counts),'seconds',round(time.monotonic()-started),flush=True)
            def way(self,w):
                t=dict(w.tags)
                kind='road' if 'highway' in t else 'rail' if t.get('railway') in ('rail','light_rail','tram') else 'river' if 'waterway' in t else None
                if not kind or t.get('area')=='yes' or t.get('highway') in ('proposed','construction') or t.get('tunnel')=='yes':return
                try:self.emit(kind,'w'+str(w.id),t,shapely.wkb.loads(factory.create_linestring(w),hex=True))
                except (RuntimeError,shapely.errors.GEOSException) as e:invalid[type(e).__name__]+=1
            def area(self,a):
                t=dict(a.tags)
                kind='building' if t.get('building','no')!='no' or t.get('building:part','no')!='no' else 'water' if t.get('natural')=='water' or t.get('waterway')=='riverbank' or t.get('landuse') in ('reservoir','basin') else 'forest' if t.get('natural') in ('wood','scrub') or t.get('landuse')=='forest' else 'park' if t.get('leisure') in ('park','garden','golf_course','playground','recreation_ground') or t.get('landuse') in ('grass','meadow','recreation_ground') else 'paved' if t.get('amenity')=='parking' or t.get('highway')=='pedestrian' else 'urban' if t.get('landuse') in ('residential','commercial','retail','industrial','construction','brownfield','railway','garages','institutional','education','religious','military','cemetery') else None
                if not kind:return
                try:self.emit(kind,('w' if a.from_way() else 'r')+str(a.orig_id()),t,shapely.wkb.loads(factory.create_multipolygon(a),hex=True))
                except (RuntimeError,shapely.errors.GEOSException) as e:invalid[type(e).__name__]+=1
        Extract().apply_file(str(root/'socal-261024.osm.pbf'),locations=True,idx='flex_mem')
    report={'counts':dict(counts),'invalid':dict(invalid),'boundary_bbox':bbox,'seconds':time.monotonic()-started}
    assert counts['building']>10000 and counts['road']>10000,report
    record(root/'feature-extraction.json',report);temporary.replace(path);print('Extraction complete',report,flush=True);return report

def pixel(lon,lat):return (lon+180)/360*N*256,(1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*N*256
def terrain(root,city):
    cache=root/'terrarium';cache.mkdir(exist_ok=True)
    box=city.bounds;a,b=pixel(box[0]-.03,box[3]+.03);c,d=pixel(box[2]+.03,box[1]-.03)
    keys=[(x,y) for x in range(int(a)//256,int(c)//256+1) for y in range(int(b)//256,int(d)//256+1)]
    def download(key):
        x,y=key;path=cache/f'{x}_{y}.png';url=f'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{ZOOM}/{x}/{y}.png'
        for attempt in range(4):
            try:
                if not path.exists():
                    r=requests.get(url,timeout=90);r.raise_for_status();write(path,r.content)
                with Image.open(path) as im:assert im.size==(256,256);im.verify()
                return {'tile':[ZOOM,x,y],'url':url,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
            except Exception:
                if attempt==3:raise
                time.sleep(2+attempt*3)
    print('DEM tiles',len(keys),flush=True);records=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for item in pool.map(download,keys):
            records.append(item)
            if len(records)%25==0:print('DEM ready',len(records),'/',len(keys),flush=True)
    record(root/'terrain-source.json',{'format':'Terrarium','zoom':ZOOM,'retrieved':'2026-10-25','attribution':'Mapzen Terrain Tiles; SRTM/GMTED2010 courtesy USGS; ETOPO1 NOAA','tiles':records})

class Terrain:
    def __init__(self,root):
        self.cache=root/'terrarium';self.tiles={}
        self.core_ground=self.raw_ground(*project(-118.243,34.05575))
    def ground(self,x,z):
        raw=self.raw_ground(x,z)
        x0,z0=project(-118.248,34.0595);x1,z1=project(-118.238,34.052)
        distance=max(0,x0-x,x-x1,z0-z,z-z1)
        t=min(1,distance/128);t=t*t*(3-2*t)
        return round(self.core_ground+(raw-self.core_ground)*t)
    def raw_ground(self,x,z):
        # Invert the wrapped global projection before sampling western-hemisphere DEM.
        lon=(114.048+x/SX+180)%360-180;lat=22.552-z/SZ;px,py=pixel(lon,lat)
        ix,iy=math.floor(px-.5),math.floor(py-.5);fx,fy=px-.5-ix,py-.5-iy
        def value(a,b):
            key=(a//256,b//256)
            if key not in self.tiles:
                rgb=np.array(Image.open(self.cache/f'{key[0]}_{key[1]}.png'),dtype=np.float32)
                self.tiles[key]=rgb[:,:,0]*256+rgb[:,:,1]+rgb[:,:,2]/256-32768
            return float(self.tiles[key][b%256,a%256])
        elevation=((1-fx)*value(ix,iy)+fx*value(ix+1,iy))*(1-fy)+((1-fx)*value(ix,iy+1)+fx*value(ix+1,iy+1))*fy
        return max(2,min(1350,round(63+elevation)))

def number(value,default):
    try:
        text=str(value).split(';')[0].strip().lower()
        if "'" in text:
            a,_,b=text.partition("'");return float(a)*.3048+(float(b.replace('"','').strip())*.0254 if b.strip('" ') else 0)
        if text.endswith('ft'):return float(text[:-2].strip())*.3048
        return float(text.removesuffix('m').strip())
    except (ValueError,TypeError):return default
def components(g):
    if g.geom_type in ('Polygon','LineString'):yield g
    elif hasattr(g,'geoms'):
        for child in g.geoms:yield from components(child)
def flat(coords):return [round(v,1) for p in coords for v in p[:2]]
def geometry(g):
    pp=[];ll=[]
    for p in components(g):
        if p.geom_type=='Polygon':pp.append([flat(p.exterior.coords)]+[flat(r.coords) for r in p.interiors])
        elif p.geom_type=='LineString':ll.append(flat(p.coords))
    return pp,ll
def build(root,out,city):
    dem=Terrain(root);features=set();counts=collections.Counter();started=time.monotonic()
    # Keep a million OSM buildings out of RAM; this is a disposable build-time spool.
    db=sqlite3.connect(root/'atlas-build.sqlite');db.execute('PRAGMA journal_mode=OFF');db.execute('PRAGMA synchronous=OFF');db.execute('PRAGMA cache_size=-32768')
    db.execute('DROP TABLE IF EXISTS features');db.execute('CREATE TABLE features (x INTEGER,z INTEGER,k INTEGER,m INTEGER,h INTEGER,id INTEGER,data TEXT)');pending=[]
    with gzip.open(root/'los-angeles-features.jsonl.gz','rt',encoding='utf8') as stream:
        for index,line in enumerate(stream):
            raw=json.loads(line);g=G.shape(raw['geometry']);tags=raw['tags'];kind=raw['kind'];k=KINDS[kind]
            centre=g.representative_point();base=dem.ground(centre.x,centre.y)
            ident=int.from_bytes(hashlib.sha256(raw['id'].encode()).digest()[:4],'big')&0x7fffffff
            levels=number(tags.get('building:levels'),2 if tags.get('building') in ('house','detached','residential','apartments','terrace','semidetached_house') else 4)
            height=max(3,min(700,number(tags.get('height'),levels*3.3))) if k==8 else 0
            low=max(0,min(height-2,number(tags.get('min_height'),number(tags.get('building:min_level'),0)*3.3))) if k==8 else 0
            road=tags.get('highway','');default={'motorway':16,'motorway_link':8,'trunk':14,'primary':12,'secondary':10,'tertiary':8,'residential':6,'service':4,'footway':2,'path':2,'steps':2,'cycleway':3}.get(road,5)
            width=max(1,min(60,number(tags.get('width'),max(default,number(tags.get('lanes'),0)*3))))
            if k==5:width=max(2,min(60,number(tags.get('width'),5)))
            if k==7:width=3
            layer=round(max(0,min(10,number(tags.get('layer'),1 if tags.get('bridge')=='yes' else 0))))
            data={'k':k,'id':ident,'y':max(64 if k==8 else 2,base),'h':round(height),'m':round(low),'w':width,'l':layer}
            if k==8:data.update(a=0,n=tags.get('name',''))
            if k==4:data['y']=max(63,base)
            bounds=g.buffer(24 if k==0 else width/2+(24 if k==6 else 5)).bounds if k in (0,5,6,7) else g.bounds
            for tz in range(math.floor(bounds[1]/SIZE),math.floor(bounds[3]/SIZE)+1):
                for tx in range(math.floor(bounds[0]/SIZE),math.floor(bounds[2]/SIZE)+1):
                    clip=G.box(tx*SIZE-64,tz*SIZE-64,(tx+1)*SIZE+64,(tz+1)*SIZE+64)
                    if not g.intersects(clip):continue
                    part=g if k==8 else g.intersection(clip);pp,ll=geometry(part)
                    if pp or ll:
                        features.add((tx,tz));pending.append((tx,tz,k,data['m'],data['h'],ident,json.dumps(dict(data,p=pp,s=ll),ensure_ascii=False,separators=(',',':'))))
                        if len(pending)>=5000:db.executemany('INSERT INTO features VALUES (?,?,?,?,?,?,?)',pending);pending.clear()
            counts[kind]+=1
            if index%25000==0:print('Indexed',index,'tiles',len(features),flush=True)
    projected=shapely.ops.transform(project,city);minx,minz,maxx,maxz=projected.bounds
    for z in range(math.floor(minz/SIZE),math.floor(maxz/SIZE)+1):
        for x in range(math.floor(minx/SIZE),math.floor(maxx/SIZE)+1):
            if projected.intersects(G.box(x*SIZE,z*SIZE,(x+1)*SIZE,(z+1)*SIZE)):features.add((x,z))
    if pending:db.executemany('INSERT INTO features VALUES (?,?,?,?,?,?,?)',pending);pending.clear()
    db.execute('CREATE INDEX cells ON features (x,z,k,m,h,id)');db.commit()
    manifest=[];keys=sorted(features,key=lambda p:(p[1],p[0]));out.mkdir(parents=True,exist_ok=True)
    for i,(x,z) in enumerate(keys):
        heights=[dem.ground(x*SIZE+dx*STEP,z*SIZE+dz*STEP) for dz in range(33) for dx in range(33)]
        fs=[json.loads(row[0]) for row in db.execute('SELECT data FROM features WHERE x=? AND z=? ORDER BY k,m,h,id',(x,z))]
        packed=gzip.compress(json.dumps({'x':x,'z':z,'e':heights,'f':fs},ensure_ascii=False,separators=(',',':')).encode(),compresslevel=9,mtime=0)
        write(out/f'{x}_{z}.json.gz',packed);manifest.append({'x':x,'z':z,'bytes':len(packed),'sha256':hashlib.sha256(packed).hexdigest()})
        if i%100==0:print('Written',i,'/',len(keys),flush=True)
    source=json.loads((root/'osm-source.json').read_text());boundary_meta=json.loads((root/'boundary-source.json').read_text())
    index={'schema':1,'region':REGION,'name':'洛杉矶市 / Los Angeles','dimension':'blacklight:world','tileSize':SIZE,'elevationStep':STEP,'projection':{'originLon':114.048,'originLat':22.552,'metresPerLongitudeDegree':SX,'metresPerLatitudeDegree':SZ,'wrapLongitude':True},'bounds':[minx,minz,maxx,maxz],'counts':dict(counts),'tiles':manifest,'arrival':{'longitude':-118.2455,'latitude':34.0561,'name':'Grand Park / 洛杉矶市政中心'},'core':{'bbox':[34.052,-118.248,34.0595,-118.238],'ground':dem.core_ground,'blendMetres':128,'generator':'Arnis 3.3.0'},'source':source,'boundarySource':boundary_meta,'sources':['© OpenStreetMap contributors / ODbL 1.0 / Geofabrik Southern California 2026-10-24','Mapzen Terrain Tiles; SRTM/GMTED2010 USGS; ETOPO1 NOAA','Arnis 3.3.0 / Apache-2.0 / OSM civic-centre extract 2026-10-25'],'limitations':['Municipal Los Angeles relation 207359, not Los Angeles County or the entire metropolitan area.','OSM geometry is incomplete; untagged heights, building facades and interiors are procedural.','Fixed Shenzhen projection preserves existing saves; east-west scale in Los Angeles differs from 1 block/metre.','32-metre resampled DEM; civic core is flat with a 128-metre terrain blend; elevations above 1287 metres are capped.','Only newly generated chunks receive the map; existing chunks are preserved.']}
    db.close();record(out/'index.json',index);print('Atlas complete',len(keys),'tiles',sum(m['bytes'] for m in manifest),'bytes',round(time.monotonic()-started),'seconds',flush=True)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--phase',choices=['extract','terrain','build','all'],default='all');args=p.parse_args()
    root=args.source;root.mkdir(parents=True,exist_ok=True);city=boundary(root)
    if args.phase in ('extract','all'):extract(root,city)
    if args.phase in ('terrain','all'):terrain(root,city)
    if args.phase in ('build','all'):build(root,args.output,city)
if __name__=='__main__':main()
