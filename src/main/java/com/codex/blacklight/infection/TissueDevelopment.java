package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;

/** One metabolism/integration step shared by converted and preserved-native hosts.
 * Contains only value data. No world, entity, AI or foreign capability is copied. */
public final class TissueDevelopment {
    private TissueDevelopment() {}
    public record Step(long age,TissueInheritance inheritance,int nutrition,int debt,
                       int cultivation,double[] fractions,double repairFraction) {
        public Step { fractions=fractions.clone(); }
        @Override public double[] fractions(){return fractions.clone();}
    }
    private static long time(double[] fractions,int lane,long ticks,double speed){
        var step=TissueMetabolism.timeStep(fractions[lane],ticks,speed);
        fractions[lane]=step.remainder();return step.ticks();
    }
    public static boolean integrating(TissueInheritance genes){
        return genes.organs().values().stream().anyMatch(o->o.work()>0||!o.mature())
                ||genes.sources().values().stream().anyMatch(s->s.work()>0);
    }
    public static Step advance(long age,TissueInheritance genes,long seed,TissueCore nativeCore,
                               int nutrition,int debt,int cultivation,double[] previousFractions,
                               long elapsed,double speed,boolean active,boolean repairing,TissueInheritance.Rules rules){
        if(previousFractions.length!=4)throw new IllegalArgumentException("Four tissue work clocks required");
        double[] fractions=previousFractions.clone();
        for(int i=0;i<fractions.length;i++)fractions[i]=TissueMetabolism.timeFraction(fractions[i]);
        if(elapsed<=0)return new Step(age,genes,TissueMetabolism.clamp(nutrition),debt,cultivation,fractions,0);
        boolean integrating=integrating(genes);
        int demand=active?TissueMetabolism.demand(age,integrating,repairing,speed):0;
        var budget=TissueMetabolism.budget(nutrition,debt,demand);
        var developed=genes;
        if(active){
            double unfed=GameplayBalanceConfig.value(GameplayBalanceConfig.UNFED_INTEGRATION);
            developed=genes.digest(time(fractions,2,elapsed,speed*(unfed+(1-unfed)*budget.fraction())
                            *GameplayBalanceConfig.value(GameplayBalanceConfig.INTEGRATION_SPEED)),
                    true,seed,age,rules,nativeCore);
            if(budget.supplied()&&developed.hasMatureInheritance()){
                var paid=TissueMetabolism.cultivation(cultivation,time(fractions,3,elapsed,speed*budget.fraction()));
                cultivation=paid.remainder();
                developed=developed.cultivate(paid.work(),nativeCore,age,rules);
            }
        }
        long natural=time(fractions,0,elapsed,speed);
        long bonus=budget.supplied()&&TissueMetabolism.growing(age,integrating)
                ?time(fractions,1,TissueMetabolism.fedBonusTicks(),speed*budget.fraction()):0;
        long nextAge=TissueGrowth.advance(age,TissueGrowth.advance(natural,bonus));
        return new Step(nextAge,developed,budget.reserve(),budget.debt(),cultivation,fractions,
                budget.supplied()&&repairing?budget.fraction():0);
    }
}
