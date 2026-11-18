package com.codex.blacklight.combat;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;

/** Paired aerial carry and throw clocks; gravity and projectile collision stay authoritative. */
public final class AerialCarryMotion {
    public static final String THROW_KEY="air_throw";
    private AerialCarryMotion(){}
    public static boolean airborne(LivingEntity owner){
        return !owner.onGround()&&!owner.isInWaterOrBubble()&&!owner.isInLava()
                &&!owner.isFallFlying()&&com.codex.blacklight.registry.ModAttachments.getData(owner,ModAttachments.GLIDE_UNTIL)<owner.level().getGameTime();
    }
    public static int duration(){return definition().durationTicks();}
    public static int holdDuration(){return LocalVisualMotion.find("consume_carry_air_actor").orElseThrow().durationTicks();}
    public static float holdProgress(ConsumeVisual visual,long now,float partial){return progress(LocalVisualMotion.find("consume_carry_air_actor").orElseThrow(),visual,now,partial);}
    private static LocalVisualMotion.Definition definition(){return LocalVisualMotion.find("consume_air_throw_actor").orElseThrow();}
    public static boolean available(){return LocalVisualMotion.find("consume_air_throw_actor").isPresent()
            &&LocalVisualMotion.find("consume_air_throw_victim").isPresent();}
    public static int releaseTick(){
        var clip=definition();int tick=clip.animation().getAsJsonObject("source").get("releaseTick").getAsInt();
        if(tick<2||tick>=clip.durationTicks())throw new IllegalArgumentException("Invalid aerial throw release clock");
        return tick;
    }
    public static float progress(ConsumeVisual visual,long now,float partial){
        return progress(definition(),visual,now,partial);
    }
    private static float progress(LocalVisualMotion.Definition clip,ConsumeVisual visual,long now,float partial){
        var times=clip.animation().getAsJsonArray("animation").get(0).getAsJsonObject().getAsJsonArray("time");
        float end=times.get(times.size()-1).getAsFloat();
        return PairedMotionClock.progress(now-visual.started(),partial,clip.transitionSeconds(),end);
    }
}
