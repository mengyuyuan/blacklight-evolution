"""Actual ModelPart CPU preview. Does not start Minecraft, Gradle or a GL context."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess
import numpy as np
from PIL import Image,ImageDraw
from preview_blackwatch_reference import classpath,triangle,font

def render_scene(scene,repo):
    w,h=800,620;canvas=np.full((h,w,4),[232,237,233,255],dtype='uint8');depth=np.full((h,w),-np.inf)
    camera=np.array([[.866,0,-.5],[.18,-.94,.312],[.47,.34,.81]]);light=np.array([-.4,.8,.5]);light/=np.linalg.norm(light)
    textures=[np.asarray(Image.open(repo/f'src/main/resources/assets/blacklight/textures/entity/military_vehicles_{scene["faction"]}.png').convert('RGBA')),np.asarray(Image.open(repo/f'src/main/resources/assets/blacklight/textures/entity/{scene["faction"]}_soldier_layered.png').convert('RGBA'))]
    vertices=np.array(scene['vertices']);points=vertices[:,:3]@camera.T;points[:,0]=points[:,0]*67+w*.52;points[:,1]=points[:,1]*67+h*.69
    for at in range(0,len(vertices),4):
        shade=.7+.3*max(0,float(vertices[at,5:8]@light));texture=textures[int(vertices[at,8])]
        for idx in ((at,at+1,at+2),(at,at+2,at+3)):triangle(canvas,depth,points[list(idx)],vertices[list(idx),3:5],texture,shade)
    image=Image.fromarray(canvas);draw=ImageDraw.Draw(image);faction='军方' if scene['faction']=='military' else '黑色守望';chassis={'truck':'卡车','apc':'装甲车','tank':'坦克'}[scene['chassis']]
    draw.text((28,25),f'{faction} · {chassis}'+(' / 实际座位与坐姿' if 'crew' in scene['name'] else '')+(' / 剖视' if 'interior' in scene['name'] else ''),font=font(26),fill='#21372d')
    draw.text((28,h-61),'游戏生产 ModelPart / 原创贴图 / 服务端同源挂点',font=font(18),fill='#405b4e')
    draw.text((28,h-32),'CPU 资产预览，非游戏截图；实际驾驶视角仍须进游戏复核。',font=font(16),fill='#52685c')
    return image

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',default='art/military-vehicles/previews/production');args=parser.parse_args()
    repo=Path(__file__).resolve().parents[1];out=(repo/args.output).resolve();out.mkdir(parents=True,exist_ok=True)
    build=repo/'build/military6-visual-capture';build.mkdir(exist_ok=True);classes=build/'classes';classes.mkdir(exist_ok=True)
    neo,cp=classpath(repo);cp=[str(classes)]+cp
    jdk=Path('F:/MC建筑/.blackwatch-tools/gradle/jdks')/('eclipse_adoptium-21-amd64-windows.2' if neo else 'eclipse_adoptium-17-amd64-windows.2')/'bin'
    sources=[repo/'tools/VehicleMeshCapture.java',repo/'src/main/java/com/codex/blacklight/client/model/MilitaryVehicleModel.java',repo/'src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java']
    def argfile(path,values,encoding):
        path.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in values),encoding=encoding);return '@'+str(path)
    javac=argfile(build/'javac.args',['-encoding','UTF-8','-proc:none','-cp',os.pathsep.join(cp),'-d',classes,*sources],'utf8' if neo else 'gbk')
    mesh=build/'actual-vehicle-meshes.json';java=argfile(build/'java.args',['-Djava.awt.headless=true','-cp',os.pathsep.join(cp),'VehicleMeshCapture',mesh],'gbk')
    for step,command in [('compile',[str(jdk/'javac.exe'),javac]),('capture',[str(jdk/'java.exe'),java])]:
        r=subprocess.run(command,capture_output=True,cwd=repo);(build/(step+'.log')).write_bytes(r.stdout+r.stderr)
        if r.returncode:raise RuntimeError(f'{step} failed: {build/(step+".log")}')
    scenes=json.loads(mesh.read_text('utf8'));cards=[]
    for scene in scenes:
        image=render_scene(scene,repo)
        image.save(out/(scene['name']+'.png'));cards.append(image)
    sheet=Image.new('RGB',(1600,620*4),'#e8ede9')
    for i,card in enumerate(cards):sheet.paste(card,(i%2*800,i//2*620))
    sheet.save(out/'vehicles-production-board.png')
    manifest={'kind':'actual-production-ModelPart-CPU-preview','not_game_screenshot':True,'scenes':len(scenes),'capture_sha256':hashlib.sha256(mesh.read_bytes()).hexdigest(),'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.glob('*.png')}}
    interior_mesh=build/'actual-truck-interior-meshes.json';diagnostics=out/'diagnostics';diagnostics.mkdir(exist_ok=True)
    for scene in json.loads(interior_mesh.read_text('utf8')):render_scene(scene,repo).save(diagnostics/(scene['name']+'.png'))
    manifest['diagnostic_capture_sha256']=hashlib.sha256(interior_mesh.read_bytes()).hexdigest()
    manifest['diagnostic_files']={str(p.relative_to(out)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in diagnostics.glob('*.png')}
    (out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n','utf8');print(json.dumps({'output':str(out),'scenes':len(scenes)},ensure_ascii=False))
if __name__=='__main__':main()
