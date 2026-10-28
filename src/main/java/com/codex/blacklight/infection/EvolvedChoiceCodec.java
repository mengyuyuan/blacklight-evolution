package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** A choice is a frozen save fact, not a fresh random roll during rendering or reload. */
public final class EvolvedChoiceCodec {
    private EvolvedChoiceCodec(){}
    public static CompoundTag write(EvolvedMapping.Choice choice){
        var tag=new CompoundTag();tag.putInt("Schema",1);tag.putString("Form",choice.form());tag.putString("Type",choice.type());tag.putString("Bias",choice.bias());
        tag.putString("Table",choice.table());tag.putLong("Seed",choice.seed());tag.putBoolean("Fallback",choice.fallback());
        var scores=new CompoundTag();choice.scores().forEach(scores::putDouble);tag.put("Scores",scores);return tag;
    }
    public static Optional<EvolvedMapping.Choice> read(CompoundTag tag){
        if(tag.getInt("Schema")!=1||!TissueInheritance.validSpecies(tag.getString("Form"))||!TissueInheritance.validSpecies(tag.getString("Type"))||!TissueInheritance.validSpecies(tag.getString("Bias"))
                ||!tag.getString("Table").matches("[0-9a-f]{64}"))return Optional.empty();
        var scores=new TreeMap<String,Double>();var saved=tag.getCompound("Scores");if(saved.size()>64)return Optional.empty();
        for(var id:saved.getAllKeys()){
            double score=saved.getDouble(id);if(!TissueInheritance.validSpecies(id)||!Double.isFinite(score)||score<0)return Optional.empty();scores.put(id,score);
        }
        return Optional.of(new EvolvedMapping.Choice(tag.getString("Form"),tag.getString("Type"),tag.getString("Bias"),tag.getString("Table"),tag.getLong("Seed"),tag.getBoolean("Fallback"),scores));
    }
}
