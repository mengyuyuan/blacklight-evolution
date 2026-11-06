package com.codex.blacklight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

/** Replaces the vanilla bar for the Supreme Hunter: a wide flesh bar with phase notches and the weaken line. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class SupremeHunterBossBar {
    private static final String KEY="entity.blacklight.supreme_hunter";
    private static float shown=-1,trail=-1;private static long lastTick;
    private SupremeHunterBossBar(){}
    static boolean ours(Component name){return name.getContents() instanceof TranslatableContents t&&t.getKey().equals(KEY);}
    @SubscribeEvent public static void bar(CustomizeGuiOverlayEvent.BossEventProgress event){
        var boss=event.getBossEvent();if(!ours(boss.getName()))return;
        event.setCanceled(true);event.setIncrement(30);
        var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;
        int center=event.getX()+91,width=Math.min(300,g.guiWidth()-40),left=center-width/2,top=event.getY()+3,h=8;
        float p=net.minecraft.util.Mth.clamp(boss.getProgress(),0,1);
        long now=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        if(shown<0||p>shown)trail=p;shown=p;
        if(now!=lastTick){trail=Math.max(p,trail-(trail-p)*.08F-.0008F);lastTick=now;}
        // frame
        g.fill(left-3,top-3,left+width+3,top+h+3,0xE0100606);
        g.fill(left-2,top-2,left+width+2,top-1,0xFF5A1410);g.fill(left-2,top+h+1,left+width+2,top+h+2,0xFF5A1410);
        g.fill(left-2,top-1,left-1,top+h+1,0xFF5A1410);g.fill(left+width+1,top-1,left+width+2,top+h+1,0xFF5A1410);
        g.fill(left,top,left+width,top+h,0xFF1C0B0A);
        // damage trail, then live health with a darker lower half for depth
        g.fill(left,top,left+Math.round(width*trail),top+h,0xFFE8C9A0);
        int fill=Math.round(width*p);boolean weak=p<=.12F;
        int hi=weak?0xFFB070FF:p<=.33F?0xFFD0322A:0xFFB3201A,lo=weak?0xFF5A2A90:p<=.33F?0xFF7A1410:0xFF6A100C;
        g.fill(left,top,left+fill,top+h/2,hi);g.fill(left,top+h/2,left+fill,top+h,lo);
        // sinew streaks
        for(int x=left+((int)(now/2)%12);x<left+fill;x+=12)g.fill(x,top+1,x+3,top+2,0x55FFFFFF);
        // phase notches at 66% and 33%, weaken line at 12%
        for(float mark:new float[]{.66F,.33F}){int x=left+Math.round(width*mark);g.fill(x-1,top-3,x+1,top+h+3,0xFFF2E6D0);}
        int wx=left+Math.round(width*.12F);g.fill(wx,top-4,wx+1,top+h+4,0xFFB070FF);
        // name plate and percentage
        Component name=boss.getName();int nw=font.width(name);
        g.fill(center-nw/2-6,top-15,center+nw/2+6,top-4,0xC0100606);
        g.drawString(font,name,center-nw/2,top-13,0xFFF2D7B0,true);
        String pct=Math.round(p*100)+"%";g.drawString(font,pct,left+width-font.width(pct),top+h+4,weak?0xFFB070FF:0xFFD9B8A0,true);
        if(weak)g.drawString(font,Component.translatable("hud.blacklight.supreme_weakened"),left,top+h+4,0xFFB070FF,true);
    }
}
