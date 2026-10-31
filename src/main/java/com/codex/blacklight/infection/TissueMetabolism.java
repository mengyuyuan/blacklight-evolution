package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;
import static com.codex.blacklight.config.GameplayBalanceConfig.value;

/** Food pays for actual growth/repair, never idle decay. Natural age still advances unfed. */
public final class TissueMetabolism {
    public static final int MAX_RESERVE=2400, BONUS_TICKS=180, RECOVERY_DELAY=100;
    public static final int WORK_PER_UNIT=100;
    public static final int CULTIVATION_BATCH=1200;
    public record Cultivation(int remainder,int work){}
    /** Sparse genetic publications rather than a whole ancestry packet per second. */
    public static Cultivation cultivation(int previous,long paidTicks){
        int total=com.codex.blacklight.forge.WingMath.clamp(previous,0,CULTIVATION_BATCH-1)+(int)com.codex.blacklight.forge.WingMath.clamp(paidTicks,0,CULTIVATION_BATCH);
        return total>=CULTIVATION_BATCH?new Cultivation(total-CULTIVATION_BATCH,CULTIVATION_BATCH):new Cultivation(total,0);
    }
    public record Budget(int reserve,int debt,boolean supplied,double fraction) {
        public Budget(int reserve,int debt,boolean supplied){this(reserve,debt,supplied,supplied?1:0);}
    }
    private TissueMetabolism() {}
    public static int clamp(int reserve){return com.codex.blacklight.forge.WingMath.clamp(reserve,0,MAX_RESERVE);}
    public static int add(int reserve,int offered){return (int)Math.min(MAX_RESERVE,(long)clamp(reserve)+Math.max(0,offered));}
    public static int initialReserve(float originalHealth){return !Float.isFinite(originalHealth)||originalHealth<=0?0:com.codex.blacklight.forge.WingMath.clamp(meal(originalHealth,TissueCore.MUSCLE)*2,8,24);}
    public static boolean growing(long age,boolean integrating){return age<Long.MAX_VALUE||integrating;}
    public static long growthPhase(long age){return TissueGrowth.generation(age);}
    public static double phaseFood(long age){return value(GameplayBalanceConfig.FOOD_BASE)+value(GameplayBalanceConfig.FOOD_PER_GENERATION)*growthPhase(Math.max(0,age));}
    public static double worldSpeed(long overworldDayTime){return value(GameplayBalanceConfig.GROWTH_SPEED)*(1+value(GameplayBalanceConfig.WORLD_ACCELERATION)*Math.sqrt((Math.max(0,overworldDayTime)/24000L)/value(GameplayBalanceConfig.WORLD_ACCELERATION_DAYS)));}
    public static int fedBonusTicks(){return value(GameplayBalanceConfig.FED_BONUS_TICKS);}
    public static int repairDelay(){return value(GameplayBalanceConfig.REPAIR_DELAY);}
    public record TimeStep(long ticks,double remainder){}
    public static double timeFraction(double remainder){return Double.isFinite(remainder)&&remainder>=0&&remainder<1?remainder:0;}
    /** Carry sub-tick work across batches; rounding every second makes small
     * configured speeds much faster than requested, or freezes them forever. */
    public static TimeStep timeStep(double previous,long ticks,double speed){
        double fraction=timeFraction(previous);
        if(ticks<=0||speed==0)return new TimeStep(0,fraction);
        if(!Double.isFinite(speed)||speed<0)speed=1;
        double total=fraction+ticks*speed;
        if(total>=Long.MAX_VALUE)return new TimeStep(Long.MAX_VALUE,0);
        long whole=(long)total;return new TimeStep(whole,total-whole);
    }
    public static long scaledTicks(long ticks,double speed){return timeStep(0,ticks,speed).ticks();}
    /** Estimated extra food per biological stage; natural age has no food fee. */
    public static int demand(long age,boolean integrating,boolean repairing){
        return demand(age,integrating,repairing,1);
    }
    public static int demand(long age,boolean integrating,boolean repairing,double speed){
        int phaseLength=age<24000?12000:24000;
        double growth=growing(age,integrating)?phaseFood(age)*fedBonusTicks()*speed/phaseLength*WORK_PER_UNIT*value(GameplayBalanceConfig.FOOD_COST):0;
        return (int)Math.min(Integer.MAX_VALUE,Math.ceil(growth+(repairing?20:0)));
    }
    public static Budget budget(int reserve,int debt,int demand){
        reserve=clamp(reserve);debt=com.codex.blacklight.forge.WingMath.clamp(debt,0,WORK_PER_UNIT-1);
        if(reserve==0||demand<=0)return new Budget(reserve,debt,false);
        long available=(long)reserve*WORK_PER_UNIT-debt,paid=Math.min(available,(long)demand),work=debt+paid;
        return new Budget(reserve-(int)(work/WORK_PER_UNIT),(int)(work%WORK_PER_UNIT),paid>0,paid/(double)demand);
    }
    /** Use unscaled prey health, with a bounded meal even for edited/modded mobs. */
    public static int meal(float health,TissueCore core){
        if(!Float.isFinite(health)||health<=0)return 0;
        int base=(int)com.codex.blacklight.forge.WingMath.clamp(Math.ceil(health/4D),2,48);
        int result=core==TissueCore.JAW?(base*5+3)/4:base;
        return (int)Math.min(MAX_RESERVE,Math.ceil(result*value(GameplayBalanceConfig.MEAL_MULTIPLIER)));
    }
}
