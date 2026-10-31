"""Read-only checks against real source and PNGs; writes one asset-inspection record."""
from datetime import datetime
from pathlib import Path
import hashlib
import json
import math
from PIL import Image
from preview_soldiers import ROOT,STAGE,VARIANTS,KINDS,read_geometry,selected,face_data


def main(output="inspection.json"):
    source=ROOT/"src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java"
    pieces=read_geometry(source.read_text(encoding="utf8"))
    directory=ROOT/"src/main/resources/assets/blacklight/textures/entity"
    atlases={f:Image.open(directory/(f+"_soldier_layered.png")).convert("RGBA") for f in ["military","blackwatch"]}
    checks={}
    checks["texture_sizes"]={f:list(im.size) for f,im in atlases.items()}
    assert all(im.size==(512,512) for im in atlases.values())
    checks["independent_faction_pngs"]=atlases["military"].tobytes()!=atlases["blackwatch"].tobytes()
    assert checks["independent_faction_pngs"]
    occupied=[]
    for p in pieces:
        u,v=p["uv"];_,_,_,w,h,d=p["box"]
        bounds=(u,v,u+math.ceil(2*(w+d)),v+math.ceil(h+d))
        assert min(bounds)>=0 and max(bounds)<=256,p["name"]
        for other in occupied:
            a,b,c,e=other
            assert bounds[2]<=a or c<=bounds[0] or bounds[3]<=b or e<=bounds[1],(p["name"],bounds,other)
        occupied.append(bounds)
        _,faces=face_data(p)
        for _,_,(a,b,w,h) in faces:
            for fx in [.02,.5,.98]:
                for fy in [.02,.5,.98]:
                    px,py=int((a+w*fx)*2),int((b+h*fy)*2)
                    assert all(im.getpixel((px,py))[3]==255 for im in atlases.values()),(p["name"],px,py)
    checks["disjoint_uv_islands"]=len(occupied)
    checks["all_cube_face_samples_opaque"]=True
    checks["variants"]={}
    for slug,_,faction,heavy,equipped in VARIANTS:
        weapon=KINDS[1 if heavy else 0] if equipped else None
        active=[p for p in pieces if selected(p,faction,heavy,equipped,weapon)]
        if not equipped:
            assert len(active)==10
            assert all(p["name"].startswith("uniform_") for p in active)
        assert not any(p["group"].startswith("weapon_") for p in pieces if selected(p,faction,heavy,equipped,None))
        checks["variants"][slug]=dict(pieces=len(active),equipped=equipped)
    checks["empty_hand_hides_every_weapon_family"]=True
    checks["production_java_sha256"]=hashlib.sha256(source.read_bytes()).hexdigest()
    checks["inspected_at"]=datetime.now().astimezone().isoformat()
    checks["scope"]="Static geometry / UV / PNG inspection only, not a game-client render or Gradle build."
    (STAGE/output).write_text(json.dumps(checks,ensure_ascii=False,indent=2),encoding="utf8")
    print(json.dumps(checks,ensure_ascii=False))
    return checks


if __name__=="__main__":main()
