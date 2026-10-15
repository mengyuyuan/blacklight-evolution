package com.codex.blacklight.city;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Progress is per save, never shared across servers or seeds. Tickets are deliberately not persistent. */
final class CityGenerationData extends SavedData {
    boolean initialized,running;
    int mode=1,cursor;
    String failure="";
    static CityGenerationData get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(new Factory<>(CityGenerationData::new,CityGenerationData::load),"blacklight_city_generation");
    }
    static CityGenerationData load(CompoundTag tag,HolderLookup.Provider registry){
        var d=new CityGenerationData();d.initialized=tag.getBoolean("initialized");d.running=tag.getBoolean("running");
        d.mode=tag.getInt("mode")==2?2:1;
        d.cursor=Math.max(0,Math.min(CityGenerationPlan.cells(d.mode).size(),tag.getInt("cursor")));
        d.failure=tag.getString("failure");return d;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry){
        tag.putBoolean("initialized",initialized);tag.putBoolean("running",running);tag.putInt("mode",mode);
        tag.putInt("cursor",cursor);tag.putString("failure",failure);return tag;
    }
}
