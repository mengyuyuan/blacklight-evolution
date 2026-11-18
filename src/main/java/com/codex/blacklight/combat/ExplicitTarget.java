package com.codex.blacklight.combat;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.vehicle.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import java.util.*;

/** A lock is a selectable entity, not necessarily an absorbable living body. */
public final class ExplicitTarget {
    private ExplicitTarget(){}
    public static boolean enabled(Player p){return p!=null&&p.isAlive()&&!p.isSpectator()
            &&(com.codex.blacklight.power.PrototypeMetabolism.active(p)
            ||com.codex.blacklight.firearms.FirearmsCombat.holdingFirearm(p)||p.getVehicle() instanceof MilitaryVehicle
            ||p.getVehicle() instanceof BlackwatchHelicopter craft&&craft.health()>0&&craft.phase()!=BlackwatchHelicopter.Phase.CRASH);}
    public static boolean mechanical(Entity e){return e instanceof MilitaryVehicle||e instanceof BlackwatchHelicopter;}
    public static Entity resolve(Entity e){
        // Cockpit occupants are not competing lock identities. Grabbing and
        // hijacking retain their own living-body interaction path.
        if(e!=null&&e.getRootVehicle() instanceof BlackwatchHelicopter craft)return craft;
        if(e instanceof BlackwatchHelicopter)return e;
        var vehicle=VehicleDamageBridge.vehicle(e);return vehicle!=null?vehicle:CombatTargeting.resolveTarget(e);
    }
    public static boolean selectable(Player p,Entity e){
        if(p==null||e==null||p.isPassenger()&&e.getRootVehicle()==p.getRootVehicle())return false;
        if(e instanceof LivingEntity living)return CombatTargeting.selectable(p,living);
        if(e instanceof BlackwatchHelicopter craft)return craft.level()==p.level()&&!craft.isRemoved()&&craft.health()>0&&craft.phase()!=BlackwatchHelicopter.Phase.CRASH
                &&craft!=p.getRootVehicle()&&!craft.isInvisibleTo(p)&&!craft.isInvulnerable()&&!VehicleDamageBridge.protectedAircraft(p,craft);
        return e instanceof MilitaryVehicle v&&v.level()==p.level()&&!v.isRemoved()&&v.health()>0
                &&v!=p.getVehicle()&&!v.isInvisibleTo(p)&&!VehicleDamageBridge.protectedHull(p,v);
    }
    public static Vec3 point(Player p,Entity e){
        if(e instanceof LivingEntity living)return CombatTargeting.lockPoint(p,living);
        if(e instanceof MilitaryVehicle v)return closestPoint(p.getEyePosition(),v);
        if(e instanceof BlackwatchHelicopter craft){var point=com.codex.blacklight.military.aircraft.HelicopterSkinContact.visiblePoint(p,craft,Double.MAX_VALUE);return point!=null?point:com.codex.blacklight.military.aircraft.HelicopterSkinContact.closestPoint(craft,p.getEyePosition());}
        return e.getBoundingBox().getCenter();
    }
    static Set<Entity> hullsIn(Player player,AABB area){
        var result=new LinkedHashSet<Entity>();
        for(Entity part:player.level().getEntities(player,area.inflate(2),e->e instanceof BlackwatchHelicopter||VehicleDamageBridge.vehicle(e)!=null)){
            Entity hull=resolve(part);
            if(selectable(player,hull))result.add(hull);
        }
        return result;
    }
    static boolean hurtHull(net.minecraft.server.level.ServerPlayer player,Entity hull,float damage,Vec3 point,java.util.function.BooleanSupplier current){
        if(!current.getAsBoolean())return false;
        boolean hit=hull instanceof BlackwatchHelicopter craft?VehicleDamageBridge.hurtPhysicalAircraft(player,craft,damage,null,point,current):
                VehicleDamageBridge.hurtPhysical(player,(MilitaryVehicle)hull,damage,null,point);
        if(hit&&hull instanceof BlackwatchHelicopter)
            com.codex.blacklight.firearms.GunplayEffects.metalContact(player,point,player.getEyePosition().subtract(point).normalize());
        return hit;
    }
    static List<HydraContact.Box> hullBoxes(Entity hull){
        if(hull instanceof BlackwatchHelicopter craft)return com.codex.blacklight.military.aircraft.HelicopterSkinContact.boxes(craft);
        var vehicle=(MilitaryVehicle)hull;
        var shape=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
        var origin=new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ());
        var result=new ArrayList<HydraContact.Box>();
        for(var corners:shape.worldCorners(origin,vehicle.getYRot())){
            var low=worldPoint(corners.get(0));
            var right=worldPoint(corners.get(1)).subtract(low);
            var up=worldPoint(corners.get(2)).subtract(low);
            var forward=worldPoint(corners.get(4)).subtract(low);
            var half=new Vec3(right.length()/2,up.length()/2,forward.length()/2);
            result.add(HydraContact.oriented(low.lerp(worldPoint(corners.get(7)),.5),half,right.normalize(),up.normalize(),forward.normalize()));
        }
        return result;
    }
    private static Vec3 worldPoint(VehicleDefinition.Vec3 p){return new Vec3(p.x(),p.y(),p.z());}
    public static Vec3 closestPoint(Vec3 eye,MilitaryVehicle v){
            var at=v.position();
            var hit=VehicleHullShape.from(v.definition(),v.turretYaw(),v.gunPitch()).closestPoint(
                    new VehicleDefinition.Vec3(at.x,at.y,at.z),v.getYRot(),new VehicleDefinition.Vec3(eye.x,eye.y,eye.z));
            return new Vec3(hit.x(),hit.y(),hit.z());
    }
    public static double distanceSquared(Player p,Entity e){
        return e instanceof LivingEntity living?CombatTargeting.distanceSquared(p.getEyePosition(),living):p.getEyePosition().distanceToSqr(point(p,e));
    }
    public static boolean visibleWithinRange(Player p,Entity e,double range){
        if(!selectable(p,e)||range<0)return false;
        if(e instanceof LivingEntity living)return CombatTargeting.visibleWithinRange(p,living,range);
        if(e instanceof BlackwatchHelicopter craft)return com.codex.blacklight.military.aircraft.HelicopterSkinContact.visiblePoint(p,craft,range)!=null;
        return distanceSquared(p,e)<=range*range+1e-6&&CombatTargeting.clearRay(p,p.getEyePosition(),point(p,e));
    }
    public static List<Entity> candidates(Player p,double range){
        var query=p.getBoundingBox().inflate(range);var result=new LinkedHashSet<Entity>();
        result.addAll(CombatTargeting.candidates(p,query,t->CombatTargeting.selectable(p,t)));
        // High above the world: also search the column below, any depth, for a Bulletdive lock.
        if(!p.onGround()&&p.getY()-p.level().getMinBuildHeight()>range){
            var column=new net.minecraft.world.phys.AABB(p.getX()-range,p.level().getMinBuildHeight(),p.getZ()-range,p.getX()+range,p.getY(),p.getZ()+range);
            result.addAll(CombatTargeting.candidates(p,column,t->CombatTargeting.selectable(p,t)));
        }
        for(var entity:p.level().getEntities(p,query,e->e instanceof BlackwatchHelicopter||VehicleDamageBridge.vehicle(e)!=null)){
            var parent=resolve(entity);if(selectable(p,parent))result.add(parent);
        }
        var resolved=new LinkedHashSet<Entity>();
        for(var entity:result){var target=resolve(entity);if(selectable(p,target))resolved.add(target);}
        return new ArrayList<>(resolved);
    }
}
