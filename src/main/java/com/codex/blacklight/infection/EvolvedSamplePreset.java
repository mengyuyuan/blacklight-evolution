package com.codex.blacklight.infection;

import java.util.*;

/** Operator sample organs. One dominant mature organ chooses the archetype through the normal mapping table. */
public final class EvolvedSamplePreset {
    private static final double PRIMARY=600,FILLER=150;
    private static final TissueCore[] LEGACY={TissueCore.MUSCLE,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.BLADE,TissueCore.HYDROSTAT};
    private static final TissueCore[] FILLERS={TissueCore.HEART,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.CREST,TissueCore.TENDON};
    private EvolvedSamplePreset(){}
    public static TissueCore primary(EvolvedArchetype type){
        return switch(type){
            case BLADE->TissueCore.BLADE;case STRENGTH->TissueCore.CLUB;case ARMORED->TissueCore.OSTEODERM;case PREDATOR->TissueCore.PHARYNX;
            case TENDRIL->TissueCore.HYDROSTAT;case SKIRMISHER->TissueCore.TENDON;case SENSORY->TissueCore.CREST;case REGENERATOR->TissueCore.ABSORPTIVE;
        };
    }
    public static Optional<EvolvedArchetype> byKey(String key){
        for(var type:EvolvedArchetype.values())if(type.key().equals(key))return Optional.of(type);return Optional.empty();
    }
    public static String[] keys(){return Arrays.stream(EvolvedArchetype.values()).map(EvolvedArchetype::key).toArray(String[]::new);}
    /** Null keeps the original mixed six-organ sample. */
    public static EnumMap<TissueCore,TissueInheritance.Organ> organs(EvolvedArchetype type){
        var genes=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        if(type==null){for(var core:LEGACY)genes.put(core,organ(core,350));return genes;}
        var main=primary(type);genes.put(main,organ(main,PRIMARY));
        for(var core:FILLERS){if(genes.size()>=6)break;if(core!=main)genes.put(core,organ(core,FILLER));}
        return genes;
    }
    private static TissueInheritance.Organ organ(TissueCore core,double strength){
        int weapons=core==TissueCore.BLADE?TissueInheritance.BLADE:core==TissueCore.GRASP?TissueInheritance.CLAWS:core==TissueCore.HYDROSTAT?TissueInheritance.TENDRIL:0;
        return new TissueInheritance.Organ(strength,0,0,0,weapons,71+core.ordinal());
    }
}