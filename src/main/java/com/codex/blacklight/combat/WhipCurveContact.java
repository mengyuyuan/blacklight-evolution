package com.codex.blacklight.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The rendered centerline and authoritative collision share the same actor-local path. */
public final class WhipCurveContact {
    private WhipCurveContact() {}

    /** Correct the attachment over the first physical metre, independent of
     * exported node density. The damaging distant path stays authoritative. */
    public static List<Vec3> attachHand(List<Vec3> points,Vec3 hand){
        if(points.isEmpty())return points;
        var result=new ArrayList<Vec3>(points.size());var offset=hand.subtract(points.get(0));double length=0;
        for(int i=0;i<points.size();i++){
            if(i>0)length+=points.get(i).distanceTo(points.get(i-1));
            double weight=com.codex.blacklight.util.MathCompat.clamp(1-length/1.25,0,1);weight=weight*weight*(3-2*weight);
            result.add(points.get(i).add(offset.scale(weight)));
        }
        return List.copyOf(result);
    }

    public static List<Vec3> worldPoints(WhipCurve curve, double time, Vec3 feet, float yaw) {
        return worldPoints(curve,time,feet,yaw,1);
    }
    public static List<Vec3> worldPoints(WhipCurve curve,double time,Vec3 feet,float yaw,Entity actor){
        return worldPoints(curve,time,feet,yaw,AttackReach.scale(actor));
    }
    private static List<Vec3> worldPoints(WhipCurve curve,double time,Vec3 feet,float yaw,double scale){
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        var points=curve.sample(time);var root=points.get(0);
        return points.stream().map(p -> feet.add(right.scale(root.right()+(p.right()-root.right())*scale))
                .add(0,p.up(),0).add(forward.scale(root.forward()+(p.forward()-root.forward())*scale))).toList();
    }

    /** A wall cuts off the rest of the whip, including a tip located behind it. */
    public static List<Vec3> visiblePoints(List<Vec3> points, Level level, Entity actor) {
        var visible = new ArrayList<Vec3>();
        visible.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            Vec3 start = points.get(i - 1), end = points.get(i);
            var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, actor));
            visible.add(hit.getType() == HitResult.Type.MISS ? end : hit.getLocation());
            if (hit.getType() != HitResult.Type.MISS) break;
        }
        return List.copyOf(visible);
    }

    public static AABB sweptBounds(WhipCurve curve, double fromTime, double toTime,
                                  Vec3 fromFeet, Vec3 toFeet, float yaw) {
        return sweptBoundsScaled(curve,fromTime,toTime,fromFeet,toFeet,yaw,1);
    }
    public static AABB sweptBounds(WhipCurve curve,double fromTime,double toTime,Vec3 fromFeet,Vec3 toFeet,float yaw,Entity actor){
        return sweptBoundsScaled(curve,fromTime,toTime,fromFeet,toFeet,yaw,AttackReach.scale(actor));
    }
    private static AABB sweptBoundsScaled(WhipCurve curve,double fromTime,double toTime,Vec3 fromFeet,Vec3 toFeet,float yaw,double scale){
        var start = worldPoints(curve, fromTime, fromFeet, yaw,scale);
        AABB bounds = new AABB(start.get(0), start.get(0));
        for (var point : start) bounds = bounds.minmax(new AABB(point, point));
        // Every source key inside the tick is included; endpoint-only bounds miss a lash that reverses.
        for (var sample : curve.samples()) {
            if (sample.time() <= fromTime || sample.time() >= toTime) continue;
            double alpha = (sample.time() - fromTime) / Math.max(1e-9, toTime - fromTime);
            for (var point : worldPoints(curve, sample.time(), fromFeet.lerp(toFeet, alpha), yaw,scale))
                bounds = bounds.minmax(new AABB(point, point));
        }
        for (var point : worldPoints(curve, toTime, toFeet, yaw,scale))
            bounds = bounds.minmax(new AABB(point, point));
        return bounds.inflate(curve.collisionRadius());
    }

    public static boolean hits(WhipCurve curve, double fromTime, double toTime,
                               Vec3 fromFeet, Vec3 toFeet, float yaw, AABB target,
                               Level level, Entity actor) {
        return hits(curve,fromTime,toTime,fromFeet,toFeet,yaw,yaw,target,target,level,actor);
    }
    public static boolean hits(WhipCurve curve, double fromTime, double toTime,
                               Vec3 fromFeet, Vec3 toFeet, float fromYaw,float yaw,AABB beforeTarget,AABB target,
                               Level level, Entity actor) {
        var times = new ArrayList<Double>();
        times.add(fromTime);
        for (var sample : curve.samples())
            if (sample.time() > fromTime && sample.time() < toTime) times.add(sample.time());
        times.add(toTime);
        for (int interval = 1; interval < times.size(); interval++) {
            double begin = times.get(interval - 1), end = times.get(interval);
            Vec3 beginFeet = feetAt(begin, fromTime, toTime, fromFeet, toFeet);
            Vec3 endFeet = feetAt(end, fromTime, toTime, fromFeet, toFeet);
            var a = worldPoints(curve, begin, beginFeet, fromYaw,actor);
            var b = worldPoints(curve, end, endFeet, yaw,actor);
            double travel = Math.min(2,beforeTarget.getCenter().distanceTo(target.getCenter()));
            for (int node = 0; node < a.size(); node++) travel = Math.max(travel, a.get(node).distanceTo(b.get(node)));
            int steps = Math.max(1, (int)Math.ceil(travel / Math.max(.025, curve.collisionRadius() * .4)));
            steps = Math.min(1024, steps);
            for (int step = 0; step <= steps; step++) {
                double alpha = step / (double)steps;
                double time=begin+(end-begin)*alpha;
                double frameAlpha=com.codex.blacklight.util.MathCompat.clamp((time-fromTime)/Math.max(1e-9,toTime-fromTime),0,1);
                float turn=fromYaw+net.minecraft.util.Mth.wrapDegrees(yaw-fromYaw)*(float)frameAlpha;
                AABB expanded=CombatPursuit.interpolate(beforeTarget,target,frameAlpha).inflate(curve.collisionRadius());
                var points = worldPoints(curve,time,beginFeet.lerp(endFeet,alpha),turn,actor);
                var visible = visiblePoints(points, level, actor);
                for (int segment = 1; segment < visible.size(); segment++) {
                    Vec3 first = visible.get(segment - 1), last = visible.get(segment);
                    Vec3 contact = expanded.contains(first) ? first : expanded.clip(first, last).orElse(null);
                    if (contact == null && expanded.contains(last)) contact = last;
                    if (contact != null && level.clip(new ClipContext(contact, target.getCenter(),
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType() == HitResult.Type.MISS)
                        return true;
                }
            }
        }
        return false;
    }

    public static boolean intersectsPolyline(List<Vec3> points, AABB expandedTarget) {
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1), b = points.get(i);
            if (expandedTarget.contains(a) || expandedTarget.contains(b) || expandedTarget.clip(a, b).isPresent()) return true;
        }
        return false;
    }

    private static Vec3 feetAt(double time, double begin, double end, Vec3 first, Vec3 last) {
        return first.lerp(last, com.codex.blacklight.util.MathCompat.clamp((time - begin) / Math.max(1e-9, end - begin), 0, 1));
    }
}
