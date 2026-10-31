package com.codex.blacklight.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned simulation tuning. Combat and infection intensity are independently configurable. */
public final class GameplayBalanceConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CONTACT_ENABLED,LINEAGE_GUARANTEES;
    public static final ForgeConfigSpec.DoubleValue CONTACT_CHANCE,VIAL_RADIUS,VIAL_CENTER,VIAL_EDGE,VIAL_DIRECT;
    public static final ForgeConfigSpec.IntValue CONTACT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue GROWTH_SPEED,WORLD_ACCELERATION,WORLD_ACCELERATION_DAYS,INTEGRATION_SPEED,
            UNFED_INTEGRATION,FOOD_COST,FOOD_BASE,FOOD_PER_GENERATION,MEAL_MULTIPLIER,REPAIR_FRACTION,
            HOST_HEALTH_GROWTH,HOST_ATTACK_GROWTH,HOST_ARMOR_GROWTH;
    public static final ForgeConfigSpec.IntValue FED_BONUS_TICKS,REPAIR_DELAY;
    public static final ForgeConfigSpec.IntValue GREEN_CHANCE,YELLOW_CHANCE,RED_CHANCE,GREEN_CAP,YELLOW_CAP,
            SPAWN_MIN,SPAWN_MAX,SPAWN_SAFETY,SPAWN_ATTEMPTS,SPAWN_HEIGHT,EXPOSURE_RADIUS,
            GREEN_BIRTHS,YELLOW_BIRTHS,RED_BIRTHS;
    public static final ForgeConfigSpec.DoubleValue EXPOSURE_BASE,EXPOSURE_PER_STAGE,SEED_CHANCE,EARLY_GROWING,EARLY_PIONEER;
    public static final ForgeConfigSpec.IntValue[] STAGE_DAYS=new ForgeConfigSpec.IntValue[4];
    public static final ForgeConfigSpec.IntValue[][] TIER_WEIGHTS=new ForgeConfigSpec.IntValue[5][5];
    public static final ForgeConfigSpec.DoubleValue CORE_CHANCE,BROOD_CHANCE,BROOD_COST;
    public static final ForgeConfigSpec.IntValue BROOD_TICKS,HUNTER_EVERY,OCCUPATION_TICKS,OCCUPATION_BASE,OCCUPATION_PER_STAGE;
    static {
        var b=new ForgeConfigSpec.Builder();
        b.comment("感染传播。概率使用0～1；20 tick约1秒。仅服务端决定结果；不修改强制感染指令。").push("infection");
        CONTACT_ENABLED=b.comment("允许感染生物的近战攻击传播病毒。").define("contactEnabled",true);
        CONTACT_CHANCE=b.comment("每个接触冷却窗口的近战感染概率；0关闭，1必定。").defineInRange("contactChance",.08,0,1);
        CONTACT_COOLDOWN=b.comment("同一受害者两次接触感染尝试的间隔；失败也进入冷却。").defineInRange("contactCooldownTicks",200,1,240000);
        VIAL_RADIUS=b.comment("摔瓶感染半径，单位方块；仍检查墙壁与真实距离。").defineInRange("vialRadius",16D,1,64);
        VIAL_DIRECT=b.comment("直接命中可感染生物时的概率；默认必定。玩家觉醒仍为自愿。").defineInRange("vialDirectHitChance",1D,0,1);
        VIAL_CENTER=b.comment("溅射中心感染概率。").defineInRange("vialCenterChance",1D,0,1);
        VIAL_EDGE=b.comment("溅射边缘感染概率；中心到边缘线性插值。").defineInRange("vialEdgeChance",.5,0,1);b.pop();
        b.comment("感染宿主成长与器官整合。倍率0允许暂停对应过程；不会追赶离线时间。").push("evolution");
        GROWTH_SPEED=b.comment("总进化速度倍率，同时影响自然生长、食物加速和器官整合；0暂停。").defineInRange("growthSpeedMultiplier",1D,0,100);
        WORLD_ACCELERATION=b.comment("世界天数加速强度；0关闭。速度=总倍率×(1+强度×sqrt(世界天数/基准天数))。").defineInRange("worldAgeAcceleration",1D,0,20);
        WORLD_ACCELERATION_DAYS=b.comment("世界天数加速的基准游戏天数；1天=24000世界日照刻。").defineInRange("worldAgeReferenceDays",7D,1,1000000);
        INTEGRATION_SPEED=b.comment("吞噬后器官消化、整合的额外速度倍率。").defineInRange("organIntegrationMultiplier",1D,0,100);
        UNFED_INTEGRATION=b.comment("缺乏养分时器官整合速度占正常速度的比例。").defineInRange("unfedIntegrationFraction",.2,0,1);
        FED_BONUS_TICKS=b.comment("每次代谢更新中，有养分时额外推进的基础生长刻；更新周期仍由服务器控制。").defineInRange("fedBonusGrowthTicks",180,0,24000);
        FOOD_BASE=b.comment("最初生长阶段加速所需的基础养分估计值。").defineInRange("initialStageNutrients",4D,0,100000);
        FOOD_PER_GENERATION=b.comment("后续每代增加的加速养分需求；不是直接一次性扣除。").defineInRange("additionalNutrientsPerGeneration",3D,0,100000);
        FOOD_COST=b.comment("成长加速的养分消耗倍率；不产生无活动自然流失。").defineInRange("growthNutrientCostMultiplier",1D,0,100);
        MEAL_MULTIPLIER=b.comment("吞食普通猎物获得养分的倍率；仍遵守容积与真实吞食规则。").defineInRange("preyNutrientMultiplier",1D,0,100);
        REPAIR_FRACTION=b.comment("有养分且停止受伤后，每次代谢恢复的最大生命比例。").defineInRange("repairMaxHealthFraction",.005,0,.5);
        REPAIR_DELAY=b.comment("受伤后暂停养分修复的刻数。").defineInRange("repairDelayTicks",100,0,24000);
        HOST_HEALTH_GROWTH=b.comment("感染宿主成长及继承带来的额外生命倍率；0取消该成长增益，不删除基础生命。").defineInRange("hostHealthGrowthMultiplier",1D,0,100);
        HOST_ATTACK_GROWTH=b.comment("感染宿主成长及继承带来的额外攻击增益倍率。").defineInRange("hostAttackGrowthMultiplier",1D,0,100);
        HOST_ARMOR_GROWTH=b.comment("感染宿主成长及继承带来的额外护甲增益倍率。").defineInRange("hostArmorGrowthMultiplier",1D,0,100);b.pop();
        b.comment("自然环境出生与感染。种群上限、总开关、批次与一天长度见blacklight-outbreak-server.toml。").push("outbreak");
        GREEN_CHANCE=b.comment("每次候选位置在绿区允许环境刷怪的百分比，0～100。").defineInRange("greenSpawnChancePercent",12,0,100);
        YELLOW_CHANCE=b.comment("黄区环境刷怪百分比。").defineInRange("yellowSpawnChancePercent",55,0,100);
        RED_CHANCE=b.comment("红区环境刷怪百分比。").defineInRange("redSpawnChancePercent",100,0,100);
        GREEN_CAP=b.comment("绿区局部感染体软上限，仍受总局部上限约束。").defineInRange("greenLocalCap",8,0,128);
        YELLOW_CAP=b.comment("黄区局部感染体软上限。").defineInRange("yellowLocalCap",20,0,128);
        GREEN_BIRTHS=b.comment("每名玩家每轮在绿区最多成功补充的感染体数，0关闭该玩家区域的补怪；仍受维度共享预算限制。").defineInRange("greenBirthsPerPlayer",1,0,8);
        YELLOW_BIRTHS=b.comment("黄区每名玩家每轮最多补充的感染体数。").defineInRange("yellowBirthsPerPlayer",2,0,8);
        RED_BIRTHS=b.comment("红区每名玩家每轮最多补充的感染体数。").defineInRange("redBirthsPerPlayer",3,0,8);
        SPAWN_MIN=b.comment("候选刷怪点到玩家的最小水平距离。").defineInRange("minimumSpawnDistance",28,1,128);
        SPAWN_MAX=b.comment("候选刷怪点的最大水平距离；小于最小值时按最小值处理，不加载未加载区块。").defineInRange("maximumSpawnDistance",64,1,128);
        SPAWN_SAFETY=b.comment("刷怪点必须远离所有玩家的安全距离。").defineInRange("playerSpawnSafetyDistance",24,0,128);
        SPAWN_ATTEMPTS=b.comment("每名玩家每批次寻找候选点的最大尝试次数；受共享出生预算约束。").defineInRange("spawnPositionAttempts",12,1,32);
        SPAWN_HEIGHT=b.comment("候选点与玩家最大高度差。").defineInRange("maximumSpawnHeightDifference",96,1,320);
        EXPOSURE_RADIUS=b.comment("环境随机感染查询半径；仅已加载生物。").defineInRange("ambientInfectionRadius",64,1,128);
        EXPOSURE_BASE=b.comment("每批次环境感染尝试的基础概率；还要通过所在区域概率检查。").defineInRange("ambientInfectionBaseChance",.2,0,1);
        EXPOSURE_PER_STAGE=b.comment("每个世界阶段增加的环境感染概率，最终不超过1。").defineInRange("ambientInfectionChancePerStage",.2,0,1);
        SEED_CHANCE=b.comment("非绿区成功自然出生后播种组织的概率。").defineInRange("tissueSeedChance",.25,0,1);
        EARLY_GROWING=b.comment("早期保证谱系后的生长宿主比例。").defineInRange("earlyGrowingChance",.25,0,1);
        EARLY_PIONEER=b.comment("早期补足成长谱系的额外尝试概率。").defineInRange("earlyLineageChance",.1,0,1);
        LINEAGE_GUARANTEES=b.comment("优先补足普通补给、成长、成熟谱系名额。关闭后仅按权重出生；权重0不再被名额补足覆盖。").define("lineageGuarantees",true);
        int[] days={1,3,7,14};
        for(int i=0;i<4;i++)STAGE_DAYS[i]=b.comment("进入阶段"+(i+1)+"需要的释放病毒后游戏天数；有效值至少比上一阶段多1天。").defineInRange("stage"+(i+1)+"Day",days[i],1,1000000);
        int[][] weights={{100,0,0,0,0},{75,25,0,0,0},{65,22,9,4,0},{60,22,12,5,1},{60,14,16,8,2}};
        String[] names={"common","growing","mature","hunter","ancient"};
        for(int stage=0;stage<5;stage++){
            b.comment("阶段"+stage+"的相对出生权重；无需总和为100。全为0时回退普通感染者；关闭自然出生请用区域概率或总开关。").push("stage"+stage+"Weights");
            for(int tier=0;tier<5;tier++)TIER_WEIGHTS[stage][tier]=b.defineInRange(names[tier],weights[stage][tier],0,10000);
            b.pop();
        }b.pop();
        b.comment("组织占领建筑与繁殖；空间范围、工作预算、成熟时间和开关见blacklight-building-infection-server.toml。").push("buildings");
        CORE_CHANCE=b.comment("自然组织尝试建立建筑核心的概率。").defineInRange("naturalCoreChance",.0625,0,1);
        BROOD_TICKS=b.comment("成熟建筑两轮繁殖尝试的间隔刻数；有养分且可生长时计时。").defineInRange("broodIntervalTicks",600,20,240000);
        BROOD_CHANCE=b.comment("每轮到期繁殖尝试概率；失败不消耗养分，0关闭繁殖。").defineInRange("broodChance",1D,0,1);
        HUNTER_EVERY=b.comment("每多少次建筑出生优先尝试猎手，仍需世界阶段允许；0关闭此优先项。").defineInRange("hunterEveryBirths",4,0,10000);
        BROOD_COST=b.comment("建筑繁殖养分费用倍率；最终至少1。").defineInRange("broodNutrientCostMultiplier",1D,.01,100);
        OCCUPATION_TICKS=b.comment("稳定附着建筑的自主供能间隔刻数。").defineInRange("occupationIncomeIntervalTicks",40,5,240000);
        OCCUPATION_BASE=b.comment("每轮自主供能基础养分；不是玩家背包或物品复制。").defineInRange("occupationBaseNutrients",2,0,10000);
        OCCUPATION_PER_STAGE=b.comment("每个建筑成熟阶段增加的自主养分。").defineInRange("occupationNutrientsPerStage",2,0,10000);b.pop();
        SPEC=b.build();
    }
    private GameplayBalanceConfig(){}
    public static <T> T value(ForgeConfigSpec.ConfigValue<T> setting){return SPEC.isLoaded()?setting.get():setting.getDefault();}
    public static int birthsPerPlayer(com.codex.blacklight.city.DistrictThreat.Zone zone){return value(switch(zone){case GREEN->GREEN_BIRTHS;case YELLOW->YELLOW_BIRTHS;case RED->RED_BIRTHS;});}
    public static double exposureChance(int stage){return Math.min(1,value(EXPOSURE_BASE)+com.codex.blacklight.forge.WingMath.clamp(stage,0,4)*value(EXPOSURE_PER_STAGE));}
    public static int stageDay(int stage){int day=0;for(int i=0;i<=com.codex.blacklight.forge.WingMath.clamp(stage,0,3);i++)day=Math.max(day+1,value(STAGE_DAYS[i]));return day;}
    public static int[] weights(int stage){var result=new int[5];for(int i=0;i<5;i++)result[i]=value(TIER_WEIGHTS[com.codex.blacklight.forge.WingMath.clamp(stage,0,4)][i]);return result;}
}
