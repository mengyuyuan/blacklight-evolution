package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;

/** One rebound rig for every state. HAMMER and CHARGE have decoded FIG slices;
 * remaining combat/exposure durations are explicit Minecraft adapters. */
public final class GoliathMotion {
    private GoliathMotion() {}
    public static SourceBossMotion table(){return SourceBossMotion.get(EncounterKind.GOLIATH);}
    /** P2 evaluates reciprocal parent scale after channel interpolation. Mixing
     * two cached reciprocals is not the reciprocal of the mixed source scale. */
    static float[][] compensate(float[][] pose){
        var table=table();
        for(int i=0;i<table.bones.length;i++)if(table.bones[i].startsWith("source_parent_scale_")){
            int parent=table.parents[i];
            for(int axis=6;axis<9;axis++)pose[i][axis]=1/pose[parent][axis];
        }
        return pose;
    }
    public static float[][] sample(String clip,float ticks,boolean loop){return compensate(table().sample(clip,ticks,loop));}
    public static float[][] attack(EncounterMove move,float age){
        var table=table();var action=SourceBossAction.get(EncounterKind.GOLIATH,move);
        if(action!=null)return compensate(table.actionPose(action,age));
        if(move==EncounterMove.ERUPTION)return eruption(age);
        String key=move.name();if(!table.has(key))return sample("idle",0,false);
        float progress=Math.max(0,Math.min(1,age/Math.max(1,move.duration)));
        var pose=table.sample(key,progress*table.clip(key).seconds()*20,false);
        float weight=Math.max(0,Math.min(1,Math.min(age/3,(move.duration-age)/4)));
        var idle=table.sample("idle",0,false);for(int i=0;i<pose.length;i++)pose[i]=SourceBossMotion.blend(idle[i],pose[i],weight);
        return compensate(pose);
    }
    private static float[][] eruption(float age){
        // The raw crawl-front-spike clip contains a separate airborne
        // transition. The FIG uses this clip's 0..24, 90..118, and 118..195
        // slices around the release. Keep the Minecraft hit at its existing tick.
        int release=EncounterMove.ERUPTION.contact-6;
        int recovery=EncounterMove.ERUPTION.contact+6;
        int duration=EncounterMove.ERUPTION.duration;
        if(age<20){
            var tell=sampleFrame("ERUPTION",frameBetween(0,24,age,20));
            return blendPose(sample("idle",0,false),tell,age/5);
        }
        if(age<release){
            return blendPose(sampleFrame("ERUPTION",24),sampleFrame("ERUPTION",90),
                (age-20)/(release-20));
        }
        if(age<recovery)return sampleFrame("ERUPTION",frameBetween(90,118,age-release,recovery-release));
        var pose=sampleFrame("ERUPTION",frameBetween(118,195,age-recovery,duration-recovery));
        return age>duration-8?blendPose(pose,sample("idle",0,false),1-(duration-age)/8):pose;
    }
    private static float frameBetween(float first,float last,float age,int ticks){
        return first+(last-first)*Math.max(0,Math.min(1,age/ticks));
    }
    private static float[][] sampleFrame(String clip,float frame){
        return sample(clip,frame*20/table().clip(clip).fps(),false);
    }
    static float[][] recovery(float age,int duration,long impact,float idleAge){
        var table=table();
        float at=GoliathRecovery.poseProgress(age,duration)*table.clip("exposed").seconds()*20;
        var pose=table.sample("exposed",at,false);
        if(!GoliathRecovery.wallReaction(duration)){
            float remaining=duration-age;
            if(remaining<GoliathRecovery.DAMAGE_STAND_BLEND_TICKS)
                return blendPose(pose,table.sample("idle",idleAge,true),1-remaining/GoliathRecovery.DAMAGE_STAND_BLEND_TICKS);
            return compensate(pose);
        }
        if(impact!=0&&age<GoliathRecovery.IMPACT_BLEND_TICKS){
            var charge=GoliathCharge.pose(new GoliathCharge.Snapshot(GoliathRecovery.impactPhase(impact),0),
                GoliathRecovery.impactAge(impact));
            return blendPose(charge,pose,age/GoliathRecovery.IMPACT_BLEND_TICKS);
        }
        int riseStart=GoliathRecovery.riseStart(duration);
        if(age<riseStart)return compensate(pose);
        var rise=table.sample("rise",GoliathRecovery.riseFrame(age,duration)*20/table.clip("rise").fps(),false);
        if(age<riseStart+GoliathRecovery.RISE_BLEND_TICKS){
            return blendPose(pose,rise,(age-riseStart)/GoliathRecovery.RISE_BLEND_TICKS);
        }
        if(age>duration-GoliathRecovery.STAND_BLEND_TICKS){
            var idle=table.sample("idle",idleAge,true);
            return blendPose(rise,idle,1-(duration-age)/GoliathRecovery.STAND_BLEND_TICKS);
        }
        return compensate(rise);
    }
    private static float[][] blendPose(float[][] from,float[][] to,float progress){
        float t=Math.max(0,Math.min(1,progress));
        float weight=t*t*(3-2*t);
        for(int i=0;i<to.length;i++)to[i]=SourceBossMotion.blend(from[i],to[i],weight);
        return compensate(to);
    }
    public static float[][] pose(EncounterMob mob,float partial){
        if(!mob.isAlive())return sample("death",mob.deathTime+partial,false);
        if(mob.exposed())return recovery(mob.exposedAge(partial),mob.exposedDuration(),mob.goliathCrashSample(),mob.tickCount+partial);
        if(GoliathCharge.uses(mob.kind(),mob.move()))return GoliathCharge.pose(mob.goliathChargeSnapshot(),mob.moveAge(partial));
        if(mob.move()!=EncounterMove.NONE)return attack(mob.move(),mob.moveAge(partial));
        var gait=mob.goliathGait();
        // The server needs the completed sample for hurt geometry; the client
        // interpolates between the last two local movement samples.
        float fraction=mob.level().isClientSide?partial:1;
        float weight=gait.weight(fraction);
        var idle=sample("idle",mob.tickCount+partial,true);
        if(weight<=0)return idle;
        var walk=sample("walk",gait.phase(fraction,table().clip("walk").seconds()*20),true);
        return weight>=1?walk:blendPose(idle,walk,weight);
    }
}
