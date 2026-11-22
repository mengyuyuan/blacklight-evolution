package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** The jaw, swept damage volume and client effect share the same turning clock. */
public final class SpiderPainBeam {
    public static final int FIRE=32, SHUT=76, RECOVER=93;
    private static final double RANGE=60, RADIUS=2.05;
    public record Lane(Vec3 mouth,Vec3 end,Vec3 axis,double radius,Vec3 impactNormal){
        public boolean blocked(){return impactNormal.lengthSqr()>.5;}
    }
    private SpiderPainBeam(){}

    public static float opening(float t){return SpiderGesture.phrase(t,1,25,SHUT,RECOVER);}

    static void steer(EncounterMob boss,ServerPlayer prey,int t){
        if(t>=SHUT)return;
        var state=boss.spiderData();
        float oldYaw=boss.skinYaw(1),oldPitch=state.getFloat("BeamPitch");
        var to=prey.getBoundingBox().getCenter().subtract(boss.spider.mouth(1));
        if(to.lengthSqr()<.1)return;
        float yaw=(float)Math.toDegrees(Math.atan2(-to.x,to.z));
        float pitch=Mth.clamp((float)-Math.toDegrees(Math.atan2(to.y,to.horizontalDistance())),-70,75);
        float nextYaw=Mth.approachDegrees(oldYaw,yaw,t<FIRE?4.8F:2.35F);
        float nextPitch=Mth.approach(oldPitch,pitch,t<FIRE?3.5F:1.9F);
        if(nextYaw==oldYaw && nextPitch==oldPitch && state.contains("BeamTick"))return;
        var next=state.copy();next.putLong("BeamTick",boss.level().getGameTime());
        next.putFloat("FaceFrom",oldYaw);next.putFloat("Facing",nextYaw);next.putFloat("ActionYaw",nextYaw);
        next.putLong("FaceTick",boss.level().getGameTime());
        next.putFloat("FromPitch",oldPitch);next.putFloat("BeamPitch",nextPitch);boss.spiderData(next);
        boss.setYRot(nextYaw);boss.yBodyRot=nextYaw;boss.setYHeadRot(nextYaw);
    }

    public static Lane lane(EncounterMob boss,float partial){
        var state=boss.spiderData();float age=boss.spider.actions.age(partial);
        var mouth=boss.spider.mouth(partial);
        float blend=Mth.clamp(boss.level().getGameTime()-state.getLong("BeamTick")+partial,0,1);
        float pitch=Mth.lerp(blend,state.getFloat("FromPitch"),state.getFloat("BeamPitch"));
        Vec3 axis=Vec3.directionFromRotation(pitch,boss.skinYaw(partial));
        double reach=RANGE*SpiderGesture.ease(age,FIRE-1,FIRE+5);
        double radius=RADIUS*(.63+.37*SpiderGesture.ease(age,FIRE,FIRE+5))*(1-SpiderGesture.ease(age,SHUT,SHUT+7));
        var end=mouth.add(axis.scale(reach));
        for(double d=4;d<reach+4;d+=4){
            if(!boss.level().hasChunkAt(BlockPos.containing(mouth.add(axis.scale(Math.min(d,reach)))))){
                end=mouth.add(axis.scale(Math.max(0,d-4)));break;
            }
        }
        var hit=boss.level().clip(new ClipContext(mouth,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        boolean blocked=hit.getType()==HitResult.Type.BLOCK;
        Vec3 normal=blocked?Vec3.atLowerCornerOf(hit.getDirection().getNormal()):Vec3.ZERO;
        return new Lane(mouth,blocked?hit.getLocation():end,axis,radius,normal);
    }

    static void tick(EncounterMob boss,ServerPlayer target,int t){
        steer(boss,target,t);
        var mouth=boss.spider.mouth(1);
        if(t==2 || t==21)SpiderFoley.play(boss,mouth,t==2?SpiderFoley.Cue.INHALE:SpiderFoley.Cue.STRAIN,t==2?1.6F:1.25F);
        if(t==FIRE)SpiderFoley.play(boss,mouth,SpiderFoley.Cue.RUPTURE,2.4F);
        if(t==SHUT)SpiderFoley.play(boss,mouth,SpiderFoley.Cue.TEAR,1.4F);
        if(t<FIRE+2 || t>=SHUT || (t-FIRE-2)%8!=0 || !(boss.level() instanceof ServerLevel level))return;
        var beam=lane(boss,1);
        SpiderFoley.play(boss,mouth,SpiderFoley.Cue.STRAIN,.85F);
        if(beam.blocked())SpiderFoley.strands(boss,beam.end(),beam.axis().scale(-1),6);
        for(var player:level.getEntitiesOfClass(ServerPlayer.class,new AABB(mouth,beam.end()).inflate(beam.radius()),
                p->p.isAlive()&&!p.isCreative()&&!p.isSpectator())){
            if(DevastatorCollision.capsuleDistanceSquared(mouth,beam.end(),player.getBoundingBox())>beam.radius()*beam.radius())continue;
            var from=mouth;var to=player.getBoundingBox().getCenter();
            if(level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss)).getType()!=HitResult.Type.MISS)continue;
            if(player.hurt(boss.damageSources().mobAttack(boss),22F)){
                var direction=beam.axis();player.push(direction.x*.25,.08,direction.z*.25);player.hurtMarked=true;
            }
        }
    }
}
