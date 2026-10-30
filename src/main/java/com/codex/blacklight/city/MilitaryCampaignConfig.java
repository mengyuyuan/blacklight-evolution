package com.codex.blacklight.city;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Shared rules, independently stored operations in each actual dimension. */
public final class MilitaryCampaignConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, NATURAL_BASES, REINFORCEMENTS;
    public static final ModConfigSpec.IntValue INTERVAL, RANGE, SQUAD, ACTIVE, TIMEOUT, COOLDOWN,
            SABOTAGE, RECOVERY, REDUCTION, SECURED_PRESSURE, SURVIVORS, DESTROY_REWARD, INFILTRATE_REWARD,
            REINFORCEMENT_INTERVAL, FIELD_LIMIT, FIELD_LIFETIME;
    static {
        var b=new ModConfigSpec.Builder();
        ENABLED=b.comment("启用主世界和黑光维度的军事清剿与基地争夺。关闭不删除已有实体或据点。").define("enabled",true);
        NATURAL_BASES=b.comment("在新的主世界与黑光城市区块选择不同阵营的天然军事基地；不覆盖已探索区块。").define("naturalBases",true);
        REINFORCEMENTS=b.comment("红区持续派入地面增援，使用新编队和有限弹药，不重置原驻军。遵守刷怪规则和人数预算。").define("redZoneReinforcements",true);
        REINFORCEMENT_INTERVAL=b.comment("红区尝试补充一队增援的间隔；一秒20刻。无有效加载地面时等待。").defineInRange("reinforcementIntervalTicks",400,100,240000);
        FIELD_LIMIT=b.comment("每维度同时加载的增援人数上限，且受总驻军上限约束，不随玩家数倍增。").defineInRange("fieldPersonnelLimit",24,0,64);
        FIELD_LIFETIME=b.comment("增援最大实际活动寿命；超时撤离，远离玩家后退场。停服不累计。").defineInRange("fieldLifetimeTicks",12000,1200,240000);
        INTERVAL=b.comment("基地尝试组织清剿的间隔刻数；一秒20刻，按20刻节拍向上取整。").defineInRange("dispatchIntervalTicks",600,20,240000);
        RANGE=b.comment("基地搜索实际已加载感染源的最大距离，不据此加载区块。").defineInRange("objectiveRange",192,32,512);
        SQUAD=b.comment("每队从已有闲置驻军中选择的人数，留守军官不参加。").defineInRange("squadSize",4,2,8);
        ACTIVE=b.comment("每维度同时存在的清剿队预算，不按玩家人数倍增。").defineInRange("activeOperations",2,0,8);
        TIMEOUT=b.comment("实际活动的行动最长刻数；未加载期间暂停。").defineInRange("operationTimeoutTicks",6000,200,240000);
        COOLDOWN=b.comment("一次行动后的基地实际活动休整时间。").defineInRange("baseCooldownTicks",2400,20,240000);
        SABOTAGE=b.comment("潜入吞噬军官成功后的基地瘫痪活动刻数。").defineInRange("sabotageTicks",24000,200,2400000);
        RECOVERY=b.comment("清理感染源后的地区恢复期，按实际活动时间计。").defineInRange("districtRecoveryTicks",72000,200,2400000);
        REDUCTION=b.comment("摧毁真实感染源造成的当地压力下降；范围0到10000。").defineInRange("sourcePressureReduction",4500,0,10000);
        SECURED_PRESSURE=b.comment("无活动感染源、残留感染体很少时，清剿后的压力上限。").defineInRange("securedPressure",1200,0,1599);
        SURVIVORS=b.comment("允许完成地区恢复的最多残留普通感染体数量，仍需无活动巢穴。").defineInRange("securedSurvivors",4,0,16);
        DESTROY_REWARD=b.comment("同一基地首次摧毁可获得的进化点，无法确认玩家归因时不发放。").defineInRange("baseDestructionReward",200,0,100000);
        INFILTRATE_REWARD=b.comment("同一基地首次潜入完成奖励，重复交互与读档不重发。").defineInRange("infiltrationReward",100,0,100000);
        SPEC=b.build();
    }
    public static int interval(){return ((INTERVAL.get()+19)/20)*20;}
    public static int reinforcementInterval(){return ((REINFORCEMENT_INTERVAL.get()+19)/20)*20;}
    public static boolean naturalBases(){try{return NATURAL_BASES.get();}catch(IllegalStateException notLoaded){return true;}}
    private MilitaryCampaignConfig() {}
}
