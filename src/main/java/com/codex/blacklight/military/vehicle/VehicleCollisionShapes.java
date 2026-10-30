package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One pose and one query cached per parent, shared by its multipart query proxy. */
public final class VehicleCollisionShapes {
    private VehicleCollisionGeometry geometry;
    private float bodyYaw,turretYaw,pitch;
    private double x,y,z;
    private AABB lastQuery;
    private VoxelShape lastShape;
    public VoxelShape query(MilitaryVehicle vehicle,AABB query){
        if(geometry==null||bodyYaw!=vehicle.getYRot()||turretYaw!=vehicle.turretYaw()||pitch!=vehicle.gunPitch()){
            bodyYaw=vehicle.getYRot();turretYaw=vehicle.turretYaw();pitch=vehicle.gunPitch();
            geometry=new VehicleCollisionGeometry(vehicle.definition(),bodyYaw,turretYaw,pitch);lastQuery=null;
        }
        if(query.equals(lastQuery)&&x==vehicle.getX()&&y==vehicle.getY()&&z==vehicle.getZ())return lastShape;
        x=vehicle.getX();y=vehicle.getY();z=vehicle.getZ();lastQuery=query;
        var local=query.move(-x,-y,-z).inflate(1e-7);
        var grid=geometry.rasterize(new VehicleHullShape.Bounds(local.minX,local.minY,local.minZ,local.maxX,local.maxY,local.maxZ));
        lastShape=grid.isEmpty()?Shapes.empty():new ContactGrid(grid).move(x,y,z);
        return lastShape;
    }
    private static final class ContactGrid extends ArrayVoxelShape {
        ContactGrid(VehicleCollisionGeometry.Grid grid){super(voxels(grid),grid.xs(),grid.ys(),grid.zs());}
        private static BitSetDiscreteVoxelShape voxels(VehicleCollisionGeometry.Grid grid){
            int ny=grid.ySize(),nz=grid.zSize();var result=new BitSetDiscreteVoxelShape(grid.xSize(),ny,nz);
            for(int index=grid.filled().nextSetBit(0);index>=0;index=grid.filled().nextSetBit(index+1))
                result.fill(index/(ny*nz),(index/nz)%ny,index%nz);
            return result;
        }
    }
}
