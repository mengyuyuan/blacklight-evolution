package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;

/** Keep the two available decisions visible for as long as a target is held. */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class GrabHud {
    private GrabHud() {}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||mc.options.hideGui)return;
        int stage=com.codex.blacklight.registry.ModAttachments.getData(mc.player, ModAttachments.GRAB_STAGE);
        if(!ConsumeVisual.holding(stage))return;
        event.getGuiGraphics().drawCenteredString(mc.font,Component.translatable(com.codex.blacklight.combat.BlockThrowSystem.held(mc.player)!=null?"message.blacklight.block_held":stage==ConsumeVisual.MOUNT_HOLD?"message.blacklight.grab_mounted":"message.blacklight.grab_held"),
                mc.getWindow().getGuiScaledWidth()/2,mc.getWindow().getGuiScaledHeight()/2+42,0xffe5ddd7);
    }
}
