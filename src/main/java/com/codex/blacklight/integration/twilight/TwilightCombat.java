package com.codex.blacklight.integration.twilight;

import com.codex.blacklight.combat.CrossModCombat;
import com.codex.blacklight.config.CrossModCombatConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Scoped to the optional Hydra/Lich bridges; never grants global invulnerability bypass. */
public final class TwilightCombat {
    private TwilightCombat() {}

    public static boolean applies(LivingEntity target, DamageSource source, float amount) {
        return !target.level().isClientSide && target.isAlive() && !target.isRemoved()
                && !target.isInvulnerable() && Float.isFinite(amount) && amount > 0
                && BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace().equals("twilightforest")
                && CrossModCombatConfig.OFFENSE.get() && CrossModCombatConfig.TRUE_DAMAGE.get()
                && CrossModCombat.nativeAttack(source);
    }

    public record Hit(DamageSource source, float amount) {}

    public static Hit prepare(LivingEntity target, DamageSource source, float amount) {
        // Mark the hit before entering the boss so LivingEntity cannot amplify it a second time.
        float adjusted = CrossModCombat.enhanced(target, source) ? CrossModCombat.offensiveAmount(amount) : amount;
        return new Hit(new MechanismSource(source), adjusted);
    }

    private static final class MechanismSource extends CrossModCombat.NativeAttackSource {
        private MechanismSource(DamageSource source) { super(source, true); }
        @Override public boolean is(TagKey<DamageType> tag) {
            return tag.equals(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.is(tag);
        }
    }
}
