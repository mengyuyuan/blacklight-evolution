package com.codex.blacklight.military;

import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Short-lived shelter intent. Geometry selection never performs an unbudgeted path search. */
public final class MilitaryCover {
    private record Claim(Vec3 feet,long until){}
    private static final Map<ServerLevel,Map<UUID,Claim>> CLAIMS=new WeakHashMap<>();
    private final MilitarySoldier soldier;
    private final Set<BlockPos> rejected=new HashSet<>();
    private BlockPos destination;private Vec3 threat;private UUID target;
    private long observedAt,selectedAt,nextScan,progressAt;private double bestDistance;private boolean planned,arrived;
    private int selections,replans;
    public MilitaryCover(MilitarySoldier soldier){this.soldier=soldier;}
    public BlockPos destination(){return destination;}
    public Vec3 lastThreat(){return threat;}
    public boolean arrived(){return arrived;}
    public int selections(){return selections;}public int replans(){return replans;}
    public void clear(){
        release();if(destination!=null)soldier.getNavigation().stop();
        destination=null;threat=null;target=null;rejected.clear();observedAt=selectedAt=nextScan=progressAt=0;planned=arrived=false;
    }
    private void release(){if(soldier.level() instanceof ServerLevel level){var claims=CLAIMS.get(level);if(claims!=null)claims.remove(soldier.getUUID());}}
    private void reject(long now){
        if(destination!=null)rejected.add(destination);release();destination=null;planned=arrived=false;nextScan=now;replans++;soldier.getNavigation().stop();
    }
    private static Map<UUID,Claim> claims(ServerLevel level){
        var claims=CLAIMS.computeIfAbsent(level,ignored->new HashMap<>());long now=level.getGameTime();
        claims.entrySet().removeIf(e->e.getValue().until()<now||!(level.getEntity(e.getKey()) instanceof MilitarySoldier s)||!s.tacticsAvailable());return claims;
    }
    private boolean free(Map<UUID,Claim> claims,Vec3 feet){
        for(var entry:claims.entrySet())if(!entry.getKey().equals(soldier.getUUID())&&entry.getValue().feet().distanceToSqr(feet)<2.25)return false;
        return true;
    }
    private boolean standing(ServerLevel level,Vec3 feet){
        var box=soldier.getDimensions(Pose.STANDING).makeBoundingBox(feet);var below=BlockPos.containing(feet.add(0,-.05,0));
        return VehicleKinematics.loaded(level,box.inflate(1))&&level.getWorldBorder().isWithinBounds(box)
                &&BlackwatchSquadSystem.withinTerritory(soldier,feet)&&level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)
                &&!level.containsAnyLiquid(box)&&level.noCollision(soldier,box)
                &&level.getEntitiesOfClass(LivingEntity.class,box.inflate(.15),e->e!=soldier&&e.isAlive()).isEmpty();
    }
    private boolean shielded(ServerLevel level,Vec3 feet){
        Vec3 eye=feet.add(0,Math.max(1.6,soldier.getEyeHeight()),0);
        if(threat==null||!VehicleDamageBridge.loaded(level,eye,threat))return false;
        var hit=level.clip(new ClipContext(eye,threat,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,soldier));
        return hit.getType()==HitResult.Type.BLOCK&&eye.distanceToSqr(hit.getLocation())<=9;
    }
    private boolean observe(LivingEntity enemy,long now){
        if(!enemy.getUUID().equals(target)){clear();target=enemy.getUUID();}
        Vec3 seen=null;long at=0;
        if(soldier.hasLineOfSight(enemy)){seen=enemy.getEyePosition();at=now;}
        else {var contact=BlackwatchSquadSystem.contact(soldier).filter(c->c.target().equals(enemy.getUUID()));if(contact.isPresent()){seen=BlackwatchSquadSystem.vector(contact.get().position()).add(0,1.5,0);at=contact.get().observedAt();}}
        if(seen!=null&&at>=observedAt){
            if(threat!=null&&threat.distanceToSqr(seen)>9){rejected.clear();nextScan=0;}
            threat=seen;observedAt=at;
        }
        return threat!=null&&now-observedAt<com.codex.blacklight.entity.BlackwatchSquad.CONTACT_TICKS;
    }
    /** True owns the movement intent, including while its FIFO path job is still waiting. */
    public boolean move(LivingEntity enemy){
        if(!(soldier.level() instanceof ServerLevel level)||!soldier.tacticsAvailable()||!soldier.validTarget(enemy)){clear();return false;}
        long now=level.getGameTime();if(!observe(enemy,now)){clear();return false;}
        var claims=claims(level);
        if(destination!=null){var feet=Vec3.atBottomCenterOf(destination);if(!standing(level,feet)||!free(claims,feet)||!shielded(level,feet))reject(now);}
        if(destination==null){
            if(now<nextScan)return false;nextScan=now+12;double score=Double.POSITIVE_INFINITY;
            // At most 75 nearby geometry candidates. Path feasibility is deferred to the shared queue.
            for(int x=-6;x<=6;x+=3)for(int z=-6;z<=6;z+=3)for(int y:new int[]{0,1,-1}){
                var point=soldier.blockPosition().offset(x,y,z);if(rejected.contains(point))continue;var feet=Vec3.atBottomCenterOf(point);
                double cost=feet.distanceToSqr(soldier.position())+Math.abs(y)*2;
                if(cost>=score||!free(claims,feet)||!standing(level,feet)||!shielded(level,feet))continue;
                destination=point;score=cost;
            }
            if(destination==null)return false;
            selections++;selectedAt=progressAt=now;bestDistance=Double.POSITIVE_INFINITY;planned=arrived=false;
        }
        Vec3 feet=Vec3.atBottomCenterOf(destination);claims.put(soldier.getUUID(),new Claim(feet,now+20));
        soldier.getLookControl().setLookAt(threat.x,threat.y,threat.z,30,30);
        double distance=feet.distanceToSqr(soldier.position());
        if(distance<.64&&shielded(level,soldier.position())){arrived=true;soldier.getNavigation().stop();return true;}
        if(distance+.05<bestDistance){bestDistance=distance;progressAt=now;}
        if(planned&&now-progressAt>=30){reject(now);return true;}
        MilitaryFormationSystem.navigateExact(soldier,destination,1.15);
        var result=MilitaryFormationSystem.lastPlan(soldier).filter(r->r.at()>=selectedAt&&r.requested().distanceToSqr(feet)<.001);
        if(result.isPresent()){
            if(!result.get().reachable()){reject(now);return true;}
            if(!planned){planned=true;progressAt=now;}
        }
        return true;
    }
}
