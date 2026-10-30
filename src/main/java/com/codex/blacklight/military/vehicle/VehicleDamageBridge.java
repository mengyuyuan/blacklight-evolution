package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.VehicleHullPart;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.firearms.WeaponDefinition;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** Mechanical candidates stay outside infection, consume and LivingEntity target identities. */
public final class VehicleDamageBridge {
    public record MechanicalHit(MilitaryVehicle vehicle,Vec3 point,double distance){}
    private VehicleDamageBridge(){}
    private static final ThreadLocal<AttackEntityEvent> ATTACK_EVENT=new ThreadLocal<>();
    public static boolean ownsAttackEvent(AttackEntityEvent event){return ATTACK_EVENT.get()==event && vehicle(event.getTarget())!=null;}
    public static MilitaryVehicle vehicle(Entity entity){return entity instanceof MilitaryVehicle v?v:entity instanceof VehicleHullPart part?part.getParent():null;}
    private static VehicleDefinition.Vec3 point(Vec3 p){return new VehicleDefinition.Vec3(p.x,p.y,p.z);}
    public static boolean valid(LivingEntity actor,ServerLevel level){return actor!=null&&actor.isAlive()&&!actor.isRemoved()&&actor.level()==level;}
    public static boolean loaded(ServerLevel level,Vec3 from,Vec3 to){return VehicleKinematics.loaded(level,new AABB(from,to).inflate(.001));}
    public static MechanicalHit nearest(ServerLevel level,Entity except,MilitaryVehicle ignored,Vec3 from,Vec3 to){
        double distance=from.distanceTo(to);if(distance<1e-6)return null;
        Vec3 direction=to.subtract(from).scale(1/distance);var seen=new HashSet<UUID>();MechanicalHit nearest=null;
        for(Entity entity:level.getEntities(except,new AABB(from,to).inflate(.3),entity->vehicle(entity)!=null)){
            var v=vehicle(entity);if(v==ignored||v.isRemoved()||v.health()<=0||!seen.add(v.getUUID()))continue;
            var hit=VehicleHullShape.from(v.definition(),v.turretYaw(),v.gunPitch()).raycast(point(v.position()),v.getYRot(),point(from),point(direction),distance);
            if(hit.isPresent()&&(nearest==null||hit.getAsDouble()<nearest.distance))nearest=new MechanicalHit(v,from.add(direction.scale(hit.getAsDouble())),hit.getAsDouble());
        }
        return nearest;
    }
    public static boolean protectedHull(LivingEntity actor,MilitaryVehicle target){
        boolean legitimateCrew=false;
        for(Entity passenger:target.getPassengers())if(passenger instanceof LivingEntity living){
            if(FirearmsCombat.protectedTarget(actor,living))return true;
            if(actor instanceof Player player&&living instanceof Player other&&(!player.canHarmPlayer(other)
                    ||target.level() instanceof ServerLevel level&&!level.getServer().isPvpAllowed()))return true;
            if(actor instanceof MilitarySoldier soldier&&soldier.validTarget(living)
                    &&(com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(living)||soldier.getTarget()==living))legitimateCrew=true;
        }
        // A troop does not acquire an empty or allied native chassis as a new proactive enemy.
        return actor instanceof MilitarySoldier&&!legitimateCrew;
    }
    private static boolean permitted(LivingEntity actor,MilitaryVehicle target,float damage){
        if(!(target.level() instanceof ServerLevel level)||!valid(actor,level)||target.isRemoved()||!Float.isFinite(damage)||damage<=0||protectedHull(actor,target))return false;
        if(actor instanceof ServerPlayer player){
            if(player.isSpectator()||!level.mayInteract(player,target.blockPosition()))return false;
            var event=new AttackEntityEvent(player,target);var previous=ATTACK_EVENT.get();ATTACK_EVENT.set(event);
            try{NeoForge.EVENT_BUS.post(event);}finally{if(previous==null)ATTACK_EVENT.remove();else ATTACK_EVENT.set(previous);}
            if(event.isCanceled())return false;
        }
        return true;
    }
    /** Heavy contact has its own explicit gameplay armor balance, never a fabricated firearm identity. */
    public static boolean hurtPhysical(LivingEntity actor,MilitaryVehicle target,float damage,Entity projectile){
        if(projectile!=null&&(projectile.isRemoved()||projectile.level()!=target.level()))return false;
        if(!permitted(actor,target,damage))return false;
        float coefficient=switch(target.definition().armor()){case LIGHT->1F;case ARMORED->.75F;case HEAVY->.5F;};
        var source=projectile!=null?actor.damageSources().thrown(projectile,actor)
                :actor instanceof Player player?actor.damageSources().playerAttack(player):actor.damageSources().mobAttack(actor);
        return target.hurt(source,damage*coefficient);
    }
    public static boolean hurt(LivingEntity actor,MilitaryVehicle target,WeaponDefinition definition,float damage,Entity projectile){
        if(!permitted(actor,target,damage))return false;
        VehicleWeaponDefinition specific=VehicleWeaponDefinition.ALL.stream().filter(value->value.weapon().id().equals(definition.id())).findFirst().orElse(null);
        float amount;
        if(specific!=null)amount=specific.mechanicalDamage(damage,target.definition().armor());
        else {
            boolean bullet=definition.kind()==WeaponDefinition.Kind.BULLET;var armor=target.definition().armor();
            amount=damage*(armor==VehicleDefinition.Armor.LIGHT?1:bullet?(armor==VehicleDefinition.Armor.ARMORED?.25F:.10F):(armor==VehicleDefinition.Armor.ARMORED?.75F:.50F));
        }
        return target.hurt(actor.damageSources().mobProjectile(projectile==null?actor:projectile,actor),amount);
    }
    public static int blast(ServerLevel level,LivingEntity actor,WeaponDefinition definition,Vec3 center,Entity projectile,MilitaryVehicle direct){
        if(!valid(actor,level)||definition.radius()<=0)return 0;
        double radius=Math.min(4,definition.radius());var seen=new HashSet<UUID>();int hits=0;
        for(Entity entity:level.getEntities(projectile,new AABB(center,center).inflate(radius),entity->vehicle(entity)!=null)){
            var target=vehicle(entity);if(target==direct||target.isRemoved()||!seen.add(target.getUUID()))continue;
            var closest=VehicleHullShape.from(target.definition(),target.turretYaw(),target.gunPitch()).closestPoint(point(target.position()),target.getYRot(),point(center));
            var surface=new Vec3(closest.x(),closest.y(),closest.z());
            double distance=center.distanceTo(surface);if(distance>radius||!loaded(level,center,surface))continue;
            var wall=level.clip(new ClipContext(center,surface,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,projectile));
            if(wall.getType()!=HitResult.Type.MISS)continue;
            if(hurt(actor,target,definition,definition.damage()*(float)Math.max(.2,1-distance/radius),projectile))hits++;
            if(seen.size()>=64)break;
        }
        return hits;
    }
    /** Adds a nearer real mechanical hull to the existing ordinary bullet path without replacing living-target policy. */
    public static boolean interceptOrdinaryBullet(LivingEntity actor,WeaponDefinition definition,Vec3 direction,float scale){
        if(definition.kind()!=WeaponDefinition.Kind.BULLET||!(actor.level() instanceof ServerLevel level)||!valid(actor,level)
                ||!Double.isFinite(direction.lengthSqr())||direction.lengthSqr()<1e-8||!Float.isFinite(scale)||scale<=0)return false;
        Vec3 from=actor.getEyePosition().add(0,-.18,0),to=from.add(direction.normalize().scale(definition.range()));
        var ignored=vehicle(actor.getVehicle());var hit=nearest(level,actor,ignored,from,to);if(hit==null||!loaded(level,from,hit.point))return false;
        var living=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,actor,from,hit.point,new AABB(from,hit.point).inflate(.3),entity->entity!=actor&&entity instanceof LivingEntity target&&target.isAlive()&&entity.isPickable());
        if(living!=null&&living.getLocation().distanceTo(from)<hit.distance-.001)return false;
        if(level.clip(new ClipContext(from,hit.point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor)).getType()!=HitResult.Type.MISS)return false;
        hurt(actor,hit.vehicle,definition,definition.damage()*scale,null);
          com.codex.blacklight.firearms.GunplayEffects.shot(actor,definition,com.codex.blacklight.firearms.GunplayEffects.muzzle(actor,direction),hit.point,3,direction.normalize().scale(-1));
        return true;
    }
}
