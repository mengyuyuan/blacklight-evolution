package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.codex.blacklight.combat.ForgeEntityTickEvent;

import java.util.Comparator;
import java.util.List;

/** Loaded-time outbreak progression. Infection itself never promotes a colossal boss. */
@EventBusSubscriber(modid = "blacklight")
public final class ColossusEvolutionSystem {
    public static final int CHECK_INTERVAL = 100;
    public static final int HIVE_MATURITY_TICKS = 12000;
    public static final int EVOLUTION_TICKS = 18000;
    public static final int REQUIRED_INFECTED = 12;
    public static final int REGION_RADIUS = 48;
    public static final int PLAYER_RADIUS = 96;
    public static final int COLOSSUS_EXCLUSION_RADIUS = 192;
    private static final int MISSING_HOST_GRACE = 600;
    // More than two coordinator intervals: entity tick ordering must not undo growth.
    private static final int HEARTBEAT_GRACE = 300;
    private static final String HIVE_STATE = "BlacklightColossusEvolution";
    private static final String HOST_STATE = "BlacklightColossusLink";

    private ColossusEvolutionSystem() {}

    @SubscribeEvent
    public static void onTick(com.codex.blacklight.combat.ForgeEntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity instanceof BlacklightHive hive) {
            int interval = HiveAcceleration.multiplier(hive) > 1 ? 20 : CHECK_INTERVAL;
            if (hive.tickCount % interval == 0) tickHive(level, hive, interval);
        }
        else if (entity instanceof BlacklightInfectedHost host && host.isAlive()
                && !host.giantBoss() && host.evolutionTicks() > 0 && host.tickCount % CHECK_INTERVAL == 0) tickOrphan(level, host);
    }

    private static void tickHive(ServerLevel level, BlacklightHive hive, int interval) {
        if (!hive.isAlive()) return;
        CompoundTag state = writableState(hive, HIVE_STATE);
        if (state.getBoolean("Spent")) { HiveAcceleration.clear(hive); return; }
        if (level.getDifficulty() != Difficulty.PEACEFUL) {
            state.putInt("MaturityTicks", Math.min(HIVE_MATURITY_TICKS,
                    state.getInt("MaturityTicks") + interval * HiveAcceleration.multiplier(hive)));
        }
        // One bounded regional query serves both infection density and candidate selection.
        List<Mob> infected = regionalInfected(level, hive);
        BlacklightInfectedHost host = chosenHost(level, state);
        if (state.hasUUID("Chosen") && host == null) {
            // A missing entity might be unloaded, so do not spend its grace period while
            // its last known chunk is absent. Returning hosts also verify this Hive's UUID.
            boolean lastChunkLoaded = state.contains("ChosenX") && level.hasChunkAt(new BlockPos(
                    state.getInt("ChosenX"), hive.getBlockY(), state.getInt("ChosenZ")));
            if (lastChunkLoaded) state.putInt("MissingTicks", state.getInt("MissingTicks") + interval);
            if (state.getInt("MissingTicks") < MISSING_HOST_GRACE) {
                state.putString("BlockedReason", "等待已选宿主所在区块或实体恢复");
                return;
            }
            state.remove("Chosen");
            state.putInt("MissingTicks", 0);
        }
        if (host != null && (!host.isAlive() || host.giantBoss())) {
            HiveAcceleration.clear(hive);
            // Promotion by an administrator also consumes this Hive's single encounter.
            if (host.giantBoss()) state.putBoolean("Spent", true);
            state.remove("Chosen");
            return;
        }
        boolean playerNearby = nearbyPlayer(level, hive);
        boolean giantNearby = nearbyColossus(level, hive);
        if (host == null && state.getInt("MaturityTicks") >= HIVE_MATURITY_TICKS
                && canSupplyAnyHost(infected) && playerNearby && !giantNearby
                && level.getDifficulty() != Difficulty.PEACEFUL) {
            host = infected.stream().filter(BlacklightInfectedHost.class::isInstance)
                    .map(BlacklightInfectedHost.class::cast).filter(ColossusEvolutionSystem::eligibleHost)
                    .filter(candidate -> regionReady(infected.size(), candidate))
                    .filter(candidate -> availableClaim(level, candidate, hive))
                    .min(Comparator.comparingInt((BlacklightInfectedHost candidate) ->
                            DevourerFeedingSystem.isDevourer(candidate) ? 0 : 1)
                            .thenComparingDouble(hive::distanceToSqr)).orElse(null);
            if (host != null) {
                state.putUUID("Chosen", host.getUUID());
                state.putInt("MissingTicks", 0);
                writableState(host, HOST_STATE).putUUID("Hive", hive.getUUID());
            }
        }
        if (host == null) {
            state.putString("BlockedReason", idleReason(level, state, infected.size(), playerNearby, giantNearby));
            return;
        }
        CompoundTag link = writableState(host, HOST_STATE);
        // Conflicting stale saves cannot let two Hives advance one body.
        if (!link.hasUUID("Hive") || !link.getUUID("Hive").equals(hive.getUUID())) {
            state.remove("Chosen");
            return;
        }
        long now = level.getGameTime();
        link.putLong("Heartbeat", now);
        state.putInt("ChosenX", host.getBlockX());
        state.putInt("ChosenZ", host.getBlockZ());
        state.putInt("MissingTicks", 0);

        if (DevourerFeedingSystem.isDevourer(host) && !DevourerHabitat.suppliesLoaded(host)) {
            state.putString("BlockedReason", "供养节点或核心区块尚未加载，培育暂停");
            return;
        }
        String reason = "";
        if (level.getDifficulty() == Difficulty.PEACEFUL) reason = "和平难度，停止培育";
        else if (state.getInt("MaturityTicks") < HIVE_MATURITY_TICKS) reason = "蜂巢尚未成熟";
        else if (host.distanceToSqr(hive) > REGION_RADIUS * REGION_RADIUS) reason = "宿主离开感染区域";
        else if (!regionReady(infected.size(), host)) reason = "区域感染密度不足";
        else if (DevourerFeedingSystem.isDevourer(host) && !DevourerHabitat.canFeed(host)) reason = "巢内供养节点或蜂巢被破坏";
        if (!reason.isEmpty()) {
            decay(host, now);
            state.putString("BlockedReason", reason);
            if (host.evolutionTicks() == 0 && host.distanceToSqr(hive) > REGION_RADIUS * REGION_RADIUS) {
                state.remove("Chosen");
                link.remove("Hive");
            }
            return;
        }
        if (!eligibleHost(host)) reason = "宿主正被抓取、骑乘、拴绳或处于不可培育状态";
        else if (!playerNearby) reason = "附近没有存活的非旁观玩家，培育暂停";
        else if (giantNearby) reason = "附近已有巨构，培育暂停";
        if (!reason.isEmpty()) {
            state.putString("BlockedReason", reason);
            return;
        }
        int next = Math.min(EVOLUTION_TICKS, host.evolutionTicks() + interval * HiveAcceleration.multiplier(host));
        if (DevourerFeedingSystem.isDevourer(host)) {
            next = Math.min(next, DevourerFeedingSystem.availableGrowthTicks(host));
            if (next <= host.evolutionTicks()) {
                state.putString("BlockedReason", "蜂巢吸收养分不足，需要捕食口完成吸收");
                return;
            }
        }
        reason = spaceProblem(level, host, next);
        if (!reason.isEmpty()) {
            state.putString("BlockedReason", reason);
            return; // No clearing terrain, teleporting or growth/decay oscillation at walls.
        }
        if (!canChange(link, now, interval)) return;
        boolean changed = next == EVOLUTION_TICKS ? host.promoteToColossus() : host.setEvolutionProgress(next);
        if (!changed) return;
        link.putLong("LastChange", now);
        state.putString("BlockedReason", "培育中");
        if (host.giantBoss()) {
            HiveAcceleration.clear(hive);
            state.putBoolean("Spent", true);
            state.putString("BlockedReason", "本蜂巢已完成一次巨构诞生");
        }
        announceStage(level, host, link);
    }

    /** An orphan shrinks by loaded check intervals, including after Hive death/removal. */
    private static void tickOrphan(ServerLevel level, BlacklightInfectedHost host) {
        if (DevourerFeedingSystem.isDevourer(host) && !DevourerHabitat.suppliesLoaded(host)) return;
        CompoundTag link = writableState(host, HOST_STATE);
        Entity owner = link.hasUUID("Hive") ? level.getEntity(link.getUUID("Hive")) : null;
        boolean valid = owner instanceof BlacklightHive hive && hive.isAlive()
                && host.distanceToSqr(hive) <= REGION_RADIUS * REGION_RADIUS
                && isChosen(hive, host)
                && level.getGameTime() - link.getLong("Heartbeat") <= HEARTBEAT_GRACE;
        if (!valid) decay(host, level.getGameTime());
    }

    private static boolean isChosen(BlacklightHive hive, BlacklightInfectedHost host) {
        CompoundTag state = hive.getPersistentData().getCompound(HIVE_STATE);
        return !state.getBoolean("Spent") && state.hasUUID("Chosen")
                && state.getUUID("Chosen").equals(host.getUUID());
    }

    private static boolean availableClaim(ServerLevel level, BlacklightInfectedHost host, BlacklightHive hive) {
        if (DevourerFeedingSystem.isDevourer(host)
                && (!hive.getPersistentData().getBoolean("BlacklightColossalHive")
                || hive.getPersistentData().getLong("BlacklightHiveHome") != DevourerFeedingSystem.home(host).asLong()))
            return false;
        CompoundTag link = host.getPersistentData().getCompound(HOST_STATE);
        if (!link.hasUUID("Hive") || link.getUUID("Hive").equals(hive.getUUID())) return true;
        Entity owner = level.getEntity(link.getUUID("Hive"));
        // Wait for orphan progress to decay before assigning a body to another Hive.
        return host.evolutionTicks() == 0 && (!(owner instanceof BlacklightHive other)
                || !other.isAlive() || !isChosen(other, host));
    }

    private static boolean eligibleHost(BlacklightInfectedHost host) {
        return host.isAlive() && host.hasMultipartBody() && !host.giantBoss()
                && !host.isNoAi() && !host.isInvulnerable() && !host.isPassenger()
                && !host.isVehicle() && !host.isLeashed()
                && !com.codex.blacklight.registry.ModAttachments.getData(host, ModAttachments.CONSUME_VISUAL).active();
    }

    private static boolean regionReady(int livingCount, BlacklightInfectedHost host) {
        // Digested prey remains part of this nursery's accumulated outbreak. Eating
        // its own food stock must not make the devourer lose the population gate.
        return livingCount + (DevourerFeedingSystem.isDevourer(host) ? DevourerFeedingSystem.meals(host) : 0)
                >= REQUIRED_INFECTED;
    }

    private static boolean canSupplyAnyHost(List<Mob> infected) {
        return infected.size() >= REQUIRED_INFECTED || infected.stream()
                .filter(BlacklightInfectedHost.class::isInstance).map(BlacklightInfectedHost.class::cast)
                .anyMatch(host -> regionReady(infected.size(), host));
    }

    private static List<Mob> regionalInfected(ServerLevel level, BlacklightHive hive) {
        return level.getEntitiesOfClass(Mob.class, hive.getBoundingBox().inflate(REGION_RADIUS),
                mob -> mob.isAlive() && !(mob instanceof BlacklightHive) && !(mob instanceof com.codex.blacklight.entity.HiveTendril)
                        && !(mob instanceof BlacklightInfectedHost host && host.giantBoss())
                        && mob.distanceToSqr(hive) <= REGION_RADIUS * REGION_RADIUS
                        && BlacklightInfectionSystem.isInfected(mob));
    }

    private static boolean nearbyPlayer(ServerLevel level, BlacklightHive hive) {
        return !level.getPlayers(player -> player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(hive) <= PLAYER_RADIUS * PLAYER_RADIUS).isEmpty();
    }

    private static boolean nearbyColossus(ServerLevel level, BlacklightHive hive) {
        return !level.getEntitiesOfClass(BlacklightInfectedHost.class,
                hive.getBoundingBox().inflate(COLOSSUS_EXCLUSION_RADIUS),
                host -> host.isAlive() && host.giantBoss()
                        && host.distanceToSqr(hive) <= COLOSSUS_EXCLUSION_RADIUS * COLOSSUS_EXCLUSION_RADIUS).isEmpty();
    }

    private static BlacklightInfectedHost chosenHost(ServerLevel level, CompoundTag state) {
        if (!state.hasUUID("Chosen")) return null;
        Entity chosen = level.getEntity(state.getUUID("Chosen"));
        return chosen instanceof BlacklightInfectedHost host ? host : null;
    }

    private static void decay(BlacklightInfectedHost host, long now) {
        CompoundTag link = writableState(host, HOST_STATE);
        if (host.evolutionTicks() > 0 && canChange(link, now)
                && host.setEvolutionProgress(Math.max(0, host.evolutionTicks() - CHECK_INTERVAL))) {
            link.putLong("LastChange", now);
        }
    }

    private static boolean canChange(CompoundTag link, long now) {
        return canChange(link, now, CHECK_INTERVAL);
    }

    private static boolean canChange(CompoundTag link, long now, int interval) {
        return !link.contains("LastChange") || now - link.getLong("LastChange") >= interval;
    }

    private static String spaceProblem(ServerLevel level, BlacklightInfectedHost host, int next) {
        AABB visual = host.evolutionVisualBounds(next);
        if (!level.getWorldBorder().isWithinBounds(visual)) return "成长范围超过世界边界";
        if (visual.minY < level.getMinBuildHeight() || visual.maxY > level.getMaxBuildHeight())
            return "成长范围超过世界高度";
        int minX = Mth.floor(visual.minX) >> 4, maxX = Mth.floor(visual.maxX) >> 4;
        int minZ = Mth.floor(visual.minZ) >> 4, maxZ = Mth.floor(visual.maxZ) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!level.hasChunkAt(new BlockPos(x << 4, host.getBlockY(), z << 4)))
                return "成长范围内的区块尚未加载";
        }
        // Check actual future body pieces, never the empty space between wings/legs.
        for (AABB box : host.evolutionCollisionBoxes(next)) {
            if (!level.noCollision(host, box)) return "成长空间被方块或实体阻挡";
        }
        return "";
    }

    private static String idleReason(ServerLevel level, CompoundTag state, int count,
                                     boolean playerNearby, boolean giantNearby) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return "和平难度，停止培育";
        if (state.getInt("MaturityTicks") < HIVE_MATURITY_TICKS) return "蜂巢尚未成熟";
        if (count < REQUIRED_INFECTED) return "区域感染密度不足";
        if (!playerNearby) return "附近没有存活的非旁观玩家，培育暂停";
        if (giantNearby) return "附近已有巨构，培育暂停";
        return "需要可行动的感染铁傀儡、监守者、凋零或末影龙宿主";
    }

    private static void announceStage(ServerLevel level, BlacklightInfectedHost host, CompoundTag link) {
        int progress = host.evolutionTicks();
        int stage = host.giantBoss() ? 4 : progress >= 12000 ? 3 : progress >= 6000 ? 2 : progress > 0 ? 1 : 0;
        if (stage <= link.getInt("AnnouncedStage")) return;
        link.putInt("AnnouncedStage", stage);
        String key = switch (stage) {
            case 1 -> "message.blacklight.colossus_malformed";
            case 2 -> "message.blacklight.colossus_cocoon";
            case 3 -> "message.blacklight.colossus_awakening";
            default -> "message.blacklight.colossus_birth";
        };
        for (ServerPlayer player : level.getPlayers(p -> p.isAlive() && !p.isSpectator()
                && p.distanceToSqr(host) <= PLAYER_RADIUS * PLAYER_RADIUS)) {
            player.sendSystemMessage(Component.translatable(key, host.getDisplayName()));
        }
        level.playSound(null, host.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                SoundSource.HOSTILE, .5F, stage == 4 ? .65F : .85F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, host.getX(), host.getY() + Math.min(8, host.getBbHeight() * .5),
                host.getZ(), 16 + stage * 8, 1, 1, 1, .025);
    }

    private static CompoundTag writableState(Entity entity, String key) {
        CompoundTag persistent = entity.getPersistentData();
        if (!persistent.contains(key)) persistent.put(key, new CompoundTag());
        return persistent.getCompound(key);
    }

    /** Read-only administrator diagnostics; no chunk loads, timer increments or NBT writes. */
    public static Component describe(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlacklightHive hive = level.getEntitiesOfClass(BlacklightHive.class,
                player.getBoundingBox().inflate(PLAYER_RADIUS), h -> h.isAlive()
                        && h.distanceToSqr(player) <= PLAYER_RADIUS * PLAYER_RADIUS).stream()
                .min(Comparator.comparingInt((BlacklightHive candidate) ->
                        candidate.getPersistentData().getBoolean("BlacklightColossalHive") ? 0 : 1)
                        .thenComparingDouble(player::distanceToSqr)).orElse(null);
        if (hive == null) return Component.literal("96 格内没有已加载的存活黑光蜂巢。");
        CompoundTag state = hive.getPersistentData().getCompound(HIVE_STATE);
        int maturity = Mth.clamp(state.getInt("MaturityTicks"), 0, HIVE_MATURITY_TICKS);
        List<Mob> residents = regionalInfected(level, hive);
        int count = residents.size();
        BlacklightInfectedHost host = chosenHost(level, state);
        boolean selected = host != null;
        if (host == null && !state.hasUUID("Chosen")) {
            // The nursery already has a feeding body during its maturity period.
            // Show that body without assigning a host or changing progression.
            host = residents.stream().filter(BlacklightInfectedHost.class::isInstance)
                    .map(BlacklightInfectedHost.class::cast).filter(DevourerFeedingSystem::isDevourer)
                    .filter(candidate -> hive.getPersistentData().getLong("BlacklightHiveHome")
                            == DevourerFeedingSystem.home(candidate).asLong())
                    .min(Comparator.comparingDouble(hive::distanceToSqr)).orElse(null);
        }
        boolean spent = state.getBoolean("Spent");
        String detail;
        if (host == null) detail = state.hasUUID("Chosen") ? "已选宿主暂未加载" : "尚未选定宿主";
        else detail = host.getDisplayName().getString() + " · "
                + (host.giantBoss() ? "巨构" : !selected ? "等待蜂巢成熟" : host.evolutionTicks() >= 12000 ? "苏醒"
                : host.evolutionTicks() >= 6000 ? "结茧" : "畸变")
                + " " + host.evolutionTicks() / 20 + "/" + EVOLUTION_TICKS / 20 + " 秒";
        if (host != null && DevourerFeedingSystem.isDevourer(host))
            detail += "\n吞噬 " + DevourerFeedingSystem.meals(host) + " 只 · 生物质 "
                    + DevourerFeedingSystem.biomass(host) + "/60 · 供养节点 " + DevourerHabitat.liveNodes(host) + "/4（至少 2 个）"
                    + "\n进食：" + DevourerFeedingSystem.describe(host);
        int multiplier = HiveAcceleration.multiplier(hive);
        String reason = spent ? "本蜂巢已经产生过巨构" : state.getString("BlockedReason");
        if (reason.isEmpty() || !spent && maturity < HIVE_MATURITY_TICKS)
            reason = idleReason(level, state, count, nearbyPlayer(level, hive), nearbyColossus(level, hive));
        return Component.literal("蜂巢 " + hive.getBlockX() + ", " + hive.getBlockY() + ", " + hive.getBlockZ()
                + " · 局部培育倍率 " + multiplier + "×"
                + "\n成熟度 " + maturity / 20 + "/" + HIVE_MATURITY_TICKS / 20 + " 秒"
                + (maturity < HIVE_MATURITY_TICKS && level.getDifficulty() != Difficulty.PEACEFUL
                ? "（保持加载约还需 " + (HIVE_MATURITY_TICKS - maturity + 20 * multiplier - 1) / (20 * multiplier) + " 秒）" : "")
                + " · 48 格内感染生物 " + count + "/" + REQUIRED_INFECTED
                + "\n宿主：" + detail + "\n状态：" + reason);
    }
}
