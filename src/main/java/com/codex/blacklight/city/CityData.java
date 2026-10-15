package com.codex.blacklight.city;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-world state: reloads do not revive hives or repopulate cleared residential blocks. */
public final class CityData extends SavedData {
    public long populatedMask, infectedMask;
    public int destroyedMask;
    public final int[] brokenPorts = new int[2];
    public final int[] facadeProgress = new int[2];
    public final int[] spreadProgress = new int[64];
    public long nextSpread;
    public boolean population=true,spread=true,facade=true;
    public final java.util.Set<Long> editedBlocks=new java.util.HashSet<>();
    public static CityData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(CityData::new, CityData::load),"blacklight_city");
    }
    public static CityData load(CompoundTag tag,HolderLookup.Provider registry) {
        var d=new CityData();d.populatedMask=tag.getLong("populated");d.infectedMask=tag.getLong("infected");d.destroyedMask=tag.getInt("destroyed");
        d.nextSpread=tag.getLong("nextSpread");
        if(tag.contains("population"))d.population=tag.getBoolean("population");
        if(tag.contains("spread"))d.spread=tag.getBoolean("spread");
        if(tag.contains("facade"))d.facade=tag.getBoolean("facade");
        copy(tag.getIntArray("ports"),d.brokenPorts);copy(tag.getIntArray("facadeProgress"),d.facadeProgress);copy(tag.getIntArray("spreadProgress"),d.spreadProgress);
        for(long pos:tag.getLongArray("edited"))d.editedBlocks.add(pos);
        return d;
    }
    private static void copy(int[] from,int[] to){System.arraycopy(from,0,to,0,Math.min(from.length,to.length));}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry) {
        tag.putLong("populated",populatedMask);tag.putLong("infected",infectedMask);tag.putInt("destroyed",destroyedMask);tag.putLong("nextSpread",nextSpread);
        tag.putBoolean("population",population);tag.putBoolean("spread",spread);tag.putBoolean("facade",facade);
        tag.putLongArray("edited",editedBlocks.stream().mapToLong(Long::longValue).toArray());
        tag.putIntArray("ports",brokenPorts);tag.putIntArray("facadeProgress",facadeProgress);tag.putIntArray("spreadProgress",spreadProgress);return tag;
    }
    public boolean destroyed(int hive){return (destroyedMask&(1<<hive))!=0;}
    public boolean infected(int x,int z){return (infectedMask&(1L<<(x+z*8)))!=0;}
}
