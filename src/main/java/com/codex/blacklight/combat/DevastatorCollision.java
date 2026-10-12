package com.codex.blacklight.combat;

import java.util.List;

/** Separate native CollisionHitTracks, decoded with the complete 276-byte layout.
 * Times are relative to the active body node, not to its preceding antic. Only
 * tracks whose source mask includes Health are included for Minecraft actors.
 * Native TimeBetweenHits=-1 does not prove per-tick damage; each lane deduplicates.
 */
public final class DevastatorCollision {
    public record Track(int sourceTrack,double begin,double end,double radius,float damage,
                         double offsetY,double forwardOffset,double arc) {
        public boolean extendingCapsule(){return sourceTrack==4456||sourceTrack==54708;}
        public double travelVelocity(){return extendingCapsule()?30*DevastatorTiming.SOURCE_WORLD_SCALE:0;}
    }
    private DevastatorCollision() { }
    public static List<Track> tracks(WeaponMove move) {
        return switch(move) {
            case DEV_GROUNDSPIKE,DEV_AIR_GROUNDSPIKE -> List.of(
                    new Track(20756,0,.133333,5,250,-1,0,180),
                    new Track(21052,2.8,3,25,150,0,0,360));
            case DEV_TENDRIL -> List.of(
                    new Track(36344,.433333,.5,5,100,0,.25,360),
                    new Track(36640,2.8,3,25,75,0,0,360));
            case DEV_AIR_TENDRIL -> List.of(new Track(78020,.416667,.533333,5,20,0,.25,180));
            // StaticObject-only sibling records 4752/55004 cannot hit living
            // actors a second time. Health uses the native ExtendingCapsule;
            // StaticObject uses a travelling Sphere and is deliberately omitted.
            case DEV_CRITICAL_PAIN -> List.of(new Track(4456,.666670,2.5,1.5F,50,0,0,0));
            case DEV_AIR_CRITICAL_PAIN -> List.of(new Track(54708,.666670,2.5,1,50,0,0,180));
            default -> List.of();
        };
    }
    public static double activeStart(WeaponMove move,DevastatorTiming timing) {
        return DevastatorSystem.kind(move)==1?timing.effectStart()-.4:timing.effectStart();
    }
    public static double spendAt(WeaponMove move,DevastatorTiming timing) {
        return activeStart(move,timing)+switch(DevastatorSystem.kind(move)) {
            case 1 -> .433333;
            case 2 -> .666667;
            default -> 0;
        };
    }
    public static double targetDuration(WeaponMove move) {
        return DevastatorSystem.kind(move)==2?(move.aerial()?1.666667:1.333333):.5;
    }
    /** Minimum squared distance to a segment is convex. Numeric minimization
     * handles thin boxes and rounded capsule corners without a box-inflation
     * false positive. No stepping proportional to travel distance is required. */
    public static double capsuleDistanceSquared(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,
                                                net.minecraft.world.phys.AABB box){
        if(box.contains(from)||box.contains(to)||box.clip(from,to).isPresent())return 0;
        double lo=0,hi=1;
        for(int i=0;i<40;i++){
            double a=(2*lo+hi)/3,b=(lo+2*hi)/3;
            if(segmentDistance(from,to,box,a)<segmentDistance(from,to,box,b))hi=b;else lo=a;
        }
        return Math.min(segmentDistance(from,to,box,(lo+hi)*.5),
                Math.min(segmentDistance(from,to,box,0),segmentDistance(from,to,box,1)));
    }
    private static double segmentDistance(net.minecraft.world.phys.Vec3 a,net.minecraft.world.phys.Vec3 b,
                                           net.minecraft.world.phys.AABB box,double t){
        double x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t,z=a.z+(b.z-a.z)*t;
        double dx=x-Math.clamp(x,box.minX,box.maxX),dy=y-Math.clamp(y,box.minY,box.maxY),dz=z-Math.clamp(z,box.minZ,box.maxZ);
        return dx*dx+dy*dy+dz*dz;
    }
}
