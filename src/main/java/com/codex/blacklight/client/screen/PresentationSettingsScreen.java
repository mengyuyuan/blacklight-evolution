package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.BlacklightUi;

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
import net.neoforged.neoforge.network.PacketDistributor;

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
        speed=p==null?100:p.getData(ModAttachments.SKILL_SETTINGS).wingSpeedPercent();
    }
    public boolean previewsHealth(){return hud;}
    private int controlsTop(){return Math.max(40,(height-(hud?140:116))/2);}
    @Override protected void init(){
        int w=Math.min(240,width-24),x=(width-w)/2,y=controlsTop();
        if(hud){
            addRenderableWidget(new Slider(x,y,w,"setting.blacklight.health_horizontal",horizontal,v->horizontal=v));
            addRenderableWidget(new Slider(x,y+24,w,"setting.blacklight.health_vertical",vertical,v->vertical=v));
        }else addRenderableWidget(new Slider(x,y,w,"setting.blacklight.wing_speed",(speed-10)/90D,v->speed=10+(int)Math.round(v*90)));
        int buttons=y+(hud?52:28), half=(w-4)/2;
        addRenderableWidget(Button.builder(Component.translatable("controls.reset"),b->{horizontal=vertical=0;speed=100;rebuildWidgets();}).bounds(x,buttons,half,20).build(TutorialButton::new));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+half+4,buttons,w-half-4,20).build(TutorialButton::new));
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fillGradient(0,0,width,height,0xA917171D,0xC5101116);
        int w=Math.min(240,width-24),left=(width-w)/2,top=controlsTop();
        var hint=Component.translatable(hud?"setting.blacklight.health_hud.desc":"setting.blacklight.wing_speed.desc");
        var lines=font.split(hint,w-4);
        int textY=top+(hud?82:58);
        BlacklightUi.panel(g,left-6,top-26,left+w+6,textY+lines.size()*10+7);
        BlacklightUi.title(g,font,title,left,top-17);
        for(var line:lines){g.drawString(font,line,left+2,textY,BlacklightUi.MUTED,false);textY+=10;}
        if(hud){
            int meter=HealthHudLayout.meterWidth(width);
            var point=HealthHudLayout.position(width,height,Math.min(width-20,meter*2),horizontal,vertical);
            if(!PrototypeHealthHud.preview(g,point.x(),point.y(),meter)){
                g.fill(point.x(),point.y(),point.x()+meter,point.y()+18,0xFF401A21);
                g.fill(point.x()+2,point.y()+2,point.x()+meter*3/4,point.y()+16,0xFFE24C43);
            }
        }
        super.render(g,x,y,partial);
    }
    @Override public void onClose(){
        if(hud){PassiveHudConfig.HEALTH_X.set(horizontal);PassiveHudConfig.HEALTH_Y.set(vertical);PassiveHudConfig.SPEC.save();}
        else PacketDistributor.sendToServer(new WingSpeedPayload(speed));
        minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen(){return false;}
    private final class Slider extends AbstractSliderButton {
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){
            BlacklightUi.slider(g,net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX(),getY(),getWidth(),getHeight(),value,active,isHoveredOrFocused());
        }

        private final String key;private final java.util.function.DoubleConsumer apply;
        Slider(int x,int y,int w,String key,double initial,java.util.function.DoubleConsumer apply){
            super(x,y,w,20,Component.empty(),initial);this.key=key;this.apply=apply;updateMessage();
        }
        @Override protected void updateMessage(){setMessage(Component.translatable(key).append(": "+Math.round(hud?value*100:10+value*90)+"%"));}
        @Override protected void applyValue(){apply.accept(value);}
    }
}
