package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Verified source Hit timing/contact on the source-bound Goliath rig. */
public final class GoliathSlam {
    public record Sphere(Vec3 origin,double radius,float damageScale) {}
    public record Contact(String bone,Vec3 origin,Vec3 point,Vec3 normal,float damageScale) {}
    private GoliathSlam() {}
    public static boolean uses(EncounterKind kind,EncounterMove move){return kind==EncounterKind.GOLIATH&&move==EncounterMove.HAMMER;}
    public static boolean canReach(Vec3 origin,float yaw,AABB target){
        return canReach(origin,yaw,target,1F);
    }
    public static boolean canReach(Vec3 origin,float yaw,AABB target,float scale){
        for(var sphere:spheres(action().contact(),origin,yaw,scale))if(contact(sphere,null,target)!=null)return true;
        return false;
    }
    private static SourceBossAction action(){return SourceBossAction.get(EncounterKind.GOLIATH,EncounterMove.HAMMER);}
    /** P2 Hit5467068/5467492: one short Motion_Root contact, inner750 and
     * outer400. The table already includes the source-root reference offset;
     * neither target dimensions nor the visible fist enlarge these spheres. */
    public static List<Sphere> spheres(int age,Vec3 root,float yaw){
        return spheres(age,root,yaw,1F);
    }
    public static List<Sphere> spheres(int age,Vec3 root,float yaw,float scale){
        var action=action();float largest=0;
        for(var window:action.windows)largest=Math.max(largest,window.damage());
        var result=new ArrayList<Sphere>();
        for(var window:action.windows){
            if(age<Math.ceil(window.begin()*20-.001F)||age>Math.floor(window.end()*20+.001F))continue;
            var offset=window.localOffset();
            Vec3 local=offset==null?Vec3.ZERO:new Vec3(-offset[0],offset[1],-offset[2]).scale(action.spatialScale()*scale);
            result.add(new Sphere(root.add(local.yRot((float)Math.toRadians(-yaw))),action.worldRadius(window)*scale,largest>0?window.damage()/largest:1));
        }
        // Preserve the existing explicit MC strongest-accepted contact policy;
        // never add the two simultaneous source Hit amounts together.
        result.sort(Comparator.comparingDouble(Sphere::damageScale).reversed());
        return List.copyOf(result);
    }
    public static AABB bounds(List<Sphere> spheres){
        AABB result=null;
        for(var sphere:spheres){var box=new AABB(sphere.origin,sphere.origin).inflate(sphere.radius);result=result==null?box:result.minmax(box);}
        return GoliathAttackBounds.includingTouch(result);
    }
    public static Contact contact(Sphere sphere,String bone,AABB target){
        return contact(sphere,bone,ProjectileSphereContact.sweep(sphere.origin,sphere.origin,sphere.radius,target));
    }
    public static Contact contact(Sphere sphere,String bone,HydraContact.Box target){
        return contact(sphere,bone,ProjectileSphereContact.sweep(sphere.origin,sphere.origin,sphere.radius,target));
    }
    private static Contact contact(Sphere sphere,String bone,ProjectileSphereContact.Hit hit){
        return hit==null?null:new Contact(bone,sphere.origin,hit.point(),hit.normal(),sphere.damageScale);
    }
}
