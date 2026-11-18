package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.client.particle.ExpandingImpactParticle;
import com.codex.blacklight.client.particle.GroundShockwaveParticle;
import com.codex.blacklight.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/** Binds Blacklight particle types to their client-only sprite providers. */
@EventBusSubscriber(modid = BlacklightNeoMod.MOD_ID, value = Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class ClientParticleEvents {
    private ClientParticleEvents() {
    }

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CONTACT_DUST.get(), com.codex.blacklight.client.particle.ContactDustParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BIOMASS_SLASH.get(),
                ExpandingImpactParticle.SlashProvider::new);
        event.registerSpriteSet(ModParticles.DESTRUCTION_BURST.get(),
                ExpandingImpactParticle.DestructionProvider::new);
        event.registerSpriteSet(ModParticles.IMPACT_SMOKE.get(),
                ExpandingImpactParticle.SmokeProvider::new);
        event.registerSpriteSet(ModParticles.SPRINT_SHOCKWAVE.get(),
                GroundShockwaveParticle.Provider::new);
    }
}
