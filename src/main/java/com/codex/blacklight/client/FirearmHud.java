package com.codex.blacklight.client;

import com.codex.blacklight.firearms.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;

/** Compact, inventory-derived gun status. No repeated firing messages across the viewport. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class FirearmHud {
    private FirearmHud(){}
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||mc.screen!=null||mc.player.isPassenger())return;
        var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof FirearmItem))stack=mc.player.getOffhandItem();
        if(!(stack.getItem() instanceof FirearmItem gun))return;
        var ammo=FirearmItem.state(stack,0);long started=FirearmReloadSystem.started(stack),now=mc.level.getGameTime();
        boolean loading=started>=0&&now<ammo.readyAt();
        int width=134,x=mc.getWindow().getGuiScaledWidth()-width-8,y=mc.getWindow().getGuiScaledHeight()-83;
        var g=e.getGuiGraphics();
        BlacklightUi.hud(g,x-5,y-4,x+width+4,y+(loading?27:23),BlacklightUi.GOLD);
        g.drawString(mc.font,Component.literal(ammo.magazine()+" / "+gun.definition().capacity()+"  ·  "+gun.reserveAmmo(mc.player)),x,y,ammo.magazine()==0?0xFFFFAA77:0xFFE8EDD9,false);
        var label=loading?Component.translatable("hud.blacklight.firearm_reloading"):stack.getHoverName();
        g.drawString(mc.font,label,x,y+11,0xFFB9C7CF,false);
        if(loading){g.fill(x,y+22,x+width,y+24,0xFF333A42);g.fill(x,y+22,x+(int)(width*com.codex.blacklight.forge.WingMath.clamp((now-started)/(double)gun.definition().reloadTicks(),0,1)),y+24,0xFFFFBD70);}
    }
}
