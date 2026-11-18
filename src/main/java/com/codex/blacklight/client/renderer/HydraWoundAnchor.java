package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightHydra;
import net.minecraft.world.phys.Vec3;

/** A short-lived spray nozzle on the displayed receiving bone. Detached tissue stays free. */
final class HydraWoundAnchor {
    private final String bone;private final int cube;
    private final Vec3 local,localNormal;
    private Vec3 point,normal;
    private HydraWoundAnchor(String bone,int cube,Vec3 local,Vec3 localNormal,Vec3 point,Vec3 normal){this.bone=bone;this.cube=cube;this.local=local;this.localNormal=localNormal;this.point=point;this.normal=normal;}
    static HydraWoundAnchor capture(com.codex.blacklight.combat.AnimatedHurtBody h,Vec3 point,Vec3 direction,float partial){
        return capture(h.hurtRegions(partial),point,direction);
    }
    static HydraWoundAnchor capture(java.util.List<com.codex.blacklight.combat.HydraContact.Region> regions,Vec3 point,Vec3 direction){
        var normal=direction.normalize();if(normal.lengthSqr()<.01)normal=new Vec3(0,1,0);
        int ri=-1,ci=-1;double distance=Double.POSITIVE_INFINITY;Vec3 surface=null;
        // Metadata may have advanced the head since the damage tick. Project onto
        // the visible entry skin along the incoming direction, not into its interior.
        var from=point.add(normal.scale(2));var to=point.subtract(normal.scale(.4));
        for(int r=0;r<regions.size();r++)for(int c=0;c<regions.get(r).boxes().size();c++){
            var b=regions.get(r).boxes().get(c);var hit=b.clip(from,to);if(hit.isEmpty())continue;
            double d=from.distanceToSqr(hit.get());if(d<distance){ri=r;ci=c;distance=d;surface=hit.get();}
        }
        if(ri<0)return null;
        var region=regions.get(ri);var box=region.boxes().get(ci);var local=box.localPoint(surface);
        return new HydraWoundAnchor(region.bone(),ci,local,box.localPoint(surface.add(normal)).subtract(local),surface.add(normal.scale(.035)),normal);
    }
    void update(com.codex.blacklight.combat.AnimatedHurtBody h,float partial,float age){
        if(age>=4)return;
        update(h.hurtRegions(partial),age);
    }
    void update(java.util.List<com.codex.blacklight.combat.HydraContact.Region> regions,float age){
        if(age>=4)return;
        for(var region:regions)if(region.bone().equals(bone)&&cube<region.boxes().size()){
            var b=region.boxes().get(cube);var at=b.worldPoint(local);normal=b.worldPoint(local.add(localNormal)).subtract(at).normalize();point=at.add(normal.scale(.035));return;
        }
    }
    Vec3 point(){return point;}
    Vec3 normal(){return normal;}
}
