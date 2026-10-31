package com.codex.blacklight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Explicit target feedback is native to Blacklight and follows every user-selected perspective. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class TargetLockHud {
    private record WorldFrame(Object level,long frame,Matrix4f view,Matrix4f projection,Vec3 camera,float partial) { }
    public record Marker(int targetId,float x,float y,boolean inFront,boolean onScreen,String cameraType) { }
    private static long frame;
    private static WorldFrame world;
    private static Marker marker;
    private static Object candidateLevel;
    private static int candidateTick=-100,readyCount;
    private static java.util.List<net.minecraft.world.entity.Mob> candidates=java.util.List.of();
    private TargetLockHud() { }
    public static Marker lastMarker(){return marker;}
    public static int readyMarkerCount(){return readyCount;}
    @SubscribeEvent public static void begin(net.minecraftforge.event.TickEvent.RenderTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.START)return;frame++;marker=null;if(Minecraft.getInstance().level==null){world=null;candidateLevel=null;candidateTick=-100;candidates=java.util.List.of();}}
    @SubscribeEvent public static void captureWorld(RenderLevelStageEvent event) {
        // Forge 1.20 supplies GameRenderer's projection stack at AFTER_LEVEL.
        // Entity-stage PoseStack is the real view transform used for world points.
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();
        world=new WorldFrame(mc.level,frame,new Matrix4f(event.getPoseStack().last().pose()),new Matrix4f(event.getProjectionMatrix()),
                event.getCamera().getPosition(),event.getPartialTick());
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();var g=event.getGuiGraphics();
        readyCount=0;
        if(mc.player==null||mc.level==null||mc.options.hideGui)return;
        consumeMarkers(g,mc);
        var dive=com.codex.blacklight.registry.ModAttachments.getData(mc.player, com.codex.blacklight.registry.ModAttachments.DEVASTATOR_VISUAL);
        if(dive.kind()==3&&dive.phase()==com.codex.blacklight.combat.DevastatorSystem.FLIGHT&&dive.diveAim()!=null){
            Vec3 delta=dive.diveAim().subtract(mc.player.position());
            String aim=net.minecraft.network.chat.Component.translatable("hud.blacklight.bulletdive_aim",
                    String.format(java.util.Locale.ROOT,"%.1f",Math.hypot(delta.x,delta.z))).getString();
            panel(g,aim,g.guiWidth()/2,g.guiHeight()-82,0xFFFFB297);
        }
        if(!ClientTargetLock.active())return;
        var target=ClientTargetLock.entityTarget();
        if(target==null){
            String notice=ClientTargetLock.notice();
            if(!notice.isBlank())panel(g,notice,g.guiWidth()/2,g.guiHeight()-54,0xFFFFCA77);
            return;
        }
        String name=(ClientTargetLock.pending()?"锁定中 · ":"锁定 · ")+target.getDisplayName().getString();
        var captured=world;
        if(captured!=null&&captured.level==mc.level&&captured.frame==frame) {
            var focus=ClientTargetLock.focusPoint(target).add(target.getPosition(captured.partial).subtract(target.position()));
            var eye=captured.camera;
            var point=WorldHudProjection.project(captured.view,captured.projection,eye.x,eye.y,eye.z,
                    focus.x,focus.y,focus.z,g.guiWidth(),g.guiHeight());
            marker=new Marker(target.getId(),point.x(),point.y(),point.inFront(),point.onScreen(),mc.options.getCameraType().name());
            if(point.onScreen()) {
                int x=Math.round(point.x()),y=Math.round(point.y()),color=ClientTargetLock.pending()?0xFFFFCA77:0xFFFF6B64;
                brackets(g,x,y,color);
            }
        }
        String status=name+"  "+Math.round(Math.sqrt(com.codex.blacklight.combat.ExplicitTarget.distanceSquared(mc.player,target)))+"m"
                +(marker==null||!marker.onScreen()?" · 目标在视野外":"");
        if(target instanceof net.minecraft.world.entity.Mob mob&&ready(mc,mob))status+=" · "+net.minecraft.network.chat.Component.translatable("hud.blacklight.consume_ready_short",SkillBindings.label("key.blacklight.grab")).getString();
        var shown=mc.font.plainSubstrByWidth(status,182);int statusY=g.guiHeight()-108;
        g.fill(4,statusY-4,12+Math.min(182,mc.font.width(shown)),statusY+12,0xA6090B10);
        g.drawString(mc.font,shown,8,statusY,0xFFE8DDD6,false);
        if(target instanceof com.codex.blacklight.entity.MilitaryVehicle vehicle){
            int w=72,x=8,y=statusY+13;
            g.fill(x,y,x+w,y+3,0xB0202020);g.fill(x,y,x+(int)(w*com.codex.blacklight.forge.WingMath.clamp(vehicle.health()/vehicle.maxHealth(),0,1)),y+3,0xFFFF6B64);
            g.drawString(mc.font,String.format(java.util.Locale.ROOT,"%.1f / %.0f",vehicle.health(),vehicle.maxHealth()),x+w+5,y-3,0xFFBFAEA8,false);
        }
        if(ClientClawEvents.targetInfoKey().isDown()&&mc.screen==null&&target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&com.codex.blacklight.power.AssimilationSystem.tissue(host)){
            var core=host.nativeTissueCore();
            int quality=com.codex.blacklight.infection.TissueCore.quality(host.tissueAge());
            String condition=com.codex.blacklight.combat.ConsumeSystem.healthReady(mc.player,host)?"ready":quality>=2&&host.tissueCoreFracture()<1?"armored":"weaken";
            var qualityLabel=net.minecraft.network.chat.Component.translatable(quality==0?"hud.blacklight.organ_developing":"hud.blacklight.organ_quality",quality);
            String info=net.minecraft.network.chat.Component.translatable("hud.blacklight.organ_target",net.minecraft.network.chat.Component.translatable("upgrade.blacklight."+core.id),qualityLabel,
                    net.minecraft.network.chat.Component.translatable("hud.blacklight.organ_"+condition)).getString();
            panel(g,info,g.guiWidth()/2,g.guiHeight()-68,0xFFDDA6A1);
            long generation=com.codex.blacklight.infection.TissueGrowth.generation(host.tissueAge());
            String evolution=net.minecraft.network.chat.Component.translatable("hud.blacklight.host_evolution",generation,host.tissueNutrition(),com.codex.blacklight.infection.TissueMetabolism.MAX_RESERVE).getString();
            var digestion=host.digestiveVisual();
            if(digestion.getInt("Queued")>0||digestion.getInt("Fat")>0)evolution+=" · "+net.minecraft.network.chat.Component.translatable("hud.blacklight.digestive_reserves",digestion.getInt("Queued"),digestion.getInt("Fat")).getString();
            panel(g,evolution,g.guiWidth()/2,g.guiHeight()-82,0xFFB9B0A3);
            String growthCost=net.minecraft.network.chat.Component.translatable("hud.blacklight.host_growth_cost",String.format(java.util.Locale.ROOT,"%.0f",com.codex.blacklight.infection.TissueMetabolism.phaseFood(host.tissueAge()))).getString();
            panel(g,growthCost,g.guiWidth()/2,g.guiHeight()-96,0xFFB9B0A3);
        }
    }
    private static boolean ready(Minecraft mc,net.minecraft.world.entity.Mob mob){return mob.isAlive()&&!mob.isRemoved()&&com.codex.blacklight.combat.ConsumeSystem.eligible(mob)&&com.codex.blacklight.combat.ConsumeSystem.healthReady(mc.player,mob);}
    private static void consumeMarkers(GuiGraphics g,Minecraft mc){
        var state=com.codex.blacklight.registry.ModAttachments.getData(mc.player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);var captured=world;
        if(!state.isInfected()||!state.isPrototypeMode()||captured==null||captured.level!=mc.level||captured.frame!=frame)return;
        if(candidateLevel!=mc.level||mc.player.tickCount-candidateTick>=10||mc.player.tickCount<candidateTick){
            candidateLevel=mc.level;candidateTick=mc.player.tickCount;
            candidates=mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,mc.player.getBoundingBox().inflate(16),mob->ready(mc,mob)&&mc.player.hasLineOfSight(mob)).stream()
                    .sorted(java.util.Comparator.comparingDouble(mc.player::distanceToSqr)).limit(8).toList();
        }
        for(var mob:candidates){
            if(!ready(mc,mob)||!mc.player.hasLineOfSight(mob)||mc.player.distanceToSqr(mob)>16*16)continue;
            var p=mob.getPosition(captured.partial).add(0,mob.getBbHeight()+.35,0);var eye=captured.camera;
            var screen=WorldHudProjection.project(captured.view,captured.projection,eye.x,eye.y,eye.z,p.x,p.y,p.z,g.guiWidth(),g.guiHeight());
            if(!screen.onScreen())continue;readyCount++;int x=Math.round(screen.x()),y=Math.round(screen.y());
            int pulse=205+(int)(35*Math.sin((mc.level.getGameTime()+captured.partial)*.16)),red=pulse<<24|0xFF5650;
            int[] twist={-3,-4,-2,2,4,3};
            for(int i=0;i<twist.length;i++){
                int yy=y-6+i*2,dx=twist[i];
                g.fill(x+dx-1,yy-1,x+dx+3,yy+3,0x99000000);g.fill(x-dx-1,yy-1,x-dx+3,yy+3,0x99000000);
                g.fill(x+dx,yy,x+dx+2,yy+2,red);g.fill(x-dx,yy,x-dx+2,yy+2,red);
                if(i%2==0)g.fill(x-Math.abs(dx)+1,yy+1,x+Math.abs(dx)+1,yy+2,0xCCFFB0A4);
            }
        }
    }
    private static void brackets(GuiGraphics g,int x,int y,int color) {
        int r=9,l=5;
        for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1}) {
            int px=x+sx*r,py=y+sy*r;
            g.fill(Math.min(px,px-sx*l),py,Math.max(px,px-sx*l)+1,py+2,color);
            g.fill(px,Math.min(py,py-sy*l),px+2,Math.max(py,py-sy*l)+1,color);
        }
    }
    private static void panel(GuiGraphics g,String text,int x,int y,int color) {
        var font=Minecraft.getInstance().font;text=font.plainSubstrByWidth(text,Math.max(20,g.guiWidth()-24));
        int w=font.width(text);x=net.minecraft.util.Mth.clamp(x,w/2+6,Math.max(w/2+6,g.guiWidth()-w/2-6));
        g.fill(x-w/2-4,y-2,x+w/2+5,y+11,0xA6090B10);
        g.drawCenteredString(font,text,x,y,color);
    }
}
