package com.codex.blacklight.infection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One saved outbreak clock for the save, shared by every dimension. */
public final class OutbreakData extends SavedData {
    private boolean active;private long started;
    private final java.util.Set<String> seeded=new java.util.HashSet<>();
    public boolean seeded(String dimension){return seeded.contains(dimension);}
    public void markSeeded(String dimension){if(seeded.add(dimension))setDirty();}
    public static OutbreakData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OutbreakData::new,OutbreakData::load),"blacklight_outbreak");}
    public boolean active(){return active;}
    public long elapsed(long now){return active?Math.max(0,now-started):0;}
    public boolean start(long now){if(active)return false;active=true;started=Math.max(0,now);setDirty();return true;}
    public static OutbreakData load(CompoundTag tag,HolderLookup.Provider registry){var d=new OutbreakData();d.active=tag.getBoolean("active");d.started=Math.max(0,tag.getLong("started"));
        var list=tag.getList("seeded",net.minecraft.nbt.Tag.TAG_STRING);for(int i=0;i<list.size();i++)d.seeded.add(list.getString(i));return d;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry){tag.putBoolean("active",active);tag.putLong("started",started);
        var list=new net.minecraft.nbt.ListTag();for(var k:seeded)list.add(net.minecraft.nbt.StringTag.valueOf(k));tag.put("seeded",list);return tag;}
}
