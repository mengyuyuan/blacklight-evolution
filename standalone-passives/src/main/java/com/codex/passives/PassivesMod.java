package com.codex.passives;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

@Mod(PassivesMod.ID)
public final class PassivesMod {
    public static final String ID = "adaptive_passives";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredItem<SerumItem> ADAPTATION_SERUM = ITEMS.register("adaptation_serum", () -> new SerumItem(1, 1));
    public static final DeferredItem<SerumItem> REGENERATION_SERUM = ITEMS.register("regeneration_serum", () -> new SerumItem(2, 1));
    public static final DeferredItem<SerumItem> ADAPTATION_II_SERUM = ITEMS.register("adaptation_ii_serum", () -> new SerumItem(1, 2));
    public static final DeferredItem<SerumItem> REGENERATION_II_SERUM = ITEMS.register("regeneration_ii_serum", () -> new SerumItem(2, 2));
    public static final DeferredItem<SerumItem> VITALITY_SERUM = ITEMS.register("vitality_serum", () -> new SerumItem(4, 1));
    public static final DeferredHolder<MobEffect, MobEffect> ADAPTATION = EFFECTS.register("adaptation", () -> new MarkerEffect(0x48B3D1));
    public static final DeferredHolder<MobEffect, MobEffect> REGENERATION = EFFECTS.register("regeneration", () -> new MarkerEffect(0xC54859));
    public static final DeferredHolder<MobEffect, MobEffect> VITALITY = EFFECTS.register("vitality", () -> new MarkerEffect(0xE8AD45));
    static {
        TABS.register("potions", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.adaptive_passives"))
                .icon(() -> ADAPTATION_SERUM.get().getDefaultInstance()).displayItems((p, out) -> {
                    out.accept(ADAPTATION_SERUM.get()); out.accept(REGENERATION_SERUM.get());
                    out.accept(ADAPTATION_II_SERUM.get()); out.accept(REGENERATION_II_SERUM.get()); out.accept(VITALITY_SERUM.get());
                }).build());
    }
    public PassivesMod(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, PassiveDisplayConfig.SPEC, "adaptive_passives-client.toml");
        ITEMS.register(bus); EFFECTS.register(bus); TABS.register(bus);
        bus.addListener(PassiveNetwork::register);
        bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) -> e.enqueueWork(() -> {
            var health = (RangedAttribute)Attributes.MAX_HEALTH.value();
            ((com.codex.passives.mixin.HealthLimitAccessor)health).adaptivePassives$maximum(Math.max(health.getMaxValue(), Float.MAX_VALUE / 16.0));
        }));
    }
}
