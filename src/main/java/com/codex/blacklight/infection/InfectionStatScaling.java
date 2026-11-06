package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.power.CombatScale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.*;

/** Threshold policy is based on the pre-infection source, never its later evolution. */
public final class InfectionStatScaling {
    public record Rules(boolean enabled,double healthThreshold,double healthBonus,double healthExcess,
                        double attackThreshold,double attackBonus,double attackExcess,boolean nativeUnits,boolean nativeThreat) {}
    private InfectionStatScaling() {}
    public static Rules rules() {
        return new Rules(GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_STATS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_HEALTH_THRESHOLD),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_HEALTH_BONUS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_HEALTH_EXCESS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_ATTACK_THRESHOLD),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_ATTACK_BONUS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_ATTACK_EXCESS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_NATIVE_UNITS),
                GameplayBalanceConfig.value(GameplayBalanceConfig.INFECTION_NATIVE_THREAT));
    }
    public static double health(double source,Rules r) {
        return Math.max(18,r.enabled()?InfectionConversionMath.enhance(source,r.healthThreshold(),r.healthBonus(),r.healthExcess()):InfectionConversionMath.nonnegative(source*1.4));
    }
    public static double attack(double sourceHealth,double sourceAttack,Rules r) {
        double fallback=Math.max(4,Math.min(18,InfectionConversionMath.nonnegative(sourceHealth)*.14));
        double inherited=r.enabled()?InfectionConversionMath.enhance(sourceAttack,r.attackThreshold(),r.attackBonus(),r.attackExcess()):InfectionConversionMath.nonnegative(sourceAttack*1.3);
        return Math.max(fallback,inherited);
    }
    public static double sourceHealth(LivingEntity entity) {
        if(entity instanceof BlacklightInfectedHost host)return host.giantBoss()?0:host.infectionSourceHealth();
        if(!(entity instanceof Mob)||BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("blacklight"))return 0;
        return InfectionConversionMath.nonnegative(CombatScale.originalMaximum(entity));
    }
    public static boolean nativeHealthUnits(LivingEntity entity) {
        if(!(entity instanceof Mob))return false;
        var r=rules();return r.enabled()&&r.nativeUnits()&&sourceHealth(entity)>=r.healthThreshold();
    }
    /** Reuse the existing elite contact ceiling rather than treating a strong infected
     * foreign mob as a ten-HP trash enemy. Never raises weak incoming damage. */
    public static boolean nativeThreat(Mob entity) {
        var r=rules();if(!r.enabled()||!r.nativeThreat()||!BlacklightInfectionSystem.isInfected(entity))return false;
        if(entity instanceof BlacklightInfectedHost host&&host.giantBoss())return false;
        double attack=entity.getAttribute(Attributes.ATTACK_DAMAGE)==null?0:entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        return sourceHealth(entity)>=r.healthThreshold()||attack>=r.attackThreshold();
    }
    /** Apply the normal attribute operations locally, without changing the global
     * vanilla attack cap. Only infected hosts use this to retain large mod attacks
     * and their earned growth; other entities keep their own attribute behavior. */
    public static double attackBeyondVanillaCap(AttributeInstance attribute,double normal) {
        if(attribute==null||!(attribute.getAttribute().value() instanceof RangedAttribute range)||normal<range.getMaxValue())return normal;
        double base=attribute.getBaseValue();
        for(var m:attribute.getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_VALUE)base+=m.amount();
        double result=base;
        for(var m:attribute.getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_BASE)result+=base*m.amount();
        for(var m:attribute.getModifiers())if(m.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)result*=1+m.amount();
        return InfectionConversionMath.nonnegative(result);
    }
}
