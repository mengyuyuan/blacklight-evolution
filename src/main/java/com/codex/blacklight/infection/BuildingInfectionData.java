package com.codex.blacklight.infection;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Bounded surface frontier; no per-block entities, chunk tickets or wall-clock catch-up. */
public final class BuildingInfectionData extends SavedData {
    public final List<Colony> colonies = new ArrayList<>();
    public int cursor;
    public static final int MAX_SEEN = 65536;
    public static final int MAX_CLEARED = 32768;
    /** Compact history is independent of live colony/frontier slots. */
    public final Set<Long> clearedSites = new LinkedHashSet<>();
    public void rememberCleared(Colony colony) {
        if (clearedSites.size() < MAX_CLEARED) clearedSites.add(colony.origin.asLong());
    }
    public long activeColonies() { return colonies.stream().filter(c -> !c.dead).count(); }
    public void compactCleared() {
        for (var colony : colonies) if (colony.dead) rememberCleared(colony);
        colonies.removeIf(c -> c.dead && clearedSites.contains(c.origin.asLong()));
    }
    public static final class Colony {
        public final BlockPos origin;
        public final ArrayDeque<Long> frontier = new ArrayDeque<>();
        public final Set<Long> seen = new HashSet<>();
        public final List<Long> surfaces = new ArrayList<>();
        public int activeTicks, stage, ageCursor, broodTicks, births, workTurns;
        public ColonySupply supply=new ColonySupply();
        public long nextFoodTick,lastSupplyTick=Long.MIN_VALUE;
        public BuildingBiomassPlan mass;
        public int massCursor, massDisabled, massBlocks, massAttempt, massSupportCheck;
        public final Set<Long> severed=new HashSet<>();
        public boolean dead,sustained;
        public Colony(BlockPos origin) { this.origin = origin.immutable(); }
        public void offer(BlockPos pos) {
            if (seen.size() < MAX_SEEN && seen.add(pos.asLong())) frontier.addLast(pos.asLong());
        }
    }
    public static BuildingInfectionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(BuildingInfectionData::new, BuildingInfectionData::load), "blacklight_building_infection");
    }
    public static BuildingInfectionData load(CompoundTag tag, HolderLookup.Provider registry) {
        var data = new BuildingInfectionData();
        for (long origin : tag.getLongArray("clearedSites")) {
            if (data.clearedSites.size() >= MAX_CLEARED) break;
            data.clearedSites.add(origin);
        }
        var entries = tag.getList("colonies", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(64, entries.size()); i++) {
            var n = entries.getCompound(i);
            var c = new Colony(BlockPos.of(n.getLong("origin")));
            c.activeTicks = Math.max(0, n.getInt("ticks")); c.stage = Math.clamp(n.getInt("stage"), 0, 3);
            c.dead = n.getBoolean("dead"); c.broodTicks = Math.clamp(n.getInt("brood"), 0, 600);
            if (c.dead) { data.rememberCleared(c); continue; }
            c.births = Math.max(0, n.getInt("births"));
            c.supply=ColonySupply.read(n.getCompound("supply"));
            c.workTurns = Math.floorMod(n.getInt("workTurns"), 4);
            if(n.contains("mass"))c.mass=BuildingBiomassPlan.load(n.getCompound("mass"));
            c.massCursor=Math.max(0,n.getInt("massCursor"));c.massDisabled=n.getInt("massDisabled")&255;
            c.massBlocks=Math.clamp(n.getInt("massBlocks"),0,BuildingBiomassPlan.CELL_LIMIT);
            for(long p:n.getLongArray("severed")){if(c.severed.size()>=BuildingBiomassPlan.CELL_LIMIT)break;c.severed.add(p);}
            for (long p : n.getLongArray("seen")) { if (c.seen.size() >= MAX_SEEN) break; c.seen.add(p); }
            var unique = new HashSet<Long>();
            for (long p : n.getLongArray("frontier")) {
                if (c.frontier.size() >= MAX_SEEN) break;
                if (unique.add(p)) { c.frontier.addLast(p); c.seen.add(p); }
            }
            unique.clear();
            for (long p : n.getLongArray("surfaces")) {
                if (c.surfaces.size() >= 16384) break;
                if (unique.add(p)) c.surfaces.add(p);
            }
            c.ageCursor = c.surfaces.isEmpty() ? 0 : Math.floorMod(n.getInt("ageCursor"), c.surfaces.size());
            if (!data.clearedSites.contains(c.origin.asLong())) data.colonies.add(c);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
        compactCleared();
        var entries = new ListTag();
        for (var c : colonies) {
            var n = new CompoundTag();
            n.putLong("origin", c.origin.asLong()); n.putInt("ticks", c.activeTicks); n.putInt("stage", c.stage);
            n.putBoolean("dead", c.dead); n.putInt("brood", c.broodTicks); n.putInt("births", c.births); n.putInt("ageCursor", c.ageCursor);
            n.putInt("workTurns", c.workTurns);
            n.put("supply",c.supply.write());
            if(c.mass!=null)n.put("mass",c.mass.save());
            n.putInt("massCursor",c.massCursor);n.putInt("massDisabled",c.massDisabled);n.putInt("massBlocks",c.massBlocks);
            n.putLongArray("severed",c.severed.stream().mapToLong(Long::longValue).toArray());
            n.putLongArray("seen", c.seen.stream().mapToLong(Long::longValue).toArray());
            n.putLongArray("frontier", c.frontier.stream().mapToLong(Long::longValue).toArray());
            n.putLongArray("surfaces", c.surfaces.stream().mapToLong(Long::longValue).toArray());
            entries.add(n);
        }
        tag.putInt("schema", 3);
        tag.putLongArray("clearedSites", clearedSites.stream().mapToLong(Long::longValue).toArray());
        tag.put("colonies", entries); return tag;
    }
}
