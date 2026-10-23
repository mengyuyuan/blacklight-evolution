package com.codex.blacklight.world;

import com.codex.blacklight.infection.BuildingInfectionSystem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Fixed core, separate from the Hydra gateway and from authored city hive ports. */
public final class BuildingInfectionCoreBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BuildingInfectionCoreBlock> CODEC = simpleCodec(BuildingInfectionCoreBlock::new);
    public BuildingInfectionCoreBlock(Properties p) {
        super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TissueAttachmentBlock.AGE, 0));
    }
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, TissueAttachmentBlock.AGE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if(c.getLevel() instanceof ServerLevel server && !BuildingInfectionSystem.canRegister(server,c.getClickedPos())) {
            if(c.getPlayer()!=null)c.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.site_unavailable"),true);
            return null;
        }
        return defaultBlockState().setValue(FACING, c.getClickedFace().getAxis().isHorizontal() ? c.getClickedFace() : c.getHorizontalDirection().getOpposite());
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if(level instanceof ServerLevel server) {
            var colony=com.codex.blacklight.infection.BuildingInfectionData.get(server).colonies.stream().filter(c->c.origin.equals(pos)).findFirst();
            if(colony.isPresent()) {
                var c=colony.get();
                int percent=(int)Math.min(100,100L*c.activeTicks/(com.codex.blacklight.infection.BuildingInfectionConfig.MATURITY.get()*20L));
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.status",c.stage+1,percent,c.surfaces.size()),true);
            } else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.site_unavailable"),true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this) && level instanceof ServerLevel server) BuildingInfectionSystem.register(server, pos);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel server) BuildingInfectionSystem.destroy(server, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
