package com.codex.blacklight.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight")
@PrefixGameTestTemplate(false)
public final class WorldSaveRecoveryGameTest {
    @GameTest(template="wall_test")
    public static void isolatedInvalidLairDoesNotBlockValidWork(GameTestHelper h) {
        var level = h.getLevel();
        var good = h.absolutePos(new BlockPos(2, 2, 2));
        var bad = good.east();
        var edit = new LairConstruction.Edit(Blocks.STONE.defaultBlockState(), Blocks.TUFF.defaultBlockState());
        level.setBlock(good, edit.original(), 2);
        level.setBlock(bad, edit.original(), 2);
        var data = new LairSystem.Data();
        data.jobs.add(LairConstruction.plan(good, Map.of(good.asLong(), edit), List.of()));
        var tag = data.save(new CompoundTag());
        var entries = tag.getList("Construction", Tag.TAG_COMPOUND);
        var truncated = LairConstruction.plan(bad, Map.of(bad.asLong(), edit), List.of()).save();
        var invalidIndex = truncated.copy();
        invalidIndex.putIntArray("Original", new int[]{-1});
        truncated.putIntArray("Target", new int[0]);
        entries.add(0, truncated);
        entries.add(invalidIndex);

        var recovered = LairSystem.Data.load(tag);
        for (var job : recovered.jobs) LairSystem.placeBlocks(level, job, Long.MAX_VALUE);
        h.assertTrue(level.getBlockState(good).is(Blocks.TUFF) && level.getBlockState(bad).is(Blocks.STONE),
                "only the intact job may change terrain");
        h.assertTrue(recovered.jobs.size() == 1, "only the intact job is scheduled");
        var saved = recovered.save(new CompoundTag()).getList("Construction", Tag.TAG_COMPOUND);
        h.assertTrue(saved.contains(truncated) && saved.contains(invalidIndex), "preserve incomplete records for recovery");
        h.succeed();
    }

    @GameTest(template="wall_test")
    public static void recoveredHiveRequiresItsSavedIdentity(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(2, 2, 2));
        var seed = com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HIVE.get().create(level);
        seed.moveTo(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 0, 0);
        var hive = seed.saveWithoutId(new CompoundTag());
        hive.remove("UUID");
        var record = LairConstruction.plan(origin, Map.of(), List.of(hive)).save();
        var entries = new ListTag();
        entries.add(record);
        var tag = new CompoundTag();
        tag.put("Construction", entries);
        var recovered = LairSystem.Data.load(tag);
        for (var job : recovered.jobs) LairSystem.spawnHives(level, job);
        h.assertTrue(recovered.jobs.isEmpty(), "hive without a saved identity must not respawn");
        h.assertTrue(recovered.save(new CompoundTag()).getList("Construction", Tag.TAG_COMPOUND).contains(record),
                "retain the original hive data");
        h.succeed();
    }

    @GameTest(template="wall_test")
    public static void invalidParticipantDoesNotAbortBossRewards(GameTestHelper h) {
        var level = h.getLevel();
        var home = h.absolutePos(new BlockPos(2, 2, 2));
        var boss = com.codex.blacklight.registry.EncounterEntities.CREATURES.get(com.codex.blacklight.entity.EncounterKind.GOLIATH).get().create(level);
        boss.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(home));
        var sites = BossSiteData.get(level);
        sites.reserve(home, boss, null);
        var squad = new CompoundTag();
        var offline = UUID.randomUUID();
        squad.putBoolean(offline.toString(), true);
        squad.putString("legacy_note", "old record");
        boss.getPersistentData().put("BlacklightBossParticipants", squad);
        BossSiteData.defeated(boss, null);
        h.assertTrue(!sites.occupied(home), "completed encounter releases its site");
        var rewards = BossSiteData.get(level.getServer().overworld()).save(new CompoundTag())
                .getList("rewards", Tag.TAG_COMPOUND);
        h.assertTrue(rewards.stream().map(CompoundTag.class::cast).anyMatch(entry ->
                offline.equals(entry.getUUID("player")) && entry.getInt("bits") == 1), "valid offline player earns the reward");
        h.succeed();
    }
}
