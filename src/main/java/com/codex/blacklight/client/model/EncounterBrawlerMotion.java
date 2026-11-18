package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.CreaturePoseTable;
import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.entity.EncounterMob;
import software.bernie.geckolib.model.GeoModel;

/** The existing Hunter mesh uses its own calibrated Brawler clips, without another retarget. */
public final class EncounterBrawlerMotion {
    public record Sample(String clip,float tick,float weight){}
    public static Sample sample(EncounterMove move,float age){
        if(move==EncounterMove.NONE)return new Sample("idle",0,0);
        float start=0,end=move.duration,contact=move.contact,sourceContact,sourceEnd;
        String clip;
        switch(move){
            case DOUBLE->{
                // Two instances of the existing swipe preserve the encounter's two damage events.
                // Playing the five-hit barrage over this move would show three phantom contacts.
                clip="swipe";sourceContact=12;sourceEnd=32;
                if(age<20){end=20;contact=12;}else{start=20;contact=25;}
            }
            case POUNCE->{clip="combat_leap";sourceContact=22;sourceEnd=48;}
            case SLAM->{clip="combat_slam";sourceContact=16;sourceEnd=48;}
            default->{clip="swipe";sourceContact=12;sourceEnd=32;}
        }
        float t=Math.max(start,Math.min(end,age));
        float tick=t<=contact?sourceContact*(t-start)/Math.max(1,contact-start)
            :sourceContact+(sourceEnd-sourceContact)*(t-contact)/Math.max(1,end-contact);
        float weight=Math.max(0,Math.min(1,Math.min((t-start)/3,(end-t)/4)));
        return new Sample(clip,tick,weight);
    }
    public static void apply(GeoModel<?> model,EncounterMob mob,float partial){
        // Let the existing idle/run animation controller handle locomotion.
        if(mob.move()==EncounterMove.NONE)return;
        var sample=sample(mob.move(),mob.moveAge(partial));
        var table=CreaturePoseTable.HUNTER;
        var pose=table.sample(sample.clip,sample.tick);var rest=table.sample("idle",0);
        for(int i=0;i<table.bones.length;i++){
            var bone=model.getAnimationProcessor().getBone(table.bones[i]);if(bone==null)continue;
            var p=CreaturePoseTable.blend(rest[i],pose[i],sample.weight);
            bone.updateRotation(p[0],p[1],p[2]);bone.setPosX(p[3]);bone.setPosY(p[4]);bone.setPosZ(p[5]);
        }
    }
    private EncounterBrawlerMotion(){}
}
