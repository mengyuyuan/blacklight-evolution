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
        Vec3 start=owner.getEyePosition().add(0,-.18,0);
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
                    if(!(target instanceof net.minecraft.server.level.ServerPlayer defender)||!com.codex.blacklight.power.ProjectileDefenseSystem.hitscan(defender,start,end,damage,4))
                        target.hurt(owner.damageSources().mobProjectile(owner,owner),damage);
                }
            }
            if(hit==null&&wall.getType()==HitResult.Type.BLOCK)FirearmBlockDamage.bullet(owner,definition,wall);
            Vec3 delta=end.subtract(start).normalize();
            // Presentation starts beyond the local camera; the hitscan above keeps its original origin.
            for(double t=2.5;t<start.distanceTo(end);t+=2.5){var point=start.add(delta.scale(t));level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,point.x,point.y,point.z,1,0,0,0,0);}
        }
        Vec3 muzzle=start.add(direction.normalize().scale(.95));
        var muzzleWall=level.clip(new ClipContext(start,muzzle,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        if(muzzleWall.getType()==HitResult.Type.MISS)
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,muzzle.x,muzzle.y,muzzle.z,1,.012,.012,.012,0);
        var soundSource=owner instanceof Player?net.minecraft.sounds.SoundSource.PLAYERS:net.minecraft.sounds.SoundSource.HOSTILE;
        boolean bullet=definition.kind()==WeaponDefinition.Kind.BULLET;
        level.playSound(null,owner.blockPosition(),bullet?net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT:net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH,soundSource,.65F,definition.capacity()>30?.7F:.95F);
        if(bullet)level.playSound(null,owner.blockPosition(),net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,soundSource,.22F,1.5F);
    }
    private FirearmsCombat(){}
}
