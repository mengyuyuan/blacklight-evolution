"""Offline DEM preparation. Never imported by the game or downloaded at runtime."""
import os
import sys, math, json, hashlib, concurrent.futures, time
from pathlib import Path

import requests, numpy as np
from PIL import Image
ROOT=Path(os.environ['BLACKLIGHT_MAP_SOURCE']).resolve()
CACHE=ROOT/'terrarium'; CACHE.mkdir(exist_ok=True)
ZOOM=12; N=2**ZOOM; SX=102811.19191866614; SZ=111319.49079327358
def pixel(lon,lat):
    return (lon+180)/360*N*256, (1-math.asinh(math.tan(math.radians(lat)))/math.pi)/2*N*256
def download(key):
    x,y=key; path=CACHE/f'{x}_{y}.png'
    url=f'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{ZOOM}/{x}/{y}.png'
    for attempt in range(4):
        try:
            if not path.exists():
                r=requests.get(url,timeout=90);r.raise_for_status()
                temporary=path.with_suffix('.tmp');temporary.write_bytes(r.content)
                with Image.open(temporary) as im: im.verify()
                temporary.replace(path)
            with Image.open(path) as im: assert im.size==(256,256)
            return {'tile':[ZOOM,x,y],'url':url,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
        except Exception:
            if attempt==3:raise
            time.sleep(2+attempt*3)
class Terrain:
    def __init__(self):self.tiles={}
    def sample(self,x,z):
        lon=114.048+x/SX;lat=22.552-z/SZ;px,py=pixel(lon,lat)
        # Pixel centre convention: interpolate across tile edges using adjacent tiles.
        ix,iy=math.floor(px-.5),math.floor(py-.5);fx,fy=px-.5-ix,py-.5-iy
        def value(a,b):
            key=(a//256,b//256)
            if key not in self.tiles:
                rgb=np.array(Image.open(CACHE/f'{key[0]}_{key[1]}.png'),dtype=np.float32)
                self.tiles[key]=rgb[:,:,0]*256+rgb[:,:,1]+rgb[:,:,2]/256-32768
            return float(self.tiles[key][b%256,a%256])
        return ((1-fx)*value(ix,iy)+fx*value(ix+1,iy))*(1-fy)+((1-fx)*value(ix,iy+1)+fx*value(ix+1,iy+1))*fy
if __name__=='__main__':
    box=json.loads((ROOT/'feature-extraction.json').read_text())['boundary_bbox']
    a,b=pixel(box[0]-.03,box[3]+.03);c,d=pixel(box[2]+.03,box[1]-.03)
    keys=[(x,y) for x in range(int(a)//256,int(c)//256+1) for y in range(int(b)//256,int(d)//256+1)]
    print('DEM tiles',len(keys),flush=True); records=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for rec in pool.map(download,keys):
            records.append(rec)
            if len(records)%20==0:print('downloaded',len(records),flush=True)
    (ROOT/'terrain-source.json').write_text(json.dumps({'format':'Terrarium','zoom':ZOOM,'retrieved':'2026-10-15','attribution':'Mapzen Terrain Tiles; SRTM/GMTED2010 courtesy USGS; ETOPO1 NOAA','tiles':records},indent=2),encoding='utf8')
    print('complete',len(records),flush=True)
