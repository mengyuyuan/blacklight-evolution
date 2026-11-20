package com.codex.blacklight.infection;

import java.util.*;

/** Operator sample organs. One dominant mature organ chooses the archetype through the normal mapping table. */
public final class EvolvedSamplePreset {
    private static final double PRIMARY=600,FILLER=150;
    private static final TissueCore[] LEGACY={TissueCore.MUSCLE,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.BLADE,TissueCore.HYDROSTAT};
    private static final TissueCore[] FILLERS={TissueCore.HEART,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.CREST,TissueCore.TENDON};
    public static com.codex.blacklight.entity.BlacklightInfectedHost matureHost(net.minecraft.server.level.ServerLevel level,
            EvolvedArchetype archetype) {
        var host=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(level);if(host==null)return null;
        host.inherit(net.minecraft.world.entity.EntityType.ZOMBIE.create(level));
        var genes=com.codex.blacklight.infection.EvolvedSamplePreset.organs(archetype);
        long seed=com.codex.blacklight.infection.TissueOrgans.seed(host.getUUID());
        var inventory=new com.codex.blacklight.infection.TissueInheritance(genes,6,2100,false,null,1,6).digest(1,true,seed,504000,com.codex.blacklight.infection.TissueInheritance.Rules.DEFAULT,com.codex.blacklight.infection.TissueCore.MUSCLE);
        var tag=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",com.codex.blacklight.infection.TissueCore.MUSCLE.key());
        tag.putLong("BlacklightTissueAge",504000);tag.put("BlacklightTissueInheritance",com.codex.blacklight.infection.TissueInheritanceCodec.write(inventory));tag.putInt("BlacklightTissueNutrition",100);host.load(tag);host.advanceTissue(0);
        return host;
    }

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