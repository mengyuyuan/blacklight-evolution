package com.codex.blacklight.registry;
import com.codex.blacklight.world.HiveFeature;
import com.codex.blacklight.world.ColossalHiveStructure;
import com.codex.blacklight.world.ColossalHivePiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import java.util.function.Supplier;
public final class ModWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,"blacklight");
    private static final DeferredRegister<StructureType<?>> STRUCTURES=DeferredRegister.create(Registries.STRUCTURE_TYPE,"blacklight");
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,"blacklight");
    public static final Supplier<StructureType<ColossalHiveStructure>> COLOSSAL_HIVE=STRUCTURES.register("colossal_hive",()->()->ColossalHiveStructure.CODEC);
    public static final Supplier<StructureType<com.codex.blacklight.city.CityMilitaryStructure>> CITY_MILITARY=STRUCTURES.register("city_military",()->()->com.codex.blacklight.city.CityMilitaryStructure.CODEC);
    public static final Supplier<StructurePieceType> CITY_MILITARY_PIECE=PIECES.register("city_military_piece",()->(context,tag)->new com.codex.blacklight.city.CityMilitaryPiece(tag));
    public static final Supplier<StructurePieceType> VEHICLE_GARAGE_PIECE=PIECES.register("vehicle_garage_piece",()->(context,tag)->new com.codex.blacklight.city.VehicleGaragePiece(tag));
    public static final Supplier<StructurePieceType> COLOSSAL_HIVE_PIECE=PIECES.register("colossal_hive_piece",()->(context,tag)->new ColossalHivePiece(tag));
    static {FEATURES.register("hive",()->new HiveFeature(NoneFeatureConfiguration.CODEC));}
    public static void register(IEventBus bus){FEATURES.register(bus);STRUCTURES.register(bus);PIECES.register(bus);}
}
