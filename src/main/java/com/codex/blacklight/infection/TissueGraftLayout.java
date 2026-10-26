package com.codex.blacklight.infection;

/** Organ identity controls anatomy selection; it is not a coat or a random extra mesh stack. */
public final class TissueGraftLayout {
    private TissueGraftLayout(){}
    public static boolean owns(TissueCore core,String mesh){
        return switch(core){
            case MUSCLE -> false; // Muscle reshapes the connected body/limbs, not a new blade awarded for muscle stats.
            case CARAPACE -> mesh.startsWith("scapular_plate")||mesh.startsWith("costal_arch");
            case JAW -> mesh.equals("mandible")||mesh.equals("palate")||mesh.startsWith("cheek_blade")||mesh.startsWith("fang_");
            case GRASP -> mesh.startsWith("claw_")||mesh.startsWith("blade_");
            case CREST -> mesh.startsWith("shoulder_crest");
            default -> mesh.startsWith(core.id+"_");
        };
    }
    /** Establish the new silhouette during digestion; combat maturity still takes three fed minutes. */
    public static float growth(TissueInheritance.Organ organ,double interpolatedWork){
        if(organ==null)return 0;
        double progress=(TissueInheritance.NEW_MATURITY_WORK-Math.max(0,organ.maturityWork()-Math.clamp(interpolatedWork,0,20)))/TissueInheritance.NEW_MATURITY_WORK;
        float t=(float)Math.clamp(progress*6,0,1);return t*t*(3-2*t);
    }
    public static float gain(TissueInheritance.Organ organ){
        return (float)(1+.42*-Math.expm1(-Math.log1p((organ.integrated()+organ.pending())/100)));
    }
    public static String key(TissueCore core,String name){
        return core==TissueCore.GRASP&&(name.startsWith("claw_")||name.startsWith("blade_"))?"digit_"+name.substring(name.indexOf('_')+1):name;
    }
}
