package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** P1 coming_out Hit geometry. The authored Neck_1 is absent from the bound
 * mother_core_skeleton, so BOTH tracks resolve to the actor root. The native
 * shapecast samples spheres; replacing it with an analytic capsule changes hits.
 * The source candidate-derived fraction bracket is not yet fully recovered.
 * The fixed i/N grid below is an explicit MC sampling adapter, not that solver. */
public final class GreeneEmergenceShape {
    public record Sample(Vec3 center,double radius){}
    private GreeneEmergenceShape(){}

    public static List<Sample> samples(GreeneEmergenceClock.Track track,Vec3 previous,Vec3 current,float yaw,double scale){
        if(previous==null||current==null||!Double.isFinite(previous.lengthSqr())||!Double.isFinite(current.lengthSqr())
                ||!Float.isFinite(yaw)||!Double.isFinite(scale)||scale<=0)return List.of();
        double radius=track.radius()*scale;
        if(!track.useShapecast())return List.of(new Sample(current,radius));
        // P1 initializes the previous sphere rotation to identity, retaining
        // only its previous translation. Current root rotation affects sample
        // COUNT even though it cannot rotate an origin-centered sphere.
        double angle=Math.abs(Math.toRadians(Mth.wrapDegrees(180-yaw)));
        int count=Math.max(1,Math.max((int)Math.ceil(previous.distanceTo(current)/radius),(int)Math.ceil(angle)));
        var result=new ArrayList<Sample>(count);
        for(int i=1;i<=count;i++)result.add(new Sample(previous.lerp(current,(double)i/count),radius));
        return List.copyOf(result);
    }
    public static AABB bounds(List<Sample> samples){
        AABB result=null;
        for(var sample:samples){var sphere=new AABB(sample.center(),sample.center()).inflate(sample.radius());result=result==null?sphere:result.minmax(sphere);}
        return result==null?null:result.inflate(1e-7);
    }
    public static ProjectileSphereContact.Hit contact(Sample sample,AABB box){return ProjectileSphereContact.sweep(sample.center(),sample.center(),sample.radius(),box);}
    public static ProjectileSphereContact.Hit contact(Sample sample,HydraContact.Box box){return ProjectileSphereContact.sweep(sample.center(),sample.center(),sample.radius(),box);}
    public static ProjectileSphereContact.Hit contact(Sample sample,List<Vec3> corners){return ProjectileSphereContact.sweep(sample.center(),sample.center(),sample.radius(),corners);}
}
