package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid="blacklight")
public final class AdaptivePassiveSystem {
    private AdaptivePassiveSystem() {}
    public static AdaptivePassiveState state(Player p) { return p.getData(ModAttachments.ADAPTIVE_PASSIVES); }
    public static boolean active(Player p, int flag) {
        if (IndependentPassives.owns(p, flag)) return false;
        var powers = p.getData(ModAttachments.PLAYER_STATE);
        var skill=flag==AdaptivePassiveState.ADAPTATION?CombatSkill.ADAPTATION:CombatSkill.TISSUE_REGENERATION;
        return powers.isInfected()&&powers.isPrototypeMode()&&powers.hasCombatSkill(skill)&&EvolutionProgression.mayActivate(p,skill);
    }
    public static boolean percentBased(Player p) {
        return active(p, AdaptivePassiveState.ADAPTATION) && p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.ADAPTATION_PERCENT)
                &&EvolutionProgression.mayActivate(p,CombatSkill.ADAPTATION_PERCENT);
    }
    private static void publish(ServerPlayer p, AdaptivePassiveState data) {
        if (data.equals(state(p))) return;
        p.setData(ModAttachments.ADAPTIVE_PASSIVES, data);
    }
    public static boolean grant(ServerPlayer p, int flag) {
        return IndependentPassives.grant(p, flag);
    }
    /** Called exactly once after armor, scale conversion, absorption and critical reserve, before HP loss. */
    public static float finalDamage(LivingEntity entity, DamageSource source, float damage) {
        if (!(entity instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()
                || !Float.isFinite(damage) || damage <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
        boolean adaptive = active(p, AdaptivePassiveState.ADAPTATION), regen = active(p, AdaptivePassiveState.REGENERATION);
        if (!adaptive && !regen) return IndependentPassives.damage(p, source, damage);
        var before = state(p);
        float actual = adaptive&&!com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(source) ? (float)(damage * AdaptivePassiveRules.remainingFraction(before.adaptation(),
                p.getHealth(), p.getMaxHealth(), percentBased(p))) : damage;
        int layers = adaptive ? AdaptivePassiveRules.adaptationAfterHit(before.adaptation(), actual, damage - (double)actual) : before.adaptation();
        int tissue = regen ? AdaptivePassiveRules.regenerationAfterHit(before.regeneration(), damage) : before.regeneration();
        long next = before.nextHealAt();
        if (regen && tissue > 0 && (before.regeneration() == 0 || next == 0)) next = p.level().getGameTime() + 20;
        publish(p, new AdaptivePassiveState(before.permanentMask(), layers, tissue, next));
        return IndependentPassives.damage(p, source, actual);
    }
    private static void refreshEffects(ServerPlayer p) {
        if (active(p, AdaptivePassiveState.ADAPTATION)) {
            if (!p.hasEffect(ModEffects.ADAPTATION)) p.addEffect(new MobEffectInstance(ModEffects.ADAPTATION, -1, 0, false, false, true));
        } else p.removeEffect(ModEffects.ADAPTATION);
        if (active(p, AdaptivePassiveState.REGENERATION)) {
            if (!p.hasEffect(ModEffects.TISSUE_REGENERATION)) p.addEffect(new MobEffectInstance(ModEffects.TISSUE_REGENERATION, -1, 0, false, false, true));
        } else p.removeEffect(ModEffects.TISSUE_REGENERATION);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) return;
        var legacy = state(p);
        if (IndependentPassives.migrate(p, legacy))
            publish(p, new AdaptivePassiveState(0, legacy.adaptation(), legacy.regeneration(), legacy.nextHealAt()));
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
