package com.codex.blacklight.command;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.HiveAcceleration;
import com.codex.blacklight.world.ColossalHiveLayout;
import com.codex.blacklight.world.ColossalHivePiece;
import com.codex.blacklight.world.ColossalHiveStructure;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Player-requested hive work, sliced on the server thread. No worker accesses worlds,
 * no task retains chunk tickets, and no task survives logout or world shutdown. */
@EventBusSubscriber(modid = "blacklight")
public final class HiveCommands {
    private static final ResourceKey<Structure> HIVE = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation("blacklight", "colossal_hive"));
    private static final int DEFAULT_RADIUS = 32768;
    private static final int MAX_CANDIDATES = 8192;
    private static final int COLUMNS_PER_TICK = 32;
    private static final long WORK_NANOS = 5_000_000L;
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

    private HiveCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("hive").requires(s -> s.hasPermission(2))
                .then(Commands.literal("locate")
                        .executes(c -> locate(c.getSource().getPlayerOrException(), DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1024, 65536))
                                .executes(c -> locate(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("place").executes(c -> place(c.getSource().getPlayerOrException())))
                .then(Commands.literal("lair").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var result = com.codex.blacklight.world.LairSystem.tryPlace(p.serverLevel(), p.getBlockX(), p.getBlockZ(), true);
                    p.sendSystemMessage(Component.literal("母巢: " + result + " " + com.codex.blacklight.world.LairSystem.lairs(p.serverLevel())));
                    return result == com.codex.blacklight.world.LairSystem.Result.PLACED ? 1 : 0; }))
                .then(Commands.literal("accelerate")
                        .executes(c -> accelerate(c.getSource().getPlayerOrException(), 20))
                        .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 20))
                                .executes(c -> accelerate(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "multiplier")))))
                .then(Commands.literal("cancel").executes(c -> cancel(c.getSource().getPlayerOrException())))
                .then(Commands.literal("status").executes(c -> status(c.getSource().getPlayerOrException())));
    }

    private static int accelerate(ServerPlayer player, int multiplier) {
        ServerLevel level = player.serverLevel();
        BlacklightHive hive = level.getEntitiesOfClass(BlacklightHive.class, player.getBoundingBox().inflate(96),
                core -> core.isAlive() && core.getPersistentData().getBoolean("BlacklightColossalHive")
                        && core.distanceToSqr(player) <= 96 * 96).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (hive == null) { tell(player, "96 格内没有已加载的存活巨巢核心。", true); return 0; }
        if (multiplier == 1) {
            HiveAcceleration.clear(hive);
            tell(player, "巨巢 " + location(hive.blockPosition()) + " 已恢复 1× 正常培育与进食间隔。", false);
            return 1;
        }
        var state = hive.getPersistentData().getCompound("BlacklightColossusEvolution");
        if (state.getBoolean("Spent")) { tell(player, "这座巨巢已经完成巨构诞生，无需加速。", true); return 0; }
        BlacklightInfectedHost host = level.getEntitiesOfClass(BlacklightInfectedHost.class,
                hive.getBoundingBox().inflate(96), candidate -> candidate.isAlive() && !candidate.giantBoss()
                        && HiveAcceleration.belongsTo(candidate, hive)
                        && (!state.hasUUID("Chosen") || state.getUUID("Chosen").equals(candidate.getUUID()))).stream()
                .min(Comparator.comparingDouble(hive::distanceToSqr)).orElse(null);
        if (host == null) {
            tell(player, "该巨巢的配对吞噬者尚未加载、已失去，或已选中其他宿主；未改变倍率。", true); return 0;
        }
        HiveAcceleration.configure(hive, host, multiplier);
        tell(player, "仅巨巢 " + location(hive.blockPosition()) + " 与配对吞噬者设为 " + multiplier
                + "×：成熟、成长和餐间等待加速；每次吞噬仍完整播放 4 秒，生物质仍靠真实进食。"
                + "用 /blacklight colossus_status 查看暂停原因，/blacklight hive accelerate 1 恢复正常；巨构诞生后自动恢复。", false);
        return 1;
    }

    private static boolean available(ServerPlayer player) {
        if (JOBS.containsKey(player.getUUID())) {
            tell(player, "已有蜂巢任务。查看 /blacklight hive status，或先 /blacklight hive cancel。", true);
            return false;
        }
        if (JOBS.values().stream().filter(j -> j.level.getServer() == player.getServer()).count() >= 4) {
            tell(player, "当前蜂巢任务已达 4 个，请等任务完成后再试。", true);
            return false;
        }
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            tell(player, "巨型蜂巢位于主世界，请回主世界执行。", true);
            return false;
        }
        return true;
    }

    private static int locate(ServerPlayer player, int radius) {
        if (!available(player)) return 0;
        var level = player.serverLevel();
        // Manual placement, like vanilla /place, does not insert a natural structure
        // start. Its loaded core still exposes the same home coordinate for discovery.
        BlacklightHive nearby = null;
        double best = (double) radius * radius;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof BlacklightHive hive && hive.isAlive()
                    && hive.getPersistentData().getBoolean("BlacklightColossalHive")) {
                double distance = hive.distanceToSqr(player);
                if (distance < best) { nearby = hive; best = distance; }
            }
        }
        if (nearby != null) {
            BlockPos home = BlockPos.of(nearby.getPersistentData().getLong("BlacklightHiveHome"));
            coordinates(player, "已找到加载中的巨巢，入口：", home.offset(0, 0, -30), false);
            return 1;
        }
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(HIVE);
        if (holder.isEmpty()) { tell(player, "巨巢结构未注册，请确认已安装当前版本。", true); return 0; }
        var state = level.getChunkSource().getGeneratorState();
        var placements = state.getPlacementsForStructure(holder.get()).stream()
                .filter(p -> p instanceof RandomSpreadStructurePlacement)
                .map(p -> (RandomSpreadStructurePlacement) p).toList();
        if (placements.isEmpty()) {
            tell(player, "当前世界生成器没有启用巨巢自然生成（部分超平坦预设如此）。可在干地使用 /blacklight hive place。", true);
            return 0;
        }
        // A data pack can reduce spacing far below the shipped 80 chunks. Bound
        // the initial enumeration too, before allocating/sorting its candidates.
        long probes = 0;
        for (var placement : placements) {
            long rings = (long) Math.ceil(radius / (placement.spacing() * 16.0)) + 1;
            probes += (2 * rings + 1) * (2 * rings + 1);
            if (probes > 65536) {
                tell(player, "当前结构配置的候选密度过高，请减小搜索半径，例如 /blacklight hive locate 1024。尚未开始搜索。", true);
                return 0;
            }
        }
        var job = new LocateJob(player, holder.get(), placements, radius);
        JOBS.put(player.getUUID(), job);
        tell(player, "开始寻找巨巢，半径 " + radius + " 格，最多 " + job.candidates.size()
                + " 个候选、180 秒；逐 tick 搜索。可用 /blacklight hive cancel 取消。", false);
        return 1;
    }

    private static int place(ServerPlayer player) {
        if (!available(player)) return 0;
        ServerLevel level = player.serverLevel();
        if (JOBS.values().stream().anyMatch(j -> j.level == level && j instanceof PlaceJob)) {
            tell(player, "本世界已有巨巢正在建造，请等它结束后再放置。", true); return 0;
        }
        int x = player.getBlockX(), z = player.getBlockZ();
        // Check loading before any heightmap or block query. getChunkNow never loads.
        BoundingBox horizontal = new BoundingBox(x - ColossalHiveLayout.RADIUS, level.getMinBuildHeight(), z - ColossalHiveLayout.RADIUS,
                x + ColossalHiveLayout.RADIUS, level.getMaxBuildHeight() - 1, z + ColossalHiveLayout.RADIUS);
        String unavailable = unloaded(level, horizontal);
        if (unavailable != null) { tell(player, unavailable, true); return 0; }
        if (!insideBorder(level, horizontal)) { tell(player, "巨巢的完整 113×113 格范围超出世界边界，中心 " + x + ", " + z + "。", true); return 0; }
        int[] heights = new int[49]; int index = 0, low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx = -48; dx <= 48; dx += 16) for (int dz = -48; dz <= 48; dz += 16) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
            BlockPos ground = new BlockPos(x + dx, y, z + dz);
            BlockState state = level.getBlockState(ground);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                tell(player, "选址失败：需要连续干地，" + location(ground) + " 是水面、熔岩或空地。", true); return 0;
            }
            low = Math.min(low, y); high = Math.max(high, y);
            if (high - low > 12) {
                tell(player, "选址失败：" + location(ground) + " 附近高差 " + (high - low) + " 格，超过 12 格；请换开阔平地。", true); return 0;
            }
            heights[index++] = y;
        }
        Arrays.sort(heights);
        int floor = ColossalHiveStructure.supportedFloor(heights[24], level.getMinBuildHeight());
        if (floor + ColossalHiveLayout.MAX_Y >= level.getMaxBuildHeight()) {
            tell(player, "选址失败：地板 Y=" + floor + " 加 64 格屋顶超出世界高度。", true); return 0;
        }
        var centre = new BlockPos(x, floor, z);
        var piece = new ColossalHivePiece(centre, level.getSeed() ^ centre.asLong() ^ 0x484956454C4F4E47L);
        String protectedReason = protectedStructure(level, piece.getBoundingBox());
        if (protectedReason != null) { tell(player, protectedReason, true); return 0; }
        if (!level.getEntitiesOfClass(BlacklightHive.class, box(piece.getBoundingBox()), BlacklightHive::isAlive).isEmpty()) {
            tell(player, "选址范围已有蜂巢核心，拒绝重复覆盖。请换位置。", true); return 0;
        }
        var job = new PlaceJob(player, centre, piece);
        JOBS.put(player.getUUID(), job);
        tell(player, "开始预检巨巢范围 " + horizontalRange(piece.getBoundingBox()) + "，地板 Y=" + floor
                + "；检查完成后逐列建造，完成前不生成居民。若你仍在建造范围内，开工前会移到入口安全平台。请保持附近区块加载。可用 /blacklight hive cancel 取消；已放置部分不会回滚。", false);
        return 1;
    }

    private static int cancel(ServerPlayer player) {
        Job job = JOBS.remove(player.getUUID());
        if (job == null) { tell(player, "没有正在执行的蜂巢任务。", true); return 0; }
        job.fail("已取消蜂巢任务。"); return 1;
    }

    private static int status(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        tell(player, job == null ? "没有正在执行的蜂巢任务。" : job.progress(), false);
        return job == null ? 0 : 1;
    }

    @SubscribeEvent public static void tick(ServerTickEvent event) {if(event.phase != net.minecraftforge.event.TickEvent.Phase.END)return;
        for (Job job : List.copyOf(JOBS.values())) {
            if (job.level.getServer() != event.getServer()) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.owner);
            if (player == null || player.serverLevel() != job.level) job.fail("玩家离线或切换维度，蜂巢任务已取消。");
            else if (System.nanoTime() - job.started > job.timeoutNanos) job.fail("蜂巢任务达到时间上限，已停止。");
            else {
                try { job.step(); }
                catch (RuntimeException error) {
                    BlacklightNeoMod.LOGGER.error("Hive task failed for {}", job.owner, error);
                    job.fail("蜂巢任务异常，已停止；详情见日志。");
                }
                if (!job.done && ++job.ticks % 100 == 0) tell(player, job.progress(), false);
            }
            if (job.done) JOBS.remove(job.owner, job);
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Job job = JOBS.remove(player.getUUID());
            if (job != null) job.fail("玩家退出，蜂巢任务已取消。");
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        for (Job job : List.copyOf(JOBS.values())) if (job.level == event.getLevel()) {
            job.fail("世界卸载，蜂巢任务已取消。"); JOBS.remove(job.owner, job);
        }
    }

    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for (Job job : List.copyOf(JOBS.values())) if (job.level.getServer() == event.getServer()) {
            job.fail("服务器关闭，蜂巢任务已取消。"); JOBS.remove(job.owner, job);
        }
    }

    private abstract static class Job {
        final UUID owner;
        final ServerLevel level;
        final long started = System.nanoTime();
        final long timeoutNanos;
        int ticks;
        boolean done;
        Job(ServerPlayer player, int seconds) { owner = player.getUUID(); level = player.serverLevel(); timeoutNanos = seconds * 1_000_000_000L; }
        abstract void step();
        abstract String progress();
        String partialNotice() { return ""; }
        void fail(String reason) {
            if (done) return;
            done = true;
            String message = reason + partialNotice();
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) tell(player, message, true);
            BlacklightNeoMod.LOGGER.info("Hive task {}: {}", owner, message);
        }
    }

    private record Candidate(ChunkPos chunk, RandomSpreadStructurePlacement placement, long distance) {}
    private record StructureReference(Structure structure, long chunk) {}

    private static final class LocateJob extends Job {
        final Holder.Reference<Structure> structure;
        final List<Candidate> candidates;
        final int radius;
        int checked;
        LocateJob(ServerPlayer player, Holder.Reference<Structure> structure, List<RandomSpreadStructurePlacement> placements, int radius) {
            super(player, 180); this.structure = structure; this.radius = radius;
            List<Candidate> result = new ArrayList<>(); var seen = new HashSet<Long>();
            ChunkPos origin = player.chunkPosition();
            long seed = level.getChunkSource().getGeneratorState().getLevelSeed();
            for (var placement : placements) {
                int spacing = placement.spacing(), rings = (int) Math.ceil(radius / (spacing * 16.0)) + 1;
                for (int dx = -rings; dx <= rings; dx++) for (int dz = -rings; dz <= rings; dz++) {
                    ChunkPos chunk = placement.getPotentialStructureChunk(seed, origin.x + dx * spacing, origin.z + dz * spacing);
                    long x = chunk.getMiddleBlockX() - (long) player.getBlockX(), z = chunk.getMiddleBlockZ() - (long) player.getBlockZ();
                    long distance = x * x + z * z;
                    if (distance <= (long) radius * radius && seen.add(chunk.toLong()) && level.getWorldBorder().isWithinBounds(chunk))
                        result.add(new Candidate(chunk, placement, distance));
                }
            }
            result.sort(Comparator.comparingLong(Candidate::distance));
            candidates = List.copyOf(result.subList(0, Math.min(MAX_CANDIDATES, result.size())));
        }
        @Override void step() {
            if (checked >= candidates.size()) { fail("半径 " + radius + " 格内的 " + checked + " 个候选未找到巨巢。可扩大半径（最大 65536），或使用 /blacklight hive place。"); return; }
            // At most one native terrain/structure check per tick, never a synchronous
            // findNearestMapStructure(radius) loop and never full chunk generation.
            Candidate candidate = candidates.get(checked++);
            var position = candidate.chunk();
            var generatorState = level.getChunkSource().getGeneratorState();
            if (!candidate.placement().isStructureChunk(generatorState, position.x, position.z)) return;
            var loaded = level.getChunkSource().getChunkNow(position.x, position.z);
            StructureCheckResult presence;
            if (loaded != null) {
                var start = loaded.getStartForStructure(structure.value());
                if (start == null || !start.isValid()) return; // An old generated chunk cannot grow a missing start.
                presence = StructureCheckResult.START_PRESENT;
            } else presence = level.structureManager().checkStructurePresence(position, structure.value(), false);
            if (presence == StructureCheckResult.START_NOT_PRESENT) return;
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player == null) { fail("玩家已退出。"); return; }
            if (presence == StructureCheckResult.START_PRESENT) {
                coordinates(player, "已定位自然巨巢：", candidate.placement().getLocatePos(position), true);
            } else {
                // A storage-format fallback can also request a chunk load. Recheck
                // terrain/biome, but label the result as a candidate, never as built.
                var generator = level.getChunkSource().getGenerator();
                var context = new Structure.GenerationContext(level.registryAccess(), generator, generator.getBiomeSource(),
                        level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), position, level,
                        structure.value().biomes()::contains);
                var stub = structure.value().findValidGenerationPoint(context);
                if (stub.isEmpty()) return;
                coordinates(player, "新区域候选的预期入口（尚未加载生成核实）：", stub.get().position().offset(0, 1, -30), false);
                tell(player, "此结果是生成候选，不代表已存在的蜂巢；进入该区域后由世界生成确认。", false);
            }
            done = true;
        }
        @Override String progress() { return "巨巢搜索：" + checked + "/" + candidates.size() + " 个候选，半径 " + radius + " 格；/blacklight hive cancel 可取消。"; }
    }

    private static final class PlaceJob extends Job {
        final BlockPos centre;
        final ColossalHivePiece piece;
        final BoundingBox bounds;
        final int width, depth, columns;
        int cursor, changed;
        boolean building, residentsStarted, movedOwner;
        PlaceJob(ServerPlayer player, BlockPos centre, ColossalHivePiece piece) {
            super(player, 240); this.centre = centre; this.piece = piece; bounds = piece.getBoundingBox();
            width = bounds.maxX() - bounds.minX() + 1; depth = bounds.maxZ() - bounds.minZ() + 1; columns = width * depth;
        }
        @Override void step() {
            String missing = unloaded(level, bounds);
            if (missing != null) { fail(missing); return; }
            if (!insideBorder(level, bounds)) { fail("世界边界已改变，巨巢任务停止。"); return; }
            long deadline = System.nanoTime() + WORK_NANOS;
            int processed = 0;
            var at = new BlockPos.MutableBlockPos();
            var players = building ? level.getPlayers(p -> !p.isSpectator() && p.getBoundingBox().intersects(box(bounds))) : List.<ServerPlayer>of();
            while (cursor < columns && processed < COLUMNS_PER_TICK && (processed == 0 || System.nanoTime() < deadline)) {
                int x = bounds.minX() + cursor / depth, z = bounds.minZ() + cursor % depth;
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    at.set(x, y, z);
                    BlockState planned = piece.plannedState(at);
                    if (planned == null) continue;
                    BlockState old = level.getBlockState(at);
                    if (!ColossalHivePiece.canReplace(old)) {
                        String reason = !old.getFluidState().isEmpty() ? "流体" : old.hasBlockEntity() ? "方块实体" : "受保护的非地形方块";
                        fail((building ? "建造期间发现" : "预检发现") + reason + "，位置 " + location(at) + "；请换干净的空旷地段。"); return;
                    }
                    if (building && !old.equals(planned)) {
                        if (planned.isSolid() && players.stream().anyMatch(p -> occupies(p, at))) {
                            fail("玩家进入了即将填充实体方块的位置 " + location(at) + "，为避免埋住玩家，建造已停止。"); return;
                        }
                        if (!level.setBlock(at, planned, 2)) { fail("方块放置失败，位置 " + location(at) + "。"); return; }
                        changed++;
                    }
                }
                cursor++; processed++;
            }
            if (cursor < columns) return;
            if (!building) {
                // Recheck references immediately before the first write. No large
                // unbounded block scan occurs in the command's initial invocation.
                String protectedReason = protectedStructure(level, bounds);
                if (protectedReason != null) { fail(protectedReason); return; }
                if (!otherPlayersSafe()) return;
                building = true; cursor = 0;
                if (!prepareEntrance()) return;
                var player = level.getServer().getPlayerList().getPlayer(owner);
                if (player != null) tell(player, "巨巢预检通过，开始逐列建造；居民将在整座结构完成后生成。", false);
                return;
            }
            // All 113x113 columns have completed. Spawn each slot in its owning full
            // chunk, preserving the natural piece's stable UUIDs and clearance checks.
            residentsStarted = true;
            for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
                for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                    BoundingBox clip = new BoundingBox(cx * 16, level.getMinBuildHeight(), cz * 16,
                            cx * 16 + 15, level.getMaxBuildHeight() - 1, cz * 16 + 15);
                    piece.spawnResidents(level, clip, List.of());
                }
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                tell(player, "巨巢建造完成，共放置 " + changed + " 个方块。", false);
                coordinates(player, "入口坐标：", centre.offset(0, 1, -30), false);
            }
            done = true;
        }
        private boolean otherPlayersSafe() {
            var at = new BlockPos.MutableBlockPos();
            for (var player : level.getPlayers(p -> !p.getUUID().equals(owner) && !p.isSpectator()
                    && p.getBoundingBox().intersects(box(bounds)))) {
                var body = player.getBoundingBox();
                for (int x = (int) Math.floor(body.minX); x < Math.ceil(body.maxX); x++)
                    for (int z = (int) Math.floor(body.minZ); z < Math.ceil(body.maxZ); z++)
                        for (int y = (int) Math.floor(body.minY); y < Math.ceil(body.maxY); y++) {
                            at.set(x, y, z);
                            if (!bounds.isInside(at)) continue;
                            var planned = piece.plannedState(at);
                            if (planned != null && planned.isSolid() && !level.getBlockState(at).equals(planned)) {
                                fail("其他玩家 " + player.getScoreboardName() + " 位于即将填充的位置 " + location(at)
                                        + "；请其先离开后重试。没有移动其他玩家。"); return false;
                            }
                        }
            }
            return true;
        }
        private boolean prepareEntrance() {
            BlockPos entrance = centre.offset(0, 1, -30);
            List<BlockPos> positions = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos at = entrance.offset(dx, dy, dz);
                    var planned = piece.plannedState(at);
                    if (planned == null || (dy == -1 ? !planned.isSolid() : !planned.isAir())
                            || !ColossalHivePiece.canReplace(level.getBlockState(at))) {
                        fail("入口平台无法安全准备，位置 " + location(at) + "；未传送玩家。"); return false;
                    }
                    positions.add(at);
                }
            // These exact voxels are part of the shared layout, not an extra platform.
            for (BlockPos at : positions) {
                var planned = piece.plannedState(at);
                if (!level.getBlockState(at).equals(planned)) {
                    if (!level.setBlock(at, planned, 2)) { fail("入口平台放置失败，位置 " + location(at) + "；未传送玩家。"); return false; }
                    changed++;
                }
            }
            var player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null && player.getBoundingBox().intersects(box(bounds))) {
                var destination = player.getBoundingBox().move(entrance.getX() + .5 - player.getX(),
                        entrance.getY() - player.getY(), entrance.getZ() + .5 - player.getZ());
                if (!level.noCollision(player, destination)) { fail("入口净空仍被占据，已停止；未传送玩家。"); return false; }
                player.teleportTo(level, entrance.getX() + .5, entrance.getY(), entrance.getZ() + .5, 0, 0);
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); player.fallDistance = 0;
                movedOwner = true;
                tell(player, "入口安全平台已完成，已将你移到 " + location(entrance) + "。整座巨巢继续建造，请停留附近。", false);
            }
            return true;
        }
        @Override String progress() { return "巨巢" + (building ? "建造" : "预检") + "：" + cursor * 100 / columns + "%（" + cursor + "/" + columns + " 列），已放置 " + changed + " 个方块。"; }
        @Override String partialNotice() {
            return changed == 0 ? " 尚未修改任何方块。" : " 已放置 " + changed + " 个方块，保留在 " + horizontalRange(bounds)
                    + "、地板 Y=" + centre.getY() + "；没有回滚。" + (movedOwner ? " 已准备的入口平台也会保留。" : "")
                    + (residentsStarted ? " 居民生成阶段中止，部分居民可能已经出现。" : " 未生成居民。");
        }
    }

    /** Reject missing chunks without getChunk(), tickets, or synchronous generation. */
    private static String unloaded(ServerLevel level, BoundingBox bounds) {
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null)
                    return "范围未完整加载：缺少区块 " + x + ", " + z + "（方块 " + (x * 16) + ", " + (z * 16)
                            + "）。需要保持整个 " + horizontalRange(bounds) + " 区域加载；请在中心等待加载或提高视距后重试。";
        return null;
    }

    /** Inspect only already loaded starts. Unknown referenced starts are a reason
     * to refuse placement, not permission to generate or overwrite neighbouring chunks. */
    private static String protectedStructure(ServerLevel level, BoundingBox bounds) {
        var seen = new HashSet<StructureReference>();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) return "结构预检时区块 " + x + ", " + z + " 已卸载，请保持完整区域加载。";
                // A chunk's own starts cover manual and newly generated starts even
                // if references have not been copied into another chunk yet.
                for (var start : chunk.getAllStarts().values())
                    if (start != null && start.isValid()) for (var part : start.getPieces())
                        if (part.getBoundingBox().intersects(bounds)) return "巨巢范围与现有结构相交："
                                + level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure())
                                + "，起始区块 " + start.getChunkPos() + "。请换位置。";
                for (var entry : chunk.getAllReferences().entrySet()) for (long packed : entry.getValue()) {
                    if (!seen.add(new StructureReference(entry.getKey(), packed))) continue;
                    ChunkPos origin = new ChunkPos(packed);
                    var source = level.getChunkSource().getChunkNow(origin.x, origin.z);
                    if (source == null) return "无法安全检查现有结构：其起始区块 " + origin + " 未加载；请换位置或先加载周围区域。";
                    var start = source.getStartForStructure(entry.getKey());
                    if (start != null && start.isValid()) for (var part : start.getPieces())
                        if (part.getBoundingBox().intersects(bounds)) return "巨巢范围与现有结构相交，起始区块 " + origin + "。请换位置。";
                }
            }
        return null;
    }

    private static AABB box(BoundingBox b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1); }
    private static boolean occupies(ServerPlayer player, BlockPos at) {
        var b = player.getBoundingBox();
        return b.maxX > at.getX() && b.minX < at.getX() + 1 && b.maxY > at.getY() && b.minY < at.getY() + 1
                && b.maxZ > at.getZ() && b.minZ < at.getZ() + 1;
    }
    private static boolean insideBorder(ServerLevel level, BoundingBox b) { return level.getWorldBorder().isWithinBounds(box(b)); }
    private static String horizontalRange(BoundingBox b) { return "X=" + b.minX() + ".." + b.maxX() + ", Z=" + b.minZ() + ".." + b.maxZ(); }
    private static String location(BlockPos p) { return p.getX() + ", " + p.getY() + ", " + p.getZ(); }
    private static void tell(ServerPlayer player, String message, boolean failure) {
        player.sendSystemMessage(Component.literal(message).withStyle(failure ? ChatFormatting.RED : ChatFormatting.YELLOW));
    }
    private static void coordinates(ServerPlayer player, String label, BlockPos position, boolean relativeY) {
        String y = relativeY ? "~" : Integer.toString(position.getY());
        String values = position.getX() + " " + y + " " + position.getZ();
        player.sendSystemMessage(Component.literal(label).append(Component.literal("[" + values + "]")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + values)))));
    }
}
