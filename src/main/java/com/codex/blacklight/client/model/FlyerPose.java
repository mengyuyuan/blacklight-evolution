package com.codex.blacklight.client.model;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.combat.FlyerMotion;
import software.bernie.geckolib.model.GeoModel;

public final class FlyerPose {
    public static void apply(GeoModel<?> model,EncounterMob mob,float partial){
        var table=FlyerMotion.table();var pose=mob.flyerPose(partial);
        for(int i=0;i<table.bones.length;i++){var b=model.getAnimationProcessor().getBone(table.bones[i]);if(b==null)continue;var p=pose[i];b.updateRotation(p[0],p[1],p[2]);b.setPosX(-p[3]);b.setPosY(p[4]);b.setPosZ(p[5]);b.setScaleX(1);b.setScaleY(1);b.setScaleZ(1);}
    }
    private FlyerPose(){}
}
