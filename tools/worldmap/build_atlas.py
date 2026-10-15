"""Frozen OSM + DEM -> bounded, self-contained 1 km geographic tiles."""
import os
import sys, math, json, gzip, collections, hashlib, time
from pathlib import Path

import shapely, shapely.geometry as G, shapely.ops
from terrain import ROOT, Terrain, SX, SZ
OUT=Path(__file__).resolve().parents[2]/'src/main/resources/data/blacklight/worldmap/shenzhen'
OUT.mkdir(parents=True,exist_ok=True)
SIZE=1024; STEP=32
project=lambda x,y,z=None:((x-114.048)*SX,(22.552-y)*SZ)
city=shapely.ops.transform(project,G.shape(json.loads((ROOT/'shenzhen-boundary.geojson').read_text())))
terrain=Terrain(); features=collections.defaultdict(list); counts=collections.Counter(); started=time.monotonic()
KINDS={'forest':1,'park':2,'paved':3,'water':4,'river':5,'road':6,'rail':7,'building':8}
def number(value,default):
    try:return float(str(value).split(';')[0].replace('m','').strip())
    except (ValueError,TypeError):return default
def ground(x,z):
    # Smoothly join the pre-existing, hand-refined Futian map's level 63 ground.
    distance=max(0,-x,x-1440,-z,z-1232)
    t=min(1,distance/192);t=t*t*(3-2*t)
    return int(round(63+terrain.sample(x,z)*t))
def components(g):
    if g.geom_type in ('Polygon','LineString'):yield g
    elif hasattr(g,'geoms'):
        for child in g.geoms:yield from components(child)
def flat(coords):return [round(v,1) for p in coords for v in p[:2]]
def geometry(g):
    pp=[];ll=[]
    for p in components(g):
        if p.geom_type=='Polygon': pp.append([flat(p.exterior.coords)]+[flat(r.coords) for r in p.interiors])
        elif p.geom_type=='LineString':ll.append(flat(p.coords))
    return pp,ll
with gzip.open(ROOT/'shenzhen-features.jsonl.gz','rt',encoding='utf8') as stream:
    for index,line in enumerate(stream):
        raw=json.loads(line);g=G.shape(raw['geometry']);tags=raw['tags'];kind=raw['kind'];k=KINDS[kind]
        centre=g.representative_point();base=ground(centre.x,centre.y)
        ident=int.from_bytes(hashlib.sha256(raw['id'].encode()).digest()[:4],'big') & 0x7fffffff
        levels=number(tags.get('building:levels'),3 if tags.get('building') in ('house','residential') else 6)
        height=max(3,min(700,number(tags.get('height'),levels*3.3))) if k==8 else 0
        low=max(0,min(height-2,number(tags.get('min_height'),number(tags.get('building:min_level'),0)*3.3))) if k==8 else 0
        road=tags.get('highway',''); default_width={'motorway':16,'trunk':14,'primary':12,'secondary':10,'tertiary':8,'residential':6,'service':4,'footway':2,'path':2,'steps':2,'cycleway':3}.get(road,5)
        width=max(1,min(60,number(tags.get('width'),max(default_width,number(tags.get('lanes'),0)*3))))
        if k==5:width=max(2,min(60,number(tags.get('width'),5)))
        if k==7:width=3
        layer=max(0,min(10,number(tags.get('layer'),1 if tags.get('bridge')=='yes' else 0)))
        # Per-building foundations/water levels are shared across all tiles, preventing seams.
        data={'k':k,'id':ident,'y':max(2,base),'h':round(height),'m':round(low),'w':width,'l':round(layer)}
        if k==4:data['y']=max(63,base)
        bounds=g.buffer(width/2+2).bounds if k in (5,6,7) else g.bounds
        for tz in range(math.floor(bounds[1]/SIZE),math.floor(bounds[3]/SIZE)+1):
            for tx in range(math.floor(bounds[0]/SIZE),math.floor(bounds[2]/SIZE)+1):
                clip=G.box(tx*SIZE-40,tz*SIZE-40,(tx+1)*SIZE+40,(tz+1)*SIZE+40)
                if not g.intersects(clip):continue
                part=g if k==8 else g.intersection(clip)
                pp,ll=geometry(part)
                if pp or ll:features[(tx,tz)].append(dict(data,p=pp,s=ll))
        counts[kind]+=1
        if index%20000==0:print('features',index,'tiles',len(features),flush=True)

# The city's administrative polygon includes coastal water and the Shenshan exclave.
# Cover all intersecting tiles; uncharted regions outside the atlas remain ocean for now.
minx,minz,maxx,maxz=city.bounds
for z in range(math.floor(minz/SIZE),math.floor(maxz/SIZE)+1):
    for x in range(math.floor(minx/SIZE),math.floor(maxx/SIZE)+1):
        if city.intersects(G.box(x*SIZE,z*SIZE,(x+1)*SIZE,(z+1)*SIZE)):features.setdefault((x,z),[])
keys=sorted(features,key=lambda p:(max(abs(p[0]),abs(p[1])),p[1],p[0]));manifest=[]
for i,(x,z) in enumerate(keys):
    heights=[max(-60,min(1350,ground(x*SIZE+dx*STEP,z*SIZE+dz*STEP))) for dz in range(33) for dx in range(33)]
    fs=sorted(features[(x,z)],key=lambda f:(f['k'],f['m'],f['h'],f['id']))
    raw=json.dumps({'x':x,'z':z,'e':heights,'f':fs},separators=(',',':')).encode()
    packed=gzip.compress(raw,compresslevel=9,mtime=0);name=f'{x}_{z}.json.gz';(OUT/name).write_bytes(packed)
    manifest.append({'x':x,'z':z,'bytes':len(packed),'sha256':hashlib.sha256(packed).hexdigest()})
    if i%200==0:print('written',i,'/',len(keys),flush=True)
meta={'schema':1,'region':'shenzhen','name':'深圳（含深汕特别合作区）','dimension':'blacklight:world','tileSize':SIZE,'elevationStep':STEP,'projection':{'originLon':114.048,'originLat':22.552,'metresPerLongitudeDegree':SX,'metresPerLatitudeDegree':SZ,'wrapLongitude':True},'bounds':[minx,minz,maxx,maxz],'counts':dict(counts),'tiles':manifest,'sources':['OpenStreetMap contributors / ODbL 1.0 / Geofabrik Guangdong 2026-10-14','Mapzen Terrain Tiles; SRTM/GMTED2010 USGS; ETOPO1 NOAA'],'limitations':['OSM coverage is incomplete. Unspecified heights and building interiors are procedural.','DEM sampled every 32 metres, interpolated at runtime; Futian core elevation blended to the existing map.','Outside mapped regions is ocean, not a complete world reconstruction.']}
(OUT/'index.json').write_text(json.dumps(meta,ensure_ascii=False,separators=(',',':')),encoding='utf8')
print('DONE',len(keys),'tiles',sum(m['bytes'] for m in manifest),'bytes',round(time.monotonic()-started),'seconds',flush=True)
