"""Lossless runtime packing of the accepted neutral mesh into rigid wing groups.

No vertex, UV, normal or texture simplification. Whole triangles belong to one
attachment/wing; folding can rotate and uniformly retract, but cannot stretch it.
"""
from pathlib import Path
import argparse,struct,json,gzip,hashlib,io
import numpy as np
from PIL import Image
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('source',type=Path,help='Accepted sovereign_wings_neutral-v11.glb')
parser.add_argument('--resources',type=Path,default=Path(__file__).resolve().parents[2]/'src/main/resources')
parser.add_argument('--report',type=Path,required=True,help='Export provenance report')
args=parser.parse_args();SOURCE=args.source
data=SOURCE.read_bytes()
if hashlib.sha256(data).hexdigest()!='3a60ad19e56bb0f767c55f9c561699f0110ba10be6e6ef480ec95dd4e1569dba':
    raise ValueError('Source does not match the accepted neutral model; review its topology before packing')
size=struct.unpack_from('<I',data,12)[0];gltf=json.loads(data[20:20+size]);blob=data[28+size:]
def read(index):
    a=gltf['accessors'][index];v=gltf['bufferViews'][a['bufferView']]
    width={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}[a['type']]
    dtype={5121:'u1',5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']]
    return np.frombuffer(blob,dtype=dtype,count=a['count']*width,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,width).copy()
textures=args.resources/'assets/blacklight/textures/wings_precision';textures.mkdir(parents=True,exist_ok=True)
def image_bytes(texture_index):
    image=gltf['images'][gltf['textures'][texture_index]['source']];v=gltf['bufferViews'][image['bufferView']]
    return blob[v.get('byteOffset',0):v.get('byteOffset',0)+v['byteLength']]
def uv_for(attrs,info):
    uv=read(attrs['TEXCOORD_'+str(info.get('texCoord',0))]);t=info.get('extensions',{}).get('KHR_texture_transform',{})
    uv*=t.get('scale',[1,1]);uv+=t.get('offset',[0,0]);return uv
out=bytearray();meshes=[];source_triangles=0;glow_triangles=0
for primitive in gltf['meshes'][0]['primitives']:
    attrs=primitive['attributes'];mat=gltf['materials'][primitive['material']]
    name=('front','back','side','side')[primitive['material']]
    color_info=mat['pbrMetallicRoughness']['baseColorTexture'];(textures/(name+'.png')).write_bytes(image_bytes(color_info['index']))
    p=read(attrs['POSITION']);p*=np.array([-2.6,2.6,-2.6]);p+=np.array([0,1,.2])
    normal=read(attrs['NORMAL'])*np.array([-1,1,-1]);uv=uv_for(attrs,color_info)
    triangles=read(primitive['indices']).reshape(-1,3);center=p[triangles].mean(1)
    owner=np.where(center[:,0]>=0,1,2)
    owner[(np.abs(center[:,0])<.29)&(center[:,1]>.70)&(center[:,1]<1.72)]=0
    emissive_info=mat.get('emissiveTexture');glow=np.zeros(len(triangles),bool)
    uv_glow=np.zeros_like(uv)
    if emissive_info:
        raw=image_bytes(emissive_info['index']);(textures/(name+'_glow.png')).write_bytes(raw)
        uv_glow=uv_for(attrs,emissive_info)
        im=np.asarray(Image.open(io.BytesIO(raw)).convert('RGB'));height,width=im.shape[:2]
        # Conservative integral-image culling removes only additive triangles
        # whose padded UV rectangle is entirely black, without deleting colour.
        nonblack=(im.max(2)>0).astype(np.int32);integral=np.pad(nonblack,((1,0),(1,0))).cumsum(0).cumsum(1)
        uvtri=uv_glow[triangles];lo=np.floor(uvtri.min(1)*[width,height]).astype(int)-1;hi=np.ceil(uvtri.max(1)*[width,height]).astype(int)+2
        wraps=(lo<0).any(1)|(hi>[width,height]).any(1)
        lo=np.clip(lo,0,[width,height]);hi=np.clip(hi,0,[width,height])
        count=integral[hi[:,1],hi[:,0]]-integral[lo[:,1],hi[:,0]]-integral[hi[:,1],lo[:,0]]+integral[lo[:,1],lo[:,0]]
        glow=(count>0)|wraps
    for part in (0,1,2):
        selected=triangles[owner==part]
        if not len(selected):continue
        unique,indices=np.unique(selected,return_inverse=True);indices=indices.reshape(-1)
        remap=np.full(len(p),-1,int);remap[unique]=np.arange(len(unique))
        emitting=remap[triangles[(owner==part)&glow]].ravel()
        values=np.column_stack((p[unique],normal[unique],uv[unique],uv_glow[unique])).astype('>f4')
        meshes.append((part,name,values,indices.astype('>i4'),emitting.astype('>i4')))
        source_triangles+=len(selected);glow_triangles+=len(emitting)//3
def integer(value):out.extend(struct.pack('>i',value))
def text(value):v=value.encode('utf8');out.extend(struct.pack('>H',len(v)));out.extend(v)
integer(0x50574731);integer(len(meshes))
for part,name,values,indices,emitting in meshes:
    integer(part);text('blacklight:textures/wings_precision/'+name+'.png');text('blacklight:textures/wings_precision/'+name+'_glow.png' if len(emitting) else '')
    integer(len(values));integer(len(indices));integer(len(emitting));out.extend(values.tobytes());out.extend(indices.tobytes());out.extend(emitting.tobytes())
path=args.resources/'assets/blacklight/wing_models/precision_rigid.bin.gz';path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(out,compresslevel=9,mtime=0))
assert source_triangles==157041
report={'source':SOURCE.name,'sha256':hashlib.sha256(data).hexdigest(),'format':'PWG1','groups':len(meshes),
        'color_triangles':source_triangles,'glow_triangles':glow_triangles,'uncompressed_bytes':len(out),'packed_bytes':path.stat().st_size,
        'binding':'Fixed central attachment plus two rigid wings. All triangles keep their original shape.',
        'folding':'Mirror root rotations, with uniform 0.58 retraction on the ground; no per-vertex cross-bone blend.',
        'texture_culling':'Only wholly black padded UV rectangles are excluded from additive emission. All colour triangles retained.'}
args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n','utf8');print(json.dumps(report,indent=2))
