package com.codex.blacklight.client;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/** Fixed-size biological meter; health growth never adds rows of hearts. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class PrototypeHealthHud {
    private static LocalPlayer owner;
    private static float lastHealth,lastMaximum,trail;
    private static double lastTime=-1,holdUntil;
    private record Bar(float x,float y,float width,float height) { }
    private static final class SourceBar {
        // Use the same exported GFX placement as NativeHealthHud, including its GUI scale.
        static final Bar VALUE=load();
        static Bar load(){
            try {
                var data=com.codex.blacklight.combat.MotionResources.read("health-hud.data",1_000_000).orElse(null);
                if(data==null||data.get("schema").getAsInt()!=1)return null;
                var b=data.getAsJsonArray("bar");float width=data.get("width").getAsFloat();
                if(!(width>0)||!Float.isFinite(width)||b.size()!=4)return null;
                var value=new Bar(b.get(0).getAsFloat()/width,b.get(1).getAsFloat()/width,
                        b.get(2).getAsFloat()/width,b.get(3).getAsFloat()/width);
                return Float.isFinite(value.x)&&Float.isFinite(value.y)&&Float.isFinite(value.width)
                        &&Float.isFinite(value.height)&&value.width>0&&value.height>0?value:null;
            }catch(RuntimeException ignored){return null;}
        }
    }
    private PrototypeHealthHud(){}
    public static boolean preview(GuiGraphics graphics,int x,int y,int width){return NativeHealthHud.render(graphics,x,y,width,.75F,.85F,0);}
    public static boolean active(){
        var p=Minecraft.getInstance().player;
        return p!=null&&!p.isSpectator()&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).isInfected();
    }
    public static int utilityTop(){
        var p=Minecraft.getInstance().player;
        if(!active())return 10;
        if(com.codex.blacklight.config.PassiveHudConfig.HEALTH_X.get()>0||com.codex.blacklight.config.PassiveHudConfig.HEALTH_Y.get()>0)return 10;
        return Minecraft.getInstance().level!=null&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ADRENALINE).active(Minecraft.getInstance().level.getGameTime())?48:40;
    }
    @SubscribeEvent public static void hideHearts(RenderGuiOverlayEvent.Pre event){
        if(active()&&event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id()))event.setCanceled(true);

    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!active()||mc.level==null){owner=null;lastTime=-1;return;}
        if(mc.options.hideGui)return;
        var p=mc.player;var g=event.getGuiGraphics();
        float maximum=Math.max(1,p.getMaxHealth()),health=Mth.clamp(p.getHealth(),0,maximum);
        double now=(mc.level.getGameTime()+mc.getFrameTime())/20D;
        boolean reset=owner!=p||lastTime<0||now<lastTime||maximum!=lastMaximum;
        if(reset){owner=p;trail=health;lastHealth=health;holdUntil=now;}
        if(health<lastHealth){trail=Math.max(trail,lastHealth);holdUntil=now+.35;}
        double dt=reset?0:net.minecraft.util.Mth.clamp(now-lastTime,0,.25);
        if(health>=trail)trail=health;
        else if(now>holdUntil)trail=Math.max(health,(float)(trail-maximum*dt*.65));
        lastHealth=health;lastMaximum=maximum;lastTime=now;
        int w=HealthHudLayout.meterWidth(mc.getWindow().getGuiScaledWidth());
        var placement=HealthHudLayout.position(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),
                Math.min(mc.getWindow().getGuiScaledWidth()-20,w*2),com.codex.blacklight.config.PassiveHudConfig.HEALTH_X.get(),
                com.codex.blacklight.config.PassiveHudConfig.HEALTH_Y.get());
        int x=placement.x(),y=placement.y();
        if(SourceBar.VALUE!=null)renderCriticalMassBacking(g,p,x,y,w,true);
        if(NativeHealthHud.render(g,x,y,w,health/maximum,trail/maximum,p.getAbsorptionAmount()/maximum)){
            renderAdrenalineStatus(g,p,x,y+24,w,mc.level.getGameTime());return;
        }
        boolean critical=health>0&&health/maximum<.25F;
        int accent=critical?0xFFFF5948:0xFFDB352E;
        cut(g,x-2,y-2,w+4,24,0xB5090C0F);
        renderCriticalMassBacking(g,p,x,y,w,false);
        g.fill(x,y,x+2,y+18,accent);
        int bx=x+6,by=y+2,bw=w-10,bh=15;
        cut(g,bx-1,by-1,bw+2,bh+2,0xFF61252A);
        cut(g,bx,by,bw,bh,0xFF1C1118);
        meter(g,bx,by,bw,bh,trail/maximum,0xFFE99666,0xFF9F443A);
        int top=p.hasEffect(MobEffects.POISON)?0xFF86BA51:p.hasEffect(MobEffects.WITHER)?0xFF927B9E:accent;
        meter(g,bx,by,bw,bh,health/maximum,top,0xFF711520);
        for(int i=1;i<5;i++){int sx=bx+bw*i/5;g.fill(sx,by+2,sx+1,by+bh-2,0x5010060C);}
        g.fill(bx+4,by+1,bx+bw-5,by+2,0x32FFE5D5);
        // Absorption has its own meter instead of extra heart rows or inflating HP.
        float absorb=p.getAbsorptionAmount();
        if(absorb>0){
            g.fill(bx,by+bh+3,bx+bw,by+bh+5,0xFF302419);
            g.fill(bx,by+bh+3,bx+Math.max(1,Math.round(bw*Math.min(1,absorb/maximum))),by+bh+5,0xFFF1C66D);
        }
        renderAdrenalineStatus(g,p,x,y+24,w,mc.level.getGameTime());
    }
    private static void renderCriticalMassBacking(GuiGraphics g,LocalPlayer p,int x,int y,int w,boolean source){
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        if(!state.hasCombatSkill(com.codex.blacklight.power.CombatSkill.CRITICAL_MASS))return;
        int capacity=com.codex.blacklight.power.CombatSkill.criticalMassCapacity(state.getCombatSkillMask());
        float reserve=com.codex.blacklight.util.MathCompat.clamp(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CRITICAL_MASS).reserve(),0,capacity);
        if(reserve<=0)return;
        var bar=source?new Bar(x+SourceBar.VALUE.x*w,y+SourceBar.VALUE.y*w,
                SourceBar.VALUE.width*w,SourceBar.VALUE.height*w):new Bar(x+6,y+2,w-10,15);
        int left=Math.round(bar.x),top=Math.round(bar.y),right=Math.round(bar.x+bar.width);
        int height=Math.max(3,Math.round(bar.height));
        // Each charge is 25% extra maximum health. Blue continues behind the red bar
        // into its right end, so a full red meter cannot hide newly consumed health.
        int start=source?right:right+1;
        int room=Math.max(0,Minecraft.getInstance().getWindow().getGuiScaledWidth()-10-start);
        int capacityWidth=Math.min(room,Math.max(1,Math.round(bar.width*capacity
                *com.codex.blacklight.power.CriticalMassState.CAPACITY_HEALTH_FRACTION)));
        int extension=Math.min(capacityWidth,Math.max(1,Math.round(capacityWidth*reserve/capacity)));
        g.fill(left,top,start+extension,top+height,0xE62B8FBF);
        g.fill(left,top,start+extension,top+1,0xEE66DAF0);
        g.fill(start,top+height-1,start+extension,top+height,0xD51D527A);
        for(int i=1;i<capacity;i++){
            int mark=start+Math.round(capacityWidth*i/(float)capacity);
            if(mark<start+extension)g.fill(mark,top+1,mark+1,top+height,0x7020394B);
        }
    }
    private static void renderAdrenalineStatus(GuiGraphics g, LocalPlayer p, int x, int y, int w, long now) {
        var mass=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CRITICAL_MASS);
        var surge=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ADRENALINE);
        if(surge.active(now)){
            boolean blocked=surge.ready(now)&&!mass.adrenalineReleaseAllowed(now);
            var hint=net.minecraft.network.chat.Component.translatable(surge.undamageable(now)?"hud.blacklight.adrenaline_grace"
                    :surge.chargeAvailable()?blocked?"hud.blacklight.adrenaline_cooldown":"hud.blacklight.adrenaline_ready"
                    :"hud.blacklight.adrenaline_recover",
                    (int)Math.ceil((surge.undamageable(now)?surge.undamageableUntil()-now
                            :blocked?mass.adrenalineWaitTicks(now):surge.until()-now)/20D));
            drawStatus(g,hint,x+6,y,w-10,0xFFFFBD53);
        }
    }
    private static void drawStatus(GuiGraphics g,net.minecraft.network.chat.Component label,int x,int y,int available,int color){
        var font=Minecraft.getInstance().font;
        float scale=Math.min(1,available/(float)Math.max(1,font.width(label)));
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.drawString(font,label,0,0,color,true);g.pose().popPose();
    }
    private static void cut(GuiGraphics g,int x,int y,int w,int h,int color){
        for(int row=0;row<h;row++){
            int inset=Math.max(0,4-Math.min(row,h-1-row));
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
        }
    }
    private static void meter(GuiGraphics g,int x,int y,int w,int h,float fraction,int top,int bottom){
        int right=x+Math.round(w*Mth.clamp(fraction,0,1));
        for(int row=0;row<h;row++){
            int inset=Math.max(0,4-Math.min(row,h-1-row)),from=x+inset,to=Math.min(right,x+w-inset);
            if(to<=from)continue;
            float t=row/(float)(h-1);
            int r=(int)Mth.lerp(t,(top>>16)&255,(bottom>>16)&255);
            int green=(int)Mth.lerp(t,(top>>8)&255,(bottom>>8)&255);
            int b=(int)Mth.lerp(t,top&255,bottom&255);
            g.fill(from,y+row,to,y+row+1,0xFF000000|r<<16|green<<8|b);
        }
    }
}
