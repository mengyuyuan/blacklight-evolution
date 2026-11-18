package com.codex.blacklight.client.model;

import com.codex.blacklight.military.vehicle.VehicleDestruction;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

/** Detached authored bones, with a bounded arc and a settled pose. Cosmetic only. */
public final class VehicleWreckPose {
    public static void fragment(ModelPart part,float age,Vec3 velocity,float floor,float roll){
        var move=VehicleDestruction.fragment(age,velocity,floor);
        part.x+=(float)move.x*16;part.y+=(float)move.y*16;part.z+=(float)move.z*16;
        part.zRot+=roll*Math.min(1,Math.max(0,age)/25);
        // Include the actual rotated child cubes when resting, so long rotor tips do not sink below the support plane.
        double[] lowest={Double.POSITIVE_INFINITY};
        part.visit(new com.mojang.blaze3d.vertex.PoseStack(),(pose,path,index,cube)->{
            for(float x:new float[]{cube.minX,cube.maxX})for(float y:new float[]{cube.minY,cube.maxY})for(float z:new float[]{cube.minZ,cube.maxZ})
                lowest[0]=Math.min(lowest[0],pose.pose().transformPosition(new org.joml.Vector3f(x/16,y/16,z/16)).y);
        });
        if(lowest[0]<.025)part.y+=(float)(.025-lowest[0])*16;
    }
    private VehicleWreckPose(){}
}
