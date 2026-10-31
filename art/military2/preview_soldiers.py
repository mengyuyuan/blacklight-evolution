"""CPU texture-sampled preview of actual BlackwatchModel.java geometry, not a game capture.

Reads declarative piece(...) calls from production Java (or --staged for draft QA),
reads the real faction PNGs, performs orthographic triangle depth buffering, and
embeds the identical geometry/atlases into a dependency-free offline HTML viewer.
"""
from pathlib import Path
import argparse
import base64
import hashlib
import io
import json
import math
import re
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
STAGE=Path(__file__).resolve().parent
OUT=ROOT / "docs/previews"
PIVOTS={"head":[0,0,0],"body":[0,0,0],"right_arm":[-5,2,0],"left_arm":[5,2,0],
        "right_leg":[-1.9,12,0],"left_leg":[1.9,12,0]}
KINDS=["weapon_rifle","weapon_machine","weapon_grenade","weapon_launcher"]
VARIANTS=[("uniform-military","军方 · 制服基础层","military",False,False),
          ("uniform-blackwatch","Blackwatch · 制服基础层","blackwatch",False,False),
          ("military-regular","军方 · 普通步枪兵","military",False,True),
          ("military-heavy","军方 · 重装机枪兵","military",True,True),
          ("blackwatch-regular","Blackwatch · 普通步枪兵","blackwatch",False,True),
          ("blackwatch-heavy","Blackwatch · 重装机枪兵","blackwatch",True,True)]
PATTERN=re.compile(r'piece\(root,military,heavy,"([^"]+)","([^"]+)","([^"]+)",([^;]+)\);')
FONT=Path("C:/Windows/Fonts/msyh.ttc")


def read_geometry(source):
    result=[]
    for group,parent,name,numbers in PATTERN.findall(source):
        n=[float(v.rstrip("Ff")) for v in numbers.split(",")]
        if len(n)!=8:raise ValueError((name,n))
        result.append(dict(group=group,parent=parent,name=name,uv=n[:2],box=n[2:]))
    if len(result)<100:raise ValueError("The native source geometry reader did not find all pieces")
    return result


def selected(p,faction,heavy,equipped,weapon,rank=3,role="trooper"):
    g=p["group"]
    if "Role_" in g:
        prefix,identity=g.split("Role_",1)
        return equipped and identity==role and prefix==("military" if faction=="military" else "bw")
    if g=="military" and faction!="military":return False
    if g=="bw" and faction!="blackwatch":return False
    if g=="heavy" and not heavy:return False
    if g=="regular" and heavy:return False
    if g=="bwHeavy" and (not heavy or faction!="blackwatch"):return False
    if p["name"].startswith("gear_") and not equipped:return False
    if role in ("officer","commander"):
        if p["parent"]=="head" and p["name"].startswith("gear_"):
            if faction=="military" or p["name"].startswith("gear_hood_"):return False
        if p["name"] in ("gear_patrol_pack","gear_radio","gear_aerial","gear_supply_pack","gear_supply_lid"):return False
    if g.startswith("rank_"):return equipped and g==f"rank_{rank}"
    if g.startswith("weapon_"):return equipped and g==weapon
    return True


def rotation(x=0,y=0,z=0):
    cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    rx=np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    ry=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])
    rz=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])
    return rz@ry@rx


def arm_angles(pose,weapon):
    if weapon is None:return {}
    aiming=pose!="ready"
    launcher=weapon=="weapon_launcher"
    right=[-1.48 if aiming else -1.10,-.08 if launcher else -.38,0]
    left=[-1.42 if aiming else -1.16,.95 if launcher else .72,-.06]
    if pose=="reload":right[0]+=.22;left=[-1.1,.35,-.06]
    return {"right_arm":right,"left_arm":left}


def face_data(p):
    x,y,z,w,h,d=p["box"]
    u,v=p["uv"]
    pts=np.array([[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],
                  [x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]])
    return pts,[([0,1,2,3],(0,0,-1),[u+d,v+d,w,h]),
                ([5,4,7,6],(0,0,1),[u+2*d+w,v+d,w,h]),
                ([4,0,3,7],(-1,0,0),[u,v+d,d,h]),
                ([1,5,6,2],(1,0,0),[u+d+w,v+d,d,h]),
                ([4,5,1,0],(0,-1,0),[u+d,v,w,d]),
                ([3,2,6,7],(0,1,0),[u+d+w,v,w,d])]


def render(pieces,texture,faction,heavy,equipped,weapon,pose="ready",yaw=0,width=640,height=800,role="trooper",rank=3):
    rgba=np.array(texture)
    canvas=np.zeros((height,width,3),dtype=np.uint8)
    # Neutral studio, same geometry/texture sampling as the browser viewer.
    for y in range(height):canvas[y,:,:]=[237-y*7//height,240-y*7//height,236-y*5//height]
    zbuffer=np.full((height,width),np.inf,dtype=np.float32)
    camera=rotation(.095,math.radians(yaw),0)
    angles=arm_angles(pose,weapon if equipped else None)
    scale=min(width/32,height/41)
    active=[p for p in pieces if selected(p,faction,heavy,equipped,weapon,rank,role)]
    all_points=[]
    for p in active:
        pts,_=face_data(p)
        local=rotation(*angles.get(p["parent"],[0,0,0]))
        transformed=(pts@local.T+PIVOTS[p["parent"]])@camera.T
        all_points.extend(transformed.tolist())
    points=np.array(all_points)
    centerx=(points[:,0].min()+points[:,0].max())/2
    # Stable body-height framing allows base/full silhouettes to compare honestly.
    origin=np.array([width/2-centerx*scale,230])
    light=np.array([-.4,-.65,-.9]);light/=np.linalg.norm(light)
    texscale=texture.width/256
    for p in active:
        points,faces=face_data(p)
        local=rotation(*angles.get(p["parent"],[0,0,0]))
        transformed=(points@local.T+PIVOTS[p["parent"]])@camera.T
        for indices,normal,rect in faces:
            n=camera@local@np.array(normal)
            if n[2]>=-.00001:continue
            shade=.67+.33*max(0,float(n@light))
            vertices=transformed[indices]
            screen=vertices[:,:2]*scale+origin
            u,v,w,h=rect
            uv=np.array([[u,v],[u+w,v],[u+w,v+h],[u,v+h]])*texscale
            for tri in [(0,1,2),(0,2,3)]:
                a,b,c=screen[list(tri)]
                z=vertices[list(tri),2]
                tu=uv[list(tri)]
                x0=max(0,int(np.floor(min(a[0],b[0],c[0]))));x1=min(width,int(np.ceil(max(a[0],b[0],c[0])))+1)
                y0=max(0,int(np.floor(min(a[1],b[1],c[1]))));y1=min(height,int(np.ceil(max(a[1],b[1],c[1])))+1)
                if x1<=x0 or y1<=y0:continue
                area=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
                if abs(area)<1e-8:continue
                xx,yy=np.meshgrid(np.arange(x0,x1)+.5,np.arange(y0,y1)+.5)
                wa=((b[0]-xx)*(c[1]-yy)-(b[1]-yy)*(c[0]-xx))/area
                wb=((c[0]-xx)*(a[1]-yy)-(c[1]-yy)*(a[0]-xx))/area
                wc=1-wa-wb
                depth=wa*z[0]+wb*z[1]+wc*z[2]
                uvx=wa*tu[0,0]+wb*tu[1,0]+wc*tu[2,0]
                uvy=wa*tu[0,1]+wb*tu[1,1]+wc*tu[2,1]
                tx=np.clip(np.floor(uvx).astype(int),0,texture.width-1)
                ty=np.clip(np.floor(uvy).astype(int),0,texture.height-1)
                samples=rgba[ty,tx]
                mask=(wa>=-1e-5)&(wb>=-1e-5)&(wc>=-1e-5)&(depth<zbuffer[y0:y1,x0:x1])&(samples[:,:,3]>128)
                canvas[y0:y1,x0:x1][mask]=np.clip(samples[:,:,:3][mask]*shade,0,255).astype(np.uint8)
                zbuffer[y0:y1,x0:x1][mask]=depth[mask]
    return Image.fromarray(canvas)


def font(size):return ImageFont.truetype(str(FONT),size)


def contact_sheet(pieces,textures,variant):
    slug,title,faction,heavy,equipped=variant
    weapon=("weapon_machine" if heavy else "weapon_rifle") if equipped else None
    sheet=Image.new("RGB",(2560,930),(237,240,236));draw=ImageDraw.Draw(sheet)
    draw.text((34,18),title,fill=(34,47,48),font=font(35))
    draw.text((34,68),"原生 Java 几何 + 实际 PNG 纹理采样 / 原创 P2 风格适配 / 离线资产预览，非实机截图",fill=(87,101,100),font=font(22))
    for i,(label,yaw,pose) in enumerate([("正面 / FRONT",0,"ready"),("侧面 / SIDE",-90,"ready"),
                                         ("背面 / BACK",180,"ready"),("持枪 / AIM",-28,"aim")]):
        sheet.paste(render(pieces,textures[faction],faction,heavy,equipped,weapon,pose,yaw),(i*640,110))
        draw.text((i*640+24,877),label,fill=(62,79,77),font=font(24))
    target=OUT/f"military2-soldiers-{slug}.png"
    sheet.save(target)
    return target


HTML=r'''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Military.2 — 实际资产查看器</title>
<style>*{box-sizing:border-box}body{margin:0;background:#eaf0e9;color:#243533;font:16px system-ui,"Microsoft YaHei",sans-serif}main{max-width:1340px;margin:auto;padding:28px}h1{font-size:29px;letter-spacing:-1px;margin:5px 0 12px}.eyebrow{font-size:12px;letter-spacing:2px;color:#5c7069}.note{max-width:940px;line-height:1.7;color:#586e66}.layout{display:grid;grid-template-columns:1fr 330px;gap:28px;margin-top:22px}canvas{width:100%;max-height:790px;object-fit:contain;touch-action:none;border:1px solid #d1dbd1;cursor:grab}aside{padding-top:10px}label{display:block;font-size:13px;color:#5a7167;margin:18px 0 7px}select,input{width:100%;padding:10px;border:1px solid #b7c8bc;background:#f8fbf5;color:#263e36;font-size:15px}button{border:1px solid #b7c8bc;background:#f8fbf5;color:#263e36;padding:9px 14px;cursor:pointer;margin:4px 5px 0 0}small{display:block;line-height:1.65;color:#5b7168;margin-top:18px}.gallery{margin:35px 0;display:grid;gap:20px}.gallery img{width:100%;display:block;border:1px solid #cfdacf}.gallery a{color:#375547}.texture{image-rendering:pixelated;width:100%;margin-top:10px;background:repeating-conic-gradient(#c9d3c9 0 25%,#eef2ea 0 50%) 50%/16px 16px}.controls{display:flex;gap:8px;flex-wrap:wrap}code{font-size:11px;word-break:break-all}@media(max-width:850px){.layout{grid-template-columns:1fr}main{padding:18px}aside{display:grid;grid-template-columns:1fr 1fr;gap:10px}.note{font-size:14px}}</style>
<main><div class="eyebrow">BLACKLIGHT / MILITARY.2 / SOURCE ASSET INSPECTOR</div><h1>分层兵装 · 原生资产查看器</h1><p class="note">使用生产 BlackwatchModel.java 的 __PIECES__ 块声明几何及独立军方 / Blackwatch PNG 纹理。脱下护具后保留完整制服与人脸；枪型选择对应真实持有物品的四类视觉。此处是离线正交光照预览，不是 Minecraft 客户端截图，也不声称原作严格复刻。</p>
<div class="layout"><canvas id="view" width="760" height="820" aria-label="可旋转实际模型"></canvas><aside>
<div><label>外观组合</label><select id="variant"></select></div><div><label>实际持枪类别的视觉映射</label><select id="weapon"><option value="weapon_rifle">步枪 / 卡宾枪</option><option value="weapon_machine">机枪</option><option value="weapon_grenade">榴弹发射器</option><option value="weapon_launcher">肩扛发射器</option><option value="none">空手 — 隐藏枪模</option></select></div>
<div><label><input id="equipped" type="checkbox" checked style="width:auto"> 显示整套护具 / 取消后看完整制服</label><label>姿态</label><select id="pose"><option value="ready">低位持枪</option><option value="aim">瞄准 / 射击基姿</option><option value="reload">简化换弹</option></select></div><div><label>观察角度 <span id="angle">−28°</span></label><input id="yaw" type="range" min="-180" max="180" value="-28"><div class="controls"><button data-yaw="0">正面</button><button data-yaw="-90">侧面</button><button data-yaw="180">背面</button></div></div>
<small>可拖动模型旋转。装备按实体 equipped 状态成组显隐，军衔只在全装时显示。护具随现有 Humanoid 骨架；此处没有独立肘关节 IK，肩扛与换弹仍是简化表现。</small><div><label>当前实际纹理（像素放大）</label><img class="texture" id="atlas"><small id="source"></small></div></aside></div>
<h2>正 · 侧 · 背 · 持枪</h2><div class="gallery" id="gallery"></div></main>
<script>const DATA=__DATA__;const $=id=>document.getElementById(id);const ctx=$('view').getContext('2d');const textures={};let ready=0;
for(const [key,url]of Object.entries(DATA.textures)){let image=new Image;image.onload=()=>{let c=document.createElement('canvas');c.width=c.height=image.width;c.getContext('2d').drawImage(image,0,0);textures[key]={image,pixels:c.getContext('2d').getImageData(0,0,c.width,c.height).data,size:c.width};if(++ready===2)draw()};image.src=url}
DATA.variants.forEach((v,i)=>$('variant').add(new Option(v[1],i)));$('variant').value=5;
$('source').textContent='Java SHA-256: '+DATA.sourceHash+' · UV 256 × 256 / PNG 512 × 512';
for(const v of DATA.variants){let a=document.createElement('a');a.href='military2-soldiers-'+v[0]+'.png';a.textContent=v[1]+' / 打开原始 PNG';let im=document.createElement('img');im.loading='lazy';im.src=DATA.sheets[v[0]];im.alt=v[1]+'真实纹理三视图';let div=document.createElement('div');div.append(a,im);$('gallery').append(div)}
function rot(p,a){let[x,y,z]=p,[rx,ry,rz]=a,c=Math.cos,s=Math.sin;[y,z]=[y*c(rx)-z*s(rx),y*s(rx)+z*c(rx)];[x,z]=[x*c(ry)+z*s(ry),-x*s(ry)+z*c(ry)];return[x*c(rz)-y*s(rz),x*s(rz)+y*c(rz),z]}
function draw(){if(ready<2)return;let v=DATA.variants[+$('variant').value],faction=v[2],heavy=v[3],equipped=v[4]&&$('equipped').checked,role=v[5]||'trooper',rank=v[6]??3,weapon=$('weapon').value,pose=$('pose').value,yaw=+$('yaw').value*Math.PI/180;$('angle').textContent=$('yaw').value+'°';$('atlas').src=DATA.textures[faction];const tex=textures[faction],W=760,H=820,img=ctx.createImageData(W,H),pix=img.data,zbuf=new Float32Array(W*H);zbuf.fill(Infinity);for(let y=0;y<H;y++)for(let x=0;x<W;x++){let i=(y*W+x)*4;pix[i]=237-(y*7/H|0);pix[i+1]=240-(y*7/H|0);pix[i+2]=236-(y*5/H|0);pix[i+3]=255}
let selected=DATA.pieces.filter(p=>{let g=p.group;if(g.includes('Role_')){let[prefix,identity]=g.split('Role_');return equipped&&identity===role&&prefix===(faction==='military'?'military':'bw')}if(g==='military'&&faction!=='military'||g==='bw'&&faction!=='blackwatch'||g==='heavy'&&!heavy||g==='regular'&&heavy||g==='bwHeavy'&&(!heavy||faction!=='blackwatch'))return false;if(p.name.startsWith('gear_')&&!equipped)return false;if(role==='officer'||role==='commander'){if(p.parent==='head'&&p.name.startsWith('gear_')&&(faction==='military'||p.name.startsWith('gear_hood_')))return false;if(['gear_patrol_pack','gear_radio','gear_aerial','gear_supply_pack','gear_supply_lid'].includes(p.name))return false}if(g.startsWith('rank_'))return equipped&&g==='rank_'+rank;if(g.startsWith('weapon_'))return equipped&&g===weapon;return true});
let angles={};if(equipped&&weapon!=='none'){let aim=pose!=='ready',launch=weapon==='weapon_launcher';angles.right_arm=[aim?-1.48:-1.10,launch?-.08:-.38,0];angles.left_arm=[aim?-1.42:-1.16,launch?.95:.72,-.06];if(pose==='reload'){angles.right_arm[0]+=.22;angles.left_arm=[-1.1,.35,-.06]}}
let prepared=[],minx=Infinity,maxx=-Infinity;
for(let p of selected){let[x,y,z,w,h,d]=p.box,[u,vv]=p.uv,points=[[x,y,z],[x+w,y,z],[x+w,y+h,z],[x,y+h,z],[x,y,z+d],[x+w,y,z+d],[x+w,y+h,z+d],[x,y+h,z+d]],a=angles[p.parent]||[0,0,0],pivot=DATA.pivots[p.parent];points=points.map(q=>rot(rot(q,a).map((n,i)=>n+pivot[i]),[.095,yaw,0]));for(let q of points){minx=Math.min(minx,q[0]);maxx=Math.max(maxx,q[0])}let faces=[[[0,1,2,3],[0,0,-1],[u+d,vv+d,w,h]],[[5,4,7,6],[0,0,1],[u+2*d+w,vv+d,w,h]],[[4,0,3,7],[-1,0,0],[u,vv+d,d,h]],[[1,5,6,2],[1,0,0],[u+d+w,vv+d,d,h]],[[4,5,1,0],[0,-1,0],[u+d,vv,w,d]],[[3,2,6,7],[0,1,0],[u+d+w,vv,w,d]]];prepared.push({points,a,faces})}
const scale=20,origin=[W/2-(minx+maxx)*scale/2,230],light=[-.339,-.551,-.763];
for(const {points,a,faces}of prepared)for(const [ids,n,uvrect]of faces){let normal=rot(rot(n,a),[.095,yaw,0]);if(normal[2]>=-.00001)continue;let shade=.67+.33*Math.max(0,normal.reduce((s,n,i)=>s+n*light[i],0)),screen=ids.map(i=>[points[i][0]*scale+origin[0],points[i][1]*scale+origin[1],points[i][2]]),[u,v,w,h]=uvrect,uv=[[u,v],[u+w,v],[u+w,v+h],[u,v+h]].map(q=>q.map(n=>n*tex.size/256));for(const tr of [[0,1,2],[0,2,3]]){let[a,b,c]=tr.map(i=>screen[i]),[ta,tb,tc]=tr.map(i=>uv[i]),area=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);if(Math.abs(area)<1e-8)continue;let x0=Math.max(0,Math.floor(Math.min(a[0],b[0],c[0]))),x1=Math.min(W,Math.ceil(Math.max(a[0],b[0],c[0]))),y0=Math.max(0,Math.floor(Math.min(a[1],b[1],c[1]))),y1=Math.min(H,Math.ceil(Math.max(a[1],b[1],c[1])));for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){let xx=x+.5,yy=y+.5,wa=((b[0]-xx)*(c[1]-yy)-(b[1]-yy)*(c[0]-xx))/area,wb=((c[0]-xx)*(a[1]-yy)-(c[1]-yy)*(a[0]-xx))/area,wc=1-wa-wb;if(Math.min(wa,wb,wc)<-.00001)continue;let z=wa*a[2]+wb*b[2]+wc*c[2],i=y*W+x;if(z>=zbuf[i])continue;let tx=Math.max(0,Math.min(tex.size-1,Math.floor(wa*ta[0]+wb*tb[0]+wc*tc[0]))),ty=Math.max(0,Math.min(tex.size-1,Math.floor(wa*ta[1]+wb*tb[1]+wc*tc[1]))),t=(ty*tex.size+tx)*4;if(tex.pixels[t+3]<128)continue;zbuf[i]=z;for(let k=0;k<3;k++)pix[i*4+k]=tex.pixels[t+k]*shade}}}ctx.putImageData(img,0,0)}
let timer;function schedule(){clearTimeout(timer);timer=setTimeout(draw,10)}for(let id of ['variant','weapon','pose','yaw','equipped'])$(id).addEventListener('input',schedule);document.querySelectorAll('[data-yaw]').forEach(b=>b.onclick=()=>{$('yaw').value=b.dataset.yaw;draw()});let start=null;$('view').onpointerdown=e=>{start=[e.clientX,+$('yaw').value];$('view').setPointerCapture(e.pointerId)};$('view').onpointermove=e=>{if(start){$('yaw').value=Math.max(-180,Math.min(180,start[1]+(e.clientX-start[0])*.4));schedule()}};$('view').onpointerup=()=>start=null;
</script></html>'''


def png_data(path):return "data:image/png;base64,"+base64.b64encode(path.read_bytes()).decode("ascii")


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--staged",action="store_true")
    parser.add_argument("--check-js",action="store_true");args=parser.parse_args()
    source_path=STAGE/"BlackwatchModel.java" if args.staged else ROOT/"src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java"
    textures_dir=STAGE if args.staged else ROOT/"src/main/resources/assets/blacklight/textures/entity"
    source=source_path.read_text(encoding="utf8");pieces=read_geometry(source)
    textures={f:Image.open(textures_dir/(f+"_soldier_layered.png")).convert("RGBA") for f in ["military","blackwatch"]}
    OUT.mkdir(parents=True,exist_ok=True)
    sheets={}
    for variant in VARIANTS:
        target=contact_sheet(pieces,textures,variant);sheets[variant[0]]=png_data(target)
    # Separate readable four-family weapon holding check using the same BW heavy geometry.
    weapon_sheet=Image.new("RGB",(2560,930),(237,240,236));draw=ImageDraw.Draw(weapon_sheet)
    draw.text((34,18),"实际物品 → 四类枪型 / 持枪轮廓检查",font=font(35),fill=(34,47,48))
    draw.text((34,68),"空手不显示任何枪模；武器模型与 item 的 FirearmItem 定义关联。此图是离线实际资产预览。",font=font(22),fill=(87,101,100))
    for i,(kind,label) in enumerate(zip(KINDS,["步枪 / RIFLE","机枪 / MACHINE GUN","榴弹 / GRENADE","肩扛 / LAUNCHER"])):
        weapon_sheet.paste(render(pieces,textures["blackwatch"],"blackwatch",True,True,kind,"aim",-65),(i*640,110))
        draw.text((i*640+24,877),label,font=font(24),fill=(62,79,77))
    weapon_sheet.save(OUT/"military2-soldiers-weapon-families.png")
    overview=Image.new("RGB",(2400,970),(237,240,236));draw=ImageDraw.Draw(overview)
    draw.text((34,18),"分层士兵 · 实际纹理与源码几何",font=font(40),fill=(34,47,48))
    draw.text((34,78),"完整制服 / 军方普通·重装 / Blackwatch 普通·重装 — 项目原创建模，非实机截图",font=font(25),fill=(87,101,100))
    for i,variant in enumerate([VARIANTS[0],*VARIANTS[2:]]):
        _,title,faction,heavy,equipped=variant
        image=render(pieces,textures[faction],faction,heavy,equipped,"weapon_machine" if heavy else "weapon_rifle","aim",-28,width=480,height=780)
        overview.paste(image,(i*480,145));draw.text((i*480+20,920),title,font=font(22),fill=(62,79,77))
    overview.save(OUT/"military2-soldiers-overview.png")
    data=dict(pieces=pieces,pivots=PIVOTS,variants=VARIANTS,sheets=sheets,
              sourceHash=hashlib.sha256(source_path.read_bytes()).hexdigest(),
              textures={f:png_data(textures_dir/(f+"_soldier_layered.png")) for f in textures})
    html=HTML.replace("__DATA__",json.dumps(data,ensure_ascii=False)).replace("__PIECES__",str(len(pieces)))
    (OUT/"military2-soldiers-viewer.html").write_text(html,encoding="utf8")
    if args.check_js:
        (STAGE/"viewer-check.js").write_text(re.search(r'<script>([\s\S]*)</script>',html).group(1),encoding="utf8")
    manifest=dict(source=str(source_path),source_sha256=data["sourceHash"],pieces=len(pieces),staged=args.staged,
                  textures={f:dict(path=str(textures_dir/(f+"_soldier_layered.png")),sha256=hashlib.sha256((textures_dir/(f+"_soldier_layered.png")).read_bytes()).hexdigest(),size=list(textures[f].size)) for f in textures})
    (OUT/"military2-soldiers-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf8")
    print(json.dumps(dict(pieces=len(pieces),pngs=8,viewer=str(OUT/"military2-soldiers-viewer.html"),staged=args.staged),ensure_ascii=False))


if __name__=="__main__":main()
