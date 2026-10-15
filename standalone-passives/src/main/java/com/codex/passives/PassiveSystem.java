package com.codex.passives;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;

public final class PassiveSystem {
    private static final Map<ServerPlayer, CompoundTag> SENT = new WeakHashMap<>();
    private static final Map<LivingEntity, PendingKill> KILLS = new WeakHashMap<>();
    private record PendingKill(ServerPlayer player, double maximum, long at) {}
    private PassiveSystem() {}
    public static void sync(ServerPlayer p, boolean force) {
        var n = PassiveData.read(p).encode();
        if (force || !n.equals(SENT.get(p))) { PassiveNetwork.send(p, n); SENT.put(p, n.copy()); }
    }
    public static void refresh(ServerPlayer p, PassiveData d) {
        if (d.adaptationTier > 0 && (!p.hasEffect(PassivesMod.ADAPTATION.get()) || p.getEffect(PassivesMod.ADAPTATION.get()).getAmplifier() != d.adaptationTier - 1))
            p.addEffect(new MobEffectInstance(PassivesMod.ADAPTATION.get(), -1, d.adaptationTier - 1, false, false, true));
        if (d.regenerationTier > 0 && (!p.hasEffect(PassivesMod.REGENERATION.get()) || p.getEffect(PassivesMod.REGENERATION.get()).getAmplifier() != d.regenerationTier - 1))
            p.addEffect(new MobEffectInstance(PassivesMod.REGENERATION.get(), -1, d.regenerationTier - 1, false, false, true));
        if (d.vitality && !p.hasEffect(PassivesMod.VITALITY.get()))
            p.addEffect(new MobEffectInstance(PassivesMod.VITALITY.get(), -1, 0, false, false, true));
    }
    public static void tick(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator()) return;
        var d = PassiveData.read(p);
        refresh(p, d); HealthGrowth.apply(p, d.bonusHealth);
        long now = p.level().getGameTime();
        if (d.regenerationTier > 0 && d.regeneration > 0) {
            if (d.nextHeal == 0 || d.nextHeal > now + 20) { d.nextHeal = now + 20; d.save(p); }
            else if (now >= d.nextHeal) {
                float before = p.getHealth(), amount = PassiveRules.healing(d.regeneration, before, p.getMaxHealth(), d.regenerationTier);
                // Tell compatible health-unit conversions that this amount is already measured in HP.
                boolean previous = p.getPersistentData().getBoolean("adaptive_passives:healing");
                p.getPersistentData().putBoolean("adaptive_passives:healing", true);
                try { if (amount > 0) p.heal(amount); }
                finally { p.getPersistentData().putBoolean("adaptive_passives:healing", previous); }
                d.regeneration = PassiveRules.afterHeal(d.regeneration, Math.max(0, p.getHealth() - before));
                d.nextHeal = now + 20; d.save(p);
            }
        }
        sync(p, false);
    }
    public static void queueDeath(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || victim instanceof ArmorStand || victim.getPersistentData().getBoolean("adaptive_passives:reward_paid")
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.getEntity() instanceof ServerPlayer p) || p == victim || !PassiveData.read(p).vitality) return;
        // Record the fatal hit's owner, never the older last-attacker fallback.
        KILLS.put(victim, new PendingKill(p, victim.getMaxHealth(), victim.level().getGameTime()));
    }
    public static void finishDeaths() {
        var it = KILLS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next(); var v = entry.getKey(); var pending = entry.getValue();
            if (v.isAlive()) { it.remove(); continue; } // Canceled death, revival or totem.
            if (v.deathTime <= 0 && !v.isRemoved()) {
                if (v.level().getGameTime() - pending.at() > 200) it.remove();
                continue;
            }
            it.remove();
            if (v.getPersistentData().getBoolean("adaptive_passives:reward_paid")) continue;
            var p = pending.player(); var d = PassiveData.read(p);
            if (!p.isAlive() || p.isRemoved() || !d.vitality) continue;
            int amount = PassiveRules.killGain(pending.maximum());
            if (amount <= 0) continue;
            v.getPersistentData().putBoolean("adaptive_passives:reward_paid", true);
            d.bonusHealth = Math.min(Float.MAX_VALUE / 32.0, d.bonusHealth + amount);
            d.save(p); HealthGrowth.apply(p, d.bonusHealth); sync(p, true);
        }
    }
    public static void clear() { KILLS.clear(); SENT.clear(); }
}
