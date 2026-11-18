package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** The existing soldier/ally/disguise rules remain authoritative; CLEAR never adds civilian targets. */
public final class VehicleThreatBridge {
    private static MilitarySoldier actor(MilitaryVehicle vehicle){
        return vehicle.weaponOperator() instanceof MilitarySoldier soldier&&soldier.faction()==vehicle.faction()&&MilitaryVehicle.crewOperational(soldier)?soldier:null;
    }
    public static boolean legalTarget(MilitaryVehicle vehicle,LivingEntity target){
        var actor=actor(vehicle);
        return actor!=null&&!vehicle.controlLocked()&&vehicle.playerOwner()==null&&vehicle.getPassengers().stream().noneMatch(Player.class::isInstance)
                &&target!=null&&target.getVehicle()!=vehicle&&BlacklightInfectionSystem.isInfected(target)&&actor.validTarget(target)
                &&!FirearmsCombat.protectedTarget(actor,target)&&vehicle.distanceToSqr(target)<=32*32
                &&VehicleKinematics.loaded(vehicle.level(),new AABB(actor.getEyePosition(),target.getBoundingBox().getCenter()).inflate(.1))
                &&actor.hasLineOfSight(target);
    }
    public static Optional<LivingEntity> find(MilitaryVehicle vehicle){
        if(!(vehicle.level() instanceof ServerLevel level)||actor(vehicle)==null)return Optional.empty();
        return level.getEntitiesOfClass(LivingEntity.class,vehicle.getBoundingBox().inflate(32),target->legalTarget(vehicle,target)).stream()
                .sorted(Comparator.comparingDouble((LivingEntity target)->vehicle.distanceToSqr(target)).thenComparing(LivingEntity::getUUID)).findFirst();
    }
    public static boolean engage(MilitaryVehicle vehicle,LivingEntity target){
        if(!legalTarget(vehicle,target))return false;var actor=actor(vehicle);
        boolean armored=target.getVehicle() instanceof MilitaryVehicle;
        boolean largeOrGroup=target.getBbWidth()>1.4F||target.getBbHeight()>3
                ||vehicle.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(3),other->other!=target&&legalTarget(vehicle,other)).size()>=2;
        var weapons=new ArrayList<>(vehicle.definition().weapons().keySet());
        weapons.sort(Comparator.comparingInt((String id)->VehicleFireControl.priority(VehicleWeaponDefinition.require(id),armored,largeOrGroup)).thenComparing(Comparator.naturalOrder()));
        for(var id:weapons){
            var spec=VehicleWeaponDefinition.require(id);var weapon=spec.weapon();if(vehicle.inventory().ammunition(weapon.ammoId())==0)continue;
            if(vehicle.level().getGameTime()<vehicle.inventory().cooldownUntil(id))continue;
            // Select one safe weapon before rotating. Never turn the turret several times per tick.
            var end=target.getBoundingBox().getCenter();
            if(weapon.radius()>0){
                if(vehicle.distanceToSqr(target)<100)continue;
                if(!((ServerLevel)vehicle.level()).getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(weapon.radius()+2),other->other!=target&&!legalTarget(vehicle,other)).isEmpty())continue;
            }
            if(!vehicle.aimAiWeapon(actor,target,id))return false;
            var origin=new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ());var mount=vehicle.definition().mountWorld(spec.muzzle(),origin,vehicle.getYRot(),vehicle.turretYaw(),vehicle.gunPitch());
            var muzzle=new Vec3(mount.x(),mount.y(),mount.z());
            if(!VehicleKinematics.loaded(vehicle.level(),new AABB(muzzle,end).inflate(1))||vehicle.level().clip(new ClipContext(muzzle,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()!=HitResult.Type.MISS)return false;
            var hull=VehicleDamageBridge.nearest((ServerLevel)vehicle.level(),actor,vehicle,muzzle,end);
            if(hull!=null&&(hull.vehicle()!=target.getVehicle()||VehicleDamageBridge.protectedHull(actor,hull.vehicle())))return false;
            var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(vehicle.level(),actor,muzzle,end,new AABB(muzzle,end).inflate(.4),entity->entity instanceof LivingEntity&&entity!=actor&&entity.getVehicle()!=vehicle);
            if(hit!=null&&hit.getEntity()!=target)return false;
            return VehicleAttackService.fire(vehicle,actor,id);
        }
        return false;
    }
    private VehicleThreatBridge(){}
}
