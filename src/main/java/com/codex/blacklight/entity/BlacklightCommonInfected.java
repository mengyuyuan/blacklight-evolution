package com.codex.blacklight.entity;

import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** The replenishable lower trophic tier. Meals, rather than mere age, unlock mutation. */
public final class BlacklightCommonInfected extends Zombie {
    private int meals;
    public BlacklightCommonInfected(EntityType<? extends Zombie> type, Level level) { super(type, level); xpReward=3; }
    public static AttributeSupplier.Builder attributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH,12).add(Attributes.ATTACK_DAMAGE,2)
                .add(Attributes.ARMOR,0).add(Attributes.MOVEMENT_SPEED,.21).add(Attributes.FOLLOW_RANGE,32);
    }
    @Override protected boolean isSunSensitive() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean accepted=super.killedEntity(level,victim);
        if(accepted&&!victim.isAlive()&&victim!=this&&victim.getLastHurtByMob()==this
                &&!com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(victim))meals=Math.min(3,meals+1);
        return accepted;
    }
    @Override public void tick() {
        super.tick();
        if(!(level() instanceof ServerLevel level)||!isAlive()||meals<3||tickCount%100!=0
                ||getData(com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL).active()
                ||com.codex.blacklight.combat.ImpactControl.stunned(this))return;
        // Retain a normal zombie source rig for all existing evolution/organ code.
        var donor=EntityType.ZOMBIE.create(level);var host=ModEntities.INFECTED_HOST.get().create(level);
        if(donor==null||host==null)return;
        host.copyPosition(this);host.inherit(donor);host.advanceTissue(6000);
        if(!level.noCollision(host)||!level.getWorldBorder().isWithinBounds(host.getBoundingBox()))return;
        host.setHealth(host.getMaxHealth()*getHealth()/getMaxHealth());
        host.setCustomName(getCustomName());host.setCustomNameVisible(isCustomNameVisible());
        getTags().forEach(host::addTag);
        if(getPersistentData().getBoolean("blacklight:outbreak_spawn"))host.getPersistentData().putBoolean("blacklight:outbreak_spawn",true);
        if(!level.addFreshEntity(host))return;
        // Promotion is a conversion, never a death or a second loot payout.
        for(var slot:EquipmentSlot.values())host.setItemSlot(slot,getItemBySlot(slot).copyAndClear());
        discard();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag);tag.putInt("BlacklightMeals",meals); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag);meals=Math.clamp(tag.getInt("BlacklightMeals"),0,3); }
}
