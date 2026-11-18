package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import software.bernie.geckolib.model.GeoModel;

/** One receiver pose for the native wing rig and its grip surface queries.
 * A scaled human neck coordinate is not a socket on this horizontal body. */
public final class FlyerCarryPose {
    private FlyerCarryPose() {}
    public static float[][] sample(EncounterMob mob,float partial) {
        var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
        var pose=FlyerReceiverPose.sample(mob,GrappleMotion.sceneKey(mob,visual),GrappleMotion.receiverProgress(mob,visual,partial),partial);
        // The paired state may arrive before the entity's yaw interpolation.
        // Match the carrier immediately in both skin and grip queries, instead
        // of turning the whole horizontal receiver a tick after HOLD begins.
        if(GrappleMotion.active(visual)){
            float bodyYaw=net.minecraft.util.Mth.rotLerp(partial,mob.yBodyRotO,mob.yBodyRot);
            pose[0][1]+=(float)java.lang.Math.toRadians(net.minecraft.util.Mth.wrapDegrees(bodyYaw-GrappleMotion.facing(mob,visual,partial)));
        }
        return pose;
    }
    public static void apply(GeoModel<?> model,EncounterMob mob,float partial) {
        var pose=sample(mob,partial);var names=FlyerMotion.table().bones;
        for(int i=0;i<names.length;i++){var b=model.getAnimationProcessor().getBone(names[i]);if(b==null)continue;var p=pose[i];b.updateRotation(p[0],p[1],p[2]);b.setPosX(-p[3]);b.setPosY(p[4]);b.setPosZ(p[5]);}
    }
    /** Closest point on the posed head or thorax, never an offset from the entity AABB. */
    public static Vec3 contact(EncounterMob mob,Vec3 desired,float partial,boolean blade) {
        var origin=mob.getPosition(partial).add(com.codex.blacklight.client.ClientCarryPlacement.consumeOffset(mob,partial));
        var body=ConsumeVictimPose.bodyTransform(mob,partial);var inverse=new Matrix4f(body).invert();
        var local=inverse.transformPosition(desired.subtract(origin).toVector3f());var point=new Vec3(local.x,local.y,local.z);
        float yaw=net.minecraft.util.Mth.rotLerp(partial,mob.yBodyRotO,mob.yBodyRot);
        Vec3 closest=null;double distance=Double.POSITIVE_INFINITY;
        for(var surface:FlyerContact.body(sample(mob,partial),Vec3.ZERO,yaw))if(surface.bone().equals(blade?"torso":"head")) {
            var candidate=surface.box().nearest(point);double d=candidate.distanceToSqr(point);
            if(d<distance){closest=candidate;distance=d;}
        }
        if(closest==null)return desired;
        var world=body.transformPosition(closest.toVector3f());return origin.add(world.x,world.y,world.z);
    }
}
