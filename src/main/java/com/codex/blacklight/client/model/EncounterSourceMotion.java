package com.codex.blacklight.client.model;

import com.codex.blacklight.combat.EncounterMove;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.model.GeoModel;

/** Retains the shipped Supreme Hunter idle/walk reference for heavy creatures.
 * Combat uses EncounterAnatomyPose: source combat root rotations don't fit these rigs.
 * Timing/localDelta remain available for reference inspection, not live attack retargeting. */
public final class EncounterSourceMotion {
    public record Timing(String clip,float first,float second,float length){
        public float sourceTick(EncounterMove move,float age){
            if(move==EncounterMove.NONE)return age;
            float t=Math.max(0,Math.min(move.duration,age));
            if(move==EncounterMove.DOUBLE){if(t<=12)return first*t/12;if(t<=25)return first+(second-first)*(t-12)/13;return second+(length-second)*(t-25)/(move.duration-25);}
            if(move.contact==0)return length*t/Math.max(1,move.duration);
            return t<=move.contact?first*t/move.contact:first+(length-first)*(t-move.contact)/(move.duration-move.contact);
        }
    }
    public static Timing timing(EncounterMove move){return switch(move){
        case CLAW->new Timing("backhand",12,0,49);
        case DOUBLE->new Timing("claw_slash",10,30,57);
        case HAMMER,ERUPTION->new Timing("overhand",21,0,43);
        case SLAM->new Timing("stomp",12,0,40);
        case POUNCE->new Timing("leap",34,0,55);
        case CHARGE->new Timing("shoulder_charge",3,0,24);
        case HOWL->new Timing("roar",40,0,125);
        case GUARD->new Timing("block",0,0,21);
        default->new Timing("idle",0,0,36);
    };}
    public static boolean supports(EncounterKind kind){return kind==EncounterKind.LEADER_HUNTER||kind==EncounterKind.JUGGERNAUT||kind==EncounterKind.GOLIATH;}
    private static final String[][] MAP={{"torso","Spine_3",""},{"head","Head","Spine_3"},{"jaw","Jaw","Head"},
        {"right_arm","Shoulder_R","Spine_3"},{"right_forearm","Elbow_R","Shoulder_R"},{"right_hand","Wrist_R","Elbow_R"},
        {"left_arm","Shoulder_L","Spine_3"},{"left_forearm","Elbow_L","Shoulder_L"},{"left_hand","Wrist_L","Elbow_L"},
        {"right_leg","Hip_R",""},{"right_shin","Knee_R","Hip_R"},{"left_leg","Hip_L",""},{"left_shin","Knee_L","Hip_L"}};
    private static int index(SupremeHunterBlockRig rig,String name){for(int i=0;i<rig.names.length;i++)if(rig.names[i].equals(name))return i;throw new IllegalArgumentException(name);}
    private static Quaternionf rotation(SupremeHunterBlockRig rig,float[] row,String name){if(name.isEmpty())return new Quaternionf();int i=index(rig,name)*4;return new Quaternionf(row[i],row[i+1],row[i+2],row[i+3]);}
    public static Quaternionf localDelta(SupremeHunterBlockRig rig,float[] row,float[] rest,String bone,String parent){
        var current=rotation(rig,row,parent).conjugate().mul(rotation(rig,row,bone));
        var neutral=rotation(rig,rest,parent).conjugate().mul(rotation(rig,rest,bone));
        return current.mul(neutral.conjugate()).normalize();
    }
    public static void apply(GeoModel<?> model,EncounterMob mob,float partial){
        if(!supports(mob.kind())||mob.exposed()||mob.move()!=EncounterMove.NONE)return;
        var rig=SupremeHunterBlockRig.get();var move=mob.move();float age=mob.moveAge(partial);var timing=timing(move);
        String clip=move==EncounterMove.NONE&&mob.getDeltaMovement().horizontalDistanceSqr()>.0002?"walk":timing.clip;
        float clock=move==EncounterMove.NONE?mob.tickCount+partial:timing.sourceTick(move,age);
        var row=rig.sample(clip,clock);float weight=move==EncounterMove.NONE?1:Math.max(0,Math.min(1,Math.min(age/3,(move.duration-age)/4)));
        var rest=rig.sample("idle",0);row=SupremeHunterBlockRig.blend(rest,row,weight);
        for(var mapping:MAP){
            // Goliath's independently hittable leg plates retain their synchronized leg clock.
            if(mob.kind()==EncounterKind.GOLIATH&&(mapping[0].endsWith("_leg")||mapping[0].endsWith("_shin")))continue;
            var bone=model.getAnimationProcessor().getBone(mapping[0]);if(bone==null)continue;
            var q=localDelta(rig,row,rest,mapping[1],mapping[2]);
            // The authored Geo rig mirrors the source model's horizontal axis.
            q.set(q.x,-q.y,-q.z,q.w);var e=q.getEulerAnglesZYX(new Vector3f());bone.updateRotation(e.x,e.y,e.z);
        }
    }
    private EncounterSourceMotion(){}
}
