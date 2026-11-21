package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Small set of tintable impact particles used by Prototype combat and traversal. */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, BlacklightNeoMod.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BIOMASS_SLASH =
            PARTICLES.register("biomass_slash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DESTRUCTION_BURST =
            PARTICLES.register("destruction_burst", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPACT_SMOKE =
            PARTICLES.register("impact_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRINT_SHOCKWAVE =
            PARTICLES.register("sprint_shockwave", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTACT_DUST =
            PARTICLES.register("contact_dust", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAMMER_WAVE_DUST = PARTICLES.register("hammer_wave_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAMMER_FIRE = PARTICLES.register("hammer_fire", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAMMER_BURST_DUST = PARTICLES.register("hammer_burst_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAMMER_DIRT = PARTICLES.register("hammer_dirt", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HAMMER_DEBRIS = PARTICLES.register("hammer_debris", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
