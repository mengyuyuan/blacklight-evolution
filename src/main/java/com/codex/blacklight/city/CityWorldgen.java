package com.codex.blacklight.city;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CityWorldgen {
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, "blacklight");
    static { GENERATORS.register("city", () -> CityChunkGenerator.CODEC); }
    public static void register(IEventBus bus) { GENERATORS.register(bus); }
}
