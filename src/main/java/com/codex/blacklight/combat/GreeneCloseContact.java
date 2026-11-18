package com.codex.blacklight.combat;

import java.util.Arrays;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real shared-pose origins for the three non-swept source Slug Hit windows.
 * No aim-assist, eye ray, previous-to-current sweep or expanded victim root.
 * The source sphere radius is fixed; source-to-MC units/presentation are applied
 * once. Terrain occlusion below is a deliberate MC melee-safety adapter. */
public final class GreeneCloseContact {
    public record Sphere(Vec3 center,double radius){public AABB bounds(){return new AABB(center,center).inflate(radius);}}
    private GreeneCloseContact(){}
    public static Sphere sphere(GreeneCloseAttack.Side side,GreeneCloseAttack.Track track,float[][] pose,Vec3 origin,float yaw){
        if(side==null||track==null||pose==null||origin==null||!Double.isFinite(origin.lengthSqr())||!Float.isFinite(yaw))
            throw new IllegalArgumentException("Invalid Greene close contact pose");
        var table=GreeneMotion.table();String bone=track==GreeneCloseAttack.Track.TIP?side.joint():"native_Motion_Root";
        int index=Arrays.asList(table.bones).indexOf(bone);
        if(index<0)throw new IllegalStateException("Missing actual Slug contact joint "+bone);
        // Native helpers have zero pivots and store original local source axes.
        // Their matrix already includes presentation; never mirror X or apply
        // presentation a second time to this local joint offset.
        double rawScale=table.sourceUnitBlocks()/GreenePresentation.SCALE;float[] offset=track.offset();
        var point=new Vector3f((float)(offset[0]*rawScale),(float)(offset[1]*rawScale),(float)(offset[2]*rawScale));
        new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).mul(table.transforms(pose)[index]).transformPosition(point);
        return new Sphere(origin.add(point.x,point.y+.01,point.z),track.radius()*table.sourceUnitBlocks());
    }
    public static ProjectileSphereContact.Hit contact(Sphere sphere,AABB box){return ProjectileSphereContact.sweep(sphere.center,sphere.center,sphere.radius,box);}
    public static ProjectileSphereContact.Hit contact(Sphere sphere,HydraContact.Box box){return ProjectileSphereContact.sweep(sphere.center,sphere.center,sphere.radius,box);}
    public static ProjectileSphereContact.Hit contact(Sphere sphere,List<Vec3> corners){return ProjectileSphereContact.sweep(sphere.center,sphere.center,sphere.radius,corners);}
    /** The tip may already be behind a wall before its Hit window opens.
     * Check the articulated limb's connection too, not just tip-to-victim. */
    public static boolean connected(ServerLevel level,Entity owner,GreeneCloseAttack.Side side,GreeneCloseAttack.Track track,
                                    float[][] pose,Vec3 origin,float yaw,Sphere sphere){
        var table=GreeneMotion.table();var matrices=table.transforms(pose);var names=Arrays.asList(table.bones);
        if(track!=GreeneCloseAttack.Track.TIP)
            return unobstructed(level,owner,new Sphere(origin.add(0,.01,0),0),sphere.center);
        String prefix=side.joint().substring(0,side.joint().length()-1);
        Vec3 previous=null;
        for(String bone:new String[]{prefix+"Base",prefix+"1",prefix+"2",prefix+"3",prefix+"4",prefix+"5"}){
            int index=names.indexOf(bone);if(index<0)throw new IllegalStateException("Missing Slug cover joint "+bone);
            var point=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).mul(matrices[index]).transformPosition(new Vector3f());
            var current=origin.add(point.x,point.y+.01,point.z);
            if(previous!=null&&!unobstructed(level,owner,new Sphere(previous,0),current))return false;
            previous=current;
        }
        return unobstructed(level,owner,new Sphere(previous,0),sphere.center);
    }
    /** Reject unloaded/world-border corridors before the block query. Exact
     * touching can end on the victim's support face; only an earlier block
     * blocks this sample. This does not turn a miss into a target-root hit. */
    public static boolean unobstructed(ServerLevel level,Entity owner,Sphere sphere,Vec3 contact){
        if(contact==null||!Double.isFinite(contact.lengthSqr()))return false;var from=sphere.center;
        var border=level.getWorldBorder();
        for(var point:List.of(from,contact))if(point.x<border.getMinX()||point.x>border.getMaxX()||point.z<border.getMinZ()||point.z>border.getMaxZ()
                ||point.y<level.getMinBuildHeight()||point.y>=level.getMaxBuildHeight())return false;
        int x0=Mth.floor(Math.min(from.x,contact.x)-.001)>>4,x1=Mth.floor(Math.max(from.x,contact.x)+.001)>>4;
        int z0=Mth.floor(Math.min(from.z,contact.z)-.001)>>4,z1=Mth.floor(Math.max(from.z,contact.z)+.001)>>4;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        var hit=level.clip(new ClipContext(from,contact,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        return hit.getType()==HitResult.Type.MISS||hit.getLocation().distanceToSqr(from)+1e-7>=contact.distanceToSqr(from);
    }
}
