package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.HealthHudLayout;
import com.codex.blacklight.client.PrototypeHealthHud;
import com.codex.blacklight.config.PassiveHudConfig;
import com.codex.blacklight.network.WingSpeedPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.codex.blacklight.forge.PacketDistributor;

/** HUD placement is local. Flight preference is validated, saved and echoed by the server. */
public final class PresentationSettingsScreen extends Screen {
    private final Screen parent;
    private final boolean hud;
    private double horizontal,vertical;
    private int speed;
    public PresentationSettingsScreen(Screen parent,boolean hud){
        super(Component.translatable(hud?"setting.blacklight.health_hud":"setting.blacklight.wing_speed"));
        this.parent=parent;this.hud=hud;
        horizontal=PassiveHudConfig.HEALTH_X.get();vertical=PassiveHudConfig.HEALTH_Y.get();
        var p=net.minecraft.client.Minecraft.getInstance().player;
        speed=p==null?100:ModAttachments.getData(p,ModAttachments.SKILL_SETTINGS).wingSpeedPercent();
    }
    @Override protected void init(){
        int w=Math.min(240,width-24),x=(width-w)/2,y=Math.max(38,(height-130)/2);
        if(hud){
            addRenderableWidget(new Slider(x,y,w,"setting.blacklight.health_horizontal",horizontal,v->horizontal=v));
            addRenderableWidget(new Slider(x,y+24,w,"setting.blacklight.health_vertical",vertical,v->vertical=v));
        }else addRenderableWidget(new Slider(x,y,w,"setting.blacklight.wing_speed",(speed-10)/90D,v->speed=10+(int)Math.round(v*90)));
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"),b->{horizontal=vertical=0;speed=100;rebuildWidgets();}).bounds(x,y+56,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x,y+80,w,20).build());
    }
    @Override public void renderBackground(GuiGraphics g){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(0,0,width,height,0xA015171B);
        g.drawCenteredString(font,title,width/2,12,0xFFF2EAE4);
        var hint=Component.translatable(hud?"setting.blacklight.health_hud.desc":"setting.blacklight.wing_speed.desc");
        int textY=height-30;
        for(var line:font.split(hint,Math.max(80,width-24))) {g.drawCenteredString(font,line,width/2,textY,0xFFD2CBC4);textY+=10;}
        super.render(g,x,y,partial);
        if(hud){
            int w=HealthHudLayout.meterWidth(width);
            var point=HealthHudLayout.position(width,height,Math.min(width-20,w*2),horizontal,vertical);
            if(!PrototypeHealthHud.preview(g,point.x(),point.y(),w)){
                g.fill(point.x(),point.y(),point.x()+w,point.y()+18,0xFF401A21);
                g.fill(point.x()+2,point.y()+2,point.x()+w*3/4,point.y()+16,0xFFE24C43);
            }
        }
    }
    @Override public void onClose(){
        if(hud){PassiveHudConfig.HEALTH_X.set(horizontal);PassiveHudConfig.HEALTH_Y.set(vertical);PassiveHudConfig.SPEC.save();}
        else PacketDistributor.sendToServer(new WingSpeedPayload(speed));
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen(){return false;}
    private final class Slider extends AbstractSliderButton {
        private final String key;private final java.util.function.DoubleConsumer apply;
        Slider(int x,int y,int w,String key,double initial,java.util.function.DoubleConsumer apply){
            super(x,y,w,20,Component.empty(),initial);this.key=key;this.apply=apply;updateMessage();
        }
        @Override protected void updateMessage(){setMessage(Component.translatable(key).append(": "+Math.round(hud?value*100:10+value*90)+"%"));}
        @Override protected void applyValue(){apply.accept(value);}
    }
}
