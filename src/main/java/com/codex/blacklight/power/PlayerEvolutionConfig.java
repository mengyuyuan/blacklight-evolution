package com.codex.blacklight.power;

import net.minecraftforge.common.ForgeConfigSpec;

/** Same fixed price at every rank; synchronized server configuration. */
public final class PlayerEvolutionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue COST;
    public static final ForgeConfigSpec.IntValue PASSIVE_LEVEL,PERCENT_LEVEL;
    public static final ForgeConfigSpec.DoubleValue HEALTH_PER_LEVEL,ATTACK_PER_LEVEL,SKILL_PER_LEVEL,DEFENSE_PER_LEVEL,RECOVERY_PER_LEVEL,MOBILITY_PER_LEVEL;
    public static final ForgeConfigSpec.IntValue RECOVERY_DELAY;
    public static final ForgeConfigSpec.BooleanValue KEEP_LEGACY_PASSIVES;
    static {
        var b=new ForgeConfigSpec.Builder();
        COST=b.comment("Fixed EP cost for every repeatable attribute purchase, independent of rank.")
                .defineInRange("repeatableAttributeCost",200,1,1000000);
        PASSIVE_LEVEL=b.comment("适应性I和组织再生I的门槛：六项无限进化属性分别达到此等级，非等级总和。旧20/50键不再使用，首次生成新键默认50/150。")
                .defineInRange("passiveTierOneAttributeRank",50,1,1000000);
        PERCENT_LEVEL=b.comment("适应性II和组织再生II的逐项等级门槛；有效值不低于一级门槛，仍需购买对应一级技能。")
                .defineInRange("passiveTierTwoAttributeRank",150,1,1000000);
        KEEP_LEGACY_PASSIVES=b.comment("Keep old learned passives active without new qualifications. False preserves learning/stacks but suspends effects until qualified.")
                .define("keepLegacyPassivesActive",false);
        HEALTH_PER_LEVEL=b.comment("每级生命无限强化增加的实际HP。").defineInRange("healthPerLevel",10D,0,1000000);
        ATTACK_PER_LEVEL=b.comment("每级普通攻击增幅，0.02=2%。").defineInRange("attackFractionPerLevel",.02,0,100);
        SKILL_PER_LEVEL=b.comment("每级黑光技能伤害增幅，0.04=4%。").defineInRange("skillFractionPerLevel",.04,0,100);
        DEFENSE_PER_LEVEL=b.comment("防御：受到伤害除以1+本值×等级；不影响虚空或管理击杀。").defineInRange("defenseCoefficientPerLevel",.025,0,100);
        RECOVERY_PER_LEVEL=b.comment("每级脱战后每秒恢复的实际HP；不是适应性或组织再生层数。").defineInRange("recoveryHpPerLevel",.25,0,1000000);
        RECOVERY_DELAY=b.comment("无限恢复属性的脱战等待刻数；20刻约1秒。").defineInRange("recoveryDelayTicks",80,0,240000);
        MOBILITY_PER_LEVEL=b.comment("移速倍率=1+本值×ln(1+等级)，不改变动作播放速度。").defineInRange("mobilityLogCoefficient",.025,0,10);
        SPEC=b.build();
    }
    private PlayerEvolutionConfig(){}
    public static <T> T value(ForgeConfigSpec.ConfigValue<T> setting){return SPEC.isLoaded()?setting.get():setting.getDefault();}
}
