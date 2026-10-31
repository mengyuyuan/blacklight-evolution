package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.firearms.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server-owned shot boundary. Caller cannot supply a hit, position, ammunition or damage. */
public final class VehicleAttackService {
    private VehicleAttackService(){}
    public static boolean fire(MilitaryVehicle vehicle,LivingEntity actor,String weapon){
        if(vehicle==null||!(vehicle.level() instanceof ServerLevel level)||!VehicleDamageBridge.valid(actor,level)
                ||vehicle.isRemoved()||vehicle.controlLocked()||vehicle.health()<=0||vehicle.weaponOperator()!=actor||actor.getVehicle()!=vehicle
                ||!vehicle.getPassengers().contains(actor)||!MilitaryVehicle.crewOperational(actor)||!vehicle.definition().weapons().containsKey(weapon))return false;
        if(actor instanceof ServerPlayer player&&vehicle.controlLease(player.getUUID())==null)return false;
        VehicleWeaponDefinition specification;
        try{specification=VehicleWeaponDefinition.require(weapon);}catch(IllegalArgumentException invalid){return false;}
        var definition=specification.weapon();var origin=new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ());
        var a=vehicle.definition().mountWorld(specification.breech(),origin,vehicle.getYRot(),vehicle.turretYaw(),vehicle.gunPitch());
        var b=vehicle.definition().mountWorld(specification.muzzle(),origin,vehicle.getYRot(),vehicle.turretYaw(),vehicle.gunPitch());
        Vec3 breech=new Vec3(a.x(),a.y(),a.z()),muzzle=new Vec3(b.x(),b.y(),b.z()),direction=muzzle.subtract(breech).normalize();
        if(direction.lengthSqr()<.99||!VehicleDamageBridge.loaded(level,breech,muzzle.add(direction.scale(.15)))
                ||level.clip(new ClipContext(breech,muzzle.add(direction.scale(.15)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()!=HitResult.Type.MISS)return false;
        var before=vehicle.inventory().snapshot();
        if(!vehicle.inventory().tryFire(weapon,level.getGameTime()))return false;
        var debited=vehicle.inventory().snapshot();
        if(definition.kind()==WeaponDefinition.Kind.BULLET)hitscan(vehicle,actor,definition,muzzle,direction);
        else{
            var projectile=new VehicleOrdnanceProjectile(level,actor,vehicle,weapon);
            projectile.setPos(muzzle);projectile.shoot(direction.x,direction.y,direction.z,(float)specification.speed(),0);
            if(!level.addFreshEntity(projectile)){vehicle.restoreResourcesIfUnchanged(before,debited);return false;}
        }
        vehicle.recordShot(weapon);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,muzzle.x,muzzle.y,muzzle.z,2,.025,.025,.025,.005);
        level.playSound(null,net.minecraft.core.BlockPos.containing(muzzle),definition.kind()==WeaponDefinition.Kind.BULLET?net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT:net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,
                actor instanceof Player?net.minecraft.sounds.SoundSource.PLAYERS:net.minecraft.sounds.SoundSource.HOSTILE,1F,definition.kind()==WeaponDefinition.Kind.BULLET?.8F:.6F);
        return true;
    }
    private static void hitscan(MilitaryVehicle origin,LivingEntity actor,WeaponDefinition definition,Vec3 start,Vec3 direction){
        var level=(ServerLevel)actor.level();Vec3 end=start;
        // The visual ray stops before a missing chunk instead of asking it to generate.
        for(double t=.5;t<=definition.range();t+=.5){var next=start.add(direction.scale(t));if(!VehicleKinematics.loaded(level,new AABB(start,next).inflate(1)))break;end=next;}
        var wall=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        var mechanical=VehicleDamageBridge.nearest(level,actor,origin,start,end);
        var living=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,actor,start,end,new AABB(start,end).inflate(.3),entity->entity!=actor
                &&entity instanceof LivingEntity target&&target.isAlive()&&!(entity.getVehicle() instanceof MilitaryVehicle)&&entity.isPickable());
        if(mechanical!=null&&(living==null||mechanical.distance()<=start.distanceTo(living.getLocation()))){
            end=mechanical.point();VehicleDamageBridge.hurt(actor,mechanical.vehicle(),definition,definition.damage(),null);
        }else if(living!=null){
            end=living.getLocation();var target=(LivingEntity)living.getEntity();
            if(!FirearmsCombat.protectedTarget(actor,target)&&(!(actor instanceof Player p)||!(target instanceof Player q)||p.canHarmPlayer(q)&&level.getServer().isPvpAllowed()))
                if(!(target instanceof ServerPlayer defender)||!com.codex.blacklight.power.ProjectileDefenseSystem.hitscan(defender,start,end,definition.damage(),4))
                    target.hurt(actor.damageSources().mobProjectile(actor,actor),definition.damage());
        }else if(wall.getType()==HitResult.Type.BLOCK)FirearmBlockDamage.bullet(actor,definition,wall);
        for(double t=1;t<start.distanceTo(end);t+=2){var at=start.add(end.subtract(start).normalize().scale(t));level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,at.x,at.y,at.z,1,0,0,0,0);}
    }
}
