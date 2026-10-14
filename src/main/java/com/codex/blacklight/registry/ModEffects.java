package com.codex.blacklight.registry;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.effect.BlacklightInfectionEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, BlacklightNeoMod.MOD_ID);

    public static final RegistryObject<MobEffect> BLACKLIGHT_INFECTION = EFFECTS.register(
            "blacklight_infection", () -> new BlacklightInfectionEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("infection_speed"),
                            0.16D, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("infection_damage"),
                            2.5D, AttributeModifier.Operation.ADDITION)
                    .addAttributeModifier(Attributes.MAX_HEALTH, id("infection_health"),
                            6.0D, AttributeModifier.Operation.ADDITION));

    public static final RegistryObject<MobEffect> BIOMASS_CONTACT=EFFECTS.register("biomass_contact",()->new com.codex.blacklight.effect.BiomassContactEffect().addAttributeModifier(Attributes.MOVEMENT_SPEED,"4b94902b-bd7c-4a8a-84a4-b26580c9d7cb",-.45D,AttributeModifier.Operation.MULTIPLY_TOTAL));

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }

    private static String id(String path) {
        return java.util.UUID.nameUUIDFromBytes((BlacklightNeoMod.MOD_ID+":"+path).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
