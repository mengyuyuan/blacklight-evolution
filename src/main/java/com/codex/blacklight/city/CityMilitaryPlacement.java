package com.codex.blacklight.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Explicit administrator placement. Validate the entire bounded volume before changing a block. */
public final class CityMilitaryPlacement {
    public static String place(ServerLevel level,CityMilitaryPlan.Site site){
        int top=site.floor()+17;
        if(site.floor()-3<level.getMinBuildHeight()||top>=level.getMaxBuildHeight())throw new IllegalArgumentException("height");
        if(!level.getWorldBorder().isWithinBounds(new BlockPos(site.x(),site.floor(),site.z()))
                ||!level.getWorldBorder().isWithinBounds(new BlockPos(site.x()+site.width()-1,top,site.z()+site.depth()-1)))throw new IllegalArgumentException("border");
        var template=level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("blacklight",LargeBaseBlueprint.get(site.blackwatch()).name())).orElseThrow(()->new IllegalArgumentException("template"));
        // A deliberate command may load at most this one footprint; passive discovery never does.
        for(int x=site.x()>>4;x<=(site.x()+site.width()-1)>>4;x++)for(int z=site.z()>>4;z<=(site.z()+site.depth()-1)>>4;z++)level.getChunk(x,z);
        var cursor=new BlockPos.MutableBlockPos();
        for(int x=site.x();x<site.x()+site.width();x++)for(int z=site.z();z<site.z()+site.depth();z++)for(int y=site.floor();y<=top;y++){
            cursor.set(x,y,z);var state=level.getBlockState(cursor);
            if(level.getBlockEntity(cursor)!=null||!state.getFluidState().isEmpty()||y>site.floor()&&!state.isAir()&&!state.canBeReplaced())throw new IllegalArgumentException("occupied");
        }
        int[] point=site.world(0,0);var origin=new BlockPos(point[0],site.floor(),point[1]);
        var settings=new StructurePlaceSettings().setRotation(Rotation.values()[site.rotation()]).setIgnoreEntities(true);
        if(!template.placeInWorld(level,origin,origin,settings,level.random,2))throw new IllegalArgumentException("template");
        for(int x=site.x();x<site.x()+site.width();x++)for(int z=site.z();z<site.z()+site.depth();z++)for(int y=site.floor()-1;y>=site.floor()-3;y--){
            cursor.set(x,y,z);if(level.getBlockState(cursor).isAir())level.setBlock(cursor,Blocks.STONE_BRICKS.defaultBlockState(),2);
        }
        return CityMilitaryService.register(level,site);
    }
    private CityMilitaryPlacement(){}
}
