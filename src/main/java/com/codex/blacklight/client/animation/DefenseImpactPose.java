package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Damped directional recoil on top of the committed pose; never advances/restarts an action. */
public final class DefenseImpactPose {
    private DefenseImpactPose() {}
    private static DefenseImpact impact(Player p){return p.getData(ModAttachments.DEFENSE_IMPACT);}
    private static float[] local(Player p,float partial){
        var s=impact(p);float w=s.spring(p.level().getGameTime()+partial);
        Vec3 forward=Vec3.directionFromRotation(0,BlacklightRenderPose.facing(p,partial));
        Vec3 right=new Vec3(forward.z,0,-forward.x),v=new Vec3(s.x(),s.y(),s.z());
        return new float[]{(float)v.dot(forward)*w,(float)v.dot(right)*w,w,(float)v.dot(right)};
    }
    public static BlacklightPose apply(LivingEntity e,BlacklightPose original,float partial){
        if(!(e instanceof Player p)||!p.isAlive()||BiomassSystem.active(p))return original;
        var s=impact(p);if(Math.abs(s.spring(p.level().getGameTime()+partial))+s.followThrough(p.level().getGameTime()+partial)<.0001)return original;
        if(!UtilityPowerSystem.active(p,s.shield()?PowerForm.SHIELD:PowerForm.ARMOR))return original;
        var pose=original.copy();var v=local(p,partial);
        if(!s.braced()){
            // The projectile loses: the stance, head and leg cycle stay untouched.
            pose.orElseEmpty("Chest").rotation().rotateX(v[0]*.025F).rotateZ(-v[1]*.025F);
            if(s.shield()){
                pose.orElseEmpty("Shoulder_L").rotation().rotateX(v[2]*.055F);
                pose.orElseEmpty("Elbow_L").rotation().rotateX(v[2]*.045F);
            }else{
                pose.orElseEmpty(v[3]>0?"Shoulder_R":"Shoulder_L").rotation().rotateZ(-v[1]*.10F).rotateX(v[0]*.10F);
            }
            return pose;
        }
        float stroke=s.followThrough(p.level().getGameTime()+partial);
        float moving=(float)Math.min(1,p.getDeltaMovement().horizontalDistance()/.22);
        float body=1-.55F*moving;
        float side=s.shield()?-1:v[3]>0?1:-1;
        pose.orElseEmpty("Torso").rotation().rotateX(v[0]*.12F*body).rotateZ(-v[1]*.14F*body);
        pose.orElseEmpty("Chest").rotation().rotateX(v[0]*.20F*body)
            .rotateY((v[1]*.22F-side*stroke*.18F)*body);
        pose.orElseEmpty("Head").rotation().rotateX(-v[0]*.06F*body);
        String arm=s.shield()?"L":side>0?"R":"L";
        pose.orElseEmpty("Shoulder_"+arm).rotation().rotateX(v[2]*.40F-stroke*.18F)
            .rotateY(side*(v[2]*.20F-stroke*.42F));
        pose.orElseEmpty("Elbow_"+arm).rotation().rotateX(v[2]*.78F-stroke*.24F);
        pose.orElseEmpty("Hand_"+arm).rotation().rotateZ(side*stroke*.12F);
        return pose;
    }
    public static org.joml.Matrix4f shield(Player p,float partial){
        var s=impact(p);var m=new org.joml.Matrix4f();if(!s.shield())return m;
        var v=local(p,partial);
        if(!s.braced())return m.translate(0,0,v[2]*.025F).rotateX(v[0]*.05F).rotateY(v[1]*.06F);
        float stroke=s.followThrough(p.level().getGameTime()+partial);
        return m.translate(0,0,v[2]*.10F-stroke*.04F).rotateX(v[0]*.22F).rotateY(v[1]*.25F-stroke*.20F);
    }
}
