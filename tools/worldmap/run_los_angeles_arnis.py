"""Checkpointed native Arnis -> bounded region archives for the entire municipality."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,shutil,struct,subprocess,time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--work',type=Path,required=True)
parser.add_argument('--repo',type=Path,required=True)
parser.add_argument('--arnis',type=Path,required=True)
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--vanilla-jar',type=Path,required=True)
parser.add_argument('--skeleton',type=Path,required=True,help='level.dat from a minimal Arnis-generated world')
parser.add_argument('--tool-classes',type=Path,required=True,help='Compiled ArnisArchivePacker class directory')
parser.add_argument('--workers',type=int,default=2)
parser.add_argument('--native-threads',type=int,default=4)
args=parser.parse_args();TASK=args.work.resolve();REPO=args.repo.resolve();EXE=args.arnis.resolve();JAVA=args.java_home.resolve()/'bin'/('java.exe' if os.name=='nt' else 'java');VANILLA=args.vanilla_jar.resolve()
DATA=REPO/'src/main/resources/data/blacklight/city/los_angeles_full'
def digest(path):return hashlib.file_digest(path.open('rb'),'sha256').hexdigest()
def save(path,obj):
    tmp=path.with_suffix('.tmp');tmp.write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf8');tmp.replace(path)
print('Waiting for complete original-object partitions',flush=True)
while not (TASK/'inputs-index.json').exists():time.sleep(5)
inputs=json.loads((TASK/'inputs-index.json').read_text());assert inputs['projection'].get('earthRadius')==6371000 and inputs['invalid'].get('missing_relation_member')==0,'Input projection/dependency closure not finalized';DATA.mkdir(parents=True,exist_ok=True)
shutil.copy2(TASK/'coverage.bin',DATA/'coverage.bin')
coverage={};raw=(TASK/'coverage.bin').read_bytes();assert raw[:4]==b'LACM';version,n=struct.unpack_from('>II',raw,4);assert version==1;pos=12
for _ in range(n):
    x,z=struct.unpack_from('>ii',raw,pos);mask=raw[pos+8:pos+136];pos+=136;coverage[(x,z)]=mask
assert pos==len(raw)
exe_sha=digest(EXE);packer_sha=digest(Path(__file__).parent/'ArnisArchivePacker.java');mask_sha=digest(TASK/'coverage.bin')
ENV=os.environ.copy();ENV.update({'RAYON_NUM_THREADS':str(max(1,args.native_threads)),'ARNIS_STREAM_TO_DISK':'1'})
audit=TASK/'palette-audits';audit.mkdir(exist_ok=True)
jobs=TASK/'jobs-v2';jobs.mkdir(exist_ok=True)
def run(batch):
    ident=batch['id'];directory=jobs/ident;directory.mkdir(exist_ok=True);status=directory/'status.json';old=json.loads(status.read_text()) if status.exists() else {}
    source=Path(batch['input']);assert digest(source)==batch['inputSHA256'],source
    minx,minz,maxx,maxz=batch['bounds'];rx0,rz0,rx1,rz1=minx//512,minz//512,maxx//512,maxz//512
    expected={(x,z):m for (x,z),m in coverage.items() if rx0<=x<=rx1 and rz0<=z<=rz1}
    assert expected,(ident,'No required regions')
    common={'batch':ident,'inputSHA256':batch['inputSHA256'],'arnisSHA256':exe_sha,'coverageSHA256':mask_sha,'packerSHA256':packer_sha}
    native_done=old.get('nativeComplete') and all(old.get(k)==common[k] for k in ('inputSHA256','arnisSHA256','coverageSHA256'))
    packed_done=native_done and old.get('packedComplete') and old.get('packerSHA256')==packer_sha and all((DATA/f'{x}_{z}.lar').is_file() for x,z in expected)
    if packed_done:return old
    world=directory/'Los Angeles Arnis';world.mkdir(exist_ok=True)
    if not (world/'arnis_one_world.json').exists():
        shutil.copy2(args.skeleton.resolve(),world/'level.dat')
        manifest={'version':3,'created_with':'arnis 3.3.0','created_at':int(time.time()),'origin_lat':inputs['projection']['originLat'],'origin_lon':inputs['projection']['originLon'],'scale':1.0,'ground_level':63,'terrain':True,'disable_height_limit':True,'aws_only_elevation':True,'height_multiplier':1.0,'elevation':inputs['elevation'],'next_area_id':1,'areas':[]}
        save(world/'arnis_one_world.json',manifest)
    start=time.monotonic()
    if not native_done:
        print('ARNIS START',ident,'objects',batch['objects'],flush=True)
        cmd=[str(EXE),'--file',str(source),'--bbox',','.join(map(str,batch['bbox'])),'--output-dir',str(directory),'--one-world','--world-name',world.name,'--mode','geo-terrain','--ground-level','63','--scale','1','--interior','--overture=false','--canopy-height=false','--no-3d','--signage','none','--map-item=false','--world-type','flat','--aws-only-elevation','--disable-height-limit']
        with (directory/'arnis.log').open('w',encoding='utf8') as log:
            code=subprocess.run(cmd,env=ENV,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0)).returncode
        if code:raise RuntimeError(f'Arnis failed for {ident}: exit {code}; see {directory}/arnis.log')
        manifest=json.loads((world/'arnis_one_world.json').read_text());area=manifest['areas'][-1]
        assert [area['min_x'],area['min_z'],area['max_x'],area['max_z']]==batch['bounds'],(ident,'Projection mismatch',area,batch)
        old={**common,'nativeComplete':True,'packedComplete':False,'nativeSeconds':round(time.monotonic()-start,2),'area':area};save(status,old)
        print('ARNIS DONE',ident,'seconds',old['nativeSeconds'],flush=True)
    cmd=[str(JAVA),'-Xmx768m','-Dfile.encoding=UTF-8','-Djava.util.concurrent.ForkJoinPool.common.parallelism=3','-cp',str(args.tool_classes.resolve()),'ArnisArchivePacker',str(world),str(TASK/'coverage.bin'),str(DATA),str(VANILLA),str(rx0),str(rz0),str(rx1),str(rz1)]
    with (directory/'pack.log').open('w',encoding='utf8') as log:
        code=subprocess.run(cmd,env=ENV,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0)).returncode
    if code:raise RuntimeError(f'Packing failed for {ident}: exit {code}; see {directory}/pack.log')
    total=0;archives=[]
    for (x,z),mask in expected.items():
        path=DATA/f'{x}_{z}.lar';data=path.read_bytes();assert struct.unpack_from('>II',data)==(0x4c415232,1024),path
        count=0
        for slot in range(1024):
            offset,length=struct.unpack_from('>II',data,8+slot*8);required=bool(mask[slot//8]&(1<<(slot%8)))
            assert bool(offset)==required and bool(length)==required,(path,slot,'Coverage differs')
            if required:assert offset>=8200 and offset+length<=len(data),(path,slot);count+=1
        total+=count;archives.append({'x':x,'z':z,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'chunks':count})
    palette=DATA/f'palette-{rx0}_{rz0}.txt';shutil.move(str(palette),str(audit/(ident+'.txt')))
    result={**old,**common,'nativeComplete':True,'packedComplete':True,'regions':archives,'chunks':total,'totalSeconds':round(time.monotonic()-start,2)};save(status,result)
    print('BATCH COMPLETE',ident,'chunks',total,'seconds',result['totalSeconds'],flush=True);return result

pending={};completed=[];iterator=iter(inputs['batches']);failure=None
with concurrent.futures.ThreadPoolExecutor(max_workers=max(1,args.workers)) as pool:
    for _ in range(max(1,args.workers)):
        batch=next(iterator,None)
        if batch is not None:pending[pool.submit(run,batch)]=batch['id']
    while pending:
        done,_=concurrent.futures.wait(pending,return_when=concurrent.futures.FIRST_COMPLETED)
        for future in done:
            ident=pending.pop(future)
            try:result=future.result()
            except Exception as error:
                failure=str(error);save(TASK/'failure.json',{'batch':ident,'error':failure});print('FAILED',ident,failure,flush=True)
                for other in pending:other.cancel()
                break
            completed.append(result);print('PROGRESS',len(completed),'/',len(inputs['batches']),flush=True)
            batch=next(iterator,None)
            if batch is not None:pending[pool.submit(run,batch)]=batch['id']
        if failure:break
if failure:raise RuntimeError(failure)
entries=[entry for batch in completed for entry in batch['regions']];assert len(entries)==len(coverage) and len({(r['x'],r['z']) for r in entries})==len(coverage)
expected=sum(sum(b.bit_count() for b in mask) for mask in coverage.values());assert sum(r['chunks'] for r in entries)==expected
old_index=REPO/'src/main/resources/data/blacklight/worldmap/los_angeles/index.json';atlas=json.loads(old_index.read_text())
states=set()
for p in audit.glob('*.txt'):states.update(p.read_text(encoding='utf8').splitlines())
descriptor={'schema':2,'complete':True,'generator':'Arnis 3.3.0','arnisSHA256':exe_sha,'projection':inputs['projection'],'elevation':inputs['elevation'],'worldBounds':atlas['bounds'],'coverageSHA256':mask_sha,'nativeChunks':expected,'batchCount':len(completed),'blockStates':len(states),'chunkEncoding':'LAC3','regions':sorted(entries,key=lambda r:(r['z'],r['x'])),'source':inputs['source'],'boundarySource':inputs['boundarySource'],'limitations':['Municipal Los Angeles relation 207359, not the county or metropolitan area.','Arnis-generated interiors and furnishings are procedural.','Terrain vertical scale is 0.8; building dimensions follow Arnis scale 1.0.','Below-dimension seabed is replaced by the dimension floor; entity and block-entity NBT is not imported.','Existing saved chunks are preserved.']}
save(DATA/'index.json',descriptor);atlas['fullCityTemplates']={'generator':'Arnis 3.3.0','indexSHA256':digest(DATA/'index.json'),'nativeChunks':expected,'batchCount':len(completed)};save(old_index,atlas)
(TASK/'block-states.txt').write_text('\n'.join(sorted(states))+'\n',encoding='utf8')
save(TASK/'complete.json',{'complete':True,'batches':len(completed),'regions':len(entries),'nativeChunks':expected,'bytes':sum(r['bytes'] for r in entries),'blockStates':len(states),'indexSHA256':digest(DATA/'index.json')})
print('WHOLE CITY COMPLETE',json.dumps(json.loads((TASK/'complete.json').read_text())),flush=True)
