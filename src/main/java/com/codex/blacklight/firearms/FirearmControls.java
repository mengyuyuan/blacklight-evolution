package com.codex.blacklight.firearms;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FirearmControls {
    public static final KeyMapping RELOAD=new KeyMapping("key.blacklight.firearm_reload",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"key.categories.blacklight");
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(RELOAD);}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();while(RELOAD.consumeClick()){
            if(mc.player==null||mc.level==null||mc.screen!=null||mc.isPaused()||!mc.player.isAlive()||mc.player.isSpectator())continue;
            InteractionHand hand=mc.player.getMainHandItem().getItem() instanceof FirearmItem?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
            if(mc.player.getItemInHand(hand).getItem() instanceof FirearmItem gun){
                if(gun.reserveAmmo(mc.player)==0)com.codex.blacklight.client.FirearmHud.missing();
                PacketDistributor.sendToServer(new com.codex.blacklight.network.FirearmReloadPayload(hand.ordinal()));
            }
        }
    }
    private FirearmControls(){}
}
