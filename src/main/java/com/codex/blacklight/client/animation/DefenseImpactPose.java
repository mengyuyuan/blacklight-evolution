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
    private static DefenseImpact impact(Player p){return ModAttachments.getData(p,ModAttachments.DEFENSE_IMPACT);}
    private static float[] local(Player p,float partial){
        var s=impact(p);float w=s.spring(p.level().getGameTime()+partial);
        Vec3 forward=Vec3.directionFromRotation(0,BlacklightRenderPose.facing(p,partial));
        Vec3 right=new Vec3(forward.z,0,-forward.x),v=new Vec3(s.x(),s.y(),s.z());
        return new float[]{(float)v.dot(forward)*w,(float)v.dot(right)*w,w};
    }
    public static BlacklightPose apply(LivingEntity e,BlacklightPose original,float partial){
        if(!(e instanceof Player p)||!p.isAlive()||BiomassSystem.active(p))return original;
        var s=impact(p);if(Math.abs(s.spring(p.level().getGameTime()+partial))<.0001)return original;
        if(!UtilityPowerSystem.active(p,s.shield()?PowerForm.SHIELD:PowerForm.ARMOR))return original;
        var pose=original.copy();var v=local(p,partial);
        // Brace through the arm while moving; keep the pelvis and leg cycle readable.
        float moving=(float)Math.min(1,p.getDeltaMovement().horizontalDistance()/.22);
        float body=1-.55F*moving;
        pose.orElseEmpty("Torso").rotation().rotateX(v[0]*.16F*body).rotateZ(-v[1]*.18F*body);
        pose.orElseEmpty("Chest").rotation().rotateX(v[0]*.22F*body).rotateY(v[1]*.20F*body);
        pose.orElseEmpty("Head").rotation().rotateX(-v[0]*.07F*body);
        if(s.shield()){
            pose.orElseEmpty("Shoulder_L").rotation().rotateY(v[1]*.30F).rotateX(v[2]*.25F);
            pose.orElseEmpty("Elbow_L").rotation().rotateX(v[2]*.55F);
        }else{
            pose.orElseEmpty(v[1]>0?"Shoulder_R":"Shoulder_L").rotation().rotateZ(-v[1]*.26F).rotateX(v[0]*.16F);
        }
        return pose;
    }
    public static org.joml.Matrix4f shield(Player p,float partial){
        var s=impact(p);var m=new org.joml.Matrix4f();if(!s.shield())return m;
        var v=local(p,partial);
        return m.translate(0,0,v[2]*.14F).rotateX(v[0]*.30F).rotateY(v[1]*.35F);
    }
}
