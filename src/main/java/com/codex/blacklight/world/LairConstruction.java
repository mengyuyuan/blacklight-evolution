package com.codex.blacklight.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable redo journal. The cursor is session-local because chunk and level saves can reach disk independently. */
final class LairConstruction {
    record Edit(BlockState original, BlockState target) {
        boolean canApply(BlockState current, boolean hasBlockEntity) {
            return current == original && current != target && !current.is(Blocks.BEDROCK) && !hasBlockEntity;
        }
    }

    final BlockPos entrance;
    final long[] positions;
    final long[] requiredChunks;
    final int[] original, target;
    final List<BlockState> palette;
    final List<CompoundTag> hives;
    int cursor;
    long nextHiveAttempt;

    private LairConstruction(BlockPos entrance, long[] positions, int[] original, int[] target,
                             List<BlockState> palette, List<CompoundTag> hives) {
        this.entrance = entrance;
        this.positions = positions;
        this.requiredChunks = Arrays.stream(positions)
                .map(pos -> ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4)).distinct().toArray();
        this.original = original;
        this.target = target;
        this.palette = palette;
        this.hives = hives;
    }

    static LairConstruction plan(BlockPos entrance, Map<Long, Edit> edits, List<CompoundTag> hives) {
        var states = new LinkedHashMap<BlockState, Integer>();
        int count = (int) edits.values().stream().filter(edit -> edit.original != edit.target).count();
        long[] positions = new long[count];
        int[] original = new int[count], target = new int[count];
        int i = 0;
        // Seal the walls and roof before excavating air, as in the original builder.
        for (boolean air : new boolean[]{false, true}) {
            for (var entry : edits.entrySet()) {
                Edit edit = entry.getValue();
                if (edit.original == edit.target || edit.target.isAir() != air) continue;
                positions[i] = entry.getKey();
                original[i] = states.computeIfAbsent(edit.original, key -> states.size());
                target[i++] = states.computeIfAbsent(edit.target, key -> states.size());
            }
        }
        return new LairConstruction(entrance, positions, original, target, List.copyOf(states.keySet()), List.copyOf(hives));
    }

    Edit edit(int index) {
        return new Edit(palette.get(original[index]), palette.get(target[index]));
    }

    CompoundTag save() {
        var result = new CompoundTag();
        result.putLong("Entrance", entrance.asLong());
        result.putLongArray("Positions", positions);
        result.putIntArray("Original", original);
        result.putIntArray("Target", target);
        var states = new ListTag();
        palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        result.put("Palette", states);
        var sacs = new ListTag();
        hives.forEach(sacs::add);
        result.put("Hives", sacs);
        return result;
    }

    static LairConstruction load(CompoundTag tag) {
        if (!tag.contains("Entrance", Tag.TAG_LONG) || !tag.contains("Positions", Tag.TAG_LONG_ARRAY)
                || !tag.contains("Original", Tag.TAG_INT_ARRAY) || !tag.contains("Target", Tag.TAG_INT_ARRAY)
                || !compoundList(tag, "Palette") || !compoundList(tag, "Hives"))
            throw new IllegalArgumentException("missing or mistyped construction fields");
        long[] positions = tag.getLongArray("Positions");
        int[] original = tag.getIntArray("Original"), target = tag.getIntArray("Target");
        if (positions.length != original.length || positions.length != target.length)
            throw new IllegalArgumentException("block edit arrays have different lengths");
        var palette = new ArrayList<BlockState>();
        for (var entry : tag.getList("Palette", Tag.TAG_COMPOUND))
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag)entry));
        var hives = new ArrayList<CompoundTag>();
        for (int i = 0; i < positions.length; i++) {
            if (original[i] < 0 || original[i] >= palette.size() || target[i] < 0 || target[i] >= palette.size())
                throw new IllegalArgumentException("unknown block palette index at edit " + i);
        }
        for (var entry : tag.getList("Hives", Tag.TAG_COMPOUND)) {
            var hive = (CompoundTag) entry;
            var pos = hive.getList("Pos", Tag.TAG_DOUBLE);
            if (!hive.hasUUID("UUID") || pos.size() != 3 || !Double.isFinite(pos.getDouble(0))
                    || !Double.isFinite(pos.getDouble(1)) || !Double.isFinite(pos.getDouble(2)))
                throw new IllegalArgumentException("invalid hive identity or position");
            hives.add(hive);
        }
        return new LairConstruction(BlockPos.of(tag.getLong("Entrance")), positions, original, target, palette, hives);
    }

    private static boolean compoundList(CompoundTag tag, String key) {
        return tag.get(key) instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND);
    }
}
