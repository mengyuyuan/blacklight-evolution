package com.codex.blacklight.military.vehicle;

import java.util.List;
import java.util.OptionalDouble;
import java.util.ArrayList;
import com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3;

public final class VehicleHullShape {
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Bounds {
            for(double value:new double[]{minX,minY,minZ,maxX,maxY,maxZ}) VehicleDefinition.bounded(value,-60000100,60000100,"bounds");
            if(minX>maxX || minY>maxY || minZ>maxZ) throw new IllegalArgumentException("Inverted vehicle bounds");
        }
        public boolean intersects(Bounds other) { return minX<=other.maxX && maxX>=other.minX && minY<=other.maxY && maxY>=other.minY && minZ<=other.maxZ && maxZ>=other.minZ; }
        public boolean contains(Vec3 point) { return point.x()>=minX && point.x()<=maxX && point.y()>=minY && point.y()<=maxY && point.z()>=minZ && point.z()<=maxZ; }
        Bounds union(Bounds b) { return new Bounds(Math.min(minX,b.minX),Math.min(minY,b.minY),Math.min(minZ,b.minZ),Math.max(maxX,b.maxX),Math.max(maxY,b.maxY),Math.max(maxZ,b.maxZ)); }
        Bounds inflate(double margin) { return new Bounds(minX-margin,minY-margin,minZ-margin,maxX+margin,maxY+margin,maxZ+margin); }
        Bounds inflateHorizontal(double margin) { return new Bounds(minX-margin,minY,minZ-margin,maxX+margin,maxY,maxZ+margin); }
    }
    private static final double TRANSLATION_STEP=.25, CORNER_ARC_STEP=.125;
    private final List<Bounds> parts;
    private final List<HitPart> hitParts;
    private final double radius;
    private record HitPart(Bounds localBounds, Vec3 origin, Vec3 right, Vec3 up, Vec3 forward) {
        static HitPart axisAligned(Bounds bounds) {
            return new HitPart(bounds,new Vec3(0,0,0),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        }
        double raycast(Vec3 rayOrigin,Vec3 direction,double maxDistance) {
            Vec3 offset=VehicleDefinition.subtract(rayOrigin,origin);
            Vec3 localOrigin=new Vec3(dot(offset,right),dot(offset,up),dot(offset,forward));
            Vec3 localDirection=new Vec3(dot(direction,right),dot(direction,up),dot(direction,forward));
            return rayBox(localBounds,localOrigin,localDirection,maxDistance);
        }
    }
    public VehicleHullShape(double width, double length, double height) {
        VehicleDefinition.bounded(width,.25,32,"width"); VehicleDefinition.bounded(length,.25,32,"length"); VehicleDefinition.bounded(height,.25,32,"height");
        parts=List.of(new Bounds(-width/2,0,-length/2,width/2,height,length/2));
        hitParts=List.of(HitPart.axisAligned(parts.get(0))); radius=Math.hypot(width/2,length/2);
    }
    private VehicleHullShape(List<Bounds> parts,List<HitPart> hitParts) {
        this.parts=List.copyOf(parts); this.hitParts=List.copyOf(hitParts); double r=0;
        for(Bounds b:parts) r=Math.max(r,Math.hypot(Math.max(Math.abs(b.minX),Math.abs(b.maxX)),Math.max(Math.abs(b.minZ),Math.abs(b.maxZ))));
        radius=r;
    }
    public static VehicleHullShape from(VehicleDefinition definition, double turretYaw, double pitch) {
        VehicleDefinition.finiteAngle(turretYaw); VehicleDefinition.finiteAngle(pitch);
        var body=new VehicleHullShape(definition.width(),definition.length(),definition.height());
        var boxes=new ArrayList<>(body.parts);
        var hitBoxes=new ArrayList<>(body.hitParts);
        if(definition.barrelRadius()>0) {
            Vec3 zero=new Vec3(0,0,0);
            for(String mount:definition.anchors().keySet()) if(mount.startsWith("muzzle_")) {
                String breech="breech_"+mount.substring("muzzle_".length());
                if(!definition.anchors().containsKey(breech)) throw new IllegalArgumentException("Muzzle has no matching breech");
                Vec3 a=definition.mountWorld(breech,zero,0,turretYaw,pitch), b=definition.mountWorld(mount,zero,0,turretYaw,pitch);
                boxes.add(new Bounds(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),
                        Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z())).inflate(definition.barrelRadius()));
                Vec3 segment=VehicleDefinition.subtract(b,a); double length=Math.sqrt(dot(segment,segment));
                if(length<1e-9) throw new IllegalArgumentException("Barrel requires distinct breech and muzzle");
                Vec3 forward=scale(segment,1/length);
                var frame=definition.anchors().get(breech).frame();
                Vec3 referenceRight=frameAxis(new Vec3(1,0,0),frame,turretYaw,pitch);
                Vec3 right=VehicleDefinition.subtract(referenceRight,scale(forward,dot(referenceRight,forward)));
                if(dot(right,right)<1e-12) {
                    Vec3 referenceUp=frameAxis(new Vec3(0,1,0),frame,turretYaw,pitch);
                    right=VehicleDefinition.subtract(referenceUp,scale(forward,dot(referenceUp,forward)));
                }
                right=scale(right,1/Math.sqrt(dot(right,right)));
                Vec3 up=cross(forward,right); double r=definition.barrelRadius();
                hitBoxes.add(new HitPart(new Bounds(-r,-r,0,r,r,length),a,right,up,forward));
            }
        }
        return new VehicleHullShape(boxes,hitBoxes);
    }
    public static List<Bounds> sweepArticulation(VehicleDefinition definition, Vec3 origin, double bodyYaw,
            double startTurretYaw, double endTurretYaw, double startPitch, double endPitch) {
        VehicleDefinition.finiteAngle(bodyYaw); VehicleDefinition.finiteAngle(startPitch); VehicleDefinition.finiteAngle(endPitch);
        double dy=shortestDelta(startTurretYaw,endTurretYaw), dp=endPitch-startPitch;
        if(Math.abs(dp)>180) throw new IllegalArgumentException("Unbounded gun pitch sweep");
        double maxRadius=0;
        for(var anchor:definition.anchors().values()) if(anchor.frame()!=VehicleDefinition.Frame.BODY) {
            Vec3 t=VehicleDefinition.subtract(anchor.position(),definition.turretPivot());
            Vec3 g=VehicleDefinition.subtract(anchor.position(),definition.gunPivot());
            // Includes pivot displacement after pitch so the yaw-radius bound remains conservative.
            maxRadius=Math.max(maxRadius,Math.sqrt(t.x()*t.x()+t.y()*t.y()+t.z()*t.z())+Math.sqrt(g.x()*g.x()+g.y()*g.y()+g.z()*g.z())+definition.barrelRadius());
        }
        int segments=segments(maxRadius*Math.toRadians(Math.abs(dy)+Math.abs(dp)),CORNER_ARC_STEP);
        var result=new ArrayList<Bounds>(segments);
        VehicleHullShape previous=from(definition,startTurretYaw,startPitch);
        // Lipschitz displacement bound also covers compound pitch/yaw between the sampled endpoints.
        double margin=maxRadius*Math.toRadians(Math.abs(dy)+Math.abs(dp))/segments;
        for(int i=1;i<=segments;i++) {
            VehicleHullShape next=from(definition,startTurretYaw+dy*i/segments,startPitch+dp*i/segments);
            Bounds envelope=transform(previous.parts.get(0),origin,bodyYaw);
            for(int part=1;part<previous.parts.size();part++) {
                Bounds swept=transform(previous.parts.get(part),origin,bodyYaw).union(transform(next.parts.get(part),origin,bodyYaw)).inflate(margin+1e-9);
                envelope=envelope.union(swept);
            }
            result.add(envelope); previous=next;
        }
        return List.copyOf(result);
    }
    public Bounds bounds(Vec3 origin, double yaw) {
        VehicleDefinition.finiteAngle(yaw); Bounds result=null;
        for(Bounds part:parts) {
            Bounds transformed=transform(part,origin,yaw);
            result=result==null ? transformed : result.union(transformed);
        }
        return result;
    }
    public OptionalDouble raycast(Vec3 origin, double yaw, Vec3 rayOrigin, Vec3 direction, double maxDistance) {
        VehicleDefinition.finiteAngle(yaw); VehicleDefinition.bounded(maxDistance,0,1024,"ray distance");
        double length=Math.sqrt(direction.x()*direction.x()+direction.y()*direction.y()+direction.z()*direction.z());
        if(length==0) throw new IllegalArgumentException("Zero ray direction");
        Vec3 localOrigin=VehicleDefinition.rotateYaw(VehicleDefinition.subtract(rayOrigin,origin),-yaw);
        Vec3 localDirection=VehicleDefinition.rotateYaw(new Vec3(direction.x()/length,direction.y()/length,direction.z()/length),-yaw);
        double nearest=Double.POSITIVE_INFINITY;
        for(HitPart part:hitParts) nearest=Math.min(nearest,part.raycast(localOrigin,localDirection,maxDistance));
        return Double.isFinite(nearest) ? OptionalDouble.of(nearest) : OptionalDouble.empty();
    }
    public List<Bounds> sweepRotation(Vec3 origin, double startYaw, double endYaw) {
        double delta=shortestDelta(startYaw,endYaw); int segments=segments(radius*Math.toRadians(Math.abs(delta)),CORNER_ARC_STEP);
        // A circular arc deviates from its endpoint chord by at most r*(1-cos(theta/2)).
        double margin=radius*(1-Math.cos(Math.toRadians(Math.abs(delta))/segments/2))+1e-9;
        var result=new ArrayList<Bounds>(segments); Bounds previous=bounds(origin,startYaw);
        for(int i=1;i<=segments;i++) {
            Bounds next=bounds(origin,startYaw+delta*i/segments);
            result.add(previous.union(next).inflateHorizontal(margin)); previous=next;
        }
        return List.copyOf(result);
    }
    public List<Bounds> sweepTranslation(Vec3 origin, Vec3 destination, double yaw) {
        Vec3 delta=VehicleDefinition.subtract(destination,origin);
        double distance=Math.sqrt(delta.x()*delta.x()+delta.y()*delta.y()+delta.z()*delta.z());
        if(distance>32) throw new IllegalArgumentException("Unbounded vehicle translation");
        int segments=segments(distance,TRANSLATION_STEP); var result=new ArrayList<Bounds>(segments); Bounds previous=bounds(origin,yaw);
        for(int i=1;i<=segments;i++) {
            Vec3 point=new Vec3(origin.x()+delta.x()*i/segments,origin.y()+delta.y()*i/segments,origin.z()+delta.z()*i/segments);
            Bounds next=bounds(point,yaw); result.add(previous.union(next)); previous=next;
        }
        return List.copyOf(result);
    }
    private static int segments(double distance,double limit) {
        if(!Double.isFinite(distance) || distance<0 || distance/limit>8192) throw new IllegalArgumentException("Unbounded vehicle sweep");
        return Math.max(1,(int)Math.ceil(distance/limit));
    }
    /** Closest point over the actual body and articulated barrel boxes, including their orientation. */
    public Vec3 closestPoint(Vec3 origin,double yaw,Vec3 point){
        VehicleDefinition.finiteAngle(yaw);
        for(Vec3 value:List.of(origin,point))for(double coordinate:new double[]{value.x(),value.y(),value.z()})
            VehicleDefinition.bounded(coordinate,-60000000,60000000,"physical contact position");
        Vec3 local=VehicleDefinition.rotateYaw(VehicleDefinition.subtract(point,origin),-yaw),nearest=null;double distance=Double.POSITIVE_INFINITY;
        for(HitPart part:hitParts){
            Vec3 offset=VehicleDefinition.subtract(local,part.origin);var b=part.localBounds;
            double x=Math.max(b.minX,Math.min(b.maxX,dot(offset,part.right))),y=Math.max(b.minY,Math.min(b.maxY,dot(offset,part.up))),z=Math.max(b.minZ,Math.min(b.maxZ,dot(offset,part.forward)));
            Vec3 candidate=new Vec3(part.origin.x()+part.right.x()*x+part.up.x()*y+part.forward.x()*z,
                    part.origin.y()+part.right.y()*x+part.up.y()*y+part.forward.y()*z,
                    part.origin.z()+part.right.z()*x+part.up.z()*y+part.forward.z()*z);
            Vec3 delta=VehicleDefinition.subtract(candidate,local);double squared=dot(delta,delta);
            if(squared<distance){distance=squared;nearest=candidate;}
        }
        Vec3 rotated=VehicleDefinition.rotateYaw(nearest,yaw);
        return new Vec3(origin.x()+rotated.x(),origin.y()+rotated.y(),origin.z()+rotated.z());
    }
    /** First contact distance for a world-axis-aligned physical object moving through the actual hull. */
    public OptionalDouble sweepBox(Vec3 origin,double yaw,Vec3 from,Vec3 to,Vec3 half) {
        VehicleDefinition.finiteAngle(yaw);
        for(Vec3 point:List.of(origin,from,to)) for(double value:new double[]{point.x(),point.y(),point.z()})
            VehicleDefinition.bounded(value,-60000000,60000000,"physical sweep position");
        for(double value:new double[]{half.x(),half.y(),half.z()}) VehicleDefinition.bounded(value,0,8,"physical half size");
        Vec3 motion=VehicleDefinition.subtract(to,from);double length=Math.sqrt(dot(motion,motion));
        VehicleDefinition.bounded(length,0,64,"physical sweep distance");
        Vec3[] world={new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1)};
        double nearest=Double.POSITIVE_INFINITY;
        for(HitPart part:hitParts) {
            Bounds b=part.localBounds;
            Vec3 center=new Vec3((b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2);
            Vec3 size=new Vec3((b.maxX-b.minX)/2,(b.maxY-b.minY)/2,(b.maxZ-b.minZ)/2);
            Vec3 localCenter=new Vec3(part.origin.x()+part.right.x()*center.x()+part.up.x()*center.y()+part.forward.x()*center.z(),
                    part.origin.y()+part.right.y()*center.x()+part.up.y()*center.y()+part.forward.y()*center.z(),
                    part.origin.z()+part.right.z()*center.x()+part.up.z()*center.y()+part.forward.z()*center.z());
            Vec3 worldCenter=VehicleDefinition.rotateYaw(localCenter,yaw);
            Vec3 offset=VehicleDefinition.subtract(from,new Vec3(origin.x()+worldCenter.x(),origin.y()+worldCenter.y(),origin.z()+worldCenter.z()));
            Vec3[] axes={VehicleDefinition.rotateYaw(part.right,yaw),VehicleDefinition.rotateYaw(part.up,yaw),VehicleDefinition.rotateYaw(part.forward,yaw)};
            var separating=new ArrayList<Vec3>(15);separating.addAll(List.of(world));separating.addAll(List.of(axes));
            for(Vec3 axis:world)for(Vec3 boxAxis:axes)separating.add(cross(axis,boxAxis));
            double enter=0,exit=1;boolean missed=false;
            // Continuous SAT: both boxes keep their orientation during this one bounded physical step.
            for(Vec3 axis:separating) {
                if(dot(axis,axis)<1e-16)continue;
                double radius=half.x()*Math.abs(axis.x())+half.y()*Math.abs(axis.y())+half.z()*Math.abs(axis.z())
                        +size.x()*Math.abs(dot(axis,axes[0]))+size.y()*Math.abs(dot(axis,axes[1]))+size.z()*Math.abs(dot(axis,axes[2]));
                double position=dot(offset,axis),speed=dot(motion,axis);
                if(Math.abs(speed)<1e-12){if(Math.abs(position)>radius+1e-9){missed=true;break;}continue;}
                double a=(-radius-position)/speed,c=(radius-position)/speed;
                enter=Math.max(enter,Math.min(a,c));exit=Math.min(exit,Math.max(a,c));
                if(enter>exit+1e-9){missed=true;break;}
            }
            if(!missed)nearest=Math.min(nearest,Math.max(0,enter)*length);
        }
        return Double.isFinite(nearest)?OptionalDouble.of(nearest):OptionalDouble.empty();
    }
    private static double dot(Vec3 a,Vec3 b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    private static Vec3 scale(Vec3 p,double factor) { return new Vec3(p.x()*factor,p.y()*factor,p.z()*factor); }
    private static Vec3 cross(Vec3 a,Vec3 b) { return new Vec3(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x()); }
    private static Vec3 frameAxis(Vec3 axis,VehicleDefinition.Frame frame,double yaw,double pitch) {
        if(frame==VehicleDefinition.Frame.GUN_PITCH) axis=VehicleDefinition.rotatePitch(axis,pitch);
        return frame==VehicleDefinition.Frame.BODY ? axis : VehicleDefinition.rotateYaw(axis,yaw);
    }
    private static double shortestDelta(double start,double end) {
        VehicleDefinition.finiteAngle(start); VehicleDefinition.finiteAngle(end);
        double delta=(end-start)%360; if(delta>180)delta-=360; if(delta< -180)delta+=360; return delta;
    }
    private static Bounds transform(Bounds part,Vec3 origin,double yaw) {
        double minX=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{part.minX,part.maxX}) for(double z:new double[]{part.minZ,part.maxZ}) {
            Vec3 p=VehicleDefinition.rotateYaw(new Vec3(x,0,z),yaw);
            minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());
        }
        return new Bounds(origin.x()+minX,origin.y()+part.minY,origin.z()+minZ,origin.x()+maxX,origin.y()+part.maxY,origin.z()+maxZ);
    }
    private static double rayBox(Bounds b,Vec3 origin,Vec3 direction,double distance) {
        double lo=0,hi=distance;
        double[] p={origin.x(),origin.y(),origin.z()},d={direction.x(),direction.y(),direction.z()},min={b.minX,b.minY,b.minZ},max={b.maxX,b.maxY,b.maxZ};
        for(int axis=0;axis<3;axis++) {
            if(Math.abs(d[axis])<1e-12) { if(p[axis]<min[axis] || p[axis]>max[axis]) return Double.POSITIVE_INFINITY; }
            else {
                double a=(min[axis]-p[axis])/d[axis],c=(max[axis]-p[axis])/d[axis];
                lo=Math.max(lo,Math.min(a,c)); hi=Math.min(hi,Math.max(a,c)); if(lo>hi)return Double.POSITIVE_INFINITY;
            }
        }
        return lo;
    }
}
