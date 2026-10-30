package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.BuildingInfectionCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/** A naturally rooted building is an autonomous environmental producer, not a player-fed pet. */
public final class BuildingOccupation {
    private BuildingOccupation() {}

    /** Keep one stage-appropriate brood funded before enlarging a mature autonomous body. */
    public static int growthReserve(ServerLevel level, BuildingInfectionData.Colony colony) {
        if (!colony.autonomous) return 0;
        // Keep the largest maintenance payment available even during early construction.
        // Otherwise each new patch empties the store and mature time advances only on income ticks.
        if (colony.stage < 3 || !BuildingInfectionConfig.SPAWNS.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return 5;
        return Math.max(5,ColonySupply.broodCost(switch (com.codex.blacklight.city.CityEcology.stage(level,colony.origin)) {
            case 0,1 -> OutbreakRules.Tier.GROWING;
            case 2 -> OutbreakRules.Tier.HUNTER;
            default -> OutbreakRules.Tier.ANCIENT;
        }));
    }

    public static void establish(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.BUILDING_CORE.get())) return;
        for (var colony : BuildingInfectionData.get(level).colonies) {
            if (!colony.dead && colony.origin.equals(pos)) {
                colony.autonomous = true;
                BuildingInfectionData.get(level).setDirty();
                return;
            }
        }
    }

    /** Once per actual scheduled tick. Loaded occupation time never catches up on login. */
    public static void tick(ServerLevel level, BuildingInfectionData.Colony colony) {
        long now = level.getGameTime();
        if (colony.lastOccupationTick == now) return;
        colony.lastOccupationTick = now;
        if (!colony.autonomous || colony.dead || colony.assimilating || colony.brooding
                || !BuildingInfectionConfig.AUTONOMOUS.get() || !BuildingInfectionConfig.ENABLED.get()
                || !WorldOutbreakSystem.active(level) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.hasChunkAt(colony.origin) || !level.isPositionEntityTicking(colony.origin)
                || !BuildingInfectionSystem.allowedSite(level, colony.origin)) return;
        var root = level.getBlockState(colony.origin);
        if (!root.is(ModBlocks.BUILDING_CORE.get())) return;
        var support = colony.origin.relative(root.getValue(BuildingInfectionCoreBlock.FACING).getOpposite());
        // A real, three-block wall keeps the root anchored. No detached floating producer.
        for (int y = 0; y < 3; y++) {
            var pos = support.above(y);
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.hasChunkAt(pos) || !level.isPositionEntityTicking(pos)) return;
            var state = level.getBlockState(pos);
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0
                    || !state.isFaceSturdy(level, pos, root.getValue(BuildingInfectionCoreBlock.FACING))) return;
        }
        int interval=com.codex.blacklight.config.GameplayBalanceConfig.OCCUPATION_TICKS.get();
        colony.occupationTicks = Math.min(interval, colony.occupationTicks + 5);
        if (colony.occupationTicks >= interval) {
            colony.occupationTicks = 0;
            int generated = colony.supply.receive(com.codex.blacklight.config.GameplayBalanceConfig.OCCUPATION_BASE.get() + Math.clamp(colony.stage, 0, 3) * com.codex.blacklight.config.GameplayBalanceConfig.OCCUPATION_PER_STAGE.get());
            colony.occupationProduced = Math.min(Long.MAX_VALUE - generated, colony.occupationProduced) + generated;
        }
        BuildingInfectionData.get(level).setDirty();
    }
}
