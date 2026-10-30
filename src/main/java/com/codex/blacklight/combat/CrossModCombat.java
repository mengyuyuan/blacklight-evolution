package com.codex.blacklight.combat;

import com.codex.blacklight.config.CrossModCombatConfig;
import com.codex.blacklight.power.PrototypeMetabolism;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Registry-based interoperability; no external-mod classes are required. */
public final class CrossModCombat {
    private CrossModCombat() {}
    private static final String START = "blacklight:external_damage_start", SPENT = "blacklight:external_damage_spent";

    private static String namespace(Entity entity) {
        return entity == null ? "minecraft" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
    }
    private static String namespace(ItemStack stack) {
        return stack.isEmpty() ? "minecraft" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }
    public static boolean administrative(DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
    public static boolean nativeAttack(DamageSource source) {
        if (administrative(source)) return false;
        if (source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals("blacklight")).orElse(false)
                || namespace(source.getDirectEntity()).equals("blacklight")) return true;
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                || !source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypeTags.IS_PROJECTILE)) return false;
        String weapon = namespace(attacker.getMainHandItem());
        return weapon.equals("blacklight") || attacker instanceof Player p && PrototypeMetabolism.active(p)
                && !CrossModDamageRules.externalNamespace(weapon);
    }
    public static boolean externalAttack(DamageSource source) {
        if (administrative(source) || nativeAttack(source)) return false;
        if (source.typeHolder().unwrapKey().map(k -> CrossModDamageRules.externalNamespace(k.location().getNamespace())).orElse(false)
                || CrossModDamageRules.externalNamespace(namespace(source.getDirectEntity()))
                || CrossModDamageRules.externalNamespace(namespace(source.getEntity()))) return true;
        // Melee tools commonly reuse minecraft:player_attack instead of registering a damage type.
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && CrossModDamageRules.externalNamespace(namespace(attacker.getMainHandItem()));
    }
    public static boolean enhanced(LivingEntity victim, DamageSource source) {
        return !(source instanceof NativeAttackSource) && !victim.level().isClientSide && !(victim instanceof Player)
                && CrossModCombatConfig.OFFENSE.get() && nativeAttack(source)
                && CrossModDamageRules.externalNamespace(namespace(victim));
    }
    public static boolean bypassTitanLimit(DamageSource source) {
        return DevastatorDamage.isTrueDamage(source) && source.getEntity() instanceof Player p && PrototypeMetabolism.active(p)
                || CrossModCombatConfig.OFFENSE.get() && CrossModCombatConfig.TRUE_DAMAGE.get() && nativeAttack(source);
    }
    public static DamageSource offensiveSource(DamageSource source) {
        return source instanceof NativeAttackSource ? source : new NativeAttackSource(source, CrossModCombatConfig.TRUE_DAMAGE.get());
    }
    public static float offensiveAmount(float amount) {
        return CrossModDamageRules.multiply(amount, CrossModCombatConfig.OUTGOING.get());
    }
    public static float defend(LivingEntity victim, DamageSource source, float damage) {
        if (victim.level().isClientSide || damage <= 0 || !CrossModCombatConfig.DEFENSE.get()
                || !(namespace(victim).equals("blacklight") || victim instanceof Player p && PrototypeMetabolism.active(p))
                || !externalAttack(source)) return damage;
        return budget(victim, damage, victim.getMaxHealth(), false);
    }
    public static float defendItem(ItemEntity item, DamageSource source, float damage) {
        if (item.level().isClientSide || damage <= 0 || !CrossModCombatConfig.DEFENSE.get()
                || !namespace(item.getItem()).equals("blacklight") || !externalAttack(source)) return damage;
        // Vanilla item health is integral and starts at five. A fractional subtraction still costs one HP.
        return budget(item, damage, 5, true);
    }
    private static float budget(Entity target, float damage, float maximum, boolean item) {
        var data = target.getPersistentData();
        long now = target.level().getGameTime();
        var old = new CrossModDamageRules.Budget(data.contains(START) ? data.getLong(START) : now, data.getFloat(SPENT));
        float limit = (float)(maximum * CrossModCombatConfig.WINDOW_FRACTION.get());
        float reduced = CrossModDamageRules.multiply(damage, CrossModCombatConfig.INCOMING.get());
        if(target instanceof net.minecraft.world.entity.Mob)reduced=Math.min(reduced,(float)(maximum*CrossModCombatConfig.MOB_HIT_FRACTION.get()));
        if (item && reduced > 0) { limit = Math.max(1, (float)Math.floor(limit)); reduced = Math.max(1, (float)Math.ceil(reduced)); }
        var result = old.accept(now, CrossModCombatConfig.WINDOW_TICKS.get(), reduced, limit);
        data.putLong(START, result.budget().start()); data.putFloat(SPENT, result.budget().spent());
        return result.damage();
    }

    /** Preserve the original damage ID, projectile/explosion tags, owner and death message. */
    public static class NativeAttackSource extends DamageSource {
        private final DamageSource original;
        private final boolean trueDamage;
        protected NativeAttackSource(DamageSource original, boolean trueDamage) {
            super(original.typeHolder(), original.getDirectEntity(), original.getEntity());
            this.original = original; this.trueDamage = trueDamage;
        }
        @Override public boolean is(TagKey<DamageType> tag) {
            return trueDamage && (tag.equals(DamageTypeTags.BYPASSES_ARMOR) || tag.equals(DamageTypeTags.BYPASSES_RESISTANCE)
                    || tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS) || tag.equals(DamageTypeTags.BYPASSES_SHIELD)
                    || tag.equals(DamageTypeTags.BYPASSES_EFFECTS)) || original.is(tag);
        }
        @Override public Vec3 getSourcePosition() { return original.getSourcePosition(); }
    }
}
