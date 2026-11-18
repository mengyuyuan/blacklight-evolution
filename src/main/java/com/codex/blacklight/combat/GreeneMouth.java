package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.util.Arrays;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** MotherCore's original GrabAutoSpawn attachments on its native Mouth joint.
 * Uses the same full-scale pose as the visible outer skin and receiving mesh;
 * the embedded human is a separate Body_Root branch. */
public final class GreeneMouth {
    public static Vec3 position(float[][] pose,Vec3 origin,float yaw,int slot){
        if(slot<1||slot>10)throw new IllegalArgumentException("Unknown Greene emitter "+slot);
        var point=GreeneMotion.table().socket(pose,"VomitGun"+slot);
        if(point==null)throw new IllegalStateException("Missing original Greene mouth socket");
        new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(point);
        return origin.add(point.x,point.y+.01,point.z);
    }
    public static Vec3 position(EncounterMob mob,int slot){
        return position(GreeneMotion.pose(mob,0),mob.position(),mob.yBodyRot,slot);
    }
    /** Original ShootTrack Euler angles, in degrees, in the attached weapon's
     * frame. GrabAutoSpawn has an identity child frame and no parent rotation.
     * Its final world basis is orthonormalized by 0x107bbff0 after attachment;
     * position retains the complete Mouth scale/shear, direction does not. */
    public static Vec3 direction(float[][] pose,float yaw,float[] sourceEuler){
        if(sourceEuler==null||sourceEuler.length!=3||!Float.isFinite(yaw))
            throw new IllegalArgumentException("Invalid Greene shot orientation");
        for(float angle:sourceEuler)if(!Float.isFinite(angle))
            throw new IllegalArgumentException("Invalid Greene shot angle");
        var table=GreeneMotion.table();int mouth=Arrays.asList(table.bones).indexOf("native_Mouth");
        if(mouth<0)throw new IllegalStateException("Missing original Greene Mouth joint");
        var world=new Matrix4f().rotateY((float)Math.toRadians(180-yaw)).mul(table.transforms(pose)[mouth]);
        // P1 row-vector basis rows are JOML column-vector basis columns. Match
        // its asymmetric Gram-Schmidt order: X, then X cross old Y, then Z cross X.
        Vec3 axisX=unit(new Vec3(world.m00(),world.m01(),world.m02()));
        Vec3 axisZ=unit(axisX.cross(new Vec3(world.m10(),world.m11(),world.m12())));
        Vec3 axisY=axisZ.cross(axisX);
        double x=Math.toRadians(sourceEuler[0]),y=Math.toRadians(sourceEuler[1]),z=Math.toRadians(sourceEuler[2]);
        double cx=Math.cos(x),sx=Math.sin(x),cy=Math.cos(y),sy=Math.sin(y),cz=Math.cos(z),sz=Math.sin(z);
        // 0x101122e0 negates 0x107bb870's third row. This is not a generic
        // rotationXYZ forward vector. Native Greene already uses runtime X.
        return unit(axisX.scale(-(cz*cx*sy+sz*sx))
            .add(axisY.scale(-(sz*cx*sy-cz*sx))).add(axisZ.scale(-cy*cx)));
    }
    private static Vec3 unit(Vec3 value){
        double length=value.length();
        if(!Double.isFinite(length)||length<1e-12)throw new IllegalStateException("Degenerate Greene Mouth basis");
        return value.scale(1/length);
    }
    private GreeneMouth(){}
}
