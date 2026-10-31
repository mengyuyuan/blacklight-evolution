package com.codex.blacklight.city;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Progress is per save, never shared across servers or seeds. Tickets are deliberately not persistent. */
final class CityGenerationData extends SavedData {
    boolean initialized,running;
    int mode=1,cursor;
    String failure="";
    String atlasFingerprint="";
    static CityGenerationData get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(CityGenerationData::load,CityGenerationData::new,"blacklight_city_generation");
    }
    static CityGenerationData load(CompoundTag tag){
        var d=new CityGenerationData();d.initialized=tag.getBoolean("initialized");d.running=tag.getBoolean("running");
        d.mode=tag.getInt("mode")==3?3:tag.getInt("mode")==2?2:1;
        d.cursor=Math.max(0,Math.min(CityGenerationPlan.cells(d.mode).size(),tag.getInt("cursor")));
        d.failure=tag.getString("failure");d.atlasFingerprint=tag.getString("atlasFingerprint");return d;
    }
    @Override public CompoundTag save(CompoundTag tag){
        tag.putBoolean("initialized",initialized);tag.putBoolean("running",running);tag.putInt("mode",mode);
        tag.putInt("cursor",cursor);tag.putString("failure",failure);tag.putString("atlasFingerprint",atlasFingerprint);return tag;
    }
}
