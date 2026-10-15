package com.codex.passives;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;


@Mod(PassivesMod.ID)
public final class PassivesMod {
    public static final String ID = "adaptive_passives";
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final RegistryObject<SerumItem> ADAPTATION_SERUM = ITEMS.register("adaptation_serum", () -> new SerumItem(1, 1));
    public static final RegistryObject<SerumItem> REGENERATION_SERUM = ITEMS.register("regeneration_serum", () -> new SerumItem(2, 1));
    public static final RegistryObject<SerumItem> ADAPTATION_II_SERUM = ITEMS.register("adaptation_ii_serum", () -> new SerumItem(1, 2));
    public static final RegistryObject<SerumItem> REGENERATION_II_SERUM = ITEMS.register("regeneration_ii_serum", () -> new SerumItem(2, 2));
    public static final RegistryObject<SerumItem> VITALITY_SERUM = ITEMS.register("vitality_serum", () -> new SerumItem(4, 1));
    public static final RegistryObject<MobEffect> ADAPTATION = EFFECTS.register("adaptation", () -> new MarkerEffect(0x48B3D1));
    public static final RegistryObject<MobEffect> REGENERATION = EFFECTS.register("regeneration", () -> new MarkerEffect(0xC54859));
    public static final RegistryObject<MobEffect> VITALITY = EFFECTS.register("vitality", () -> new MarkerEffect(0xE8AD45));
    static {
        TABS.register("potions", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.adaptive_passives"))
                .icon(() -> ADAPTATION_SERUM.get().getDefaultInstance()).displayItems((p, out) -> {
                    out.accept(ADAPTATION_SERUM.get()); out.accept(REGENERATION_SERUM.get());
                    out.accept(ADAPTATION_II_SERUM.get()); out.accept(REGENERATION_II_SERUM.get()); out.accept(VITALITY_SERUM.get());
                }).build());
    }
    public PassivesMod() {
        IEventBus bus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, PassiveDisplayConfig.SPEC, "adaptive_passives-client.toml");
        ITEMS.register(bus); EFFECTS.register(bus); TABS.register(bus);
        PassiveNetwork.register();
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            var health = (RangedAttribute)Attributes.MAX_HEALTH;
            ((com.codex.passives.mixin.HealthLimitAccessor)health).adaptivePassives$maximum(Math.max(health.getMaxValue(), Float.MAX_VALUE / 16.0));
        }));
    }
}
