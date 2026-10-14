package com.codex.blacklight.client.camera;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.client.renderer.BulletFlightVisualRenderer;
import com.codex.blacklight.client.renderer.DevastatorEffectRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector4f;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/** Scene-only radial lens response to source explosion/retract events.
 * The scene is copied through a PostChain swap target; its depth is restored.
 * This class never changes perspective, camera rotation, input or physics. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientDevastatorPostEffect {
    private static PostChain effect;
    private static RenderTarget savedDepth;
    private static Object level;
    private static int width,height;
    private static boolean failed;
    private static long passes;
    private static float lastBurst,lastRetract,lastFlight;
    private static float lastFlowX,lastFlowY,lastFlightAxis;
    private ClientDevastatorPostEffect() { }
    public record Statistics(long passes,float burst,float retract,float flight,boolean failed,
                             float flowX,float flowY,float flightAxis) { }
    public static Statistics statistics(){return new Statistics(passes,lastBurst,lastRetract,lastFlight,failed,lastFlowX,lastFlowY,lastFlightAxis);}
    private static void close(){
        boolean destroyed=effect!=null||savedDepth!=null;
        if(effect!=null){effect.close();effect=null;}
        if(savedDepth!=null){savedDepth.destroyBuffers();savedDepth=null;}
        lastBurst=lastRetract=lastFlight=0;
        lastFlowX=lastFlowY=lastFlightAxis=0;
        // Target destruction unbinds its FBO. Native hands are drawn after
        // AFTER_LEVEL, before vanilla binds the main target again for the HUD.
        if(destroyed)Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }
    @EventBusSubscriber(modid="blacklight",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->
                Minecraft.getInstance().execute(()->{close();failed=false;}));
    }
    }
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var current=Minecraft.getInstance().level;
        if(current!=level){close();failed=false;level=current;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||!player.isAlive()){close();return;}
        var visual=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DEVASTATOR_VISUAL);
        if(visual.kind()<0||visual.phase()==0||visual.released()<0){close();return;}
        float partial=event.getPartialTick();
        var lens=DevastatorEffectTimeline.lens(visual.kind(),visual.phase(),visual.age(player,partial),
                visual.effectStart(),visual.fallHeight(),visual.downSpeed());
        var flight=BulletFlightVisualRenderer.sample(player,partial);
        if(visual.kind()==3&&visual.phase()==2)lens=new DevastatorEffectTimeline.Lens(0,0,
                flight==null?0:flight.optics().strength()*mc.options.screenEffectScale().get());
        if(!lens.active()){close();return;}
        if(failed)return;
        boolean copied=false;
        boolean wasBlend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            mc.renderBuffers().bufferSource().endBatch();
            var main=mc.getMainRenderTarget();
            if(effect==null){
                effect=new PostChain(mc.getTextureManager(),mc.getResourceManager(),main,
                        new ResourceLocation("blacklight","shaders/post/devastator_lens.json"));
                width=height=-1;
            }
            if(width!=main.width||height!=main.height){
                width=main.width;height=main.height;effect.resize(width,height);
                if(savedDepth!=null)savedDepth.destroyBuffers();
                savedDepth=new TextureTarget(width,height,true,Minecraft.ON_OSX);
            }
            float centreX=.5F,centreY=.5F;
            if(!mc.options.getCameraType().isFirstPerson()){
                var camera=event.getCamera().getPosition();
                var attached=DevastatorEffectRenderer.capturedChest(player.getId());
                var origin=(attached==null?player.getPosition(partial).add(0,player.getBbHeight()*.58,0):attached).subtract(camera);
                var projected=new Vector4f((float)origin.x,(float)origin.y,(float)origin.z,1);
                projected.mul(event.getPoseStack().last().pose()).mul(event.getProjectionMatrix());
                if(projected.w>.001F){
                    centreX=com.codex.blacklight.util.MathCompat.clamp(projected.x/projected.w*.5F+.5F,.05F,.95F);
                    centreY=com.codex.blacklight.util.MathCompat.clamp(projected.y/projected.w*.5F+.5F,.05F,.95F);
                }
            }
            savedDepth.copyDepthFrom(main);copied=true;
            lastBurst=(float)lens.burst();lastRetract=(float)lens.retract();lastFlight=(float)lens.flight();
            lastFlowX=lastFlowY=0;lastFlightAxis=1;
            if(flight!=null){
                var flow=flight.airflow();
                var viewed=new Vector4f((float)flow.x,(float)flow.y,(float)flow.z,0).mul(event.getPoseStack().last().pose());
                float aspect=width/(float)Math.max(1,height);
                lastFlowX=viewed.x/aspect;lastFlowY=viewed.y;lastFlightAxis=Math.abs(viewed.z);
            }
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"Burst",lastBurst);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"Retract",lastRetract);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"Flight",lastFlight);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"CentreX",centreX);com.codex.blacklight.client.ClientPostUniforms.set(effect,"CentreY",centreY);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"FlowX",lastFlowX);com.codex.blacklight.client.ClientPostUniforms.set(effect,"FlowY",lastFlowY);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"FlightAxis",lastFlightAxis);
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"Rush",flight==null?0:(float)DevastatorEffectTimeline.flightRush(flight.speed()));
            com.codex.blacklight.client.ClientPostUniforms.set(effect,"FlightTime",flight==null?0:(float)(flight.seconds()%4096));
            effect.process(partial);passes++;
        }catch(Exception exception){
            failed=true;BlacklightNeoMod.LOGGER.error("Devastator scene lens unavailable; reload resources to retry",exception);
        }finally{
            if(copied&&savedDepth!=null)mc.getMainRenderTarget().copyDepthFrom(savedDepth);
            mc.getMainRenderTarget().bindWrite(false);if(failed)close();
            if(wasDepth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(wasBlend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
        }
    }
}
