package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Count live arrivals from the entire cave before admitting the next rank. */
final class GoddessBrood {
    private final EncounterMob boss;
    GoddessBrood(EncounterMob boss){this.boss=boss;}
    private List<Mob> population(ServerLevel level){
        return level.getEntitiesOfClass(Mob.class,boss.getBoundingBox().inflate(83),
            m -> m.isAlive() && SpiderPhase.edible(m) && !QuillHarvest.protectedPet(m));
    }
    private boolean ours(Mob m){
        var t=m.getPersistentData();
        return t.hasUUID("EncounterOwner") && t.getUUID("EncounterOwner").equals(boss.getUUID());
    }
    private boolean available(Mob m){
        return !ConsumeControl.protectedEntity(m) && !ConsumeSystem.inFlight(m)
                && (!m.getPersistentData().hasUUID("EncounterOwner") || ours(m));
    }
    private Vec3 floor(ServerLevel level,Vec3 point){
        var h=level.clip(new ClipContext(point.add(0,7,0),point.add(0,-50,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(h.getType()!=HitResult.Type.BLOCK || !level.getFluidState(h.getBlockPos().above()).isEmpty()) return null;
        return h.getLocation().add(0,.01,0);
    }
    private Mob spawn(ServerLevel level,ServerPlayer p,boolean elite,boolean food,int seed){
        double radius=food?4.0:15+Math.floorMod(seed,11);
        Vec3 start=food?p.position():boss.position().lerp(p.position(),.45);
        for(int i=0;i<12;i++){
            double a=(seed*.81+i*2.40);
            var feet=floor(level,start.add(radius*Math.cos(a),0,radius*Math.sin(a)));
            if(feet==null || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(feet)))continue;
            Mob m=elite?ModEntities.INFECTED_HOST.get().create(level):ModEntities.COMMON_INFECTED.get().create(level);
            if(m==null) return null;
            m.moveTo(feet.x,feet.y,feet.z,(float)Math.toDegrees(a),0);
            if(!level.noCollision(m) || level.containsAnyLiquid(m.getBoundingBox()))continue;
            if(food && !CombatTargeting.clearRay(p,p.getEyePosition(),m.getEyePosition()))continue;
            if(Math.abs(p.getY()-feet.y)<4 && !m.getNavigation().isStableDestination(m.blockPosition()))continue;
            m.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
            if(!level.addFreshEntity(m))return null;
            if(elite && !((BlacklightInfectedHost)m).beginGreeneEvolution()){m.discard();return null;}
            if(!food)m.setTarget(p);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,feet.x,feet.y+.7,feet.z,9,.32,.45,.32,.03);
            return m;
        }
        return null;
    }
    boolean reinforce(ServerLevel level,ServerPlayer target,boolean strong,int rank,int sequence){
        var brood=population(level);
        int eliteCount=0,ordinary=0;
        for(var m:brood){if(SpiderPhase.reservedForThrow(m))eliteCount++;else ordinary++;}
        int eliteCap=strong?6:4,ordinaryCap=strong?24:16;
        boolean any=false;
        for(var m:brood){
            if(!available(m) || m.getPersistentData().getInt("GoddessOrder")==sequence)continue;
            if(m.distanceToSqr(target)>55*55 || !m.hasLineOfSight(target))continue;
            m.getPersistentData().putUUID("EncounterOwner",boss.getUUID());
            m.getPersistentData().putInt("GoddessOrder",sequence);
            m.setTarget(target);
            any=true;
        }
        int seed=rank+sequence*17;
        for(int i=0;i<4 && ordinary<ordinaryCap;i++) {
            var m=spawn(level,target,false,false,seed+i);
            if(m!=null){ordinary++;any=true;m.getPersistentData().putInt("GoddessOrder",sequence);}
        }
        if(eliteCount<eliteCap){
            var m=spawn(level,target,true,false,seed+11);
            if(m!=null){any=true;m.getPersistentData().putInt("GoddessOrder",sequence);}
        }
        if(any)SpiderFoley.play(boss,boss.position().add(0,5,0),SpiderFoley.Cue.SHED,1.1F);
        return any;
    }
    /** The recovery clock starts once each grounded participant has an accessible, grabbable meal. */
    boolean rest(ServerLevel level){
        var brood=population(level);
        var reserved=new HashSet<UUID>();
        boolean ready=true;
        var now=level.getGameTime();
        for(var m:brood)if(ours(m)&&available(m))pacify(m,now);
        for(var p:level.players()){
            if(p.isSpectator()||p.isCreative()||!p.isAlive()||p.distanceToSqr(boss)>100*100)continue;
            if(ConsumeSystem.active(p))continue;
            var meal=brood.stream().filter(m->m instanceof BlacklightCommonInfected && available(m) && !reserved.contains(m.getUUID())
                    && m.distanceToSqr(p)<9*9 && Math.abs(m.getY()-p.getY())<3
                    && ConsumeSystem.healthReady(p,m) && m.hasLineOfSight(p))
                    .min(Comparator.comparingDouble(m->m.distanceToSqr(p))).orElse(null);
            if(meal==null && brood.size()<30){
                meal=spawn(level,p,false,true,p.getId()+(int)(now/20));
                if(meal!=null)brood.add(meal);
            }
            if(meal==null){ready=false;continue;}
            reserved.add(meal.getUUID());
            pacify(meal,now);
        }
        return ready;
    }
    private static void pacify(Mob mob,long now){
        var t=mob.getPersistentData();
        if(mob.isNoAi()&&!t.contains("GoddessReleaseAt"))return;
        mob.setTarget(null);mob.getNavigation().stop();mob.setNoAi(true);
        t.putLong("GoddessReleaseAt",now+15);
    }
    public static void release(Mob mob){
        var t=mob.getPersistentData();
        if(t.contains("GoddessReleaseAt") && mob.level().getGameTime()>=t.getLong("GoddessReleaseAt")){
            t.remove("GoddessReleaseAt");
            if(!ConsumeControl.protectedEntity(mob))mob.setNoAi(false);
            else t.putLong("GoddessReleaseAt",mob.level().getGameTime()+5);
        }
    }
}
