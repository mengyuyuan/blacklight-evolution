package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

public final class WeaveBlock extends Block implements EntityBlock {
    private static final VoxelShape TRAY=Block.box(0,0,0,16,14,16);
    public WeaveBlock(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,@javax.annotation.Nullable BlockGetter context,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.translatable("weave.blacklight.tip"));
        if(this==com.codex.blacklight.registry.ModBlocks.WEAVE_CORE.get())lines.add(net.minecraft.network.chat.Component.translatable("weave.blacklight.controls"));
    }
    private static final VoxelShape PRESS = pressShape();
    private static VoxelShape pressShape() {
        var shape = Shapes.or(TRAY,Block.box(4.6,14,4.6,11.4,18.5,11.4));
        for (int i=0;i<4;i++) {
            shape = Shapes.or(shape,cornerBox((i&1)!=0,(i&2)!=0,.8,1.0,5.0,4.3,20.1));
        }
        return shape;
    }
    private static final VoxelShape[] CORNERS = new VoxelShape[4];
    static {
        for (int i=0; i<CORNERS.length; i++) {
            boolean east=(i&1)!=0, south=(i&2)!=0;
            CORNERS[i]=Shapes.or(TRAY,
                    cornerBox(east,south,.3,.5,12.5,4.1,16.3),
                    cornerBox(east,south,.5,.3,4.1,12.5,16.3),
                    cornerBox(east,south,.7,.7,4.3,4.3,19.9));
        }
    }
    private static VoxelShape cornerBox(boolean east,boolean south,double x,double z,double endX,double endZ,double top) {
        return Block.box(east?16-endX:x,14,south?16-endZ:z,east?16-x:endX,top,south?16-z:endZ);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context) {
        if (!(world instanceof Level level)) return TRAY;
        if (state.is(ModBlocks.WEAVE_CORE.get())) return WeaveCell.formed(level,pos)?PRESS:TRAY;
        if (state.is(ModBlocks.WEAVE_ANCHOR.get())) {
            for (int i=0;i<CORNERS.length;i++) {
                BlockPos center=pos.offset((i&1)==0?2:-2,0,(i&2)==0?2:-2);
                if (level.hasChunkAt(center) && level.getBlockState(center).is(ModBlocks.WEAVE_CORE.get())
                        && WeaveCell.formed(level,center)) return CORNERS[i];
            }
        }
        return TRAY;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WeaveCell(pos,state);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
        if(hit.getDirection()!=Direction.UP && !(hit.getDirection().getAxis().isHorizontal() && p.isShiftKeyDown() && p.getItemInHand(hand).isEmpty()))return InteractionResult.PASS;
        if(p instanceof ServerPlayer server && !p.isSpectator() && level.getBlockEntity(pos) instanceof WeaveCell cell)cell.interact(server,p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        if(level.getBlockEntity(pos) instanceof WeaveCell cell)cell.advance(level);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){
        if(!replacement.is(this) && level instanceof ServerLevel server){
            WeaveCell.interruptNearby(server,pos);
            if(level.getBlockEntity(pos) instanceof WeaveCell cell && !cell.offering().isEmpty())
                Block.popResource(level,pos,cell.offering().copy());
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
}
