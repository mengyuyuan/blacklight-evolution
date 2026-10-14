package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

@EventBusSubscriber(modid="blacklight")
public final class AdaptivePassiveSystem {
    private AdaptivePassiveSystem() {}
    public static AdaptivePassiveState state(Player p) { return ModAttachments.getData(p, ModAttachments.ADAPTIVE_PASSIVES); }
    public static boolean active(Player p, int flag) {
        var data = state(p);
        if (data.permanent(flag)) return true;
        var powers = ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return powers.isInfected() && powers.isPrototypeMode() && powers.hasCombatSkill(
                flag == AdaptivePassiveState.ADAPTATION ? CombatSkill.ADAPTATION : CombatSkill.TISSUE_REGENERATION);
    }
    public static boolean percentBased(Player p) {
        var powers = ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return powers.isInfected() && powers.isPrototypeMode()
                && powers.hasCombatSkill(CombatSkill.ADAPTATION_PERCENT);
    }
    private static void publish(ServerPlayer p, AdaptivePassiveState data) {
        if (data.equals(state(p))) return;
        ModAttachments.setData(p, ModAttachments.ADAPTIVE_PASSIVES, data);
    }
    public static boolean grant(ServerPlayer p, int flag) {
        if ((flag != AdaptivePassiveState.ADAPTATION && flag != AdaptivePassiveState.REGENERATION)
                || !p.isAlive() || p.isSpectator() || state(p).permanent(flag)) return false;
        publish(p, state(p).grant(flag));
        refreshEffects(p);
        return true;
    }
    /** Called exactly once after armor, scale conversion, absorption and critical reserve, before HP loss. */
    public static float finalDamage(LivingEntity entity, DamageSource source, float damage) {
        if (!(entity instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()
                || !Float.isFinite(damage) || damage <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
        boolean adaptive = active(p, AdaptivePassiveState.ADAPTATION), regen = active(p, AdaptivePassiveState.REGENERATION);
        if (!adaptive && !regen) return damage;
        var before = state(p);
        float actual = adaptive ? (float)(damage * AdaptivePassiveRules.remainingFraction(before.adaptation(),
                p.getHealth(), p.getMaxHealth(), percentBased(p))) : damage;
        int layers = adaptive ? AdaptivePassiveRules.adaptationAfterHit(before.adaptation(), actual, damage - (double)actual) : before.adaptation();
        int tissue = regen ? AdaptivePassiveRules.regenerationAfterHit(before.regeneration(), damage) : before.regeneration();
        long next = before.nextHealAt();
        if (regen && tissue > 0 && (before.regeneration() == 0 || next == 0)) next = p.level().getGameTime() + 20;
        publish(p, new AdaptivePassiveState(before.permanentMask(), layers, tissue, next));
        return actual;
    }
    private static void refreshEffects(ServerPlayer p) {
        if (active(p, AdaptivePassiveState.ADAPTATION)) {
            if (!p.hasEffect(ModEffects.ADAPTATION.get())) p.addEffect(new MobEffectInstance(ModEffects.ADAPTATION.get(), -1, 0, false, false, true));
        } else p.removeEffect(ModEffects.ADAPTATION.get());
        if (active(p, AdaptivePassiveState.REGENERATION)) {
            if (!p.hasEffect(ModEffects.TISSUE_REGENERATION.get())) p.addEffect(new MobEffectInstance(ModEffects.TISSUE_REGENERATION.get(), -1, 0, false, false, true));
        } else p.removeEffect(ModEffects.TISSUE_REGENERATION.get());
    }
    @SubscribeEvent public static void tick(PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || !ModAttachments.available(event.player)) return;
        if (!(event.player instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) return;
        refreshEffects(p);
        var before = state(p);
        if (!active(p, AdaptivePassiveState.REGENERATION) || before.regeneration() <= 0) return;
        long now = p.level().getGameTime();
        // No offline catch-up burst; tolerate clocks changed by world transfer / restoring a save.
        if (before.nextHealAt() == 0 || before.nextHealAt() > now + 20) {
            publish(p, new AdaptivePassiveState(before.permanentMask(), before.adaptation(), before.regeneration(), now + 20)); return;
        }
        if (now < before.nextHealAt()) return;
        int count = before.regeneration();
        publish(p, new AdaptivePassiveState(before.permanentMask(), before.adaptation(), AdaptivePassiveRules.regenerationAfterPulse(count), now + 20));
        // CombatScale's heal listener converts units once. Do not turn 20 HP into 100 HP in Prototype mode.
        p.heal(count / CombatScale.units(p));
    }
}
