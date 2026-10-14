package com.codex.blacklight.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat menu controls retain vanilla focus, narration, click and keyboard handling. */
final class TutorialButton extends Button {
    TutorialButton(int x,int y,int width,int height,Component label,OnPress action){super(x,y,width,height,label,action,DEFAULT_NARRATION);}
    @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        g.fill(x,y,x+w,y+h,active?(isHoveredOrFocused()?0xFF71302B:0xFF452523):0xFF242527);
        g.fill(x,y+h-1,x+w,y+h,active?0xFFE05A4A:0xFF535353);
        if(isFocused())g.renderOutline(x,y,w,h,0xFFEBA393);
        renderScrollingString(g,Minecraft.getInstance().font,2,active?0xFFF0E8DF:0xFFAAA8A5);
    }
}
