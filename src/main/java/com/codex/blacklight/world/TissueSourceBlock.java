package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Local surface seeding; eligible walls may establish a budgeted building colony. */
public final class TissueSourceBlock extends Block {
    public static final MapCodec<TissueSourceBlock> CODEC = simpleCodec(TissueSourceBlock::new);
    public TissueSourceBlock(Properties properties) { super(properties.randomTicks()); }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || random.nextInt(4) != 0) return;
        Direction outward = Direction.values()[random.nextInt(6)];
        BlockPos target = pos.relative(outward);
        if (!level.hasChunkAt(target) || !level.getWorldBorder().isWithinBounds(target)
                || level.isOutsideBuildHeight(target) || !level.isEmptyBlock(target)
                || !state.isFaceSturdy(level, pos, outward)) return;
        level.setBlock(target, ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState()
                .setValue(TissueAttachmentBlock.FACING, outward.getOpposite())
                .setValue(TissueAttachmentBlock.VARIANT, random.nextInt(3)), 3);
        com.codex.blacklight.infection.BuildingInfectionSystem.considerNaturalCore(level,target,random);
    }
}
