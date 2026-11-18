package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

/** Small set of tintable impact particles used by Prototype combat and traversal. */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, BlacklightNeoMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> BIOMASS_SLASH =
            PARTICLES.register("biomass_slash", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DESTRUCTION_BURST =
            PARTICLES.register("destruction_burst", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> IMPACT_SMOKE =
            PARTICLES.register("impact_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SPRINT_SHOCKWAVE =
            PARTICLES.register("sprint_shockwave", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> CONTACT_DUST =
            PARTICLES.register("contact_dust", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
