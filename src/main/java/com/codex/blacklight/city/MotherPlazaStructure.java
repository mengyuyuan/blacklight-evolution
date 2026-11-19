package com.codex.blacklight.city;

import com.codex.blacklight.registry.ModWorldgen;
import com.codex.blacklight.world.BossSiteBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.Optional;

/** One seeded street scene in the Futian district of the Blacklight world. */
public final class MotherPlazaStructure extends Structure {
    public static final Codec<MotherPlazaStructure> CODEC=simpleCodec(MotherPlazaStructure::new);
    // Surveyed 33 x 33 street lots: flat, dry and clear of civic landmarks and hive entrances.
    private static final BlockPos[] LOTS = {
            new BlockPos(144,63,912), new BlockPos(176,63,1088), new BlockPos(112,63,240), new BlockPos(1088,63,960),
            new BlockPos(1328,63,960), new BlockPos(1200,63,656), new BlockPos(1088,63,432), new BlockPos(800,63,448),
            new BlockPos(1328,63,192), new BlockPos(96,63,416), new BlockPos(384,63,1168), new BlockPos(1088,63,80),
            new BlockPos(288,63,672), new BlockPos(864,63,272), new BlockPos(784,63,64), new BlockPos(560,63,64),
            new BlockPos(192,63,80), new BlockPos(352,63,496), new BlockPos(496,63,368), new BlockPos(1216,63,1168)
    };
    public static BlockPos origin(long worldSeed){
        return LOTS[Math.floorMod(CityLayout.hash(worldSeed,378551,37),LOTS.length)];
    }
    public static BlockPos console(long worldSeed){return origin(worldSeed).offset(16,1,28);}
    private static final ResourceLocation ID=new ResourceLocation("blacklight","mother_plaza");

    public MotherPlazaStructure(StructureSettings settings){super(settings);}
    public static final class Placement extends RandomSpreadStructurePlacement {
        public static final Codec<Placement> CODEC=Codec.unit(Placement::new);
        private Placement(){ super(1,0,RandomSpreadType.LINEAR,378551); }
        @Override public ChunkPos getPotentialStructureChunk(long seed,int x,int z){return new ChunkPos(origin(seed));}
        @Override public StructurePlacementType<?> type(){return ModWorldgen.PLAZA_LOCATION.get();}
    }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context){
        var origin=origin(context.seed());
        if(!(context.chunkGenerator() instanceof BlacklightWorldGenerator) || !new ChunkPos(origin).equals(context.chunkPos()))
            return Optional.empty();
        var element=StructurePoolElement.single("blacklight:mother_plaza").apply(StructureTemplatePool.Projection.RIGID);
        var manager=context.structureTemplateManager();
        return Optional.of(new GenerationStub(origin,builder->builder.addPiece(new PoolElementStructurePiece(
                manager,element,origin,0,Rotation.NONE,element.getBoundingBox(manager,origin,Rotation.NONE)))));
    }
    @Override public StructureType<?> type(){return ModWorldgen.MOTHER_PLAZA.get();}

    /** Existing saves can have fully explored this fixed city block before the encounter was added. */
    public static boolean chart(ServerLevel level){
        var origin=origin(level.getSeed());
        var console=console(level.getSeed());
        var startChunk=new ChunkPos(origin);
        if(!level.dimension().equals(CityTravel.WORLD) || !level.getServer().getWorldData().worldGenOptions().generateStructures()
                || !(level.getChunkSource().getGenerator() instanceof BlacklightWorldGenerator generator)
                || !level.getWorldBorder().isWithinBounds(origin) || !level.getWorldBorder().isWithinBounds(origin.offset(32,0,32))) return false;
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ID);
        var start=level.getChunk(startChunk.x,startChunk.z).getStartForStructure(structure);
        var state=level.getBlockState(console);
        if(state.getBlock() instanceof BossSiteBlock && state.getValue(BossSiteBlock.MOTHER)
                && level.getBlockEntity(console) instanceof com.codex.blacklight.world.PumpStation) return true;
        if(start!=null && start.isValid()) return false;
        var template=level.getStructureManager().get(ID).orElse(null);
        if(template==null) return false;
        var size=template.getSize();
        // Only retrofit intact atlas terrain; comparison includes air, pavement and block entities.
        var cursor=new BlockPos.MutableBlockPos();
        for(int x=0;x<size.getX();x++) for(int z=0;z<size.getZ();z++){
            int wx=origin.getX()+x, wz=origin.getZ()+z;
            var column=generator.getBaseColumn(wx,wz,level,level.getChunkSource().randomState());
            for(int y=origin.getY();y<origin.getY()+size.getY();y++){
                cursor.set(wx,y,wz);
                if(level.getBlockEntity(cursor)!=null || level.getBlockState(cursor)!=column.getBlock(y)) return false;
            }
        }
        return template.placeInWorld(level,origin,origin,new StructurePlaceSettings().setIgnoreEntities(true),level.random,2);
    }
}
