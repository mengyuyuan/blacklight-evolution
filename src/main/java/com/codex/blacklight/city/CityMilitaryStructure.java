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
        if(!CityMilitaryTerrain.supported(context.chunkGenerator()))return Optional.empty();
        long seed=context.seed();boolean blackwatch=((seed^context.chunkPos().toLong())&1)!=0;
        var site=CityMilitaryPlan.find(seed,context.chunkPos().x,context.chunkPos().z,blackwatch,true,
                CityMilitaryTerrain.survey(context.chunkGenerator(),seed));
        return site.map(s->new GenerationStub(new BlockPos(s.x(),s.floor(),s.z()),pieces->{
            var survey=CityMilitaryTerrain.survey(context.chunkGenerator(),seed);
            var checkpoint=CityMilitaryPlan.checkpoint(s,context.chunkPos().x,context.chunkPos().z,survey).orElse(null);
            pieces.addPiece(new CityMilitaryPiece(s,checkpoint));
            // This callback only selects NEW starts. Saved old piece lists are
            // loaded as recorded and are never passed through this planner.
            VehicleGaragePiece.Planner.find(s,checkpoint,context.chunkPos().x,context.chunkPos().z,true,survey)
                    .ifPresent(garage->pieces.addPiece(new VehicleGaragePiece(VehicleGaragePiece.Planner.baseId(s),
                            s.blackwatch()?com.codex.blacklight.military.MilitaryFaction.BLACKWATCH:com.codex.blacklight.military.MilitaryFaction.MILITARY,garage)));
        }));
    }
    @Override public StructureType<?> type(){return ModWorldgen.CITY_MILITARY.get();}
}
