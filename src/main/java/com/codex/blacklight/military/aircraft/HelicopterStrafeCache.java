package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** P1 103e3160 / 103e3350: mutable candidate and independently copied commitment. */
final class HelicopterStrafeCache {
    record Snapshot(HelicopterStrafePath.Path path,Vec3 targetAtPlan,float age,int minimum,int maximum,boolean valid) {}
    private static Snapshot empty(){return new Snapshot(new HelicopterStrafePath.Path(Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,0),Vec3.ZERO,-1,5,40,false);}
    private Snapshot candidate=empty(),committed=empty();

    Snapshot query(HelicopterStrafePath.Grid grid,Vec3 craft,Vec3 target,float maxAge,float maxMovement,int minimum,int maximum,boolean farthest){
        if(!Float.isFinite(maxAge)||!Float.isFinite(maxMovement)||maxMovement<0||minimum< -1||maximum< -1)
            throw new IllegalArgumentException("Invalid cache query");
        if(!HelicopterFlight.finite(target)){
            candidate=new Snapshot(candidate.path,candidate.targetAtPlan,candidate.age,candidate.minimum,candidate.maximum,false);
            return candidate; // Missing native target invalidates the candidate without clearing its age or geometry.
        }
        // Source compares the cached ground point, not targetAtPlan. Only the
        // minimum key is checked; max/farthest changes alone do not force a scan.
        boolean refresh=candidate.age<0||candidate.age>maxAge||minimum!=-1&&minimum!=candidate.minimum
                ||candidate.path.targetGround().distanceToSqr(target)>(double)maxMovement*maxMovement;
        if(refresh){
            int min=minimum<0?candidate.minimum:minimum,max=maximum<0?candidate.maximum:maximum;
            var result=HelicopterStrafePath.search(grid,craft,target,min,max,farthest,candidate.path.targetGround());
            var path=result.path();
            if(path==null)path=new HelicopterStrafePath.Path(result.groundOutput(),candidate.path.start(),candidate.path.end(),candidate.path.relativeRise());
            candidate=new Snapshot(path,target,0,min,max,result.path()!=null);
        }
        return candidate;
    }
    void advance(float seconds){
        if(!Float.isFinite(seconds)||seconds<0)throw new IllegalArgumentException("Invalid simulation delta");
        if(candidate.age>-.5F)candidate=new Snapshot(candidate.path,candidate.targetAtPlan,candidate.age+seconds,candidate.minimum,candidate.maximum,candidate.valid);
    }
    Snapshot candidate(){return candidate;}
    /** The native subtype2 copies all0x44 bytes, even a failed candidate. The caller must check valid(). */
    Snapshot commit(){committed=candidate;return committed;}
    Snapshot committed(){return committed;}
}
