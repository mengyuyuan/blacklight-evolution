package com.codex.blacklight.city;

import com.codex.blacklight.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Native chunk-clipped placement. No live entities or SavedData are changed on worldgen threads. */
public final class CityMilitaryPiece extends StructurePiece {
    private final CityMilitaryPlan.Site site;
    private final CityMilitaryPlan.Checkpoint checkpoint;
    public CityMilitaryPiece(CityMilitaryPlan.Site site){this(site,null);}
    public CityMilitaryPiece(CityMilitaryPlan.Site site,CityMilitaryPlan.Checkpoint checkpoint){super(ModWorldgen.CITY_MILITARY_PIECE.get(),0,bounds(site,checkpoint));this.site=site;this.checkpoint=checkpoint;setOrientation(null);}
    public CityMilitaryPiece(CompoundTag tag){super(ModWorldgen.CITY_MILITARY_PIECE.get(),tag);
        site=new CityMilitaryPlan.Site(tag.getInt("SiteX"),tag.getInt("SiteZ"),tag.getInt("Floor"),Math.floorMod(tag.getInt("Turn"),4),tag.getBoolean("Blackwatch"));
        checkpoint=tag.contains("CheckpointX")?new CityMilitaryPlan.Checkpoint(tag.getInt("CheckpointX"),tag.getInt("CheckpointZ"),site.floor(),Math.floorMod(tag.getInt("CheckpointTurn"),4)):null;}
    public CityMilitaryPlan.Site site(){return site;}
    public CityMilitaryPlan.Checkpoint checkpoint(){return checkpoint;}
    public String templateName(){return site.blackwatch()?"large_blackwatch_base":"large_military_base";}
    public static BoundingBox bounds(CityMilitaryPlan.Site site){return new BoundingBox(site.x(),site.floor()-3,site.z(),site.x()+site.width()-1,site.floor()+17,site.z()+site.depth()-1);}
    private static BoundingBox bounds(CityMilitaryPlan.Site site,CityMilitaryPlan.Checkpoint checkpoint){var box=bounds(site);if(checkpoint!=null)box.encapsulate(new BoundingBox(checkpoint.x(),site.floor()-3,checkpoint.z(),checkpoint.x()+16,site.floor()+8,checkpoint.z()+16));return box;}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag){
        tag.putInt("SiteX",site.x());tag.putInt("SiteZ",site.z());tag.putInt("Floor",site.floor());tag.putInt("Turn",site.rotation());tag.putBoolean("Blackwatch",site.blackwatch());
        if(checkpoint!=null){tag.putInt("CheckpointX",checkpoint.x());tag.putInt("CheckpointZ",checkpoint.z());tag.putInt("CheckpointTurn",checkpoint.rotation());}
    }
    @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot){
        var id=new ResourceLocation("blacklight",templateName());
        var template=level.getLevel().getStructureManager().getOrCreate(id);
        int[] point=site.world(0,0);var origin=new BlockPos(point[0],site.floor(),point[1]);
        var settings=new StructurePlaceSettings().setRotation(Rotation.values()[site.rotation()]).setBoundingBox(clip).setIgnoreEntities(true);
        template.placeInWorld(level,origin,origin,settings,random,2);
        if(checkpoint!=null){
            var small=level.getLevel().getStructureManager().getOrCreate(new ResourceLocation("blacklight",site.blackwatch()?"blackwatch_checkpoint":"military_checkpoint"));
            int[] c=checkpoint.world(0,0);var at=new BlockPos(c[0],checkpoint.floor(),c[1]);
            small.placeInWorld(level,at,at,new StructurePlaceSettings().setRotation(Rotation.values()[checkpoint.rotation()]).setBoundingBox(clip).setIgnoreEntities(true),random,2);
            foundation(level,clip,checkpoint.x(),checkpoint.z(),17,17,checkpoint.floor());
        }
        foundation(level,clip,site.x(),site.z(),site.width(),site.depth(),site.floor());
    }
    private static void foundation(WorldGenLevel level,BoundingBox clip,int minX,int minZ,int width,int depth,int floor){
        for(int x=Math.max(minX,clip.minX());x<=Math.min(minX+width-1,clip.maxX());x++)
            for(int z=Math.max(minZ,clip.minZ());z<=Math.min(minZ+depth-1,clip.maxZ());z++)
                for(int y=floor-1;y>=floor-3;y--){
                    var at=new BlockPos(x,y,z);if(clip.isInside(at)&&level.getBlockState(at).isAir())level.setBlock(at,Blocks.STONE_BRICKS.defaultBlockState(),2);
                }
    }
}
