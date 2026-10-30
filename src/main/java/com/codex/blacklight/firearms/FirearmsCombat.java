package com.codex.blacklight.firearms;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class FirearmsCombat {
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
            var hit=ProjectileUtil.getEntityHitResult(level,owner,start,end,new AABB(start,end).inflate(.3),e->e!=owner&&e instanceof LivingEntity living&&living.isAlive()&&e.isPickable());
            if(hit!=null){
                end=hit.getLocation();
                if(hit.getEntity() instanceof LivingEntity target&&!protectedTarget(owner,target)){
                    float damage=definition.damage()*scale;
                    if(owner instanceof com.codex.blacklight.entity.MilitarySoldier soldier
                            &&soldier.faction()==com.codex.blacklight.military.MilitaryFaction.BLACKWATCH
                            &&target instanceof com.codex.blacklight.entity.BlacklightCommonInfected)damage*=4;
                    if(!(target instanceof net.minecraft.server.level.ServerPlayer defender)||!com.codex.blacklight.power.ProjectileDefenseSystem.hitscan(defender,start,end,damage,4))
                        target.hurt(owner.damageSources().mobProjectile(owner,owner),damage);
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
