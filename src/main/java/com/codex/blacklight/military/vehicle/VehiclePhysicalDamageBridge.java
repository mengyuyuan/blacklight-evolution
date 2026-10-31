package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.entity.MilitaryVehicle;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Physical contacts are mechanical candidates, never living consume/infection targets. */
public final class VehiclePhysicalDamageBridge {
    private VehiclePhysicalDamageBridge(){}
    private static VehicleDefinition.Vec3 point(Vec3 p){return new VehicleDefinition.Vec3(p.x,p.y,p.z);}
    private static boolean bounded(Vec3 from,Vec3 to,Vec3 half){
        if(!Double.isFinite(from.lengthSqr())||!Double.isFinite(to.lengthSqr())||from.distanceToSqr(to)>64*64)return false;
        return Double.isFinite(half.lengthSqr())&&half.x>=0&&half.y>=0&&half.z>=0&&half.x<=8&&half.y<=8&&half.z<=8;
    }
    /** Swept object volume against the real body/barrel OBBs; caller compares this with its same wall/living sweep. */
    public static VehicleDamageBridge.MechanicalHit nearest(ServerLevel level,Entity object,Vec3 from,Vec3 to,Vec3 half){
        if(!bounded(from,to,half))return null;
        var query=new AABB(from,to).inflate(half.x+.3,half.y+.3,half.z+.3);
        if(!VehicleKinematics.loaded(level,query))return null;
        var seen=new HashSet<UUID>();VehicleDamageBridge.MechanicalHit nearest=null;double length=from.distanceTo(to);
        for(Entity candidate:level.getEntities(object,query,entity->VehicleDamageBridge.vehicle(entity)!=null)){
            var vehicle=VehicleDamageBridge.vehicle(candidate);
            if(vehicle.isRemoved()||vehicle.health()<=0||!seen.add(vehicle.getUUID()))continue;
            var shape=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
            var distance=shape.sweepBox(point(vehicle.position()),vehicle.getYRot(),point(from),point(to),point(half));
            if(distance.isPresent()&&(nearest==null||distance.getAsDouble()<nearest.distance())){
                Vec3 contact=length<1e-9?from:from.lerp(to,distance.getAsDouble()/length);
                nearest=new VehicleDamageBridge.MechanicalHit(vehicle,contact,distance.getAsDouble());
            }
        }
        return nearest;
    }
    /** Keeps a heavy sweep from independently damaging living bodies behind its first mechanical shell. */
    public static boolean shielded(ServerPlayer player,LivingEntity target,Vec3 from){
        Vec3 to=CombatTargeting.closestPoint(from,target);
        var hull=nearest(player.serverLevel(),player,from,to,Vec3.ZERO);
        return hull!=null&&hull.distance()<from.distanceTo(to)-1e-5;
    }
    /** Contact points lie on the actual hull, inside the original heavy swing volume and in front of its locked facing. */
    public static List<VehicleDamageBridge.MechanicalHit> clawContacts(ServerPlayer player,AABB sweep,Vec3 from,Vec3 forward){
        ServerLevel level=player.serverLevel();
        if(!VehicleDamageBridge.valid(player,level)||!VehicleKinematics.loaded(level,sweep))return List.of();
        var hits=new ArrayList<VehicleDamageBridge.MechanicalHit>();var seen=new HashSet<UUID>();
        for(Entity candidate:level.getEntities(player,sweep,entity->VehicleDamageBridge.vehicle(entity)!=null)){
            MilitaryVehicle vehicle=VehicleDamageBridge.vehicle(candidate);
            if(vehicle.isRemoved()||vehicle.health()<=0||!seen.add(vehicle.getUUID()))continue;
            var definition=vehicle.definition();
            var shape=VehicleHullShape.from(definition,vehicle.turretYaw(),vehicle.gunPitch());
            var point=shape.closestPoint(point(vehicle.position()),vehicle.getYRot(),point(from));
            Vec3 contact=new Vec3(point.x(),point.y(),point.z());double distance=from.distanceTo(contact);
            Vec3 horizontal=contact.subtract(from).multiply(1,0,1);
            if(!sweep.inflate(1e-5).contains(contact)||horizontal.lengthSqr()>1e-8&&forward.dot(horizontal.normalize())<.15)continue;
            if(!VehicleDamageBridge.loaded(level,from,contact)||level.clip(new ClipContext(from,contact,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()!=HitResult.Type.MISS)continue;
            hits.add(new VehicleDamageBridge.MechanicalHit(vehicle,contact,distance));
        }
        hits.sort(Comparator.comparingDouble(VehicleDamageBridge.MechanicalHit::distance).thenComparing(hit->hit.vehicle().getUUID()));
        return List.copyOf(hits);
    }
}
