package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.ConsumeBridgeTracks.*;
import com.codex.blacklight.client.ClientCarryPlacement;
import com.codex.blacklight.client.animation.ConsumeVictimPose;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Tracks from the receiver FIG root to its authored grab-slot endpoint, on the current posed bodies. */
final class ChestConsumeTendrils {
    private record ActorPose(WeakReference<Level> level,double clock,Vec3[] points) {}
    private static final Map<LivingEntity,ActorPose> ACTORS=new WeakHashMap<>();

    private ChestConsumeTendrils(){}
    static boolean matches(ConsumeVisual v){
        return (v.stage()==ConsumeVisual.CONSUME||v.stage()==ConsumeVisual.MOBILE_CONSUME)
                &&v.form()==PowerForm.NONE.ordinal()&&ConsumeSequence.key(PowerForm.NONE,v.variant()).equals("normal");
    }
    static Cue bridges(ConsumeVisual visual){
        if(visual.stage()!=ConsumeVisual.CONSUME&&visual.stage()!=ConsumeVisual.MOBILE_CONSUME)return null;
        var forms=PowerForm.values();
        if(visual.form()<0||visual.form()>=forms.length)return null;
        var form=forms[visual.form()];
        return switch(form){
            case NONE,CLAWS,BLADE,HAMMERFISTS,MUSCLE_MASS,WHIPFIST -> ConsumeBridgeTracks.forClip(ConsumeSequence.key(form,visual.variant()));
            default -> null;
        };
    }
    // Receiver profiles are bounded to 20 seconds; keep posed sockets valid to the last moving tick.
    static void captureActor(LivingEntity actor,PoseStack stack,Matrix4f[] bones,float partial){
        if(actor==null||actor.isRemoved()||StandalonePlayerRendering.nativeHandPass())return;
        long now=actor.level().getGameTime(),tail=ModAttachments.getData(actor,ModAttachments.MOBILE_CONSUME_START);
        if(ModAttachments.getData(actor,ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME&&(tail<0||now-tail>400))return;
        var rig=PlayerMotionController.skeleton();
        Vec3 chest=joint(stack,bones,rig.chest.getId()),hip=joint(stack,bones,rig.thighR.getId());
        Vec3 pelvis=hip.lerp(joint(stack,bones,rig.thighL.getId()),.5);
        ACTORS.put(actor,new ActorPose(new WeakReference<>(actor.level()),now+partial,new Vec3[]{chest,hip,pelvis,chest.lerp(pelvis,.35)}));
    }
    private static Vec3 joint(PoseStack stack,Matrix4f[] bones,int index){
        return BlacklightBoneRendering.worldPoint(stack,bones[index].getTranslation(new Vector3f()));
    }
    static Vec3[] actorPoints(LivingEntity owner,float partial){
        var pose=ACTORS.get(owner);
        if(pose!=null&&pose.level().get()==owner.level()&&Math.abs(owner.level().getGameTime()+partial-pose.clock())<1.2)return pose.points();
        // The camera can suppress the local player model in first person.
        Vec3 feet=owner.getPosition(partial),side=left(owner.yBodyRot);
        double h=owner.getBbHeight(),w=owner.getBbWidth();
        return new Vec3[]{feet.add(0,h*.68,0),feet.add(0,h*.48,0).subtract(side.scale(w*.24)),
                feet.add(0,h*.48,0),feet.add(0,h*.60,0)};
    }
    private static Vec3 left(float yaw){return Vec3.directionFromRotation(0,yaw-90);}
    static Vec3[] receiverPoints(LivingEntity mob,float partial){
        Vec3[] physical=AdaptiveRagdoll.chestConsumeJoints(mob,partial);
        if(physical!=null)return physical;
        Vec3 base=mob.getPosition(partial).add(ClientCarryPlacement.consumeOffset(mob,partial));
        double h=mob.getBbHeight(),w=mob.getBbWidth();
        Vec3 pelvis=new Vec3(0,h*.49,0);
        Vec3[] points={pelvis,new Vec3(0,h*.80,0),pelvis.add(left(mob.yBodyRot).scale(w*.24)),new Vec3(0,h*.64,0)};
        for(int i=0;i<points.length;i++)points[i]=base.add(ConsumeVictimPose.effectPoint(mob,points[i],partial));
        return points;
    }
    private static Vec3[] route(Vec3 root,Vec3 tip,Vec3 normal,Track track,double elapsed){
        double scale=DevastatorEffectTimeline.WORLD_SCALE;
        Vec3[] path=SourceTendrilPath.sample(root,tip,track.circle()*scale,track.roll(),track.pitch(),track.segments(),elapsed*track.speed()*scale);
        if(track.target()==Target.SHOULDER_R){
            // Branch 98 alone adds a wave. No chest-punch track requests a corkscrew or wave.
            double distance=root.distanceTo(tip);
            for(int i=1;i<path.length-1;i++){
                if(path[i].distanceToSqr(root)<1.e-12)continue;
                double t=i/(double)track.segments();
                double wave=Math.sin((elapsed*10-t*distance/scale)*2*Math.PI/5);
                path[i]=path[i].add(normal.scale(.1*scale*wave*Math.min(1,elapsed/.1)*Math.sin(Math.PI*t)));
            }
        }
        return path;
    }
    private static Vec3 sourceBone(Vec3[] actor,Source bone){
        return bone==Source.HIP_L?actor[2].add(actor[2].subtract(actor[1])):actor[bone.ordinal()];
    }
    private static Vec3 targetBone(Vec3[] joints,Target bone,LivingEntity mob,float partial){
        if(bone.ordinal()<4)return joints[bone.ordinal()];
        Vec3 pelvis=joints[0],neck=joints[1],left=joints[2].subtract(pelvis),up=neck.subtract(pelvis);
        if(bone==Target.HIP_R)return pelvis.subtract(left);
        boolean knee=bone==Target.KNEE_R,armLeft=bone==Target.SHOULDER_L;
        Vec3 estimate=knee?pelvis.subtract(up.scale(.86)).subtract(left):
                neck.subtract(up.scale(.12)).add(left.scale(armLeft?2:-2));
        return AdaptiveRagdoll.consumeLimbJoint(mob,partial,knee?8:armLeft?4:2,estimate,knee);
    }
    static void draw(PoseStack stack,VertexConsumer out,int light,Vec3 camera,LivingEntity mob,LivingEntity owner,ConsumeVisual v,Cue cue,float partial){
        if(!mob.isAlive()||!owner.isAlive()||mob.isInvisible()||owner.isInvisible()||mob.level()!=owner.level())return;
        double age=v.elapsed(mob.level().getGameTime(),partial)/20.0-cue.frame()/30.0;
        if(age<=0)return;
        double remaining=1-ConsumeFracture.phase(mob,partial).returnFlow();
        if(remaining<.015)return;
        Vec3[] roots=actorPoints(owner,partial),tips=receiverPoints(mob,partial);
        if(roots[0].subtract(tips[3]).lengthSqr()>36)return;
        Vec3 side=roots[2].subtract(roots[1]).normalize();
        if(side.lengthSqr()<.1)side=left(owner.yBodyRot);
        for(Track tr:cue.tracks()){
            double elapsed=age-tr.delayFrames()/30.0;
            if(elapsed<=0)continue;
            Vec3 root=sourceBone(roots,tr.source()),tip=targetBone(tips,tr.target(),mob,partial);
            Vec3[] points=route(root,tip,side,tr,elapsed);
            for(int j=0;j<points.length;j++)points[j]=points[j].subtract(camera);
            int segments=tr.segments();
            double[] widths=new double[points.length];
            for(int j=0;j<=segments;j++)
                widths[j]=.107*DevastatorEffectTimeline.WORLD_SCALE*tr.scale()*remaining;
            ConsumeTendrilSegmentMesh.draw(stack,out,light,points,widths,side,0xffffffff);
        }
    }
}
