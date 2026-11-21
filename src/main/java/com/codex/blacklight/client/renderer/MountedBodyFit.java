package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.combat.ConsumeVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Adapts the upper-body reach of a confirmed mount to its native animated surface. */
public final class MountedBodyFit {
    private static final class Fit {
        final long scene;
        final int victim;
        final double hit;
        Vec3 requested=Vec3.ZERO,displayed=Vec3.ZERO,sampled=Vec3.ZERO;
        double clock=Double.NEGATIVE_INFINITY;
        Matrix4f inverseFrame;
        Fit(long scene,int victim,double hit){this.scene=scene;this.victim=victim;this.hit=hit;}
    }
    private static final Map<LivingEntity,Fit> FITS=new WeakHashMap<>();
    private MountedBodyFit(){}
    static void capture(LivingEntity actor,PoseStack stack){
        var fit=FITS.get(actor);
        if(fit!=null){fit.inverseFrame=new Matrix4f(stack.last().pose()).invert();fit.sampled=fit.displayed;}
    }
    static void align(LivingEntity actor,LivingEntity victim,ConsumeVisual scene,double hit,Vec3 surface,Vec3 weapon,double now){
        var fit=FITS.get(actor);
        if(fit==null||fit.scene!=scene.started()||fit.victim!=victim.getId()){
            fit=new Fit(scene.started(),victim.getId(),hit);
            FITS.put(actor,fit);
        }
        if(fit.clock==now)return;
        fit.clock=now;
        Vec3 correction=surface.subtract(weapon);
        if(correction.lengthSqr()<.017*.017)return;
        // Measured on the drawn model, so remove the previous frame's residual error rather than accumulating gait.
        Vec3 wanted=fit.sampled.add(correction.scale(1.07));
        double length=wanted.length();
        if(length>.63)wanted=wanted.scale(.63/length);
        fit.requested=wanted;
    }
    public static void apply(LivingEntity actor,LivingEntity victim,ConsumeVisual visual,BlacklightPose pose,float partial,float yaw){
        var f=FITS.get(actor);
        if(f==null||f.scene!=visual.started()||f.victim!=victim.getId())return;
        double age=visual.elapsed(actor.level().getGameTime(),partial)/20.-f.hit;
        if(age<-.30||age>.68)return;
        double release=Math.max(0,Math.min(1,(.65-age)/.30));
        // Keep pelvis and feet attached. The thorax and both animated arms lean into the measured contact.
        Vec3 world=f.requested.scale(release*release*(3-2*release));
        f.displayed=world;
        Matrix4f inverse=f.inverseFrame==null?new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).scale(.9375F).invert():f.inverseFrame;
        Vector3f model=inverse.transformDirection(world.toVector3f());
        var rig=BlacklightSkeleton.get();
        var bone=rig.searchJointByName("Torso");
        if(bone==null)return;
        var joint=pose.orElseEmpty("Torso");
        var parent=new Matrix4f(rig.getBoundTransformFor(pose,bone)).mul(joint.toMatrix().invert()).invert();
        joint.translation().add(parent.transformDirection(model));
    }
}
