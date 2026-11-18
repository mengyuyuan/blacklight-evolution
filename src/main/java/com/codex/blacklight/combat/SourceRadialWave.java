package com.codex.blacklight.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source Hit spheres converted once to world blocks, separate from the boss's
 * eye ray and movement box. Original radii/offsets remain in the action table. */
public final class SourceRadialWave {
    public record Contact(Vec3 origin,Vec3 point,float damageScale) {}
    private SourceRadialWave() {}

    private static boolean active(SourceBossAction.Window window,int age){return age>=Math.ceil(window.begin()*20-.001F)&&age<=Math.floor(window.end()*20+.001F);}
    private static Vec3 origin(SourceBossAction action,SourceBossAction.Window window,Vec3 root,float yaw){
        float[] offset=window.localOffset();
        Vec3 local=offset==null?Vec3.ZERO:new Vec3(-offset[0],offset[1],-offset[2]).scale(action.spatialScale());
        return root.add(local.yRot((float)Math.toRadians(-yaw)));
    }
    /** Broadphase union of active world spheres with their converted offsets.
     * A tiny pad retains tangencies through Minecraft's strict AABB intersects;
     * the final sphere contact still uses the exact unpadded radius. */
    public static AABB bounds(SourceBossAction action,int age,Vec3 root,float yaw){
        AABB bounds=null;
        for(var window:action.windows)if(active(window,age)){
            Vec3 origin=origin(action,window,root,yaw);double radius=action.worldRadius(window);
            AABB sphere=new AABB(origin,origin).inflate(radius);
            bounds=bounds==null?sphere:bounds.minmax(sphere);
        }
        return bounds==null?null:bounds.inflate(1e-7);
    }

    public static Contact contact(SourceBossAction action,int age,Vec3 root,float yaw,AABB target){
        float largest=0;for(var window:action.windows)largest=Math.max(largest,window.damage());
        for(var window:action.windows){
            if(!active(window,age))continue;
            Vec3 origin=origin(action,window,root,yaw);double radius=action.worldRadius(window);
            Vec3 point=new Vec3(Mth.clamp(origin.x,target.minX,target.maxX),Mth.clamp(origin.y,target.minY,target.maxY),Mth.clamp(origin.z,target.minZ,target.maxZ));
            if(origin.distanceToSqr(point)<=radius*radius)return new Contact(origin,point,largest>0?window.damage()/largest:1);
        }
        return null;
    }
}
