package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModWorldgen;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Arrays;
import java.util.Optional;

/** A surface structure split by Minecraft's normal per-chunk structure placement. */
public final class ColossalHiveStructure extends Structure {
    public static final Codec<ColossalHiveStructure> CODEC=simpleCodec(ColossalHiveStructure::new);
    public ColossalHiveStructure(StructureSettings settings) { super(settings); }
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        int centreGround=context.chunkGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(),context.randomState());
        if(centreGround<context.chunkGenerator().getSeaLevel()+1)return Optional.empty();
        // Locate checks many candidate chunks. Reject unrelated biomes before the
        // expensive surrounding terrain survey; Structure still validates the final Y.
        if(!context.validBiome().test(context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                QuartPos.fromBlock(centreGround-1),QuartPos.fromBlock(z),context.randomState().sampler())))return Optional.empty();
        int[] heights=new int[49];int count=0;
        int lowest=Integer.MAX_VALUE,highest=Integer.MIN_VALUE;
        for(int dx=-48;dx<=48;dx+=16)for(int dz=-48;dz<=48;dz+=16) {
            int ground=dx==0&&dz==0?centreGround:context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(),context.randomState());
            int surface=context.chunkGenerator().getBaseHeight(x+dx,z+dz,Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(),context.randomState());
            // Reject coastlines, rivers, submerged terrain and steep mountain faces.
            if(ground<surface||ground<context.chunkGenerator().getSeaLevel()+1)return Optional.empty();
            lowest=Math.min(lowest,ground);highest=Math.max(highest,ground);
            if(highest-lowest>12)return Optional.empty();
            heights[count++]=ground;
        }
        Arrays.sort(heights);
        int floor=supportedFloor(heights[24]-1,context.heightAccessor().getMinBuildHeight());
        if(floor+ColossalHiveLayout.MAX_Y>=context.heightAccessor().getMaxBuildHeight())return Optional.empty();
        BlockPos centre=new BlockPos(x,floor,z);
        long shapeSeed=context.random().nextLong();
        return Optional.of(new GenerationStub(centre,builder->builder.addPiece(new ColossalHivePiece(centre,shapeSeed))));
    }
    /** A low superflat world needs the foundation raised above its bedrock layer. */
    public static int supportedFloor(int terrainFloor,int minBuildHeight) {
        return Math.max(terrainFloor,minBuildHeight-ColossalHiveLayout.MIN_Y+1);
    }
    @Override public StructureType<?> type() { return ModWorldgen.COLOSSAL_HIVE.get(); }
}
