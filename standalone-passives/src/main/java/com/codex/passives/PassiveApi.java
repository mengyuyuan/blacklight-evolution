package com.codex.passives;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/** Optional integration entry point. Only the server grants or changes persistent progress. */
public final class PassiveApi {
    private PassiveApi() {}
    public static boolean owns(Player p, int flag) {
        var d = PassiveData.read(p);
        return flag == 1 ? d.adaptationTier > 0 : flag == 2 && d.regenerationTier > 0;
    }
    public static boolean grant(ServerPlayer p, int flag, int tier) {
        if (!p.isAlive() || p.isSpectator() || tier < 1 || tier > 2) return false;
        var d = PassiveData.read(p);
        if (flag == 1 && d.adaptationTier < tier) d.adaptationTier = tier;
        else if (flag == 2 && d.regenerationTier < tier) d.regenerationTier = tier;
        else if (flag == 4 && !d.vitality) d.vitality = true;
        else return false;
        d.save(p); PassiveSystem.refresh(p, d); PassiveSystem.sync(p, true); return true;
    }
    /** Called once by Blacklight after its native skill calculation, or by our standalone hook. */
    public static float damage(ServerPlayer p, DamageSource source, float damage) {
        if (!p.isAlive() || p.isSpectator() || !Float.isFinite(damage) || damage <= 0
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;
        var d = PassiveData.read(p);
        if (d.adaptationTier == 0 && d.regenerationTier == 0) return damage;
        float actual = d.adaptationTier > 0
                ? (float)(damage * ((100 - PassiveRules.mitigation(d.adaptation, p.getHealth(), p.getMaxHealth(), d.adaptationTier)) / 100.0))
                : damage;
        if (d.adaptationTier > 0) d.adaptation = PassiveRules.adaptationAfter(d.adaptation, actual, damage - (double)actual);
        if (d.regenerationTier > 0) {
            int before = d.regeneration;
            d.regeneration = PassiveRules.add(before, PassiveRules.gain(actual));
            if (d.regeneration > 0 && (before == 0 || d.nextHeal == 0)) d.nextHeal = p.level().getGameTime() + 20;
        }
        d.save(p); PassiveSystem.sync(p, false); return actual;
    }
    /** Move only potion entitlement/progress. Never purchase or modify Blacklight skills. */
    public static void migrate(ServerPlayer p, int mask, int adaptation, int regeneration) {
        var d = PassiveData.read(p);
        if ((mask & 1) != 0 && d.adaptationTier == 0) { d.adaptationTier = 1; d.adaptation = Math.max(0, adaptation); }
        if ((mask & 2) != 0 && d.regenerationTier == 0) { d.regenerationTier = 1; d.regeneration = Math.max(0, regeneration); }
        d.nextHeal = p.level().getGameTime() + 20;
        d.save(p); PassiveSystem.refresh(p, d); PassiveSystem.sync(p, true);
    }
}
