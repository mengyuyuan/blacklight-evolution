package com.codex.blacklight.item;

import com.codex.blacklight.world.FieldNotes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SurvivalManualItem extends Item {
    public static final ResourceLocation BOOK = new ResourceLocation("blacklight", "survival_manual");

    public SurvivalManualItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer reader) {
            if (reader.isShiftKeyDown()) FieldNotes.open(reader, false);
            else if (!openPatchouli(reader)) FieldNotes.open(reader, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    // Patchouli is optional on both loaders. Its public server API opens the book for this reader only.
    private static boolean openPatchouli(ServerPlayer player) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("patchouli")) return false;
        try {
            Class<?> api = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object instance = api.getMethod("get").invoke(null);
            Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI")
                    .getMethod("openBookGUI", ServerPlayer.class, ResourceLocation.class).invoke(instance, player, BOOK);
            return true;
        } catch (ReflectiveOperationException error) {
            com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Could not open Patchouli survival manual", error);
            return false;
        }
    }
}
