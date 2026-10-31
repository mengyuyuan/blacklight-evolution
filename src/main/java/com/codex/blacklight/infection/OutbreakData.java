package com.codex.blacklight.infection;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One saved outbreak clock for the save, shared by every dimension. */
public final class OutbreakData extends SavedData {
    private boolean active;private long started;
    public static OutbreakData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(OutbreakData::load,OutbreakData::new,"blacklight_outbreak");}
    public boolean active(){return active;}
    public long elapsed(long now){return active?Math.max(0,now-started):0;}
    public boolean start(long now){if(active)return false;active=true;started=Math.max(0,now);setDirty();return true;}
    public static OutbreakData load(CompoundTag tag){var d=new OutbreakData();d.active=tag.getBoolean("active");d.started=Math.max(0,tag.getLong("started"));return d;}
    @Override public CompoundTag save(CompoundTag tag){tag.putBoolean("active",active);tag.putLong("started",started);return tag;}
}
