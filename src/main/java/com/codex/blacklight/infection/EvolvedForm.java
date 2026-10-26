package com.codex.blacklight.infection;

/** One-way biological milestone, separate from the creature's uncapped organ strength. */
public final class EvolvedForm {
    public static final int DURATION=200,COMMIT=120;
    private EvolvedForm(){}
    public static boolean eligible(TissueInheritance genes,long age){
        return genes.qualified()&&genes.acquiredOrgans()>=6&&genes.meals()>=6&&genes.readyToHunt(age);
    }
    public static float smooth(float x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
    public static float oldBody(float ticks){return 1-smooth((ticks-25)/80);}
    public static float humanBody(float ticks){return smooth((ticks-COMMIT)/(DURATION-COMMIT));}
    public static float cover(float ticks){return smooth(ticks/35)*(1-smooth((ticks-145)/55));}
    public static float scale(TissueInheritance genes){
        double load=0;for(var organ:genes.organs().values())load+=Math.log1p(organ.integrated()/100);
        return (float)(1+.10*Math.log1p(load));
    }
    public static boolean blade(TissueInheritance genes,TissueCore nativeCore,long seed,long age){
        int weapons=TissueInheritance.nativeWeapons(nativeCore,seed,age);for(var organ:genes.organs().values())if(organ.mature()&&organ.integrated()>0)weapons|=organ.weapons();
        return (weapons&TissueInheritance.BLADE)!=0;
    }
    public static boolean grasp(TissueInheritance genes,TissueCore nativeCore,long age){return genes.strength(TissueCore.HYDROSTAT,nativeCore,age)>0||genes.strength(TissueCore.GRASP,nativeCore,age)>0;}
}
