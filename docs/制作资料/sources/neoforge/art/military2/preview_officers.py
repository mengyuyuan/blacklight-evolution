"""Military.3 officer previews: production Java geometry + actual PNGs, never game captures."""
import argparse
import hashlib
import json
import re
from PIL import Image,ImageDraw
from preview_soldiers import ROOT,STAGE,OUT,PIVOTS,HTML,read_geometry,render,font,png_data

VARIANTS=[]
for faction,label in [("military","军方"),("blackwatch","Blackwatch")]:
    for role,title,rank in [("team_leader","基层队长",2),("officer","野战军官",3),("commander","基地指挥官",5)]:
        VARIANTS.append((faction+"-"+role,label+" · "+title,faction,False,True,role,rank))


def sheet(pieces,textures,variant):
    slug,title,faction,heavy,equipped,role,rank=variant
    result=Image.new("RGB",(2800,930),(237,240,236));draw=ImageDraw.Draw(result)
    draw.text((30,18),title+" / MILITARY.3",font=font(35),fill=(34,47,48))
    draw.text((30,69),"生产 Java 实际几何 + 实际 PNG 纹理 / 项目原创军官装束 / 离线资产预览，非实机截图",font=font(22),fill=(87,101,100))
    for i,(label,yaw,armour,weapon,pose) in enumerate([
        ("正面 / FRONT",0,True,None,"ready"),("侧面 / SIDE",-90,True,None,"ready"),
        ("背面 / BACK",180,True,None,"ready"),("脱装 / UNIFORM",-25,False,None,"ready"),
        ("持枪 / HOLDING",-28,True,"weapon_rifle","aim")]):
        result.paste(render(pieces,textures[faction],faction,heavy,armour,weapon,pose,yaw,width=560,height=800,role=role,rank=rank),(i*560,110))
        draw.text((i*560+20,878),label,font=font(23),fill=(62,79,77))
    target=OUT/("military3-officers-"+slug+".png");result.save(target)
    return target


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--staged",action="store_true")
    parser.add_argument("--check-js",action="store_true");args=parser.parse_args()
    source=STAGE/"BlackwatchModel.java" if args.staged else ROOT/"src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java"
    directory=STAGE if args.staged else ROOT/"src/main/resources/assets/blacklight/textures/entity"
    pieces=read_geometry(source.read_text(encoding="utf8"))
    textures={f:Image.open(directory/(f+"_soldier_layered.png")).convert("RGBA") for f in ["military","blackwatch"]}
    OUT.mkdir(parents=True,exist_ok=True)
    sheets={v[0]:png_data(sheet(pieces,textures,v)) for v in VARIANTS}
    overview=Image.new("RGB",(1680,1830),(237,240,236));draw=ImageDraw.Draw(overview)
    draw.text((28,20),"双阵营指挥人员 · 实际资产总览",font=font(37),fill=(34,47,48))
    draw.text((28,78),"队长 / 野战军官 / 基地指挥官 — 原创 P2 风格适配，非实机截图",font=font(23),fill=(87,101,100))
    for i,variant in enumerate(VARIANTS):
        _,title,faction,heavy,equipped,role,rank=variant
        x,y=(i%3)*560,120+(i//3)*850
        overview.paste(render(pieces,textures[faction],faction,heavy,equipped,None,"ready",-28,width=560,height=800,role=role,rank=rank),(x,y))
        draw.text((x+20,y+770),title,font=font(24),fill=(62,79,77))
    overview.save(OUT/"military3-officers-overview.png")
    data=dict(pieces=pieces,pivots=PIVOTS,variants=VARIANTS,sheets=sheets,
              sourceHash=hashlib.sha256(source.read_bytes()).hexdigest(),
              textures={f:png_data(directory/(f+"_soldier_layered.png")) for f in textures})
    html=HTML.replace("__DATA__",json.dumps(data,ensure_ascii=False)).replace("__PIECES__",str(len(pieces)))
    html=html.replace("military2-soldiers-","military3-officers-").replace("MILITARY.2","MILITARY.3").replace("Military.2","Military.3")
    html=html.replace("分层兵装 · 原生资产查看器","双阵营指挥人员 · 原生资产查看器")
    html=html.replace("分层兵装","军官与队长").replace("六套外观","六种指挥身份")
    html=html.replace("脱下护具后保留完整制服与人脸；枪型选择对应真实持有物品的四类视觉。", "角色示例为队长（中士）、野战军官（少尉）与基地指挥官（少校）；军衔标记独立于身份。脱装保留完整制服与人脸，BW 全装保留呼吸防护。枪型对应真实主手物品。")
    (OUT/"military3-officers-viewer.html").write_text(html,encoding="utf8")
    if args.check_js:(STAGE/"officers-viewer-check.js").write_text(re.search(r'<script>([\s\S]*)</script>',html).group(1),encoding="utf8")
    manifest=dict(source=str(source),source_sha256=data["sourceHash"],pieces=len(pieces),staged=args.staged,
                  variants=[dict(id=v[0],faction=v[2],role=v[5],rank_ordinal=v[6]) for v in VARIANTS],
                  textures={f:dict(path=str(directory/(f+"_soldier_layered.png")),sha256=hashlib.sha256((directory/(f+"_soldier_layered.png")).read_bytes()).hexdigest(),size=list(textures[f].size)) for f in textures})
    (OUT/"military3-officers-manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding="utf8")
    print(json.dumps(dict(pieces=len(pieces),pngs=7,viewer=str(OUT/"military3-officers-viewer.html"),staged=args.staged),ensure_ascii=False))


if __name__=="__main__":main()
