package com.codex.blacklight.client.camera;

import com.codex.blacklight.combat.DevastatorEffectTimeline;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Bounded bullet-flight FOV presentation; no perspective or mouse override. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientDevastatorCamera {
    private ClientDevastatorCamera() { }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null||!player.isAlive()||event.getCamera()!=mc.gameRenderer.getMainCamera())return;
        var visual=player.getData(ModAttachments.DEVASTATOR_VISUAL);
        if(visual.kind()!=3||visual.phase()!=2)return;
        double response=DevastatorEffectTimeline.flightStrength(visual.fallHeight(),visual.downSpeed());
        double original=Math.clamp(event.getFOV(),1,150);
        // Source flight radial blur/zoom are real. This tan(FOV/2) response is
        // MC presentation, preserving the user's FOV and free camera controls.
        double tangent=Math.tan(Math.toRadians(original)*.5)*(1+response*.16*mc.options.fovEffectScale().get());
        event.setFOV(Math.min(155,Math.toDegrees(2*Math.atan(tangent))));
    }
}
