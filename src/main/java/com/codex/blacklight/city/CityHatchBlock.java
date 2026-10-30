package com.codex.blacklight.city;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
public final class CityHatchBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty OPEN=BooleanProperty.create("open");
    public CityHatchBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,OPEN);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override public net.minecraft.world.InteractionResult use(BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){
        var stack=player.getItemInHand(hand);
        if(com.codex.blacklight.infection.ColonyFeeding.food(stack)!=null){
            if(level.isClientSide)return net.minecraft.world.InteractionResult.SUCCESS;
            if(level instanceof net.minecraft.server.level.ServerLevel server&&CityHiveSupply.feed(server,pos,player,hand))return net.minecraft.world.InteractionResult.CONSUME;
        }
        if(level instanceof net.minecraft.server.level.ServerLevel server){
            var data=CityData.get(server);var p=CityHiveSupply.port(server,pos);
            if(CityHiveSupply.live(server,data,p))player.displayClientMessage(net.minecraft.network.chat.Component.translatable("city.blacklight.hive_supply_status",7-Integer.bitCount(data.brokenPorts[p.hive()]&127),data.hiveSupply[p.hive()].reserve(),data.hiveSupply[p.hive()].queued(),data.hiveBroodTicks[p.hive()]/2),true);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }
}
