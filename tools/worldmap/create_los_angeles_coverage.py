from pathlib import Path
import argparse,hashlib,json,math,struct
import numpy as np
import shapely,shapely.geometry as G
parser=argparse.ArgumentParser();parser.add_argument('--work',type=Path,required=True);args=parser.parse_args();root=args.work.resolve();city=G.shape(json.loads((root/'municipal-native-boundary.geojson').read_text()));shapely.prepare(city)
a,b,c,d=city.bounds;records=[];count=0
for rz in range(math.floor(b/512),math.floor(d/512)+1):
    for rx in range(math.floor(a/512),math.floor(c/512)+1):
        rect=G.box(rx*512,rz*512,(rx+1)*512,(rz+1)*512)
        if not city.intersects(rect):continue
        if city.covers(rect):selected=np.ones(1024,dtype=np.bool_)
        else:
            i=np.arange(1024);x=rx*512+(i%32)*16;z=rz*512+(i//32)*16
            selected=shapely.intersects(city,shapely.box(x,z,x+16,z+16))
        if not np.any(selected):continue
        mask=np.packbits(selected,bitorder='little').tobytes();n=int(np.sum(selected));count+=n;records.append((rx,rz,mask,n))
path=root/'coverage.bin'
with path.open('wb') as out:
    out.write(b'LACM'+struct.pack('>II',1,len(records)))
    for rx,rz,mask,n in records:out.write(struct.pack('>ii',rx,rz)+mask)
report={'schema':1,'regions':len(records),'requiredNativeChunks':count,'bytes':path.stat().st_size,'sha256':hashlib.file_digest(path.open('rb'),'sha256').hexdigest(),'nativeBounds':list(city.bounds),'selection':'Every 16x16 native chunk intersecting municipal relation 207359, including boundary-touching chunks.'}
(root/'coverage-report.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report),flush=True)
