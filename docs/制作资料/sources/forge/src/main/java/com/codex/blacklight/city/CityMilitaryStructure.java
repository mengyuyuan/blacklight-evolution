package com.codex.blacklight.city;

import com.codex.blacklight.registry.ModWorldgen;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/** Cities explicitly opt into this structure set; ordinary biomes do not receive it. */
public final class CityMilitaryStructure extends Structure {
    public static final Codec<CityMilitaryStructure> CODEC=simpleCodec(CityMilitaryStructure::new);
    public CityMilitaryStructure(StructureSettings settings){super(settings);}
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context){
        if(!CityMilitaryTerrain.supported(context.chunkGenerator()))return Optional.empty();
        long seed=context.seed();boolean blackwatch=((seed^context.chunkPos().toLong())&1)!=0;
        var site=CityMilitaryPlan.find(seed,context.chunkPos().x,context.chunkPos().z,blackwatch,true,
                CityMilitaryTerrain.survey(context.chunkGenerator(),seed));
        return site.map(s->new GenerationStub(new BlockPos(s.x(),s.floor(),s.z()),pieces->pieces.addPiece(new CityMilitaryPiece(s,
                CityMilitaryPlan.checkpoint(s,context.chunkPos().x,context.chunkPos().z,CityMilitaryTerrain.survey(context.chunkGenerator(),seed)).orElse(null)))));
    }
    @Override public StructureType<?> type(){return ModWorldgen.CITY_MILITARY.get();}
}
