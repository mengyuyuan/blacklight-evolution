package com.codex.blacklight.combat;

import com.codex.blacklight.entity.MilitaryVehicle;
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
            ||com.codex.blacklight.firearms.FirearmsCombat.holdingFirearm(p)||p.getVehicle() instanceof MilitaryVehicle);}
    public static Entity resolve(Entity e){
        var vehicle=VehicleDamageBridge.vehicle(e);return vehicle!=null?vehicle:CombatTargeting.resolveTarget(e);
    }
    public static boolean selectable(Player p,Entity e){
        if(p==null||e==null)return false;
        if(e instanceof LivingEntity living)return CombatTargeting.selectable(p,living);
        return e instanceof MilitaryVehicle v&&v.level()==p.level()&&!v.isRemoved()&&v.health()>0
                &&v!=p.getVehicle()&&!v.isInvisibleTo(p)&&!VehicleDamageBridge.protectedHull(p,v);
    }
    public static Vec3 point(Player p,Entity e){
        if(e instanceof LivingEntity living)return CombatTargeting.lockPoint(p,living);
        if(e instanceof MilitaryVehicle v)return closestPoint(p.getEyePosition(),v);
        return e.getBoundingBox().getCenter();
    }
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
        return distanceSquared(p,e)<=range*range+1e-6&&CombatTargeting.clearRay(p,p.getEyePosition(),point(p,e));
    }
    public static List<Entity> candidates(Player p,double range){
        var query=p.getBoundingBox().inflate(range);var result=new LinkedHashSet<Entity>();
        result.addAll(CombatTargeting.candidates(p,query,t->CombatTargeting.selectable(p,t)));
        for(var entity:p.level().getEntities(p,query,e->VehicleDamageBridge.vehicle(e)!=null)){
            var parent=resolve(entity);if(selectable(p,parent))result.add(parent);
        }
        return new ArrayList<>(result);
    }
}
