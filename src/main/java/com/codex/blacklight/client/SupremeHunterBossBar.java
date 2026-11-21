package com.codex.blacklight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;

/** Shared Blacklight boss HUD, retaining the Supreme Hunter flesh frame and damage trail. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class SupremeHunterBossBar {
    private static final String KEY="entity.blacklight.supreme_hunter";
    private static final java.util.Map<java.util.UUID,Trail> TRAILS=new java.util.HashMap<>();
    private static Object world;
    private static final class Trail {float shown=-1,trail=-1;long tick,seen;}
    private SupremeHunterBossBar(){}
    private static String key(Component name){return name.getContents() instanceof TranslatableContents t?t.getKey():"";}
    static boolean ours(Component name){return java.util.Set.of(KEY,"boss.blacklight.phase","boss.blacklight.weakpoints","boss.blacklight.exposed","boss.blacklight.collapsing").contains(key(name));}
    @SubscribeEvent public static void bar(CustomizeGuiOverlayEvent.BossEventProgress event){
        var boss=event.getBossEvent();if(!ours(boss.getName()))return;
        var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;TRAILS.clear();}
        String key=key(boss.getName());
        if(key.equals("boss.blacklight.weakpoints")||key.equals("boss.blacklight.exposed")||key.equals("boss.blacklight.collapsing")){defense(event,key.equals("boss.blacklight.exposed"));return;}
        event.setCanceled(true);event.setIncrement(30);
        var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;
        int center=event.getX()+91,width=Math.min(300,g.guiWidth()-40),left=center-width/2,top=event.getY()+3,h=8;
        float p=net.minecraft.util.Mth.clamp(boss.getProgress(),0,1);
        long now=Minecraft.getInstance().level==null?0:Minecraft.getInstance().level.getGameTime();
        TRAILS.entrySet().removeIf(e->now-e.getValue().seen>40);
        var state=TRAILS.computeIfAbsent(boss.getId(),id->new Trail());state.seen=now;
        if(state.shown<0||p>state.shown)state.trail=p;state.shown=p;
        if(now!=state.tick){state.trail=Math.max(p,state.trail-(state.trail-p)*.08F-.0008F);state.tick=now;}
        boolean supreme=key.equals(KEY);
        // frame
        g.fill(left-3,top-3,left+width+3,top+h+3,0xE0100606);
        g.fill(left-2,top-2,left+width+2,top-1,0xFF5A1410);g.fill(left-2,top+h+1,left+width+2,top+h+2,0xFF5A1410);
        g.fill(left-2,top-1,left-1,top+h+1,0xFF5A1410);g.fill(left+width+1,top-1,left+width+2,top+h+1,0xFF5A1410);
        g.fill(left,top,left+width,top+h,0xFF1C0B0A);
        // damage trail, then live health with a darker lower half for depth
        g.fill(left,top,left+Math.round(width*state.trail),top+h,0xFFE8C9A0);
        int fill=Math.round(width*p);boolean weak=boss.getColor()==net.minecraft.world.BossEvent.BossBarColor.PURPLE;
        int hi=weak?0xFFB070FF:p<=.33F?0xFFD0322A:0xFFB3201A,lo=weak?0xFF5A2A90:p<=.33F?0xFF7A1410:0xFF6A100C;
        g.fill(left,top,left+fill,top+h/2,hi);g.fill(left,top+h/2,left+fill,top+h,lo);
        // sinew streaks
        for(int x=left+((int)(now/2)%12);x<left+fill;x+=12)g.fill(x,top+1,x+3,top+2,0x55FFFFFF);
        // phase notches at 66% and 33%, weaken line at 12%
        for(float mark:supreme?new float[]{.66F,.33F}:new float[]{.65F,.30F}){int x=left+Math.round(width*mark);g.fill(x-1,top-3,x+1,top+h+3,0xFFF2E6D0);}
        if(supreme){int wx=left+Math.round(width*.12F);g.fill(wx,top-4,wx+1,top+h+4,0xFFB070FF);}
        // name plate and percentage
        Component name=boss.getName();int nw=font.width(name);
        g.fill(center-nw/2-6,top-15,center+nw/2+6,top-4,0xC0100606);
        g.drawString(font,name,center-nw/2,top-13,0xFFF2D7B0,true);
        String pct=Math.round(p*100)+"%";g.drawString(font,pct,left+width-font.width(pct),top+h+4,weak?0xFFB070FF:0xFFD9B8A0,true);
        if(weak)g.drawString(font,Component.translatable(supreme?"hud.blacklight.supreme_weakened":"boss.blacklight.exposed"),left,top+h+4,0xFFB070FF,true);
    }
    private static void defense(CustomizeGuiOverlayEvent.BossEventProgress event,boolean exposed){
        event.setCanceled(true);event.setIncrement(21);
        var g=event.getGuiGraphics();var font=Minecraft.getInstance().font;var boss=event.getBossEvent();
        int width=Math.min(300,g.guiWidth()-40),left=event.getX()+91-width/2,top=event.getY()+1;
        int fill=Math.round(width*net.minecraft.util.Mth.clamp(boss.getProgress(),0,1));
        g.fill(left-3,top-3,left+width+3,top+7,0xE0100606);
        g.fill(left-2,top-2,left+width+2,top-1,0xFF5A1410);g.fill(left-2,top+5,left+width+2,top+6,0xFF5A1410);
        g.fill(left,top,left+width,top+4,0xFF1C0B0A);
        g.fill(left,top,left+fill,top+2,exposed?0xFFE5B756:0xFFB070FF);
        g.fill(left,top+2,left+fill,top+4,exposed?0xFF8A5822:0xFF5A2A90);
        g.drawString(font,boss.getName(),left,top+8,exposed?0xFFE5B756:0xFFCAA9E8,true);
    }
}
