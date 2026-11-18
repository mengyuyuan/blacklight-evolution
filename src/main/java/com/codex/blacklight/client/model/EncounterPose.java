package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.combat.EncounterAnatomyPose;
import com.codex.blacklight.entity.*;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.util.Mth;

/** Pose evaluated from the server move clock, including windup, follow-through and stagger. */
public final class EncounterPose {
    private static void rotation(GeoModel<?> model,String name,float x,float y,float z){var b=model.getAnimationProcessor().getBone(name);if(b!=null)b.updateRotation(x,y,z);}
    public static void apply(GeoModel<?> model,EncounterMob mob,float partial){
        if(mob.kind()==EncounterKind.FLYER){FlyerPose.apply(model,mob,partial);return;}
        if(mob.kind()==EncounterKind.BRAWLER){EncounterBrawlerMotion.apply(model,mob,partial);return;}
        if(SourceBossPose.apply(model,mob,partial))return;
        // A source clip may have translated the root on the previous frame.
        var root=model.getAnimationProcessor().getBone("root");if(root!=null){root.updateRotation(0,0,0);root.setPosX(0);root.setPosY(0);root.setPosZ(0);}
        var torso=model.getAnimationProcessor().getBone("torso");if(torso!=null){torso.setPosX(0);torso.setPosY(0);torso.setPosZ(0);}
        for(String name:new String[]{"neck_base","neck_mid","neck_top"}){var b=model.getAnimationProcessor().getBone(name);if(b!=null){b.updateRotation(0,0,0);b.setPosX(0);b.setPosY(0);b.setPosZ(0);b.setScaleX(1);b.setScaleY(1);b.setScaleZ(1);}}
        var action=mob.move();float age=mob.moveAge(partial),load=action.anticipation(age),hit=action.followThrough(age);
        float walk=(mob.tickCount+partial)*.65F,step=mob.getDeltaMovement().horizontalDistanceSqr()>.0002?.5F:0;
        // Geo bones use upward-positive Y. Forward bend is negative X, unlike HumanoidModel.
        rotation(model,"torso",mob.exposed()?-.8F:-.1F*load-.35F*hit,0,0);rotation(model,"head",.2F*load,0,0);
        rotation(model,"right_leg",Mth.cos(walk)*step,0,0);rotation(model,"left_leg",-Mth.cos(walk)*step,0,0);
        float right=-.3F,left=-.3F,twist=0;
        switch(action){
            case CLAW,BLADE,BATON,WHIP->{right=-2*load-.7F*hit;left=-.5F*load;twist=.8F*load-1.2F*hit;}
            case DOUBLE->{float t=age<20?age:age-13;float strike=EncounterMove.CLAW.followThrough(t),wind=EncounterMove.CLAW.anticipation(t);right=age<20?-2*wind-.6F*strike:-.4F;left=age>=20?-2*wind-.6F*strike:-.4F;twist=(age<20?1:-1)*(.6F*wind-.8F*strike);}
            case HAMMER,SLAM,ERUPTION->{right=left=-2.8F*load-1.15F*hit;rotation(model,"torso",.3F*load-.8F*hit,0,0);}
            case ORBS,VOLLEY,GRENADE->{right=-2.2F*load-1.4F*hit;left=-.5F*load;}
            case HOWL->{right=left=-2.4F*load-1.8F*hit;rotation(model,"head",.6F*(load+hit),0,0);}
            case GUARD->{right=left=-1.5F;}
            case POUNCE,CHARGE,DIVE->{right=left=-1.2F;rotation(model,"torso",-.65F,0,0);}
            case DODGE->{rotation(model,"torso",-.45F,0,.45F*(float)Math.sin(Math.PI*age/action.duration));right=left=-.8F;}
            default->{}
        }
        rotation(model,"right_arm",-right,twist,-.15F);rotation(model,"left_arm",-left,-twist,.15F);
        rotation(model,"right_leg",mob.legAngle(true,partial),0,0);rotation(model,"left_leg",mob.legAngle(false,partial),0,0);
        for(String side:new String[]{"right","left"}){rotation(model,side+"_forearm",.25F+.65F*load-.12F*hit,0,0);rotation(model,side+"_hand",.12F*load,0,0);rotation(model,side+"_shin",mob.kind()==EncounterKind.GOLIATH?0:-.18F*load,0,0);}
        if(action==EncounterMove.NONE&&!mob.exposed()){
            // WalkAnimation follows actual displacement (including direct flight/charge movement),
            // unlike deltaMovement, which can be zero on a moving remote entity.
            float amount=Math.min(1,mob.walkAnimation.speed(partial)*2.5F);
            float stride=mob.walkAnimation.position(partial)*1.25F;
            float swing=Mth.sin(stride)*amount;
            float armScale=mob.kind()==EncounterKind.LEADER_HUNTER?.32F:mob.kind()==EncounterKind.GOLIATH?.16F:.5F;
            rotation(model,"torso",0,0,.025F*swing);
            rotation(model,"head",-mob.getXRot()*Mth.DEG_TO_RAD,Mth.clamp(Mth.wrapDegrees(mob.yHeadRot-mob.yBodyRot)*Mth.DEG_TO_RAD,-.65F,.65F),0);
            rotation(model,"right_arm",-swing*armScale,0,0);rotation(model,"left_arm",swing*armScale,0,0);
            rotation(model,"right_forearm",.08F*amount,0,0);rotation(model,"left_forearm",.08F*amount,0,0);
            rotation(model,"right_hand",0,0,0);rotation(model,"left_hand",0,0,0);rotation(model,"jaw",0,0,0);
            if(mob.kind()!=EncounterKind.GOLIATH){
                rotation(model,"right_leg",swing*.5F,0,0);rotation(model,"left_leg",-swing*.5F,0,0);
                rotation(model,"right_shin",-.28F*Math.max(0,swing),0,0);rotation(model,"left_shin",-.28F*Math.max(0,-swing),0,0);
            }
        }
        if(EncounterAnatomyPose.supports(mob.kind())&&action!=EncounterMove.NONE&&!mob.exposed()){
            var pose=EncounterAnatomyPose.sample(mob.kind(),action,age);
            rotation(model,"torso",pose.torso());rotation(model,"head",pose.head());
            rotation(model,"right_arm",pose.rightArm());rotation(model,"left_arm",pose.leftArm());
            rotation(model,"right_forearm",pose.rightForearm());rotation(model,"left_forearm",pose.leftForearm());
            rotation(model,"right_hand",pose.rightHand());rotation(model,"left_hand",pose.leftHand());
            rotation(model,"jaw",pose.jaw(),0,0);
            // Hip angles are also used by Goliath's server-side armor hit volumes.
            rotation(model,"right_shin",0,0,0);rotation(model,"left_shin",0,0,0);
        }
        for(int i=0;i<mob.kind().weakParts();i++){var bone=model.getAnimationProcessor().getBone("weak_"+i);if(bone!=null)bone.setHidden(mob.broken(i));}
        for(String form:new String[]{"claws","hammer","blade"}){var b=model.getAnimationProcessor().getBone(form);if(b!=null)b.setHidden(mob.kind()==EncounterKind.ALEX_MERCER&&!form.equals(mob.phase()==0?"claws":mob.phase()==1?"hammer":"blade"));}
        if(mob.kind()==EncounterKind.FLYER){rotation(model,"right_arm",0,0,-.4F-.45F*Mth.sin((mob.tickCount+partial)*.5F));rotation(model,"left_arm",0,0,.4F+.45F*Mth.sin((mob.tickCount+partial)*.5F));}
    }
    private static void rotation(GeoModel<?> model,String name,EncounterAnatomyPose.Joint joint){rotation(model,name,joint.x(),joint.y(),joint.z());}
    public static void drone(GeoModel<?> model,MilitaryUav drone,float partial){
        var torso=model.getAnimationProcessor().getBone("torso");if(torso!=null){torso.setScaleX(1);torso.setScaleY(1);torso.setScaleZ(1);}
        float time=drone.tickCount+partial;
        if(!drone.isAlive()){float death=Math.min(15,drone.deathTime+partial);time=drone.tickCount-drone.deathTime+death-death*death/30;}
        rotation(model,"rotor_0",0,time*1.4F,0);
        // The original skeleton has no independent camera gimbal: the upper sensor is fixed.
        rotation(model,"head",0,0,0);
        String[] lamps={"lamp_green","lamp_yellow","lamp_red"};int state=drone.warningLamp();
        for(int i=0;i<lamps.length;i++){var lamp=model.getAnimationProcessor().getBone(lamps[i]);if(lamp!=null)lamp.setHidden(i!=state);}
    }
    private EncounterPose(){}
}
