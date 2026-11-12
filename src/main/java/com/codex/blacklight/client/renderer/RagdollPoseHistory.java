package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import java.util.ArrayList;
import java.util.List;

/** Bounded rigid poses on the same fractional game clock as server blade samples. */
final class RagdollPoseHistory {
    record Pose(double time,Vec3 position,Quaternionf rotation,float scale) {
        Pose {rotation=new Quaternionf(rotation);}
        Vec3 toBind(Vec3 world,Vec3 center){
            var relative=new Quaternionf(rotation).conjugate().transform(world.subtract(position).toVector3f());
            return center.add(relative.x/scale,relative.y/scale,relative.z/scale);
        }
        Pose shifted(Vec3 delta){
            var offset=rotation.transform(delta.scale(scale).toVector3f());
            return new Pose(time,position.add(offset.x,offset.y,offset.z),rotation,scale);
        }
    }
    private final List<Pose> poses=new ArrayList<>();
    void record(double time,Vec3 position,Quaternionf rotation,float scale){
        if(!poses.isEmpty()&&time<poses.getLast().time)throw new IllegalArgumentException("Pose clock moved backwards");
        var pose=new Pose(time,position,rotation,scale);
        if(!poses.isEmpty()&&time==poses.getLast().time)poses.set(poses.size()-1,pose);else poses.add(pose);
        while(poses.size()>96)poses.removeFirst();
    }
    Pose sample(double time){
        if(poses.isEmpty())throw new IllegalStateException("Missing captured pose");
        if(time<=poses.getFirst().time)return poses.getFirst();
        for(int i=1;i<poses.size();i++)if(time<=poses.get(i).time){
            var a=poses.get(i-1);var b=poses.get(i);float alpha=(float)((time-a.time)/(b.time-a.time));
            return new Pose(time,a.position.lerp(b.position,alpha),new Quaternionf(a.rotation).slerp(b.rotation,alpha),a.scale+(b.scale-a.scale)*alpha);
        }
        // Never invent future motion when the server is ahead of the rendered client.
        return poses.getLast();
    }
    void inherit(RagdollPoseHistory source,Vec3 centerOffset){
        poses.clear();for(var pose:source.poses)poses.add(pose.shifted(centerOffset));
    }
    List<BladeSpan> localPath(List<BladeSpan> path,Vec3 center){
        return path.stream().map(span->{
            var pose=sample(span.time());
            return new BladeSpan(span.points().stream().map(p->pose.toBind(span.worldPoint(p),center)).toList(),span.time(),Vec3.ZERO,180);
        }).toList();
    }
}
