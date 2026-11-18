package com.codex.blacklight.military.aircraft;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;

/** Owned ReserveVolumeAction table, distinct from the anonymous exclusion spheres.
 * P1 10202aa0 / 10385060 / 10385100 / 103853d0; no lease timer is attached to a volume. */
final class HelicopterReservations {
    private static final double EPSILON=9.999999747378752e-6;
    enum Kind { POSITION, STRAFE_TRACK, OPEN_RUN }
    record Volume(Vec3 anchor,Vec3 center,Vec3 along,Vec3 side,double lengthHalf,double heightHalf,float radius,Kind kind){
        boolean intersects(Vec3 point,float queryRadius){
            var d=point.subtract(center);
            if(kind==Kind.POSITION){double sum=radius+(queryRadius>EPSILON?queryRadius:0);return d.lengthSqr()<=sum*sum;}
            double x=Math.abs(d.dot(along))-lengthHalf,z=Math.abs(d.dot(side))-radius,y=Math.abs(d.y)-heightHalf;
            // Native point containment includes the box boundary. A nonzero
            // sphere radius uses strict squared distance at rounded corners.
            if(queryRadius<=EPSILON)return x<=0&&y<=0&&z<=0;
            x=Math.max(0,x);y=Math.max(0,y);z=Math.max(0,z);
            return x*x+y*y+z*z<(double)queryRadius*queryRadius;
        }
    }
    private final Map<UUID,Volume> volumes=new LinkedHashMap<>();
    boolean reserveStrafe(UUID owner,HelicopterStrafeCache.Snapshot candidate,float radius){
        Objects.requireNonNull(owner);
        if(candidate==null||!candidate.valid()||!validRadius(radius))return false;
        var path=candidate.path();if(path==null||!HelicopterFlight.finite(path.start())||!HelicopterFlight.finite(path.end()))return false;
        var prior=volumes.get(owner);
        if(prior!=null&&prior.kind==Kind.STRAFE_TRACK&&near(prior.anchor,path.start()))return true;
        var delta=path.end().subtract(path.start()).multiply(1,0,1);double length=delta.length();
        if(!Double.isFinite(length)||length*length<1e-10F)return false;
        var along=delta.scale(1/length);var side=new Vec3(-along.z,0,along.x);
        return put(owner,new Volume(path.start(),path.start().add(path.end().subtract(path.start()).scale(.5)),along,side,length*.5,radius,radius,Kind.STRAFE_TRACK));
    }
    boolean reservePosition(UUID owner,Vec3 position,float radius){
        Objects.requireNonNull(owner);if(!HelicopterFlight.finite(position)||!validRadius(radius))return false;
        var prior=volumes.get(owner);if(prior!=null&&prior.kind==Kind.POSITION&&near(prior.anchor,position))return true;
        return put(owner,new Volume(position,position,Vec3.ZERO,Vec3.ZERO,0,0,radius,Kind.POSITION));
    }
    /** SimpleOpenStrafeRun1020eb30 reserves once at begin. Vertical extent
     * includes all three points; horizontal extent runs from approach to end. */
    boolean reserveOpen(UUID owner,Vec3 start,Vec3 aim,Vec3 end,float margin,float sideRadius){
        Objects.requireNonNull(owner);
        if(!HelicopterFlight.finite(start)||!HelicopterFlight.finite(aim)||!HelicopterFlight.finite(end)||!validRadius(margin)||!validRadius(sideRadius))return false;
        Vec3 delta=end.subtract(start).multiply(1,0,1);double length=delta.length();
        if(!Double.isFinite(length)||length*length<1e-10F)return false;
        double low=Math.min(start.y,Math.min(aim.y,end.y)),high=Math.max(start.y,Math.max(aim.y,end.y));
        Vec3 center=new Vec3(start.x+delta.x*.5,low+(high-low)*.5,start.z+delta.z*.5),along=delta.scale(1/length);
        return put(owner,new Volume(start,center,along,new Vec3(-along.z,0,along.x),length*.5+margin,(high-low)*.5+margin,sideRadius,Kind.OPEN_RUN));
    }
    private boolean put(UUID owner,Volume volume){
        // Native insertion does not query for overlap. It appends up to50
        // records; MC keeps one action per owner and reports capacity failure.
        if(!volumes.containsKey(owner)&&volumes.size()>=50)return false;
        volumes.put(owner,volume);return true;
    }
    boolean pointAvailable(Vec3 point,float radius,UUID exceptOwner){
        if(!HelicopterFlight.finite(point)||!validRadius(radius))return false;
        for(var entry:volumes.entrySet())if(!entry.getKey().equals(exceptOwner)&&entry.getValue().intersects(point,radius))return false;
        return true;
    }
    void release(UUID owner){volumes.remove(owner);}
    void discardMissing(Predicate<UUID> exists){volumes.keySet().removeIf(id->!exists.test(id));}
    Volume volume(UUID owner){return volumes.get(owner);}
    int size(){return volumes.size();}
    private static boolean validRadius(float radius){return Float.isFinite(radius)&&radius>=0;}
    private static boolean near(Vec3 a,Vec3 b){return Math.abs(a.x-b.x)<=1&&Math.abs(a.y-b.y)<=1&&Math.abs(a.z-b.z)<=1;}
}
