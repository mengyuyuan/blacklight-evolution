package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.effect.BlacklightInfectionEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, BlacklightNeoMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> BLACKLIGHT_INFECTION = EFFECTS.register(
            "blacklight_infection", () -> new BlacklightInfectionEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("infection_speed"),
                            0.16D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("infection_damage"),
                            2.5D, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MAX_HEALTH, id("infection_health"),
                            6.0D, AttributeModifier.Operation.ADD_VALUE));

    public static final DeferredHolder<MobEffect, MobEffect> BIOMASS_CONTACT=EFFECTS.register("biomass_contact",()->new com.codex.blacklight.effect.BiomassContactEffect().addAttributeModifier(Attributes.MOVEMENT_SPEED,id("biomass_contact"),-.45D,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> ADAPTATION = EFFECTS.register("adaptation", () -> new com.codex.blacklight.effect.PassiveMarkerEffect(0x48B3D1));
    public static final DeferredHolder<MobEffect, MobEffect> TISSUE_REGENERATION = EFFECTS.register("tissue_regeneration", () -> new com.codex.blacklight.effect.PassiveMarkerEffect(0xC54859));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID, path);
    }
}
