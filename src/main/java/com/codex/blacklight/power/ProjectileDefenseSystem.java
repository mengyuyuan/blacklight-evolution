package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/** Server-authoritative physical deflection; projectile impact is canceled only after a successful block. */
public final class ProjectileDefenseSystem {
    private static final String AMPLIFY="blacklight_reflected_power", BOUNCES="blacklight_reflections";
    private ProjectileDefenseSystem() {}
    public static long level(Player p){return Math.max(0,ModAttachments.getData(p,ModAttachments.RICOCHET_LEVEL));}
    public static boolean unlocked(Player p){var s=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);return s.isInfected()&&(s.hasUnlocked(PowerForm.ARMOR)||s.hasUnlocked(PowerForm.SHIELD));}
    public static void purchase(ServerPlayer p){
        long level=level(p);int cost=RicochetRules.cost(level);var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        if(!p.isAlive()||p.isSpectator()||!unlocked(p)||level==Long.MAX_VALUE||state.getEvolutionPoints()<cost)return;
        state.addEvolutionPoints(-cost);ModAttachments.syncData(p,ModAttachments.PLAYER_STATE);
        ModAttachments.setData(p,ModAttachments.RICOCHET_LEVEL,level+1);
        p.displayClientMessage(Component.translatable("message.blacklight.ricochet_upgrade",level+1),true);
        p.level().playSound(null,p.blockPosition(),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.PLAYERS,.65F,1.05F);
    }
    private static boolean shield(Player p,Vec3 incoming){return UtilityPowerSystem.shieldReady(p)
            &&!ModAttachments.getData(p,ModAttachments.CONSUMING)&&UtilityPowerSystem.shieldFaces(p,incoming.scale(-1));}
    private static double estimate(Projectile projectile,double speed){
        if(projectile instanceof ThrownTrident)return 8;
        if(projectile instanceof AbstractArrow arrow)return Math.max(.1,arrow.getBaseDamage()*speed);
        if(projectile instanceof WitherSkull)return 8;
        if(projectile instanceof LargeFireball)return 12;
        if(projectile instanceof AbstractHurtingProjectile)return 6;
        if(projectile instanceof ThrownPotion)return 6;
        if(projectile instanceof Snowball||projectile instanceof ThrownEgg)return .5;
        return 4;
    }
    @SubscribeEvent public static void impact(ProjectileImpactEvent event){
        var projectile=event.getProjectile();
        if(event.isCanceled()||projectile.level().isClientSide||projectile.isRemoved()
                ||!(event.getRayTraceResult() instanceof EntityHitResult hit)||!(hit.getEntity() instanceof ServerPlayer p)
                ||p.isSpectator()||projectile.getOwner()==p||projectile instanceof ThrownEnderpearl
                ||projectile instanceof ThrownExperienceBottle||projectile instanceof FishingHook)return;
        Vec3 velocity=projectile.getDeltaMovement();double speed=velocity.length();
        if(!Double.isFinite(speed)||speed<.01)return;
        Vec3 incoming=velocity.normalize();boolean shield=shield(p,incoming);
        if(!shield&&!UtilityPowerSystem.active(p,PowerForm.ARMOR))return;
        double previous=projectile.getPersistentData().getDouble(AMPLIFY);
        double damage=estimate(projectile,speed)*(Double.isFinite(previous)&&previous>0?previous:1);
        if(!Double.isFinite(damage)||damage<=0)return;
        double power=RicochetRules.power(damage,speed),capacity=RicochetRules.capacity(level(p),shield);
        Vec3 point=hit.getLocation();
        boolean canReflect=Double.isFinite(power)&&power<=capacity&&projectile.getPersistentData().getInt(BOUNCES)<8;
        if(shield&&RicochetRules.shieldCost(damage,CombatScale.units(p),level(p))>ModAttachments.getData(p,ModAttachments.UTILITY_STATE).shield())canReflect=false;
        Vec3 normal=shield?UtilityPowerSystem.shieldForward(p):point.subtract(p.getBoundingBox().getCenter()).normalize();
        if(normal.lengthSqr()<.1||incoming.dot(normal)>-.08)normal=incoming.scale(-1);
        Vec3 outgoing=incoming.subtract(normal.scale(2*incoming.dot(normal))).normalize();
        feedback(p,point,incoming,outgoing,power,capacity,speed,shield,canReflect);
        if(!canReflect)return; // Existing shield overflow / armor resistance processes the hit normally.
        if(shield)UtilityPowerSystem.spendShield(p,RicochetRules.shieldCost(damage,CombatScale.units(p),level(p)));
        event.setCanceled(true);
        Vec3 reflected=outgoing.scale(Math.max(.15,Math.min(4,speed*(.72+.18*Math.min(1,capacity/Math.max(1,power))))));
        // Keep the original trident owner so Loyalty cannot transfer someone else's item.
        if(!(projectile instanceof ThrownTrident))projectile.setOwner(p);
        projectile.setDeltaMovement(reflected);
        projectile.setPos(point.x+normal.x*.2,point.y+normal.y*.2,point.z+normal.z*.2);
        projectile.setYRot((float)Math.toDegrees(Math.atan2(reflected.x,reflected.z)));
        projectile.setXRot((float)Math.toDegrees(Math.atan2(reflected.y,reflected.horizontalDistance())));
        projectile.yRotO=projectile.getYRot();projectile.xRotO=projectile.getXRot();
        projectile.hasImpulse=true;projectile.hurtMarked=true;
        projectile.getPersistentData().putDouble(AMPLIFY,RicochetRules.multiplier(level(p)));
        projectile.getPersistentData().putInt(BOUNCES,projectile.getPersistentData().getInt(BOUNCES)+1);
        if(projectile instanceof AbstractHurtingProjectile fireball){
            fireball.xPower=outgoing.x*.1;fireball.yPower=outgoing.y*.1;fireball.zPower=outgoing.z*.1;
        }
        if(projectile instanceof ShulkerBullet bullet){
            var access=(com.codex.blacklight.mixin.ShulkerReflectionAccess)bullet;
            access.blacklight$clearTarget(null);access.blacklight$clearTargetId(null);
        }
        p.serverLevel().getChunkSource().broadcastAndSend(projectile,new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(projectile));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void reflectedDamage(LivingHurtEvent event){
        if(event.isCanceled()||!(event.getSource().getDirectEntity() instanceof Projectile projectile))return;
        double factor=projectile.getPersistentData().getDouble(AMPLIFY);
        if(Double.isFinite(factor)&&factor>1&&Float.isFinite(event.getAmount())&&event.getAmount()>0)
            event.setAmount((float)Math.min(Float.MAX_VALUE/16D,event.getAmount()*factor));
    }
    /** Blackwatch rifles are hitscan, so their existing ray calls the same defense policy explicitly. */
    public static boolean hitscan(ServerPlayer p,Vec3 origin,Vec3 point,float damage,double speed){
        Vec3 incoming=point.subtract(origin).normalize();boolean shield=shield(p,incoming);
        if(!shield&&!UtilityPowerSystem.active(p,PowerForm.ARMOR))return false;
        double power=RicochetRules.power(damage,speed),capacity=RicochetRules.capacity(level(p),shield);
        boolean accepted=power<=capacity&&(!shield||RicochetRules.shieldCost(damage,CombatScale.units(p),level(p))<=ModAttachments.getData(p,ModAttachments.UTILITY_STATE).shield());
        Vec3 normal=shield?UtilityPowerSystem.shieldForward(p):incoming.scale(-1);
        Vec3 outgoing=incoming.subtract(normal.scale(2*incoming.dot(normal))).normalize();
        feedback(p,point,incoming,outgoing,power,capacity,speed,shield,accepted);
        if(!accepted)return false;
        if(shield)UtilityPowerSystem.spendShield(p,RicochetRules.shieldCost(damage,CombatScale.units(p),level(p)));
        Vec3 start=point.add(outgoing.scale(.2)),end=start.add(outgoing.scale(28));
        var wall=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        var hit=ProjectileUtil.getEntityHitResult(p.level(),p,start,end,new AABB(start,end).inflate(.2),e->e!=p&&e instanceof LivingEntity living&&living.isAlive()&&e.isPickable()&&!p.isAlliedTo(e));
        if(hit!=null){end=hit.getLocation();if(!(hit.getEntity() instanceof Player other)||p.canHarmPlayer(other))
            hit.getEntity().hurt(p.damageSources().mobProjectile(p,p),(float)Math.min(Float.MAX_VALUE/16D,damage*RicochetRules.multiplier(level(p))));}
        for(double d=0;d<start.distanceTo(end);d+=1){var q=start.add(outgoing.scale(d));p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,q.x,q.y,q.z,1,0,0,0,0);}
        return true;
    }
    private static void feedback(ServerPlayer p,Vec3 point,Vec3 incoming,Vec3 outgoing,double power,double capacity,double speed,boolean shield,boolean reflected){
        float strength=RicochetRules.strength(power,capacity);long now=p.level().getGameTime();
        ModAttachments.setData(p,ModAttachments.DEFENSE_IMPACT,new DefenseImpact(now,strength,(float)incoming.x,(float)incoming.y,(float)incoming.z,shield));
        // Small additive impulse, never a stun or a replacement for the current action/movement.
        if(!ModAttachments.getData(p,ModAttachments.CONSUMING)){
            double push=Math.min(.12,.025*strength)*(shield?1:.55);
            p.push(incoming.x*push,0,incoming.z*push);p.hurtMarked=true;
        }
        int count=(int)Math.min(24,4+strength*7+Math.min(speed,6)*1.5);
        for(int i=0;i<count;i++){
            var r=p.getRandom();Vec3 scatter=outgoing.scale(.12+.08*Math.min(speed,5)).add((r.nextDouble()-.5)*.22,(r.nextDouble()-.3)*.2,(r.nextDouble()-.5)*.22);
            p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,point.x,point.y,point.z,0,scatter.x,scatter.y,scatter.z,1);
        }
        if(strength>.85)p.serverLevel().sendParticles(ParticleTypes.FLASH,point.x,point.y,point.z,1,0,0,0,0);
        p.level().playSound(null,point.x,point.y,point.z,SoundEvents.ANVIL_HIT,SoundSource.PLAYERS,Math.min(1,.3F+strength*.35F),Math.max(.65F,1.8F-strength*.55F));
    }
}
