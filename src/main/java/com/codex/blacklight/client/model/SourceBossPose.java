package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import software.bernie.geckolib.model.GeoModel;

/** Shared source pose sampling; no procedural replacement of these attacks. */
public final class SourceBossPose {
    public static boolean apply(GeoModel<?> model,EncounterMob mob,float partial){
        if(!SourceBossMotion.supports(mob.kind()))return false;
        var table=SourceBossMotion.get(mob.kind());var action=mob.sourceAction(mob.move());
        float age=mob.moveAge(partial);
        if(mob.exposed()){action=SourceBossAction.get(mob.kind(),"EXPOSED");age=mob.exposedAge(partial);}
        String key;float time;boolean loop=false;
        if(mob.move()==EncounterMove.NONE&&!mob.exposed()){
            key=mob.walkAnimation.speed(partial)>.015F&&table.has("walk")?"walk":"idle";
            time=mob.tickCount+partial;loop=true;
        }else if(action!=null){key=action.clip;time=action.sourceTick(mob.moveAge(partial));}
        else if(mob.kind()==EncounterKind.GOLIATH||mob.kind()==EncounterKind.ELIZABETH_GREENE){key="idle";time=0;} // Their incomplete move clocks remain explicit adapters in the shared pose samplers.
        else return false;
        var pose=mob.kind()==EncounterKind.GOLIATH?GoliathMotion.pose(mob,partial):mob.kind()==EncounterKind.ELIZABETH_GREENE?GreeneMotion.pose(mob,partial):action!=null?table.actionPose(action,age):table.sample(key,time,loop);
        if(mob.kind()==EncounterKind.JUGGERNAUT&&mob.move()==EncounterMove.GUARD&&mob.guardHitAge(partial)<10){
            float hitAge=mob.guardHitAge(partial);var hit=table.sample("block_hit",hitAge,false);
            float weight=Math.min(1,Math.min((hitAge+1)/2,(10-hitAge)/3));
            for(int i=0;i<pose.length;i++)pose[i]=SourceBossMotion.blend(pose[i],hit[i],weight);
        }
        for(int i=0;i<table.bones.length;i++){
            var bone=model.getAnimationProcessor().getBone(table.bones[i]);if(bone==null)continue;
            var p=pose[i];bone.updateRotation(p[0],p[1],p[2]);bone.setPosX(-p[3]);bone.setPosY(p[4]);bone.setPosZ(p[5]);
            bone.setScaleX(p.length>=9?p[6]:1);bone.setScaleY(p.length>=9?p[7]:1);bone.setScaleZ(p.length>=9?p[8]:1);
        }
        for(int i=0;i<mob.kind().weakParts();i++){
            String group="weak_"+i;
            for(String name:table.bones)if(name.equals(group)||name.startsWith(group+"_")){
                var bone=model.getAnimationProcessor().getBone(name);if(bone!=null)bone.setHidden(mob.broken(i));
            }
        }
        return true;
    }
    private SourceBossPose(){}
}
