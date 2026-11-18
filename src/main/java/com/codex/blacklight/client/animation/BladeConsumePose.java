package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.WeaponCutContact;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;

/** Fit the authored slicing arm to the receiver's current tissue. The visible
 * sharp edge still has to intersect the skin before a section is authorized. */
final class BladeConsumePose {
    private BladeConsumePose() {}

    static void apply(LivingEntity actor,LivingEntity victim,BlacklightSkeleton rig,
                      BlacklightPose pose,ConsumeVisual visual,float partial) {
        float split=ConsumeSequence.splitSeconds(PowerForm.BLADE,false,visual.variant());
        float seconds=(actor.level().getGameTime()-visual.started()+partial)/20F;
        float weight=weight(seconds,split);
        if(weight<=0)return;
        Vec3 contact=RagdollDrive.contact(victim,32,partial).subtract(actor.getPosition(partial));
        if(!Double.isFinite(contact.lengthSqr())||contact.lengthSqr()>16)return;
        Vec3 forward=Vec3.directionFromRotation(0,visual.facing()),left=new Vec3(forward.z,0,-forward.x);
        float scale=.9375F*actor.getScale();
        var desired=new Vector3f((float)(-contact.dot(left)/scale),(float)(contact.y/scale),(float)(-contact.dot(forward)/scale));
        var fitted=fit(rig,pose,desired);
        var blended=BlacklightPose.interpolatePose(pose,fitted,weight);
        for(var name:new String[]{"Arm_R","Hand_R"})pose.getJointTransformData().put(name,blended.orElseEmpty(name).copy());
    }

    static float weight(float seconds,float split) {
        if(!Float.isFinite(split))return 0;
        return RagdollDrive.smooth((seconds-(split-.30F))/.20F)
                *(1-RagdollDrive.smooth((seconds-(split+.03F))/.18F));
    }

    /** Candidates lie on the actual model edge, including reachable proximal
     * points for small receivers. No arm scaling or wrist deformation is used. */
    static BlacklightPose fit(BlacklightSkeleton rig,BlacklightPose original,Vector3f target) {
        var hand=rig.getBoundTransformFor(original,rig.handR);
        var candidates=new ArrayList<Vector3f>();
        for(var line:WeaponCutContact.sharpProfiles(PowerForm.BLADE,"Hand_R").values()){
            for(int i=1;i<line.size();i++){
                var a=line.get(i-1).toVector3f();var b=line.get(i).toVector3f();
                var worldA=hand.transformPosition(new Vector3f(a));var worldB=hand.transformPosition(new Vector3f(b));
                var ab=worldB.sub(worldA,new Vector3f());float length=ab.lengthSquared();if(length<1e-10F)continue;
                float t=net.minecraft.util.Mth.clamp(target.sub(worldA,new Vector3f()).dot(ab)/length,0,1);
                candidates.add(new Vector3f(a).lerp(b,t));
            }
            if(!line.isEmpty()){candidates.add(line.get(0).toVector3f());candidates.add(line.get(line.size()-1).toVector3f());}
        }
        candidates.sort(Comparator.comparingDouble(p->hand.transformPosition(new Vector3f(p)).distanceSquared(target)));
        BlacklightPose best=original.copy();float bestScore=Float.POSITIVE_INFINITY;
        var handToWrist=new org.joml.Matrix4f(rig.getBoundTransformFor(original,rig.searchJointByName("Tool_R"))).invert().mul(hand);
        for(var point:candidates){
            var trial=original.copy();
            // Exact two-bone reach avoids the local minimum encountered by CCD
            // in the authored slicing pose. The wrist and both lengths stay fixed.
            BlacklightBoneIk.reachTwoBone(rig,trial,"R",target,handToWrist.transformPosition(new Vector3f(point)));
            float gap=rig.getBoundTransformFor(trial,rig.handR).transformPosition(new Vector3f(point)).distanceSquared(target);
            float turn=0;for(var name:new String[]{"Arm_R","Hand_R"})turn+=1-Math.abs(original.orElseEmpty(name).rotation().dot(trial.orElseEmpty(name).rotation()));
            float score=gap+.0001F*turn;
            if(score<bestScore){bestScore=score;best=trial;}
        }
        return best;
    }
}
