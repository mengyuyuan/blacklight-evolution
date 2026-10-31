package com.codex.blacklight.entity;

import com.codex.blacklight.infection.DevourerFeedingSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Temporary, damageable knots in the feeding cable. Never saved, consumed or rewarded. */
public final class HiveTendril extends Mob {
    private BlacklightInfectedHost owner;
    public HiveTendril(EntityType<? extends HiveTendril> type, Level level) {
        super(type, level); setNoAi(true); setNoGravity(true); noPhysics=true; xpReward=0;
    }
    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.KNOCKBACK_RESISTANCE,1);
    }
    public void bind(BlacklightInfectedHost host,Vec3 start,Vec3 end,double t) {owner=host;follow(start,end,t);}
    public void follow(Vec3 start,Vec3 end,double t) {
        Vec3 at=start.lerp(end,t);setPos(at.x,at.y-getBbHeight()*.5,at.z);setDeltaMovement(Vec3.ZERO);
    }
    @Override public boolean isPushable(){return false;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide&&(owner==null||!owner.isAlive()||!DevourerFeedingSystem.active(owner)))discard();
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide||owner==null||amount<=0||isInvulnerableTo(source)||source.getEntity()==owner)return false;
        // A small nonlethal hit goes through normal hurt/cancellation hooks. The part
        // then detaches rather than dying, so it cannot farm XP/kill progression.
        boolean accepted=super.hurt(source,Math.min(amount,.25F));
        if(accepted&&isAlive())DevourerFeedingSystem.sever(owner);
        return accepted;
    }
    @Override public void die(DamageSource source) {
        if(owner!=null&&!level().isClientSide)DevourerFeedingSystem.sever(owner);
        discard();
    }
}
