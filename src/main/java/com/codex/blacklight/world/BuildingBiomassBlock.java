package com.codex.blacklight.world;

import com.codex.blacklight.infection.BuildingBiomassField;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A real solid volume whose boundary follows shared lattice corners, not a pile of cube models. */
public final class BuildingBiomassBlock extends Block {
    public static final IntegerProperty CORNERS=IntegerProperty.create("corners",1,255);
    public static final IntegerProperty MATERIAL=IntegerProperty.create("material",0,2);
    private static final VoxelShape[] SHAPES=new VoxelShape[256];
    static {
        SHAPES[0]=Shapes.empty();SHAPES[255]=Shapes.block();
        for(int mask=1;mask<255;mask++) {
            VoxelShape shape=Shapes.empty();
            for(int x=0;x<4;x++)for(int y=0;y<4;y++)for(int z=0;z<4;z++)
                if(BuildingBiomassField.scalar(mask,(x+.5)/4,(y+.5)/4,(z+.5)/4)>0)
                    shape=Shapes.or(shape,Shapes.box(x/4D,y/4D,z/4D,(x+1)/4D,(y+1)/4D,(z+1)/4D));
            SHAPES[mask]=shape.optimize();
        }
    }
    public BuildingBiomassBlock(Properties p) { super(p);registerDefaultState(stateDefinition.any().setValue(CORNERS,255).setValue(MATERIAL,0)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) { b.add(CORNERS,MATERIAL); }
    @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPES[s.getValue(CORNERS)]; }
    @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c) { return SHAPES[s.getValue(CORNERS)]; }
    @Override public int getLightBlock(BlockState s,BlockGetter l,BlockPos p) { return s.getValue(CORNERS)==255?15:0; }
    @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving) {
        if(!next.is(this)&&l instanceof net.minecraft.server.level.ServerLevel server)
            com.codex.blacklight.infection.BuildingBiomassSystem.removed(server,p);
        super.onRemove(s,l,p,next,moving);
    }
}
