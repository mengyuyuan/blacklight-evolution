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
    static void captureActor(LivingEntity actor,PoseStack stack,Matrix4f[] bones,float partial){
        if(actor==null||actor.isRemoved()||StandalonePlayerRendering.nativeHandPass())return;
        long now=actor.level().getGameTime(),tail=ModAttachments.getData(actor,ModAttachments.MOBILE_CONSUME_START);
        if(ModAttachments.getData(actor,ModAttachments.GRAB_STAGE)!=ConsumeVisual.CONSUME&&(tail<0||now-tail>18))return;
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
    private record BodySurface(Vec3 pelvis,Vec3 axis,double height,double radius) {
        static BodySurface from(Vec3 pelvis,Vec3 upper,double radius){
            Vec3 delta=upper.subtract(pelvis);
            return new BodySurface(pelvis,delta.length()>.02?delta.normalize():new Vec3(0,1,0),Math.max(.02,delta.length()),radius);
        }
        Vec3 center(Vec3 point,double rise){
            double distance=point.subtract(pelvis).dot(axis)+rise;
            return pelvis.add(axis.scale(Math.max(0,Math.min(height,distance))));
        }
        Vec3 radial(Vec3 direction){
            var tangent=direction.subtract(axis.scale(direction.dot(axis)));
            if(tangent.lengthSqr()<.01)tangent=axis.cross(new Vec3(0,0,1));
            if(tangent.lengthSqr()<.01)tangent=axis.cross(new Vec3(1,0,0));
            return tangent.normalize();
        }
        Vec3 rim(Vec3 joint,Vec3 direction,double rise,double clearance){
            return center(joint,rise).add(radial(direction).scale(radius+clearance));
        }
        Vec3 keepOutside(Vec3 point,Vec3 direction,double clearance){
            Vec3 delta=point.subtract(center(point,0));
            double limit=radius+clearance;
            if(delta.lengthSqr()>=limit*limit)return point;
            Vec3 radial=radial(direction);
            double along=delta.dot(radial);
            double step=Math.sqrt(Math.max(0,limit*limit-delta.lengthSqr()+along*along))-along;
            return point.add(radial.scale(step));
        }
    }
    private static Vec3 hermite(Vec3 start,Vec3 end,Vec3 from,Vec3 to,double t){
        double s=t*t,t3=s*t;
        return start.scale(2*t3-3*s+1).add(from.scale(t3-2*s+t)).add(end.scale(3*s-2*t3)).add(to.scale(t3-s));
    }
    private static Vec3[] route(Vec3 root,Vec3 tip,BodySurface actor,BodySurface receiver,Vec3 outward,Track track,double elapsed){
        // The source joints sit inside the flesh. Only their attachment spans enter the body.
        // Bound the free spans to the clothing surface without adding an orbit around either model.
        double padding=Math.min(actor.radius*.23,track.circle()*DevastatorEffectTimeline.WORLD_SCALE*.24);
        double receiverPad=Math.min(receiver.radius*.23,padding);
        double rise=Math.sin(Math.toRadians(track.pitch()))*track.circle()*DevastatorEffectTimeline.WORLD_SCALE*.45;
        Vec3 exit=actor.rim(root,outward,rise,padding),entry=receiver.rim(tip,outward,-rise*.4,receiverPad);
        Vec3 from=entry.subtract(root).scale(.30),to=tip.subtract(exit).scale(.30);
        int segments=track.segments(),bound=segments==5?1:2,entryIndex=segments-bound;
        var path=new Vec3[segments+1];
        for(int i=0;i<=segments;i++){
            if(i<bound)path[i]=hermite(root,exit,exit.subtract(root),from,i/(double)bound);
            else if(i<=entryIndex){
                double t=(i-bound)/(double)(entryIndex-bound);
                Vec3 point=hermite(exit,entry,from,to,t);
                if(track.target()==Target.SHOULDER_R){
                    // FIG 547760 stores speed 10, wavelength 5, amplitude .1 and a .1 s rise.
                    double scale=DevastatorEffectTimeline.WORLD_SCALE;
                    double wave=Math.sin((elapsed*10-t*entry.subtract(exit).length()/scale)*Math.PI*2/5);
                    point=point.add(actor.axis.scale(wave*.1*scale*Math.min(1,elapsed/.1)*Math.sin(Math.PI*t)));
                }
                point=actor.keepOutside(point,outward,padding);
                path[i]=receiver.keepOutside(point,outward,receiverPad);
            }else path[i]=hermite(entry,tip,to,tip.subtract(entry),(i-entryIndex)/(double)bound);
        }
        return path;
    }
    /** Reveal the authored spans by distance along their constrained curve. */
    private static Vec3[] grow(Vec3[] path,double travel,Vec3 camera){
        int segments=path.length-1;
        double[] distance=new double[path.length];
        for(int j=1;j<path.length;j++)distance[j]=distance[j-1]+path[j].subtract(path[j-1]).length();
        Vec3[] result=new Vec3[path.length];
        if(travel>=distance[segments]){
            for(int j=0;j<=segments;j++)result[j]=path[j].subtract(camera);
        }else{
            int next=1;
            for(int j=0;j<=segments;j++){
                double at=distance[j]*travel/Math.max(1e-8,distance[segments]);
                while(next<segments&&distance[next]<at)next++;
                double local=(at-distance[next-1])/Math.max(1e-8,distance[next]-distance[next-1]);
                result[j]=path[next-1].lerp(path[next],local).subtract(camera);
            }
        }
        return result;
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
        if(mob.isRemoved()||owner.isRemoved()||mob.level()!=owner.level())return;
        double age=v.elapsed(mob.level().getGameTime(),partial)/20.0-cue.frame()/30.0;
        if(age<=0)return;
        double remaining=1-ConsumeFracture.phase(mob,partial).returnFlow();
        if(remaining<.015)return;
        Vec3[] roots=actorPoints(owner,partial),tips=receiverPoints(mob,partial);
        if(roots[0].subtract(tips[3]).lengthSqr()>36)return;
        var actor=BodySurface.from(roots[2],roots[0],Math.max(owner.getBbWidth()*.52,roots[2].subtract(roots[1]).length()*2.25));
        // The hip-to-pelvis span contracts with the posed physical skin.
        var receiver=BodySurface.from(tips[0],tips[1],Math.min(mob.getBbWidth()*.63,Math.max(.025,tips[2].subtract(tips[0]).length()*2.25)));
        Vec3 side=roots[2].subtract(roots[1]).normalize();
        if(side.lengthSqr()<.1)side=left(owner.yBodyRot);
        side=actor.radial(side);Vec3 forward=actor.axis.cross(side).normalize();
        for(Track tr:cue.tracks()){
            double elapsed=age-tr.delayFrames()/30.0;
            if(elapsed<=0)continue;
            // Hip_R exits the right flank; the track ending at Hip_L follows the opposite flank.
            double sign=(tr.source()==Source.HIP_R||tr.source()==Source.SPINE_2)?-1:1;
            Vec3 outward=side.scale(sign).add(forward.scale(.55*Math.cos(Math.toRadians(tr.roll())))).normalize();
            Vec3[] path=route(sourceBone(roots,tr.source()),targetBone(tips,tr.target(),mob,partial),actor,receiver,outward,tr,elapsed);
            Vec3[] points=grow(path,elapsed*tr.speed()*DevastatorEffectTimeline.WORLD_SCALE,camera);
            int segments=tr.segments();
            double[] widths=new double[points.length];
            for(int j=0;j<=segments;j++)
                widths[j]=.107*DevastatorEffectTimeline.WORLD_SCALE*tr.scale()*remaining*(1-.45*j/segments)*Math.min(1,elapsed*20);
            ConsumeTendrils.sweep(stack,out,light,points,widths,outward,true);
        }
    }
}
