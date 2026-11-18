package com.codex.blacklight.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared keyed scene for a non-consuming grab attack. All times include the 1-tick blend. */
public final class HeldSlamMotion {
    public static final String KEY="leg_slam";
    // FIG: windup frames 0..30, hold 30..40, release; ground hit +.166667 s.
    public static final int IMPACT_TICK=31;
    private HeldSlamMotion(){}
    public static int duration(){return LocalVisualMotion.find("consume_leg_slam_actor").map(LocalVisualMotion.Definition::durationTicks).orElse(50);}
    public static boolean available(){return LocalVisualMotion.find("consume_leg_slam_actor").isPresent()&&LocalVisualMotion.find("consume_leg_slam_victim").isPresent();}
    public static float progress(ConsumeVisual visual,long now,float partial){return net.minecraft.util.Mth.clamp((now-visual.started()+partial-1)/Math.max(1F,visual.duration()-1F),0,1);}
    public static Vec3 actor(Vec3 origin,float yaw,float t){return ConsumePlacementTrack.sceneActorAnchor(origin,yaw,KEY,t);}
    public static Vec3 victim(Vec3 origin,float yaw,LivingEntity victim,float t){return ConsumePlacementTrack.sceneAnchor(origin,yaw,victim,KEY,t).orElse(victim.position());}
}
