package com.codex.blacklight.entity;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class BlacklightSpit extends ThrowableItemProjectile {
    private Vec3 damageContact;
    public Vec3 damageContact(){return damageContact;}
    public BlacklightSpit(EntityType<? extends BlacklightSpit> type,Level level){super(type,level);}
    public BlacklightSpit(Level level,LivingEntity owner){super(com.codex.blacklight.registry.ModEntities.BLACKLIGHT_SPIT.get(),owner,level);}
    @Override protected Item getDefaultItem(){return Items.ROTTEN_FLESH;}
    @Override protected void onHitEntity(EntityHitResult hit){
        super.onHitEntity(hit);if(level().isClientSide||hit.getEntity()==getOwner())return;
        if(hit.getEntity() instanceof LivingEntity target
                &&(getOwner() instanceof BlacklightMutant mutant?mutant.validTarget(target):getOwner()==null||!getOwner().isAlliedTo(target))){
            if(target.hurt(damageSources().thrown(this,getOwner()),4))target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,45,0));
        }
    }
    /** Also used when the animated nozzle itself is obstructed at release. */
    public static void splash(Entity source,Vec3 at){
        if(!(source.level() instanceof net.minecraft.server.level.ServerLevel server))return;
        server.sendParticles(com.codex.blacklight.registry.ModParticles.IMPACT_SMOKE.get(),at.x,at.y,at.z,3,.15,.15,.15,.025);
        server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.32F,.025F,.05F),.8F),at.x,at.y,at.z,6,.18,.18,.18,.025);
        com.codex.blacklight.registry.ModInfectedSounds.play(source,com.codex.blacklight.infection.InfectedSoundCue.BURST,at);
    }
    @Override protected void onHit(HitResult hit){
        if(isRemoved())return;
        // Throwable collision may supply only an entity's feet as its HitResult.
        // Recover the intersection from this tick's actual flight segment.
        Vec3 at=hit.getLocation();
        if(hit instanceof EntityHitResult entity){var box=entity.getEntity().getBoundingBox();at=box.clip(position(),position().add(getDeltaMovement())).orElseGet(()->com.codex.blacklight.combat.HunterContactPose.nearest(box,position()));}
        damageContact=at;
        try{super.onHit(hit);if(!level().isClientSide){splash(this,at);discard();}}finally{damageContact=null;}
    }
    @Override public void tick(){super.tick();if(!level().isClientSide&&tickCount>80)discard();}
}
