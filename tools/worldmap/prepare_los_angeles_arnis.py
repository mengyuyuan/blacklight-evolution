"""Partition the pinned municipal PBF into complete, offline Arnis inputs.

Original OSM IDs, tags, way nodes and multipolygon members are retained. Only
the city's objects are selected, and geometry crossing a batch boundary is
included whole on both sides. Never uses Overpass or partial web extracts.
"""
from pathlib import Path
import argparse,collections,gzip,hashlib,json,math,sqlite3,time
import osmium,shapely,shapely.geometry as G,shapely.ops,shapely.wkb

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source',type=Path,required=True,help='Frozen Los Angeles source directory')
parser.add_argument('--output',type=Path,required=True,help='External build-work directory')
args=parser.parse_args();SOURCE=args.source.resolve();OUT=args.output.resolve();OUT.mkdir(parents=True,exist_ok=True)
source_info=json.loads((SOURCE/'osm-source.json').read_text());PBF=SOURCE/source_info['file']
assert hashlib.file_digest(PBF.open('rb'),'sha256').hexdigest()==source_info['sha256'],'Frozen PBF hash mismatch'
SIZE=4096;PAD=128
LAT=34.05575;LON=-118.243;R=6371000.0;C=math.cos(math.radians(LAT));MY=R*math.log(math.tan(math.pi/4+math.radians(LAT)/2))
KEYS={'building','building:part','highway','landuse','natural','leisure','water','waterway','amenity','tourism','bridge','railway','roller_coaster','barrier','entrance','door','power','historic','emergency','advertising','man_made','aeroway','3dmr','shop','office','place'}
def wanted(t):
    if not KEYS.intersection(t):return t.get('type')=='building'
    return not (t.get('natural') in ('coastline','bay','strait') or t.get('water') in ('bay','ocean','sea') or t.get('waterway')=='tidal_channel' or t.get('landuse')=='salt_pond')
def project(lon,lat,z=None):return (R*math.radians(lon-LON)*C,(MY-R*math.log(math.tan(math.pi/4+math.radians(lat)/2)))*C)
def inverse(x,z):return (LON+math.degrees(x/(R*C)),math.degrees(2*math.atan(math.exp((MY-z/C)/R))-math.pi/2))
def record(path,obj):path.write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf8')
city=G.shape(json.loads((SOURCE/'los-angeles-boundary.geojson').read_text()));shapely.prepare(city);box=city.bounds
native=shapely.ops.transform(project,city);a,b,c,d=native.bounds
batches={}
for bz in range(math.floor(b/SIZE),math.floor(d/SIZE)+1):
    for bx in range(math.floor(a/SIZE),math.floor(c/SIZE)+1):
        if native.intersects(G.box(bx*SIZE,bz*SIZE,(bx+1)*SIZE,(bz+1)*SIZE)):
            west,south=inverse(bx*SIZE+.0001,(bz+1)*SIZE-.0001);east,north=inverse((bx+1)*SIZE-.0001,bz*SIZE+.0001)
            batches[(bx,bz)]={'id':f'{bx}_{bz}','bx':bx,'bz':bz,'bounds':[bx*SIZE,bz*SIZE,(bx+1)*SIZE-1,(bz+1)*SIZE-1],'bbox':[south,west,north,east]}
record(OUT/'municipal-native-boundary.geojson',G.mapping(native))
def assigned(bounds):
    lo,hi=project(bounds[0],bounds[3]),project(bounds[2],bounds[1])
    return [(x,z) for z in range(math.floor((lo[1]-PAD)/SIZE),math.floor((hi[1]+PAD)/SIZE)+1) for x in range(math.floor((lo[0]-PAD)/SIZE),math.floor((hi[0]+PAD)/SIZE)+1) if (x,z) in batches]
def touches(g):
    if g.is_empty:return False
    a,b,c,d=g.bounds
    return c>=box[0] and a<=box[2] and d>=box[1] and b<=box[3] and city.intersects(g)

relations={};member_ids=set();start=time.monotonic()
class Relations(osmium.SimpleHandler):
    def relation(self,r):
        tags=dict(r.tags)
        if wanted(tags):
            members=[{'type':{'n':'node','w':'way','r':'relation'}[m.type],'ref':m.ref,'role':m.role} for m in r.members]
            relations[r.id]={'type':'relation','id':r.id,'tags':tags,'members':members}
            member_ids.update(m.ref for m in r.members if m.type=='w')
print('Planning',len(batches),'batches; reading relation dependencies',flush=True)
Relations().apply_file(str(PBF));print('Relations',len(relations),'member ways',len(member_ids),'seconds',round(time.monotonic()-start),flush=True)

db=sqlite3.connect(OUT/'source-partitions.sqlite');db.execute('PRAGMA journal_mode=OFF');db.execute('PRAGMA synchronous=OFF');db.execute('PRAGMA cache_size=-32768')
db.execute('DROP TABLE IF EXISTS objects');db.execute('DROP TABLE IF EXISTS members')
db.execute('CREATE TABLE objects (bx INTEGER,bz INTEGER,kind TEXT,id INTEGER,payload TEXT,PRIMARY KEY (bx,bz,kind,id)) WITHOUT ROWID')
db.execute('CREATE TABLE members (id INTEGER PRIMARY KEY,payload TEXT)')
pending=[];member_pending=[];tagged={};counts=collections.Counter();invalid=collections.Counter();factory=osmium.geom.WKBFactory()
def flush():
    if pending:db.executemany('INSERT OR IGNORE INTO objects VALUES (?,?,?,?,?)',pending);pending.clear()
    if member_pending:db.executemany('INSERT OR REPLACE INTO members VALUES (?,?)',member_pending);member_pending.clear()
def add(keys,kind,oid,payload):
    for bx,bz in keys:pending.append((bx,bz,kind,oid,payload))
    if len(pending)+len(member_pending)>=5000:flush()
def add_relation(rid,keys):
    if not keys or rid not in relations:return
    flush();rel=relations[rid];ways=[]
    for member in rel['members']:
        if member['type']=='way':
            row=db.execute('SELECT payload FROM members WHERE id=?',(member['ref'],)).fetchone()
            if row:ways.append((member['ref'],row[0]))
            else:invalid['missing_relation_member']+=1
    payload=json.dumps([rel],ensure_ascii=False,separators=(',',':'))
    add(keys,'relation',rid,payload)
    for oid,data in ways:add(keys,'way',oid,data)

class Objects(osmium.SimpleHandler):
    def node(self,n):
        if not n.tags:return
        lat,lon=n.location.lat,n.location.lon
        if not (box[0]-.02<=lon<=box[2]+.02 and box[1]-.02<=lat<=box[3]+.02):return
        tags=dict(n.tags)
        if not wanted(tags):return
        tagged[n.id]=tags
        if city.covers(G.Point(lon,lat)):
            value={'type':'node','id':n.id,'lat':lat,'lon':lon,'tags':tags}
            add(assigned((lon,lat,lon,lat)),'node',n.id,json.dumps([value],ensure_ascii=False,separators=(',',':')));counts['taggedNodes']+=1
    def way(self,w):
        tags=dict(w.tags);member=w.id in member_ids
        if not member and not wanted(tags):return
        try:
            coords=[(n.lon,n.lat) for n in w.nodes]
            if len(coords)<2:return
            a=min(p[0] for p in coords);b=min(p[1] for p in coords);c=max(p[0] for p in coords);d=max(p[1] for p in coords)
            spatial=c>=box[0] and a<=box[2] and d>=box[1] and b<=box[3]
            keys=[]
            if spatial and wanted(tags):
                closed=len(coords)>=4 and w.nodes[0].ref==w.nodes[-1].ref
                area=closed and (tags.get('area')=='yes' or bool({'building','building:part','landuse','leisure','natural','water','amenity','aeroway'}.intersection(tags)))
                g=G.Polygon(coords) if area else G.LineString(coords)
                if not g.is_valid:g=shapely.make_valid(g)
                if touches(g):keys=assigned(g.bounds)
            if not keys and not member:return
            values=[{'type':'way','id':w.id,'nodes':[n.ref for n in w.nodes],'tags':tags}]
            values.extend({'type':'node','id':n.ref,'lon':lon,'lat':lat,**({'tags':tagged[n.ref]} if n.ref in tagged else {})} for n,(lon,lat) in zip(w.nodes,coords))
            payload=json.dumps(values,ensure_ascii=False,separators=(',',':'))
            if member:member_pending.append((w.id,payload))
            if keys:
                add(keys,'way',w.id,payload);counts['ways']+=1
                if 'building' in tags or 'building:part' in tags:counts['buildingWays']+=1
            if len(pending)+len(member_pending)>=5000:flush()
            if counts['ways'] and counts['ways']%25000==0:print('Selected',dict(counts),'seconds',round(time.monotonic()-start),flush=True)
        except (RuntimeError,shapely.errors.GEOSException):invalid['way_geometry']+=1
    def area(self,a):
        if a.from_way() or a.orig_id() not in relations:return
        try:
            g=shapely.wkb.loads(factory.create_multipolygon(a),hex=True)
            if not g.is_valid:g=shapely.make_valid(g)
            if touches(g):add_relation(a.orig_id(),assigned(g.intersection(city).bounds));counts['areaRelations']+=1
        except (RuntimeError,shapely.errors.GEOSException):invalid['area_geometry']+=1

print('Selecting original objects and preserving member geometry',flush=True)
Objects().apply_file(str(PBF),locations=True,idx='flex_mem');flush();db.commit();db.execute('CREATE INDEX object_ids ON objects(kind,id,bx,bz)');db.commit()
for rid,rel in relations.items():
    if rel['tags'].get('type')!='building':continue
    keys=set()
    for member in rel['members']:
        if member['type']=='way':keys.update(db.execute("SELECT bx,bz FROM objects WHERE kind='way' AND id=?",(member['ref'],)).fetchall())
    if keys:add_relation(rid,sorted(keys));counts['buildingRelations']+=1
flush();db.commit()
# Area callbacks can precede the final member-way callback: close dependencies after EOF.
db.execute("""INSERT OR IGNORE INTO objects (bx,bz,kind,id,payload)
SELECT o.bx,o.bz,'way',w.id,w.payload FROM objects o
JOIN json_each(o.payload,'$[0].members') m JOIN members w ON w.id=json_extract(m.value,'$.ref')
WHERE o.kind='relation' AND json_extract(m.value,'$.type')='way' """);db.commit()
missing=db.execute("""SELECT COUNT(*) FROM objects o JOIN json_each(o.payload,'$[0].members') m
LEFT JOIN members w ON w.id=json_extract(m.value,'$.ref')
WHERE o.kind='relation' AND json_extract(m.value,'$.type')='way' AND w.id IS NULL""").fetchone()[0]
assert missing==0,('Unavailable selected relation dependencies',missing)
invalid['missing_relation_member']=0
inputs=OUT/'inputs';inputs.mkdir(exist_ok=True)
ordered=sorted(batches,key=lambda p:(max(abs(p[0]),abs(p[1])),p[1],p[0]))
for i,key in enumerate(ordered):
    batch=batches[key];path=inputs/(batch['id']+'.json');n=0;objects=collections.Counter()
    with path.open('w',encoding='utf8',newline='\n') as out:
        out.write('{"elements":[')
        for kind,payload in db.execute("SELECT kind,payload FROM objects WHERE bx=? AND bz=? ORDER BY kind,id",key):
            body=payload[1:-1]
            if body:
                if n:out.write(',')
                out.write(body);n+=1;objects[kind]+=1
        out.write(']}')
    batch.update(input=str(path.resolve()),inputBytes=path.stat().st_size,inputSHA256=hashlib.file_digest(path.open('rb'),'sha256').hexdigest(),objects=dict(objects))
    if i%10==0:print('Exported',i+1,'/',len(ordered),flush=True)
report={'schema':1,'generator':'Arnis 3.3.0','source':json.loads((SOURCE/'osm-source.json').read_text()),'boundarySource':json.loads((SOURCE/'boundary-source.json').read_text()),'projection':{'kind':'web_mercator','originLat':LAT,'originLon':LON,'earthRadius':R,'scale':1},'elevation':{'min_height_m':0,'blocks_per_meter':0.8,'ground_level':63},'batchSize':SIZE,'geometryPad':PAD,'counts':dict(counts),'invalid':dict(invalid),'batches':[batches[k] for k in ordered],'seconds':round(time.monotonic()-start,2)}
record(OUT/'inputs-index.json',report);db.close();print('INPUTS COMPLETE',json.dumps({'batches':len(ordered),'counts':dict(counts),'invalid':dict(invalid),'seconds':report['seconds']}),flush=True)
