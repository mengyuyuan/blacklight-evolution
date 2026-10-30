package com.codex.blacklight.infection;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world controls, separate from building infection and player passive skills. */
public final class TissueCompetitionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED,INHERITANCE;
    public static final ModConfigSpec.IntValue SEARCHES,SESSIONS,RADIUS;
    public static final ModConfigSpec.DoubleValue FUSION;
    public static final ModConfigSpec.DoubleValue STRONG,POINTS,WEAKENED,HEAL,RECOVERY_DAMAGE,INTERRUPT;
    public static final ModConfigSpec.IntValue MEALS,RECOVERY_TICKS;
    static{
        var b=new ModConfigSpec.Builder();
        ENABLED=b.define("infectedCompetition",true);INHERITANCE=b.define("inheritOrgans",true);
        SEARCHES=b.comment("Maximum local prey searches per dimension each tick.").defineInRange("searchesPerTick",2,1,8);
        SESSIONS=b.defineInRange("simultaneousConsumptions",8,1,32);
        RADIUS=b.defineInRange("searchRadius",16,4,24);
        FUSION=b.comment("继承器官强度的融合系数。").defineInRange("fusionCoefficient",.35,0,1);
        STRONG=b.comment("计作强大器官的整合强度门槛。").defineInRange("strongOrganThreshold",300D,1,1000000000);
        MEALS=b.comment("成为进化者所需吞噬次数；至少6，且仍必须获得6个成熟器官。").defineInRange("minimumMealsForEvolved",6,6,1000000);
        POINTS=b.comment("成为进化者所需累计继承分数。").defineInRange("evolvedInheritancePoints",1000D,1,1000000000000D);
        WEAKENED=b.comment("同类血量低于该比例才允许吞噬；0.15=15%。").defineInRange("preyWeakenedHealthFraction",.15,.01,.5);
        HEAL=b.comment("成功吞噬同类后恢复的最大生命比例。").defineInRange("consumeHealFraction",.4,0,1);
        RECOVERY_TICKS=b.comment("吞噬完成后，对其他生物攻击的短暂保护时间。").defineInRange("postConsumeProtectionTicks",40,0,24000);
        RECOVERY_DAMAGE=b.comment("保护期间收到的生物伤害倍率，0完全减免，1不减伤。").defineInRange("postConsumeIncomingMultiplier",.35,0,1);
        INTERRUPT=b.comment("一次实际伤害达到最大生命该比例时中断同类吞噬。").defineInRange("consumeInterruptHealthFraction",.1,.01,1);
        SPEC=b.build();
    }
    public static TissueInheritance.Rules rules(){return new TissueInheritance.Rules(FUSION.get(),STRONG.get(),MEALS.get(),POINTS.get());}
    public static double weakenedFraction(){return SPEC.isLoaded()?WEAKENED.get():WEAKENED.getDefault();}
    private TissueCompetitionConfig(){}
}
