package com.codex.blacklight.client.camera;

import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.client.renderer.BulletFlightVisualRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Bounded bullet-flight FOV presentation; no perspective or mouse override. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientDevastatorCamera {
    private static Object previousPlayer,previousLevel;
    private static long session=Long.MIN_VALUE;
    private static double gain,lastTime=-1;
    private static Statistics statistics=new Statistics(0,0,0,0,0,0,false);
    public record Statistics(double inputFov,double outputFov,double tangentGain,double response,double downSpeed,double fallHeight,boolean flying) { }
    public static Statistics statistics(){return statistics;}
    private ClientDevastatorCamera() { }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){
        if(HydraCinematicCamera.active())return;
        var mc=Minecraft.getInstance();var player=mc.player;
        // GameRenderer requests false for native renderItemInHand's projection.
        // Flight optics affect the world only, leaving both hand anchors intact.
        if(!event.usedConfiguredFov()||event.getCamera()!=mc.gameRenderer.getMainCamera())return;
        if(player==null||mc.level==null||!player.isAlive()){
            gain=0;lastTime=-1;previousPlayer=previousLevel=null;
            statistics=new Statistics(0,0,0,0,0,0,false);return;
        }
        var visual=player.getData(ModAttachments.DEVASTATOR_VISUAL);
        double now=player.level().getGameTime()+event.getPartialTick();
        if(previousPlayer!=player||previousLevel!=mc.level||session!=visual.session()||now<lastTime-.1){
            gain=0;lastTime=now;previousPlayer=player;previousLevel=mc.level;session=visual.session();
        }
        var flight=BulletFlightVisualRenderer.sample(player,(float)event.getPartialTick());
        double target=flight==null?0:flight.optics().tangentGain();
        double dt=Math.clamp((now-lastTime)/20,0,.25);
        // Return over the source cycle's .166667s OutTime; no camera position,
        // yaw, pitch, roll, perspective or input is owned by this presentation.
        gain+=(target-gain)*(1-Math.exp(-dt/(flight==null?1D/6:.08)));
        if(gain<1e-6)gain=0;
        double original=event.getFOV(),output=DevastatorEffectTimeline.flightFov(original,gain,mc.options.fovEffectScale().get());
        event.setFOV(output);lastTime=now;
        statistics=new Statistics(original,output,gain,flight==null?0:flight.optics().strength(),
                flight==null?0:flight.speed(),flight==null?0:flight.height(),flight!=null);
    }
}
