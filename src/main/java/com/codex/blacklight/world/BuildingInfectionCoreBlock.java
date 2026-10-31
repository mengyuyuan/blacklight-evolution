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
public final class BuildingInfectionCoreBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public BuildingInfectionCoreBlock(Properties p) {
        super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TissueAttachmentBlock.AGE, 0));
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new BuildingHeartBlockEntity(pos,state); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, TissueAttachmentBlock.AGE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if(c.getLevel() instanceof ServerLevel server && !BuildingInfectionSystem.canRegister(server,c.getClickedPos())) {
            if(c.getPlayer()!=null)c.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.site_unavailable"),true);
            return null;
        }
        return defaultBlockState().setValue(FACING, c.getClickedFace().getAxis().isHorizontal() ? c.getClickedFace() : c.getHorizontalDirection().getOpposite());
    }
    @Override public net.minecraft.world.InteractionResult use(BlockState state,Level level,BlockPos pos,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        var stack=player.getItemInHand(hand);
        if(com.codex.blacklight.infection.ColonyFeeding.food(stack)!=null){
            if(level.isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
            if(level instanceof ServerLevel server&&com.codex.blacklight.infection.ColonyFeeding.feed(server,pos,player,hand))return net.minecraft.world.InteractionResult.CONSUME;
        }
        if(level instanceof ServerLevel server) {
            var colony=com.codex.blacklight.infection.BuildingInfectionData.get(server).colonies.stream().filter(c->c.origin.equals(pos)).findFirst();
            if(colony.isPresent()) {
                var c=colony.get();
                int percent=(int)Math.min(100,100L*c.activeTicks/(com.codex.blacklight.infection.BuildingInfectionConfig.MATURITY.get()*20L));
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.supply_status",c.stage+1,percent,c.surfaces.size(),c.supply.reserve(),c.supply.queued()),true);
            } else player.displayClientMessage(net.minecraft.network.chat.Component.translatable("building.blacklight.site_unavailable"),true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!old.is(this) && level instanceof ServerLevel server
                && !com.codex.blacklight.infection.NaturalInfectionPlacement.defersRegistration(server, pos))
            BuildingInfectionSystem.register(server, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel server) BuildingInfectionSystem.destroy(server, pos);
        super.onRemove(state, level, pos, next, moving);
    }
}
