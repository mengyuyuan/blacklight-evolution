"""Static military.3 role / rank / UV checks and military.2 pixel compatibility proof."""
import argparse
import hashlib
import json
import math
from PIL import Image
from inspect_soldiers import main as inspect_base
from preview_soldiers import ROOT,STAGE,VARIANTS,read_geometry,render,selected
from preview_officers import VARIANTS as OFFICERS


def baseline():
    oldsource=ROOT/"src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java"
    staged=STAGE/"BlackwatchModel.java"
    old=read_geometry(oldsource.read_text(encoding="utf8"));new=read_geometry(staged.read_text(encoding="utf8"))
    assert len(old)==110,"Run --baseline before military.3 is installed; the existing proof must be preserved."
    assert old==new[:110],"military.2 geometry / UV changed"
    result=dict(military2_geometry_and_uv_preserved=True,original_piece_count=110,
                baseline_source_sha256=hashlib.sha256(oldsource.read_bytes()).hexdigest(),
                candidate_source_sha256=hashlib.sha256(staged.read_bytes()).hexdigest(),trooper_renders={})
    olddir=ROOT/"src/main/resources/assets/blacklight/textures/entity"
    oldtextures={f:Image.open(olddir/(f+"_soldier_layered.png")).convert("RGBA") for f in ["military","blackwatch"]}
    newtextures={f:Image.open(STAGE/(f+"_soldier_layered.png")).convert("RGBA") for f in oldtextures}
    for faction in oldtextures:
        for p in old:
            u,v=p["uv"];_,_,_,w,h,d=p["box"]
            area=(int(u*2),int(v*2),int((u+math.ceil(2*(w+d)))*2),int((v+math.ceil(h+d))*2))
            assert oldtextures[faction].crop(area).tobytes()==newtextures[faction].crop(area).tobytes(),(faction,p["name"])
    result["military2_existing_uv_pixel_regions_preserved"]=True
    for slug,_,faction,heavy,equipped in VARIANTS:
        weapon=("weapon_machine" if heavy else "weapon_rifle") if equipped else None
        a=render(old,oldtextures[faction],faction,heavy,equipped,weapon,"aim",-28)
        b=render(new,newtextures[faction],faction,heavy,equipped,weapon,"aim",-28)
        assert a.tobytes()==b.tobytes(),slug
        result["trooper_renders"][slug]=hashlib.sha256(a.tobytes()).hexdigest()
    (STAGE/"military2-compatibility.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf8")
    print(json.dumps(result,ensure_ascii=False))


def main():
    parser=argparse.ArgumentParser();parser.add_argument("--baseline",action="store_true");args=parser.parse_args()
    if args.baseline:return baseline()
    result=inspect_base("officers-inspection.json")
    source=ROOT/"src/main/java/com/codex/blacklight/client/model/BlackwatchModel.java"
    pieces=read_geometry(source.read_text(encoding="utf8"))
    result["roles"]={}
    count=0
    for slug,title,faction,heavy,equipped,role,default_rank in OFFICERS:
        for rank in range(8):
            active=[p for p in pieces if selected(p,faction,heavy,True,"weapon_rifle",rank,role)]
            assert any("Role_" in p["group"] for p in active)
            assert all(p["group"]=="rank_"+str(rank) for p in active if p["group"].startswith("rank_"))
            assert any(p["group"].startswith("rank_") for p in active)
            if faction=="blackwatch":assert {"gear_respirator","gear_lens_left","gear_lens_right"}<={p["name"] for p in active}
            if faction=="military" and role!="team_leader":assert "gear_helmet_crown" not in {p["name"] for p in active}
            uniform=[p for p in pieces if selected(p,faction,heavy,False,"weapon_rifle",rank,role)]
            assert len(uniform)==10 and all(p["name"].startswith("uniform_") for p in uniform)
            empty=[p for p in pieces if selected(p,faction,heavy,True,None,rank,role)]
            assert not any(p["group"].startswith("weapon_") for p in empty)
            count+=1
        active=[p for p in pieces if selected(p,faction,heavy,True,"weapon_rifle",default_rank,role)]
        result["roles"][slug]=dict(role=role,default_rank_ordinal=default_rank,visible_pieces=len(active),
                                 role_pieces=sum("Role_" in p["group"] for p in active))
    compatibility=json.loads((STAGE/"military2-compatibility.json").read_text(encoding="utf8"))
    assert compatibility["candidate_source_sha256"]==result["production_java_sha256"]
    result["role_rank_combinations_checked"]=count
    result["uniforms_complete_empty_hands_hide_guns_bw_respirators_retained"]=True
    result["military2_geometry_uv_existing_pixels_and_six_renders_preserved"]=True
    (STAGE/"officers-inspection.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf8")
    print(json.dumps(result,ensure_ascii=False))


if __name__=="__main__":main()
