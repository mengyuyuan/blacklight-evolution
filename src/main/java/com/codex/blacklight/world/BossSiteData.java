package com.codex.blacklight.world;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.power.GrowthSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each dimension owns its consoles. Offline awards are queued in the Overworld. */
public final class BossSiteData extends SavedData {
    private static final String HOME = "BlacklightBossConsole";
    private static final String SQUAD = "BlacklightBossParticipants";
    private final Map<Long, UUID> active = new HashMap<>();
    private final Map<UUID, Integer> pending = new HashMap<>();

    static BossSiteData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BossSiteData::new, BossSiteData::load), "blacklight_boss_sites");
    }
    boolean occupied(BlockPos pos) { return active.containsKey(pos.asLong()); }
    void reserve(BlockPos pos, Entity boss, ServerPlayer operator) {
        boss.getPersistentData().putLong(HOME, pos.asLong());
        active.put(pos.asLong(), boss.getUUID());
        participant(boss, operator);
        setDirty();
    }
    public static void participant(Entity boss, Entity source) {
        if (!(source instanceof ServerPlayer player) || !boss.getPersistentData().contains(HOME)) return;
        var data = boss.getPersistentData();
        var squad = data.getCompound(SQUAD);
        squad.putBoolean(player.getStringUUID(), true);
        data.put(SQUAD, squad);
    }
    void release(Entity boss) {
        if (active.remove(boss.getPersistentData().getLong(HOME), boss.getUUID())) setDirty();
    }
    public static void removed(Entity boss) {
        if (boss.level() instanceof ServerLevel level && boss.getPersistentData().contains(HOME))
            get(level).release(boss);
    }
    /** Reserve the successor before publication, including synchronous entity-join callbacks. */
    boolean transfer(ServerLevel level, Entity from, Entity to) {
        var source=from.getPersistentData();
        if(!source.contains(HOME)) return false;
        long home=source.getLong(HOME);
        if(!active.replace(home,from.getUUID(),to.getUUID())) return false;
        to.getPersistentData().putLong(HOME,home);
        to.getPersistentData().put(SQUAD,source.getCompound(SQUAD).copy());
        setDirty();
        try {
            return level.addFreshEntity(to) && !to.isRemoved();
        } finally {
            if(level.getEntity(to.getUUID())!=to || to.isRemoved()) active.replace(home,to.getUUID(),from.getUUID());
        }
    }
    public static void defeated(EncounterMob boss, Entity killer) {
        if (!(boss.level() instanceof ServerLevel level) || !boss.getPersistentData().contains(HOME)) return;
        var data = get(level);
        if (!boss.getUUID().equals(data.active.get(boss.getPersistentData().getLong(HOME)))) return;
        participant(boss, killer);
        if(boss.kind()==com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE) {
            if(boss.greeneOutbreak()) {
                complete(boss,killer,4);
                return;
            }
            GreeneHost.releaseFrom(boss,level,BlockPos.of(boss.getPersistentData().getLong(HOME)));
            return;
        }
        complete(boss,killer,1);
    }
    static void complete(Entity boss, Entity finisher, int bit) {
        if(!(boss.level() instanceof ServerLevel level) || !boss.getPersistentData().contains(HOME)) return;
        var data=get(level);
        if(!boss.getUUID().equals(data.active.get(boss.getPersistentData().getLong(HOME)))) return;
        participant(boss,finisher);
        var rewards=get(level.getServer().overworld());
        for (String key : boss.getPersistentData().getCompound(SQUAD).getAllKeys()) {
            UUID id = UUID.fromString(key);
            rewards.pending.merge(id, bit, (a, b) -> a | b);
            var player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) rewards.deliver(player);
        }
        rewards.setDirty();
        data.release(boss);
        if (bit==4) return; // The outbreak advancement pays its XP once per participant.
        boss.spawnAtLocation(bit == 1 ? com.codex.blacklight.registry.ModItems.HEAVY_ARMOR_CORE.get()
                : com.codex.blacklight.registry.ModItems.MOTHER_CORE.get());
    }
    void deliver(ServerPlayer player) {
        var bits = pending.remove(player.getUUID());
        if (bits == null) return;
        if ((bits & 1) != 0) GrowthSystem.award(player, "convoy_cleared");
        if ((bits & 4) != 0) GrowthSystem.award(player,"greene_outbreak");
        if ((bits & 2) != 0) GrowthSystem.award(player, "plaza_cleared");
        setDirty();
    }
    static BossSiteData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var data = new BossSiteData();
        for (Tag entry : tag.getList("sites", Tag.TAG_COMPOUND)) {
            var site = (CompoundTag) entry;
            if (site.hasUUID("boss")) data.active.put(site.getLong("pos"), site.getUUID("boss"));
        }
        for (Tag entry : tag.getList("rewards", Tag.TAG_COMPOUND)) {
            var reward = (CompoundTag) entry;
            if (reward.hasUUID("player")) data.pending.put(reward.getUUID("player"), reward.getInt("bits"));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var sites = new ListTag();
        active.forEach((pos, id) -> {
            var entry = new CompoundTag();
            entry.putLong("pos", pos); entry.putUUID("boss", id); sites.add(entry);
        });
        var rewards = new ListTag();
        pending.forEach((id, bits) -> {
            var entry = new CompoundTag();
            entry.putUUID("player", id); entry.putInt("bits", bits); rewards.add(entry);
        });
        tag.put("sites", sites); tag.put("rewards", rewards);
        return tag;
    }
}
