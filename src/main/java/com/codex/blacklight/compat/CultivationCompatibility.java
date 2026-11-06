package com.codex.blacklight.compat;

import com.codex.blacklight.config.CrossModCombatConfig;
import com.codex.blacklight.combat.CrossModCombat;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;

/** Optional public-API bridge. Never copies foreign capabilities or changes player shields. */
public final class CultivationCompatibility {
    private static final String NAMESPACE = "xiaoxiang_cultivation";
    private CultivationCompatibility() {}

    public static boolean creature(LivingEntity entity) {
        return !(entity instanceof Player) && (NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace())
                || entity instanceof BlacklightInfectedHost host && host.speciesId().startsWith(NAMESPACE + ":"));
    }

    /** Cultivation already authors HP in its own units; infection still adds its normal growth. */
    public static boolean nativeHealthUnits(LivingEntity entity) {
        return CrossModCombatConfig.CULTIVATION_NATIVE_HEALTH.get() && creature(entity);
    }

    private static final ClassValue<Method> PLAYER_DATA = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {
                return Class.forName("com.xiaoxiang.cultivation.cultivation.CultivationCapability", false, type.getClassLoader())
                        .getMethod("get", Player.class);
            } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
        }
    };
    private static final ClassValue<Method> REALM = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) { return method(type, "getRealm"); }
    };
    /** A two-unit trash-mob cap applied before cultivation's two-point flat
     * defense otherwise reduces every infected melee hit to zero. */
    public static boolean lateIncomingBudget(LivingEntity victim, DamageSource source) {
        if (!CrossModCombatConfig.CULTIVATION_LATE_BUDGET.get() || !(victim instanceof Player player)
                || !(source.getEntity() instanceof net.minecraft.world.entity.Mob attacker) || !creature(attacker)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !com.codex.blacklight.registry.ModAttachments.available(player)
                || !com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE).isInfected())
            return false;
        Method dataMethod = PLAYER_DATA.get(player.getClass());
        if (dataMethod == null) return false;
        try {
            Object data = ((java.util.Optional<?>)dataMethod.invoke(null, player)).orElse(null);
            if (data == null || REALM.get(data.getClass()) == null) return false;
            Object realm = REALM.get(data.getClass()).invoke(data);
            return realm instanceof Enum<?> value && !value.name().equals("MORTAL");
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    /** Same creature budget as before, evaluated after foreign defenses and
     * before Blacklight's final HP-unit conversion. Never forces damage through immunity. */
    public static float afterCultivationDefense(LivingEntity victim, DamageSource source, float damage) {
        if (damage <= 0 || !lateIncomingBudget(victim, source)) return damage;
        float difficulty = switch (victim.level().getDifficulty()) {
            case EASY -> .75F; case HARD -> 1.5F; default -> 1;
        };
        if (source.getEntity() instanceof BlacklightInfectedHost host && host.isEvolved()
                || source.getEntity() instanceof net.minecraft.world.entity.Mob mob && com.codex.blacklight.infection.InfectionStatScaling.nativeThreat(mob))
            return com.codex.blacklight.infection.EvolvedBalance.playerDamage(damage, victim.getMaxHealth(),
                    com.codex.blacklight.power.CombatScale.units(victim), difficulty, com.codex.blacklight.infection.EvolvedBalance.rules());
        return Math.min(damage * .30F, 10 * difficulty / com.codex.blacklight.power.CombatScale.UNITS);
    }

    private record AttackAccess(Method melee, Method beastBonus, Method technique) {}
    private static final ClassValue<AttackAccess> ATTACK = new ClassValue<>() {
        @Override protected AttackAccess computeValue(Class<?> type) {
            Method melee = null;
            try {
                Class<?> handler = Class.forName("com.xiaoxiang.cultivation.event.AttackBonusHandler", false, type.getClassLoader());
                for (Method method : handler.getMethods())
                    if (method.getName().equals("npcMeleeHostDamage") && method.getParameterCount() == 3
                            && method.getParameterTypes()[0].isAssignableFrom(type)
                            && method.getParameterTypes()[1] == float.class && method.getParameterTypes()[2] == boolean.class)
                        melee = method;
            } catch (ReflectiveOperationException | LinkageError ignored) { }
            return new AttackAccess(melee, method(type, "beastAttackBonus"), method(type, "getAggregatedTechniqueBonus"));
        }
    };
    private static Method method(Class<?> type, String name) {
        try { return type.getMethod(name); } catch (NoSuchMethodException ignored) { return null; }
    }

    /** Snapshot authored melee once at conversion, without critical rolls or firing damage events. */
    public static double inheritedAttack(LivingEntity source, double attribute) {
        if (!creature(source) || !CrossModCombatConfig.CULTIVATION_INHERIT_ATTACK.get()) return attribute;
        AttackAccess access = ATTACK.get(source.getClass());
        try {
            double attack = attribute;
            if (access.melee() != null) attack = ((Number)access.melee().invoke(null, source, (float)attribute, false)).doubleValue();
            else if (access.beastBonus() != null) attack += ((Number)access.beastBonus().invoke(source)).doubleValue();
            else if (access.technique() != null) {
                // Older releases expose technique attack, but not the complete melee calculation.
                Object bonus = access.technique().invoke(source);
                attack += Math.max(0, ((Number)bonus.getClass().getField("attack").get(bonus)).doubleValue());
            }
            return Double.isFinite(attack) ? Math.max(attribute, attack) : attribute;
        } catch (ReflectiveOperationException | LinkageError ignored) { return attribute; }
    }

    private record EventAccess(Method entity, Method source) {}
    private static final ClassValue<EventAccess> EVENTS = new ClassValue<>() {
        @Override protected EventAccess computeValue(Class<?> type) {
            return new EventAccess(method(type, "getEntity"), method(type, "getSource"));
        }
    };
    public static boolean bypassMobShield(Object event) {
        if(event==null)return false;

        EventAccess access = EVENTS.get(event.getClass());
        if (access.entity() == null || access.source() == null) return false;
        try {
            if (!(access.entity().invoke(event) instanceof LivingEntity victim) || victim.level().isClientSide
                    || !creature(victim) || victim.isRemoved()) return false;
            DamageSource source = (DamageSource)access.source().invoke(event);
            return NativeCombatAccess.bypassNpcShield(victim,source);
        } catch (ReflectiveOperationException | LinkageError ignored) { return false; }
    }
    /** Framework-level policy shared by every cultivation NPC and beast. */
    public static boolean bypassNpcShield(LivingEntity victim,DamageSource source){
        if(victim==null||source==null||!creature(victim)||victim.level().isClientSide||victim.isRemoved())return false;
        if (victim.isInvulnerableTo(source)) return false;
        // Only the optional NPC shield handler is skipped. The original hurt call,
        // armor, claims, PvP rules and other listeners still decide the hit.
        if (CrossModCombatConfig.CULTIVATION_NATIVE_HITS.get() && CrossModCombat.nativeAttack(source)
                && source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)
                && !source.is(DamageTypeTags.BYPASSES_EFFECTS)
                && source.getEntity() instanceof Player player
                && com.codex.blacklight.power.PrototypeMetabolism.active(player)) return true;
        return CrossModCombatConfig.CULTIVATION_TRUE_DAMAGE.get() && (com.codex.blacklight.combat.DevastatorDamage.isTrueDamage(source)
                ||CrossModCombatConfig.LEGACY_ADVANTAGE.get()&&CrossModCombatConfig.OFFENSE.get()&&CrossModCombatConfig.TRUE_DAMAGE.get()&&CrossModCombat.nativeAttack(source)) && source.is(DamageTypeTags.BYPASSES_ARMOR)
                && source.is(DamageTypeTags.BYPASSES_EFFECTS) && !victim.isInvulnerableTo(source);
    }
    private static final ClassValue<Method> RESERVE=new ClassValue<>(){
        @Override protected Method computeValue(Class<?> type){return method(type,"getCurrentQi");}
    };
    public static java.util.OptionalDouble reserve(LivingEntity entity){
        if(entity==null||!creature(entity))return java.util.OptionalDouble.empty();
        var access=RESERVE.get(entity.getClass());if(access==null)return java.util.OptionalDouble.empty();
        try{
            Object value=access.invoke(entity);
            if(value instanceof Number number&&Double.isFinite(number.doubleValue())&&number.doubleValue()>=0)
                return java.util.OptionalDouble.of(number.doubleValue());
        }catch(java.lang.reflect.InvocationTargetException ex){
            if(ex.getCause() instanceof VirtualMachineError fatal)throw fatal;
            if(ex.getCause() instanceof ThreadDeath fatal)throw fatal;
        }catch(ReflectiveOperationException | LinkageError ignored){}
        return java.util.OptionalDouble.empty();
    }
}
