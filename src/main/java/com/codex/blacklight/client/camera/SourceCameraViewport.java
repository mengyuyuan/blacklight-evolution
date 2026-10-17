package com.codex.blacklight.client.camera;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ViewportEvent;

@EventBusSubscriber(modid=BlacklightNeoMod.MOD_ID,value=Dist.CLIENT)
public final class SourceCameraViewport {
    private static double vertical=70;
    private static double speedRatio,lastTime=-1;
    private static double transitionFrom,transitionAge=.18;
    private static Object previousPlayer,previousLevel;
    private static boolean previousOwned;
    static void reset(){vertical=70;speedRatio=0;lastTime=-1;transitionAge=.18;previousPlayer=previousLevel=null;previousOwned=false;}
    static void handoff(){if(lastTime>=0){transitionFrom=vertical;transitionAge=0;}}
    private SourceCameraViewport() {}
    static double horizontalHalfFov() {
        var window=Minecraft.getInstance().getWindow();
        double aspect=window.getWidth()/(double)Math.max(1,window.getHeight());
        return Math.toDegrees(Math.atan(Math.tan(Math.toRadians(vertical)*.5)*aspect))*.8;
    }
    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if(HydraCinematicCamera.active())return;
        var mc=Minecraft.getInstance();
        if(event.getCamera()!=mc.gameRenderer.getMainCamera())return;
        var p=mc.player;
        if(p==null||mc.level==null){reset();return;}
        if(previousPlayer!=p||previousLevel!=mc.level){reset();previousPlayer=p;previousLevel=mc.level;vertical=event.getFOV();}
        boolean owned=PrototypeChaseCamera.ownsView();
        if(owned!=previousOwned){handoff();previousOwned=owned;}
        double now=p.level().getGameTime()+event.getPartialTick();
        double dt=lastTime<0?0:net.minecraft.util.Mth.clamp((now-lastTime)/20,0,.25);
        // 0x1033a4e0 -> 0x10338fa0: reciprocal zoom scales tan(FOV/2), not the FOV angle.
        if(owned) {
            double ratio=com.codex.blacklight.movement.PrototypeLocomotion.cinematicSpeedRatio(p.getDeltaMovement().horizontalDistance(),
                    com.codex.blacklight.movement.MovementTuningSettings.sprintMaxSpeed()
                    *com.codex.blacklight.power.MovementUpgrade.sprintMultiplier(com.codex.blacklight.registry.ModAttachments.getData(p, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE).getMovementUpgradeMask()));
            // Wing flight supplies its own bounded velocity/acceleration lens response.
            if(com.codex.blacklight.movement.WingFlightSystem.flying(p))ratio=0;
            if(lastTime<0||now<lastTime-.1)speedRatio=ratio;
            else speedRatio+=(ratio-speedRatio)*(1-Math.exp(-dt/.12));
            double configured=event.getFOV()*(1+com.codex.blacklight.movement.MovementTuningSettings.fovBoost()*speedRatio*mc.options.fovEffectScale().get());
            event.setFOV(Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(Math.min(150,configured))*.5)/PrototypeChaseCamera.zoom())));
            var film=PrototypeChaseCamera.filmFrame();
            if(film!=null)event.setFOV(Math.toDegrees(2*Math.atan(net.minecraft.util.Mth.lerp(film.weight(),
                    Math.tan(Math.toRadians(event.getFOV())*.5),Math.tan(Math.toRadians(film.fov())*.5)))));
        }else speedRatio=0;
        if(transitionAge<.18) {
            double t=net.minecraft.util.Mth.clamp(transitionAge/.18,0,1),weight=t*t*(3-2*t);
            event.setFOV(Math.toDegrees(2*Math.atan(net.minecraft.util.Mth.lerp(weight,
                    Math.tan(Math.toRadians(transitionFrom)*.5),Math.tan(Math.toRadians(event.getFOV())*.5)))));
            transitionAge+=dt;
        }
        lastTime=now;
        vertical=event.getFOV();
    }
}
