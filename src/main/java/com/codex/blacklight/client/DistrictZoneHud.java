package com.codex.blacklight.client;

import com.codex.blacklight.network.DistrictZonePayload;
import com.codex.blacklight.config.PassiveHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Compact area name only; no debug counters or tutorial popups. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class DistrictZoneHud {
    private static DistrictZonePayload state;
    private DistrictZoneHud(){}
    public static void accept(DistrictZonePayload value){state=value;}
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event){state=null;}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(state==null||!state.active()||!PassiveHudConfig.SHOW_ZONE.get()||mc.level==null||mc.player==null
                ||mc.options.hideGui||mc.screen!=null||!state.dimension().equals(mc.level.dimension().location()))return;
        var label=Component.translatable("zone.blacklight."+(state.zone()==2?"red":state.zone()==1?"yellow":"green"));
        int color=state.zone()==2?0xFFF16A65:state.zone()==1?0xFFF0C76B:0xFF81CE9A;
        int w=mc.font.width(label),x=mc.getWindow().getGuiScaledWidth()-w-14,y=mc.getWindow().getGuiScaledHeight()-52;
        var g=event.getGuiGraphics();g.fill(x-10,y-4,x+w+6,y+12,0xA0101114);g.fill(x-5,y+1,x-2,y+7,color);
        g.drawString(mc.font,label,x,y,color,false);
    }
}
