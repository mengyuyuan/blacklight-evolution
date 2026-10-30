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
    /** One conserved stomach per authored hive; its seven mouths cannot each mint a second reserve. */
    public final com.codex.blacklight.infection.ColonySupply[] hiveSupply={new com.codex.blacklight.infection.ColonySupply(),new com.codex.blacklight.infection.ColonySupply()};
    public final int[] hiveLoadedTicks=new int[2],hiveBirths=new int[2],hiveFeedCursor=new int[2],hiveBroodTicks=new int[2];
    public final long[] nextHiveFoodTick=new long[2],lastHiveSupplyTick={Long.MIN_VALUE,Long.MIN_VALUE};
    public final boolean[] hiveSustained=new boolean[2];
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
        var supplies=tag.getList("hiveSupply",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(2,supplies.size());i++){
            var n=supplies.getCompound(i);d.hiveSupply[i]=com.codex.blacklight.infection.ColonySupply.read(n.getCompound("supply"));
            d.hiveLoadedTicks[i]=Math.max(0,n.getInt("ticks"));d.hiveBirths[i]=Math.max(0,n.getInt("births"));d.hiveFeedCursor[i]=Math.floorMod(n.getInt("cursor"),7);
            d.hiveBroodTicks[i]=Math.clamp(n.getInt("brood"),0,CityConfig.SPEC.isLoaded()?CityConfig.HIVE_BROOD_INTERVAL.get():CityConfig.HIVE_BROOD_INTERVAL.getDefault());
        }
        for(long pos:tag.getLongArray("edited"))d.editedBlocks.add(pos);
        return d;
    }
    private static void copy(int[] from,int[] to){System.arraycopy(from,0,to,0,Math.min(from.length,to.length));}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry) {
        tag.putLong("populated",populatedMask);tag.putLong("infected",infectedMask);tag.putInt("destroyed",destroyedMask);tag.putLong("nextSpread",nextSpread);
        tag.putBoolean("population",population);tag.putBoolean("spread",spread);tag.putBoolean("facade",facade);
        tag.putLongArray("edited",editedBlocks.stream().mapToLong(Long::longValue).toArray());
        var supplies=new net.minecraft.nbt.ListTag();for(int i=0;i<2;i++){
            var n=new CompoundTag();n.put("supply",hiveSupply[i].write());n.putInt("ticks",hiveLoadedTicks[i]);n.putInt("births",hiveBirths[i]);n.putInt("cursor",hiveFeedCursor[i]);n.putInt("brood",hiveBroodTicks[i]);supplies.add(n);
        }
        tag.put("hiveSupply",supplies);tag.putInt("schema",2);
        tag.putIntArray("ports",brokenPorts);tag.putIntArray("facadeProgress",facadeProgress);tag.putIntArray("spreadProgress",spreadProgress);return tag;
    }
    public boolean destroyed(int hive){return (destroyedMask&(1<<hive))!=0;}
    public boolean infected(int x,int z){return (infectedMask&(1L<<(x+z*8)))!=0;}
}
