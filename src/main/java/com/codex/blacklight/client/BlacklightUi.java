package com.codex.blacklight.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared surfaces and information hierarchy for Blacklight's in-game menus and HUD. */
public final class BlacklightUi {
    public static final int INK=0xFF101116, PANEL=0xFF1D2026, LINE=0xFF41424A;
    public static final int TEXT=0xFFF0ECE5, MUTED=0xFFB6B0AF, RED=0xFFF16D61, GOLD=0xFFE8BF84;
    private BlacklightUi() {}

    private static final net.minecraft.resources.ResourceLocation EMBLEMS=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","textures/gui/weapon_emblems.png");
    public static void emblem(GuiGraphics g,com.codex.blacklight.power.PowerForm form,int x,int y,int size) {
        int slot=switch(form){
            case CLAWS->0;case BLADE->1;case HAMMERFISTS->2;case WHIPFIST->3;case MUSCLE_MASS->4;case BACKSPINES->5;
            case SHIELD->7;case ARMOR->8;case THERMAL_VISION,INFECTED_VISION->9;case BIOMASS->10;case SOVEREIGN_WINGS->11;
            default->6;
        };
        g.blit(EMBLEMS,x,y,size,size,(slot%4)*32F,(slot/4)*32F,32,32,128,96);
    }

    public static void backdrop(GuiGraphics g,int w,int h) {
        g.fillGradient(0,0,w,h,0xFF232127,INK);
        g.fillGradient(0,0,w,37,0xFF322529,0xFF201C22);
        g.fill(0,36,w,37,0xFF594044);
        g.fill(0,36,Math.min(w/5,96),37,RED);
    }
    public static void panel(GuiGraphics g,int x,int y,int right,int bottom) {
        g.fillGradient(x,y,right,bottom,0xF323252C,0xF318191F);
        g.fill(x,y,right,y+1,LINE);
    }
    public static void hud(GuiGraphics g,int x,int y,int right,int bottom,int accent) {
        PrototypeHealthHud.reserve(x,y,right,bottom);
        g.fillGradient(x,y,right,bottom,0xDE23242A,0xD2101116);
        g.fill(x,y,x+2,bottom,accent);
    }
    public static void title(GuiGraphics g,Font f,Component title,int x,int y) {
        g.fill(x,y-2,x+2,y+10,RED);
        g.drawString(f,title,x+8,y,TEXT,false);
    }
    public static String fit(Font f,String text,int width) {
        if(f.width(text)<=width)return text;
        String dots="…";
        return width<f.width(dots)?"":f.plainSubstrByWidth(text,width-f.width(dots))+dots;
    }
    public static void wheelHint(GuiGraphics g,Font f,Component hint,int cx,int y,int maxWidth) {
        var lines=f.split(hint,maxWidth);
        int width=0;
        for(var line:lines)width=Math.max(width,f.width(line));
        g.fill(cx-width/2-6,y-3,cx+(width+1)/2+6,y+lines.size()*10+1,0xE61C1A20);
        for(var line:lines){g.drawCenteredString(f,line,cx,y,TEXT);y+=10;}
    }
    public static void slider(GuiGraphics g,Font font,Component label,int x,int y,int w,int h,double value,boolean active,boolean focus) {
        g.fill(x,y,x+w,y+h,focus?0xFF383139:PANEL);
        g.fill(x+3,y+h-4,x+w-3,y+h-2,LINE);
        int end=x+3+(int)Math.round((w-6)*value);
        g.fill(x+3,y+h-4,end,y+h-2,RED);
        g.fill(Math.min(x+w-5,end),y+h-5,Math.min(x+w-5,end)+2,y+h-1,TEXT);
        g.drawCenteredString(font,fit(font,label.getString(),w-10),x+w/2,y+(h-font.lineHeight)/2-1,active?TEXT:MUTED);
    }
}
