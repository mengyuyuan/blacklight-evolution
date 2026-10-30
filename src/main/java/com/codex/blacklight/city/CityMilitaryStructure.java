package com.codex.blacklight.city;

import com.codex.blacklight.registry.ModWorldgen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/** Cities explicitly opt into this structure set; ordinary biomes do not receive it. */
public final class CityMilitaryStructure extends Structure {
    public static final MapCodec<CityMilitaryStructure> CODEC=simpleCodec(CityMilitaryStructure::new);
    public CityMilitaryStructure(StructureSettings settings){super(settings);}
    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext context){
        if(!MilitaryCampaignConfig.naturalBases())return Optional.empty();
        boolean city=CityMilitaryTerrain.supported(context.chunkGenerator());
        if(!city&&!(context.chunkGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator))return Optional.empty();
        long seed=context.seed();boolean blackwatch=((seed^context.chunkPos().toLong())&1)!=0;
        var survey=city?CityMilitaryTerrain.survey(context.chunkGenerator(),seed):CityMilitaryTerrain.field(context);
        var site=CityMilitaryPlan.find(seed,context.chunkPos().x,context.chunkPos().z,blackwatch,city,survey);
        return site.map(s->new GenerationStub(new BlockPos(s.x(),s.floor(),s.z()),pieces->{
            var checkpoint=CityMilitaryPlan.checkpoint(s,context.chunkPos().x,context.chunkPos().z,survey).orElse(null);
            pieces.addPiece(new CityMilitaryPiece(s,checkpoint));
            // This callback only selects NEW starts. Saved old piece lists are
            // loaded as recorded and are never passed through this planner.
            VehicleGaragePiece.Planner.find(s,checkpoint,context.chunkPos().x,context.chunkPos().z,city,survey)
                    .ifPresent(garage->pieces.addPiece(new VehicleGaragePiece(VehicleGaragePiece.Planner.baseId(s),
                            s.blackwatch()?com.codex.blacklight.military.MilitaryFaction.BLACKWATCH:com.codex.blacklight.military.MilitaryFaction.MILITARY,garage)));
        }));
    }
    @Override public StructureType<?> type(){return ModWorldgen.CITY_MILITARY.get();}
}
