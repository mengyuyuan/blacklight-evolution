"""Render real production Java model vertices, UVs and authored animation samples.

Requires an already built local development checkout and Pillow/numpy; never runs
Gradle, downloads dependencies or imports a second hand-maintained soldier mesh.
This is a CPU asset preview, not a Minecraft/OpenGL playtest.
"""
from pathlib import Path
import argparse,hashlib,json,math,os,subprocess,sys,zipfile
import numpy as np
from PIL import Image,ImageDraw,ImageFont

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def font(size):
    for p in ['C:/Windows/Fonts/msyh.ttc','C:/Windows/Fonts/arial.ttf']:
        if Path(p).exists():return ImageFont.truetype(p,size)
    return ImageFont.load_default()
def classpath(repo):
    neo=(repo/'build/moddev/gameTestServerLegacyClasspath.txt').exists()
    file=repo/('build/moddev/gameTestServerLegacyClasspath.txt' if neo else 'build/classpath/runGameTestServer_minecraftClasspath.txt')
    if not file.exists():raise RuntimeError('Build the development checkout once before capturing production assets: '+str(file))
    cp=file.read_text(encoding='utf8').splitlines()
    if neo:cp += [str(p) for p in (repo/'build/moddev/artifacts').glob('*.jar') if 'sources' not in p.name]
    cp=[str(repo/'build/classes/java/main'),str(repo/'src/main/resources')]+cp
    return neo,cp
def capture(repo,build):
    neo,cp=classpath(repo);classes=build/'classes';classes.mkdir(parents=True,exist_ok=True)
    cp=[str(classes)]+cp
    tools=Path('F:/MC建筑/.blackwatch-tools/gradle/jdks')
    home=tools/('eclipse_adoptium-21-amd64-windows.2' if neo else 'eclipse_adoptium-17-amd64-windows.2')/'bin'
    java=home/'java.exe';javac=home/'javac.exe'
    if not java.exists():
        from shutil import which
        java=Path(which('java') or 'java');javac=Path(which('javac') or 'javac')
    sources=[repo/'tools/ReferenceMeshCapture.java']
    for name in ['client/model/BlackwatchReferenceProfile','client/model/BlackwatchRigPose','client/model/BlackwatchModel','firearms/FirearmViewState','firearms/FirearmSupportPose','firearms/FirearmViewEvents','firearms/FirearmHandPose','firearms/FirearmHandTransforms']:
        sources.append(repo/'src/main/java/com/codex/blacklight'/f'{name}.java')
    args=['-encoding','UTF-8','-proc:none','-cp',os.pathsep.join(cp),'-d',str(classes)]+[str(s) for s in sources]
    argfile=build/'capture-javac.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='gbk' if os.name=='nt' and not neo else 'utf8')
    run=subprocess.run([str(javac),'@'+str(argfile)],capture_output=True)
    (build/'api-compile.log').write_bytes(run.stdout+run.stderr)
    if run.returncode:raise RuntimeError('Production API compilation failed; see '+str(build/'api-compile.log'))
    mesh=build/'actual-production-meshes.json'
    # Direct Unicode argv avoids Windows native-codepage decoding of java @argfiles.
    run=subprocess.run([str(java),'-Djava.awt.headless=true','-cp',os.pathsep.join(cp),'ReferenceMeshCapture',str(mesh)],capture_output=True,cwd=repo)
    (build/'capture.log').write_bytes(run.stdout+run.stderr)
    if run.returncode:raise RuntimeError('Actual production capture failed; see '+str(build/'capture.log'))
    return json.loads(mesh.read_text(encoding='utf8')),sources,classes

def triangle(canvas,depth,points,uv,texture,shade,perspective=False):
    height,width=depth.shape
    x0=max(0,int(np.floor(points[:,0].min())));x1=min(width-1,int(np.ceil(points[:,0].max())))
    y0=max(0,int(np.floor(points[:,1].min())));y1=min(height-1,int(np.ceil(points[:,1].max())))
    if x1<x0 or y1<y0:return
    a,b,c=points;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
    if abs(den)<1e-7:return
    yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
    u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
    v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w=1-u-v
    z=u*a[2]+v*b[2]+w*c[2];mask=(u>=-1e-6)&(v>=-1e-6)&(w>=-1e-6)&(z>depth[y0:y1+1,x0:x1+1])
    if not mask.any():return
    if perspective:
        texu=(u*uv[0,0]*a[2]+v*uv[1,0]*b[2]+w*uv[2,0]*c[2])/z;texv=(u*uv[0,1]*a[2]+v*uv[1,1]*b[2]+w*uv[2,1]*c[2])/z
    else:texu=u*uv[0,0]+v*uv[1,0]+w*uv[2,0];texv=u*uv[0,1]+v*uv[1,1]+w*uv[2,1]
    tx=np.clip((texu*texture.shape[1]).astype(int),0,texture.shape[1]-1);ty=np.clip((texv*texture.shape[0]).astype(int),0,texture.shape[0]-1)
    colors=texture[ty,tx];mask &= colors[:,:,3]>16
    pixels=canvas[y0:y1+1,x0:x1+1];pixels[mask,:3]=np.clip(colors[mask,:3]*shade,0,255).astype('uint8');pixels[mask,3]=255
    depth[y0:y1+1,x0:x1+1][mask]=z[mask]

def render(mesh,atlas,width=340,height=440):
    canvas=np.full((height,width,4),[236,240,237,255],dtype='uint8');depth=np.full((height,width),-np.inf)
    texture=np.asarray(Image.open(atlas).convert('RGBA'));vertices=np.array(mesh['vertices']);scale=10
    # Oblique front view, with a single camera and ground baseline across every pose.
    transform=np.array([[.866,0,-.5],[-.09,.982,-.156],[-.491,-.18,-.85]])
    projected=vertices[:,:3]@transform.T;projected[:,0]=projected[:,0]*scale+width*.51;projected[:,1]=projected[:,1]*scale+105
    light=np.array([-.4,-.7,-.5]);light/=np.linalg.norm(light)
    for at in range(0,len(vertices),4):
        normal=vertices[at,5:8];shade=.7+.3*max(0,float(normal@light))
        for indices in [(at,at+1,at+2),(at,at+2,at+3)]:triangle(canvas,depth,projected[list(indices)],vertices[list(indices),3:5],texture,shade)
    im=Image.fromarray(canvas,'RGBA');draw=ImageDraw.Draw(im)
    labels={'idle':'待机 / 双手握枪','aim':'瞄准 / 双握点','shot_peak':'射击峰值 / 原始曲线','crouch':'蹲姿 / 上身与抬头补偿','reload_release':'换弹 25% / 松开支撑手','reload_magazine':'换弹 50% / 弹匣操作','reload_regrip':'换弹 85% / 返回护木','run':'奔跑 / 原始步态','left_main':'左惯用手 / 镜像握点','uniform':'脱下护具 / 保留普通人脸','officer':'军官 / 卡宾枪','commander':'指挥官 / 原角色护具'}
    label=('穿戴装备 / 实际兵种层' if mesh['gear'] else '脱下护具 / 普通外观') if mesh['name'].startswith('catalog-') else labels.get(mesh['name'],mesh['name'])
    draw.text((16,height-64),label,fill='#172b22',font=font(18))
    draw.text((16,height-35),f"{mesh['weapon'] if mesh['gear'] else 'unarmed'} · {len(vertices)//24} cubes",fill='#52685c',font=font(13))
    return im

def player_texture(repo,slim,build):
    _,cp=classpath(repo);reference=f'assets/minecraft/textures/entity/player/{"slim" if slim else "wide"}/steve.png'
    for path in cp:
        if not any(k in Path(path).name for k in ['resources','client-extra']):continue
        if not Path(path).is_file():continue
        with zipfile.ZipFile(path) as jar:
            if reference in jar.namelist():
                dest=build/f'steve-{"slim" if slim else "wide"}.png';dest.write_bytes(jar.read(reference));return np.asarray(Image.open(dest).convert('RGBA'))
    raise RuntimeError('The actual default player skin is missing from the local Minecraft resource classpath')

def clip_near(vertices,near=-.8):
    result=[]
    for index,p in enumerate(vertices):
        previous=vertices[index-1];inside=p[2]<near;was_inside=previous[2]<near
        if inside!=was_inside:
            t=(near-previous[2])/(p[2]-previous[2]);result.append(previous+(p-previous)*t)
        if inside:result.append(p)
    return result

def render_firstperson(mesh,repo,build,width=680,height=430):
    viewport=height-58;canvas=np.full((height,width,4),[225,235,236,255],dtype='uint8');depth=np.full((height,width),-np.inf)
    # Sky and ground are neutral framing; geometry/UVs come from the actual meshes above.
    canvas[viewport//2:viewport,:,:3]=[191,211,190]
    textures=[np.asarray(Image.open(repo/'src/main/resources/assets/blacklight/textures/item/firearms_palette.png').convert('RGBA')),player_texture(repo,mesh['gear'],build)]
    vertices=np.array(mesh['vertices']);focal=viewport/(2*math.tan(math.radians(70)/2));light=np.array([-.4,.7,.5]);light/=np.linalg.norm(light)
    for at in range(0,len(vertices),4):
        polygon=clip_near(vertices[at:at+4]);
        if len(polygon)<3:continue
        p=np.array(polygon);projected=np.column_stack((width/2+p[:,0]/(-p[:,2])*focal,viewport/2-p[:,1]/(-p[:,2])*focal,1/(-p[:,2])))
        shade=.75+.25*max(0,float(vertices[at,5:8]@light));texture=textures[int(vertices[at,8])]
        for i in range(1,len(polygon)-1):
            indices=[0,i,i+1];triangle(canvas,depth,projected[indices],p[indices,3:5],texture,shade,True)
    canvas[viewport:,:,:]=[236,240,237,255];im=Image.fromarray(canvas,'RGBA');d=ImageDraw.Draw(im)
    labels={'fp_right_rifle':'右主手步枪 / 双手','fp_left_rifle':'左主手步枪 / 镜像双手','fp_shot_peak':'射击峰值 / 两手与枪共同后坐','fp_reload':'换弹中段 / 支撑手离开护木','fp_slim':'纤细玩家模型 / 真实腕点校准','fp_launcher':'火箭筒 / 独立前握把位置','fp_dual':'双枪 / 一枪一手，无第三手','fp_invisible':'隐身玩家 / 仅显示持物'}
    d.text((16,viewport+7),labels[mesh['name']],font=font(19),fill='#172b22');d.text((16,viewport+32),'实际物品 JSON + 原版玩家手臂 + 生产变换；70° CPU 预览，须进游戏复核。',font=font(13),fill='#52685c')
    return im

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument('--output',type=Path)
    args=parser.parse_args();repo=args.repo.resolve();output=(args.output or repo/'docs/制作资料/参考模型/生产预览-military5').resolve();output.mkdir(parents=True,exist_ok=True)
    build=repo/'build/reference-model-preview';build.mkdir(parents=True,exist_ok=True)
    meshes,sources,classes=capture(repo,build);cards={}
    for faction in ['blackwatch','military']:
        selected=[m for m in meshes if m['faction']==faction];sheet=Image.new('RGB',(340*4,440*3+110),'#e4ebe5');d=ImageDraw.Draw(sheet)
        d.text((22,16),('黑色守望' if faction=='blackwatch' else '军方')+' · 实际生产骨架与贴图',fill='#172b22',font=font(29))
        d.text((22,61),'1145homo 参考骨骼 / 衣料 / 7 动画；换弹分相为本模组新增。CPU 资产预览，非游戏截图。',fill='#52685c',font=font(17))
        for index,mesh in enumerate(selected):
            image=render(mesh,repo/f'src/main/resources/assets/blacklight/textures/entity/{faction}_soldier_layered.png');image.save(output/f'{faction}-{mesh["name"]}.png');cards[faction+'-'+mesh['name']]=image
            sheet.paste(image,(index%4*340,index//4*440+110))
        sheet.save(output/f'{faction}-reference-poses.png')
    selected=[m for m in meshes if m['faction']=='firstperson'];sheet=Image.new('RGB',(680*2,430*4+110),'#e4ebe5');d=ImageDraw.Draw(sheet)
    d.text((22,16),'第一人称 · 实际枪械与玩家手臂',fill='#172b22',font=font(29));d.text((22,61),'普通 / 纤细 / 左右手 / 射击 / 换弹 / 双枪 / 隐身。默认 Steve 皮肤仅用于预览。',fill='#52685c',font=font(17))
    for index,mesh in enumerate(selected):
        image=render_firstperson(mesh,repo,build);image.save(output/f'{mesh["name"]}.png');sheet.paste(image,(index%2*680,index//2*430+110))
    sheet.save(output/'firstperson-reference-poses.png')
    catalogue={};kinds={'soldier':'普通步兵','heavy':'重装兵','team_leader':'队长','officer':'军官','commander':'指挥官'}
    for faction in ['military','blackwatch']:
        for suffix,label in kinds.items():
            entity=f'{faction}_{suffix}';card=Image.new('RGB',(680,525),'#e4ebe5');d=ImageDraw.Draw(card)
            d.text((18,12),('军方' if faction=='military' else '黑色守望')+' · '+label,fill='#172b22',font=font(25))
            d.text((18,48),'真实生产模型 / 当前图集 / 穿戴与脱装；CPU 预览，非游戏截图。',fill='#52685c',font=font(15))
            record={}
            for index,outfit in enumerate(['equipped','uniform']):
                mesh=next(m for m in meshes if m['name']==f'catalog-{entity}-{outfit}')
                image=render(mesh,repo/f'src/main/resources/assets/blacklight/textures/entity/{faction}_soldier_layered.png');name=f'catalog-{entity}-{outfit}.png'
                image.save(output/name);card.paste(image,(340*index,85));record[outfit]=name
                if outfit=='equipped':record.update(heavy=mesh['heavy'],registered_default_rank=mesh['defaultRank'],role=mesh['role'],weapon=mesh['weapon'],equipped_cubes=len(mesh['vertices'])//24)
            card_name=f'catalog-{entity}-card.png';card.save(output/card_name);record['card']=card_name;catalogue['blacklight:'+entity]=record
    paths=sources+[repo/'src/main/java/com/codex/blacklight/military/MilitaryRole.java',repo/'src/main/java/com/codex/blacklight/military/MilitaryRank.java',repo/'tools/preview_blackwatch_reference.py',repo/'tools/export_blackwatch_reference.py',repo/'src/main/resources/assets/blacklight/military/reference-rig.json']
    paths += list((repo/'src/main/resources/assets/blacklight/textures/entity').glob('*_soldier_layered.png'))
    paths += [repo/'src/main/resources/assets/blacklight/textures/item/firearms_palette.png']
    paths += [repo/f'src/main/resources/assets/blacklight/models/item/{n}.json' for n in ['military_rifle','military_carbine','military_machine_gun','grenade_launcher','rocket_launcher','missile_launcher','thermobaric_launcher','infection_launcher']]
    profile=json.loads((repo/'src/main/resources/assets/blacklight/military/reference-rig.json').read_text(encoding='utf8'))
    manifest={'kind':'actual_production_ModelPart_render_vertices_CPU_preview','not_game_screenshot':True,'source_sha256':profile['source_sha256'],'poses':len(meshes),'npc_poses':24,'firstperson_poses':8,'catalog_poses':20,'catalog':catalogue,
        'firstperson_method':'Real item JSON geometry, Minecraft ItemTransform, real PlayerModel arm/sleeve render vertices, shared production hand grip/FK and feedback transforms; CPU perspective at 70 degrees, no OpenGL gameplay claim.',
        'sources':{str(p.relative_to(repo)).replace('\\','/'):sha(p) for p in paths},'compiled_model_sha256':sha(classes/'com/codex/blacklight/client/model/BlackwatchModel.class'),
        'capture_sha256':sha(build/'actual-production-meshes.json'),'images':{p.name:sha(p) for p in output.glob('*.png')}}
    (output/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'output':str(output),'poses':len(meshes),'sha256':profile['source_sha256']},ensure_ascii=True))

if __name__=='__main__':main()
