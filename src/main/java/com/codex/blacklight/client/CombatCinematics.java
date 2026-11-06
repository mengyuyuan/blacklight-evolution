package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.config.PassiveHudConfig;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.opengl.GL11;

/**
 * Local hit accents driven by confirmed server impacts: a short held peak, zoom blur,
 * colour split, red vignette and an FOV punch. Never changes input, physics or damage.
 * Respects the client toggle, its strength and vanilla screen/FOV effect scales.
 */
@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class CombatCinematics {
    private static final ResourceLocation EFFECT=ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID,"shaders/post/impact_grade.json");
    private static PostChain chain;
    private static RenderTarget savedDepth;
    private static boolean failed;
    private static int width=-1,height=-1;
    private static double start=-10,peak,heavy,hold,decay,side;
    private static long passes;
    private CombatCinematics(){}

    public static void accept(PhysicalFeedbackPayload payload){
        if(!PassiveHudConfig.CINEMATIC_COMBAT.get())return;
        double base=switch(payload.effect()){
            case CLAW_HIT -> .34;
            case BLADE_CUT,BLADE_RETURN -> .40;
            case HEAVY_LANDING -> .28;
            case SPRINT_COLLISION -> .66;
            case CLAW_HEAVY -> .74;
            case BLADE_HEAVY -> .84;
            case GROUND_SPIKE,DEVASTATING_LANDING -> 1.0;
            default -> 0;
        };
        if(base<=0)return;
        double next=Math.min(1.25,base*Math.min(1.2,payload.intensity()+.25));
        double now=seconds(),current=pulse(now);
        // A light follow-up never cuts a running heavy accent short.
        if(next<current*.85)return;
        boolean big=next>=.6;
        start=now;peak=Math.max(next,current);heavy=big?1:.45;
        boolean finisher=payload.intensity()>=1.3F;
        hold=finisher?.11:big?.07:.022;decay=finisher?.62:big?.46:.24;
        side=payload.effect()==PhysicalFeedbackPayload.Effect.BLADE_RETURN?-1:1;
    }
    public static double pulse(){return pulse(seconds());}
    public static long passes(){return passes;}
    private static double seconds(){return System.nanoTime()*1e-9;}
    private static double pulse(double now){
        double age=now-start;
        if(age<0||peak<=0)return 0;
        if(age<.018)return peak*age/.018;
        if(age<hold)return peak;
        double x=(age-hold)/Math.max(.01,decay);
        if(x>=1)return 0;
        return peak*(1-x)*(1-x);
    }
    private static double scale(Minecraft mc){
        return PassiveHudConfig.CINEMATIC_STRENGTH.get()*mc.options.screenEffectScale().get();
    }
    private static boolean blocked(Minecraft mc){
        return mc.player==null||mc.level==null||!mc.player.isAlive()
                ||com.codex.blacklight.client.camera.HydraCinematicCamera.active()
                ||com.codex.blacklight.client.camera.CameraCompatibility.externalOwnsView();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){
        var mc=Minecraft.getInstance();
        if(!event.usedConfiguredFov()||event.getCamera()!=mc.gameRenderer.getMainCamera()||blocked(mc))return;
        if(!PassiveHudConfig.CINEMATIC_COMBAT.get())return;
        double p=pulse()*heavy*PassiveHudConfig.CINEMATIC_STRENGTH.get()*mc.options.fovEffectScale().get();
        if(p<.002)return;
        event.setFOV(event.getFOV()*(1-.06*Math.min(1.25,p)));
    }

    @SubscribeEvent(priority=EventPriority.LOW)
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||failed)return;
        var mc=Minecraft.getInstance();
        if(mc.screen!=null||blocked(mc)||!PassiveHudConfig.CINEMATIC_COMBAT.get())return;
        float strength=(float)Math.min(1.4,pulse()*scale(mc));
        if(strength<.01F)return;
        var main=mc.getMainRenderTarget();
        // Shader packs may own another output here; never sample or rebind over it.
        if(GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        boolean copied=false,wasBlend=GL11.glIsEnabled(GL11.GL_BLEND),wasDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try{
            if(chain==null){chain=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,EFFECT);width=height=-1;}
            if(width!=main.width||height!=main.height){
                width=main.width;height=main.height;chain.resize(width,height);
                if(savedDepth!=null)savedDepth.destroyBuffers();
                savedDepth=new TextureTarget(width,height,true,Minecraft.ON_OSX);
            }
            mc.renderBuffers().bufferSource().endBatch();
            savedDepth.copyDepthFrom(main);copied=true;
            float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
            chain.setUniform("Pulse",strength);
            chain.setUniform("Heavy",(float)heavy);
            chain.setUniform("Side",(float)side);
            chain.setUniform("Time",(float)(seconds()%4096));
            RenderSystem.disableBlend();RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();
            chain.process(partial);passes++;
        }catch(Exception exception){
            failed=true;BlacklightNeoMod.LOGGER.error("Combat impact grade unavailable; reload resources to retry",exception);
        }finally{
            if(copied&&savedDepth!=null)main.copyDepthFrom(savedDepth);
            if(wasDepth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(wasBlend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            main.bindWrite(false);
            if(failed)close();
        }
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){close();failed=false;start=-10;}
    private static void close(){
        boolean destroyed=chain!=null||savedDepth!=null;
        if(chain!=null)chain.close();chain=null;
        if(savedDepth!=null)savedDepth.destroyBuffers();savedDepth=null;
        width=height=-1;
        if(destroyed)Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
    @EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->
                    Minecraft.getInstance().execute(()->{close();failed=false;}));
        }
    }
}
