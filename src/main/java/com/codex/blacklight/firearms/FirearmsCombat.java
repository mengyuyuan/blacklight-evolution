package com.codex.blacklight.firearms;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class FirearmsCombat {
    /** Damage policy is checked on the parent after picking: friendly bodies still stop a bullet. */
    public static boolean bulletCandidate(LivingEntity owner,Entity entity){
        if(entity==owner||entity.isRemoved()||!entity.isPickable())return false;
        if(entity instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft)return craft.health()>0;
        var target=com.codex.blacklight.combat.CombatTargeting.resolveTarget(entity);
        return target!=null&&target!=owner&&target.isAlive()&&!target.isRemoved()
                &&(entity!=target||com.codex.blacklight.combat.AnimatedHurtBody.of(target)==null
                    ||!com.codex.blacklight.combat.CombatTargeting.multipart(target));
    }
    public static EntityHitResult firstBulletBody(ServerLevel level,LivingEntity owner,Vec3 from,Vec3 to){
        var query=new AABB(from,to).inflate(.3);
        var nearest=ProjectileUtil.getEntityHitResult(level,owner,from,to,query,
                e->!(e instanceof com.codex.blacklight.entity.BlackwatchHelicopter)&&bulletCandidate(owner,e));
        double distance=nearest==null?Double.POSITIVE_INFINITY:from.distanceToSqr(nearest.getLocation());
        for(var craft:level.getEntitiesOfClass(com.codex.blacklight.entity.BlackwatchHelicopter.class,query,
                c->c!=owner.getRootVehicle()&&bulletCandidate(owner,c))){
            var point=com.codex.blacklight.military.aircraft.HelicopterSkinContact.raycast(craft,from,to);
            if(point.isPresent()&&from.distanceToSqr(point.get())<distance){
                nearest=new EntityHitResult(craft,point.get());
                distance=from.distanceToSqr(point.get());
            }
        }
        return nearest;
    }
    public static boolean holdingFirearm(LivingEntity owner){return owner!=null&&(owner.getMainHandItem().getItem() instanceof FirearmItem||owner.getOffhandItem().getItem() instanceof FirearmItem);}
    public static boolean protectedTarget(LivingEntity owner,LivingEntity target){
        return target==owner||owner!=null&&owner.isAlliedTo(target)||target instanceof Player p&&(p.isCreative()||p.isSpectator());
    }
    public static void fire(LivingEntity owner,WeaponDefinition definition,Vec3 direction,float scale){
        if(!(owner.level() instanceof ServerLevel level))return;
        if(com.codex.blacklight.military.vehicle.VehicleDamageBridge.interceptOrdinaryBullet(owner,definition,direction,scale))return;
        Vec3 start=owner.getEyePosition().add(0,-.18,0),visualEnd=start.add(direction.normalize().scale(12)),normal=direction.normalize().scale(-1);int impact=0;
        if(definition.kind()!=WeaponDefinition.Kind.BULLET){
            var projectile=new OrdnanceProjectile(level,owner,definition.kind());
            projectile.setPos(start);projectile.shoot(direction.x,direction.y,direction.z,
                    definition.kind()==WeaponDefinition.Kind.ROCKET||definition.kind()==WeaponDefinition.Kind.MISSILE?2.2F:1.15F,0);
            level.addFreshEntity(projectile);
        }else{
            Vec3 end=start.add(direction.normalize().scale(definition.range()));
            var wall=level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
            if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
            var hit=firstBulletBody(level,owner,start,end);
            if(hit!=null){
                Vec3 rayEnd=end;
                end=hit.getLocation();
                if(hit.getEntity() instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft
                        &&!com.codex.blacklight.military.vehicle.VehicleDamageBridge.protectedAircraft(owner,craft))
                    craft.hurt(owner.damageSources().mobProjectile(owner,owner),definition.damage()*scale);
                var target=com.codex.blacklight.combat.CombatTargeting.resolveTarget(hit.getEntity());
                if(target!=null&&!protectedTarget(owner,target)
                        &&(!(owner instanceof Player attacker)||!(target instanceof Player playerTarget)
                            ||level.getServer().isPvpAllowed()&&attacker.canHarmPlayer(playerTarget))){
                    float damage=definition.damage()*scale;
                    if(owner instanceof com.codex.blacklight.entity.MilitarySoldier soldier
                            &&soldier.faction()==com.codex.blacklight.military.MilitaryFaction.BLACKWATCH
                            &&target instanceof com.codex.blacklight.entity.BlacklightCommonInfected)damage*=4;
                    if(!(target instanceof net.minecraft.server.level.ServerPlayer defender)||!com.codex.blacklight.power.ProjectileDefenseSystem.hitscan(defender,start,end,damage,4)){
                        var source=owner.damageSources().mobProjectile(owner,owner);
                        var body=com.codex.blacklight.combat.AnimatedHurtBody.of(target);
                        if(body!=null){
                            var contact=com.codex.blacklight.combat.HydraHurtContact.ray(body.hurtRegions(0),start,rayEnd,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
                            if(contact!=null)body.hurtAt(source,damage,contact);
                        }else hit.getEntity().hurt(source,damage);
                    }
                }
            }
            if(hit==null&&wall.getType()==HitResult.Type.BLOCK)FirearmBlockDamage.bullet(owner,definition,wall);
            visualEnd=end;impact=hit!=null?2:wall.getType()==HitResult.Type.BLOCK?1:0;
            if(impact==1)normal=Vec3.atLowerCornerOf(wall.getDirection().getNormal());
        }
        GunplayEffects.shot(owner,definition,GunplayEffects.muzzle(owner,direction),visualEnd,impact,normal);
    }
    private FirearmsCombat(){}
}
