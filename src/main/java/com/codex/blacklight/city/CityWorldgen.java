package com.codex.blacklight.city;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public final class CityWorldgen {
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
        DeferredRegister.create(Registries.CHUNK_GENERATOR, "blacklight");
    static { GENERATORS.register("city", () -> CityChunkGenerator.CODEC); }
    public static void register(IEventBus bus) { GENERATORS.register(bus); }
}
