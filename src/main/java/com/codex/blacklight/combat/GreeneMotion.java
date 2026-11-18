package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import java.util.Arrays;
import org.joml.Quaternionf;

/** Shared source pose for Greene's server-owned battle phases. The older
 * cycle helpers remain for legacy snapshots/fixtures, not live regrowth. */
public final class GreeneMotion {
    public static final int COLLAPSE_TICKS=125,RISE_TICKS=50,DEFAULT_HOLD_TICKS=400;
    public static final int GUARDED=0,COLLAPSING=1,DOWN=2,RISING=3;
    public static SourceBossMotion table(){return SourceBossMotion.get(EncounterKind.ELIZABETH_GREENE);}
    public static int hold(int ticks){return Math.max(75,Math.min(2400,ticks));}
    public static int duration(int hold){return COLLAPSE_TICKS+hold(hold)+RISE_TICKS;}
    public static int stage(float age,int hold){return age<0||age>=duration(hold)?GUARDED:age<COLLAPSE_TICKS?COLLAPSING:age<COLLAPSE_TICKS+hold(hold)?DOWN:RISING;}
    public static float regrowth(float age,int hold){return smooth((age-COLLAPSE_TICKS-hold(hold)-(RISE_TICKS-15))/15);}
    private static float smooth(float v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
    /** Native segment compensation is reciprocal of the final blended parent
     * scale. Interpolating precomputed reciprocals changes the original pose. */
    public static float[][] compensate(float[][] pose){
        var table=table();
        for(int i=0;i<table.bones.length;i++)if(table.bones[i].endsWith("_parent_scale")){
            int translation=table.parents[i],parent=table.parents[translation];
            pose[i]=Arrays.copyOf(pose[i],9);
            for(int axis=0;axis<3;axis++){
                float scale=pose[parent].length>=9?pose[parent][6+axis]:1;
                if(!Float.isFinite(scale)||scale<=0)throw new IllegalStateException("Invalid native Greene parent scale");
                pose[i][6+axis]=1/scale;
            }
        }
        return pose;
    }
    public static float[][] sample(String clip,float ticks,boolean loop){return compensate(table().sample(clip,ticks,loop));}
    public static float[][] action(SourceBossAction action,float age){return compensate(table().actionPose(action,age));}
    private static float[][] blend(float[][] a,float[][] b,float t){for(int i=0;i<a.length;i++)a[i]=SourceBossMotion.blend(a[i],b[i],t);return compensate(a);}
    private static float[][] frame(float frame){return sample("exposed",frame/24*20,false);}
    private static float[][] down(float ticks){
        float local=(ticks%75+75)%75;var pose=frame(60+local*1.2F);
        return local>73?blend(pose,frame(60),smooth((local-73)/2)):pose;
    }
    private static float[][] sourceDown(float ticks){
        float local=(ticks%75+75)%75;var pose=frame(60+local*1.2F);
        return ticks<2?blend(frame(60),pose,Math.max(0,ticks/2)):pose;
    }
    private static float[][] sink(float age){return blend(frame(209),sample("exit",age*.5F,false),Math.max(0,Math.min(1,age/60)));}
    /** Pose ages are local to the actual transition, including early recovery.
     * The entity supplies the source Move displacement; no visual-only sink
     * offset or fixed59tick underground-state assumption is added here. */
    public static float[][] battle(int stage,float age,int previousDown,float hitAge){
        return switch(stage){
            case GreeneBattleCycle.COLLAPSING -> frame(15+age*.36F);
            case GreeneBattleCycle.DOWN -> hit(sourceDown(age),hitAge);
            case GreeneBattleCycle.RISING -> {
                var pose=frame(150+age*1.2F);
                yield age<4?blend(sourceDown(previousDown),pose,Math.max(0,age/4)):pose;
            }
            case GreeneBattleCycle.SUBMERGING -> sink(age);
            case GreeneBattleCycle.UNDERGROUND -> age<4?blend(sink(59),sample("idle",0,false),Math.max(0,age/4)):sample("idle",0,false);
            case GreeneBattleCycle.EMERGE_WAIT -> sample("idle",0,false);
            case GreeneBattleCycle.EMERGING -> sample("enter",age,false);
            default -> sample("idle",0,false);
        };
    }
    public static float[][] cycle(float age,int hold,float hitAge){
        int stage=stage(age,hold);float[][] pose;
        if(stage==COLLAPSING){pose=frame(15+age*.36F);if(age<4)pose=blend(sample("idle",0,false),pose,smooth(age/4));}
        else if(stage==DOWN)pose=down(age-COLLAPSE_TICKS);
        else if(stage==RISING){float rise=age-COLLAPSE_TICKS-hold(hold);pose=frame(150+rise*1.2F);
            if(rise<4)pose=blend(down(hold(hold)),pose,smooth(rise/4));
            if(rise>RISE_TICKS-4)pose=blend(pose,sample("idle",0,false),smooth((rise-(RISE_TICKS-4))/4));
        }else return sample("idle",0,false);
        return stage==DOWN?hit(pose,hitAge):compensate(pose);
    }
    private static float[][] hit(float[][] pose,float hitAge){
        if(hitAge>=0&&hitAge<20){
            var hit=sample("hit",hitAge,false);var rest=sample("hit",0,false);
            float weight=smooth(hitAge/2)*(1-smooth((hitAge-14)/6));
            for(int i=0;i<pose.length;i++){
                var delta=new Quaternionf().rotationZYX(hit[i][2],hit[i][1],hit[i][0]).mul(new Quaternionf().rotationZYX(rest[i][2],rest[i][1],rest[i][0]).invert());
                var q=delta.mul(new Quaternionf().rotationZYX(pose[i][2],pose[i][1],pose[i][0]));
                var m=new org.joml.Matrix3f().set(q);var target=pose[i].clone();
                target[0]=(float)Math.atan2(m.m12(),m.m22());target[1]=(float)Math.asin(Math.max(-1,Math.min(1,-m.m02())));target[2]=(float)Math.atan2(m.m01(),m.m00());
                for(int axis=3;axis<6;axis++)target[axis]+=hit[i][axis]-rest[i][axis];pose[i]=SourceBossMotion.blend(pose[i],target,weight);
            }
        }
        return compensate(pose);
    }
    public static float[][] pose(EncounterMob mob,float partial){
        var state=mob.greeneBattle();
        if(state.busy())return battle(state.stage(),state.age(partial),state.previousDown(),mob.greeneHitAge(partial));
        var action=mob.sourceAction(mob.move());
        var pose=action==null?sample("idle",mob.tickCount+partial,true):action(action,mob.moveAge(partial));
        float elapsed=state.handoffElapsed(partial);
        if(state.handoffAge()>=0&&elapsed>=0&&elapsed<4)pose=blend(sample("enter",state.handoffAge()+elapsed,false),pose,elapsed/4);
        return GreeneCloseAttack.pose(compensate(pose),mob.greeneCloseSide(),mob.greeneCloseAge(partial),
                mob.move()==EncounterMove.VOLLEY||mob.move()==EncounterMove.ORBS);
    }
    private GreeneMotion(){}
}
