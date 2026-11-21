package com.codex.blacklight.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat menu controls retain vanilla focus, narration, click and keyboard handling. */
final class TutorialButton extends Button {
    TutorialButton(Builder builder){super(builder);}
    TutorialButton(int x,int y,int width,int height,Component label,OnPress action){super(x,y,width,height,label,action,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean hover=active&&isHoveredOrFocused();
        g.fillGradient(x,y,x+w,y+h,hover?0xFF694044:active?0xFF3A3035:0xFF27272C,hover?0xFF4C2D33:active?0xFF2D252B:0xFF202126);
        g.fill(x,y,x+w,y+1,hover?0xFFE28B80:active?0xFF865456:0xFF45454B);
        g.fill(x,y+h-1,x+w,y+h,0xFF18191F);
        if(isFocused())g.renderOutline(x,y,w,h,0xFFF5A096);
        renderScrollingString(g,Minecraft.getInstance().font,2,active?0xFFF0E8DF:0xFFAAA8A5);
    }
}
