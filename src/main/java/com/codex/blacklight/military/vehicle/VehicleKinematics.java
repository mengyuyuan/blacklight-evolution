package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Loaded-world rigid hull sweeps. No query in this class asks the chunk source to load terrain. */
public final class VehicleKinematics {
    public enum StopReason { NONE, NO_FUEL, BLOCKED, ROTATION_BLOCKED, UNSUPPORTED, WATER, UNKNOWN_CHUNK, INVALID, CLIENT }
    public record MoveResult(double distance,float yawDelta,StopReason reason) {}
    private record Support(double height, StopReason reason) {}
    private static final double EPSILON = 1e-6;
    private static final double MAX_STEP = .25;
    private static final double MAX_RISE = 1;
    private static final double MAX_DROP = 1;

    /** Distance/yaw are bounded requests in the server's chassis coordinate system, never positions. */
    public static MoveResult advance(MilitaryVehicle vehicle,double requestedDistance,float requestedYaw) {
        if (vehicle.level().isClientSide) return result(vehicle,0,0,StopReason.CLIENT);
        if (vehicle.isRemoved() || vehicle.health() <= 0 || !Double.isFinite(requestedDistance) || !Float.isFinite(requestedYaw)) return result(vehicle,0,0,StopReason.INVALID);
        double distance = Math.max(-vehicle.definition().maxSpeed(),Math.min(vehicle.definition().maxSpeed(),requestedDistance));
        float yaw = Math.max(-3,Math.min(3,requestedYaw));
        if (distance == 0 && yaw == 0) { vehicle.setDeltaMovement(Vec3.ZERO); return result(vehicle,0,0,StopReason.NONE); }
        Vec3 before = vehicle.position();
        var shape = VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
        var origin = point(vehicle.position());
        float initialYaw = vehicle.getYRot(), targetYaw = initialYaw+yaw;
        if (yaw != 0) {
            var sweep = shape.sweepRotation(origin,initialYaw,targetYaw);
            for (int i=0;i<sweep.size();i++) {
                StopReason reason = checkEnvelope(vehicle,sweep.get(i));
                if (reason != StopReason.NONE) return result(vehicle,0,0,reason == StopReason.BLOCKED ? StopReason.ROTATION_BLOCKED : reason);
                var support = support(vehicle,vehicle.getX(),vehicle.getY(),vehicle.getZ(),initialYaw+yaw*(i+1)/sweep.size());
                if (support.reason != StopReason.NONE || support.height > vehicle.getY()+EPSILON)
                    return result(vehicle,0,0,support.reason == StopReason.NONE ? StopReason.UNSUPPORTED : support.reason);
            }
            vehicle.setYRot(net.minecraft.util.Mth.wrapDegrees(targetYaw));
        }
        int steps = Math.max(1,(int)Math.ceil(Math.abs(distance)/MAX_STEP));
        double stride = distance/steps, completed=0;
        double radians = Math.toRadians(vehicle.getYRot());
        for (int step=0;step<steps;step++) {
            if (stride == 0) break;
            Vec3 start = vehicle.position();
            double x=start.x+Math.sin(radians)*stride,z=start.z+Math.cos(radians)*stride;
            var support = support(vehicle,x,start.y,z,vehicle.getYRot());
            if (support.reason != StopReason.NONE) { setMotion(vehicle,before); return result(vehicle,completed,yaw,support.reason); }
            Vec3 destination = new Vec3(x,support.height,z);
            // A step rises before forward motion; a descent moves above the edge before lowering.
            Vec3 bend = destination.y > start.y ? new Vec3(start.x,destination.y,start.z) : new Vec3(destination.x,start.y,destination.z);
            StopReason reason = sweep(vehicle,shape,start,bend);
            if (reason == StopReason.NONE) reason = sweep(vehicle,shape,bend,destination);
            if (reason != StopReason.NONE) { setMotion(vehicle,before); return result(vehicle,completed,yaw,reason); }
            vehicle.setPos(destination.x,destination.y,destination.z);completed+=stride;
        }
        for (var passenger:vehicle.getPassengers()) vehicle.positionRider(passenger);
        setMotion(vehicle,before);return result(vehicle,completed,yaw,StopReason.NONE);
    }
    public static boolean placementReady(MilitaryVehicle vehicle){
        if(!(vehicle.level() instanceof ServerLevel))return false;
        var envelope=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch()).bounds(point(vehicle.position()),vehicle.getYRot());
        if(checkEnvelope(vehicle,envelope)!=StopReason.NONE)return false;
        var support=support(vehicle,vehicle.getX(),vehicle.getY(),vehicle.getZ(),vehicle.getYRot());
        return support.reason==StopReason.NONE&&Math.abs(support.height-vehicle.getY())<EPSILON;
    }
    private static void setMotion(MilitaryVehicle vehicle,Vec3 before) { vehicle.setDeltaMovement(vehicle.position().subtract(before)); vehicle.hasImpulse=true; }
    private static MoveResult result(MilitaryVehicle vehicle,double distance,float yaw,StopReason reason) {
        vehicle.setStopReason(reason);vehicle.syncVehicleState();return new MoveResult(distance,yaw,reason);
    }
    private static StopReason sweep(MilitaryVehicle vehicle,VehicleHullShape shape,Vec3 start,Vec3 end) {
        for (var envelope:shape.sweepTranslation(point(start),point(end),vehicle.getYRot())) {
            StopReason reason=checkEnvelope(vehicle,envelope);if(reason!=StopReason.NONE)return reason;
        }
        return StopReason.NONE;
    }
    private static VehicleDefinition.Vec3 point(Vec3 position) { return new VehicleDefinition.Vec3(position.x,position.y,position.z); }
    /** getChunkNow is a non-loading lookup; never replace this with getChunk/heightmap navigation. */
    public static boolean loaded(Level level,AABB box) {
        if (!(level instanceof ServerLevel server)) return false;
        int minX=net.minecraft.util.Mth.floor(box.minX)>>4,maxX=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX))>>4;
        int minZ=net.minecraft.util.Mth.floor(box.minZ)>>4,maxZ=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ))>>4;
        if (maxX-minX>8 || maxZ-minZ>8 || box.minY<level.getMinBuildHeight() || box.maxY>level.getMaxBuildHeight()) return false;
        for (int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) if(server.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    public static StopReason checkEnvelope(MilitaryVehicle vehicle,VehicleHullShape.Bounds envelope) {
        AABB box=new AABB(envelope.minX()+EPSILON,envelope.minY()+EPSILON,envelope.minZ()+EPSILON,envelope.maxX()-EPSILON,envelope.maxY()-EPSILON,envelope.maxZ()-EPSILON);
        if (!loaded(vehicle.level(),box)) return StopReason.UNKNOWN_CHUNK;
        if (!vehicle.level().getWorldBorder().isWithinBounds(box)) return StopReason.BLOCKED;
        // Do not call Level.getBlockCollisions here: its getChunk(FULL,false) may wait
        // for a pending full chunk. Every terrain read stays on getChunkNow instead.
        var view=new LoadedView((ServerLevel)vehicle.level());
        var context=net.minecraft.world.phys.shapes.CollisionContext.of(vehicle);
        for (int x=net.minecraft.util.Mth.floor(box.minX)-1;x<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX))+1;x++)
            for(int y=net.minecraft.util.Mth.floor(box.minY)-1;y<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxY))+1;y++)
                for(int z=net.minecraft.util.Mth.floor(box.minZ)-1;z<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ))+1;z++){
                    var pos=new BlockPos(x,y,z);var state=view.getBlockState(pos);
                    var shape=state.getCollisionShape(view,pos,context);
                    if(view.unknownRead)return StopReason.UNKNOWN_CHUNK;
                    for(var collision:shape.toAabbs())if(collision.move(x,y,z).intersects(box))return StopReason.BLOCKED;
                    if(x>=net.minecraft.util.Mth.floor(box.minX)&&x<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX))
                            &&y>=net.minecraft.util.Mth.floor(box.minY)&&y<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxY))
                            &&z>=net.minecraft.util.Mth.floor(box.minZ)&&z<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ))
                            &&!state.getFluidState().isEmpty())return StopReason.WATER;
                }
        if (!vehicle.level().getEntityCollisions(vehicle,box).isEmpty())return StopReason.BLOCKED;
        return StopReason.NONE;
    }
    /** Nine samples span the actual rotated body footprint; missing one refuses an unsupported edge. */
    private static Support support(MilitaryVehicle vehicle,double x,double currentY,double z,double yaw) {
        var view=new LoadedView((ServerLevel)vehicle.level());
        var definition=vehicle.definition();double halfWidth=definition.width()/2-EPSILON,halfLength=definition.length()/2-EPSILON;
        double radians=Math.toRadians(yaw),c=Math.cos(radians),s=Math.sin(radians),minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
        for(double dx:new double[]{-halfWidth,0,halfWidth})for(double dz:new double[]{-halfLength,0,halfLength}){
            double px=x+dx*c+dz*s,pz=z+dz*c-dx*s;
            AABB column=new AABB(px-.001,currentY-MAX_DROP-.01,pz-.001,px+.001,currentY+MAX_RISE+.01,pz+.001);
            if(!loaded(vehicle.level(),column))return new Support(currentY,StopReason.UNKNOWN_CHUNK);
            double highest=Double.NEGATIVE_INFINITY;
            int bx=net.minecraft.util.Mth.floor(px),bz=net.minecraft.util.Mth.floor(pz);
            for(int by=net.minecraft.util.Mth.floor(currentY-MAX_DROP)-1;by<=net.minecraft.util.Mth.floor(currentY+MAX_RISE);by++){
                var pos=new BlockPos(bx,by,bz);
                if(!view.getFluidState(pos).isEmpty())return new Support(currentY,StopReason.WATER);
                for(AABB local:view.getBlockState(pos).getCollisionShape(view,pos).toAabbs()){
                    double top=by+local.maxY;
                    if(top>currentY+MAX_RISE+EPSILON || top<currentY-MAX_DROP-EPSILON)continue;
                    if(px>=bx+local.minX-EPSILON && px<=bx+local.maxX+EPSILON && pz>=bz+local.minZ-EPSILON && pz<=bz+local.maxZ+EPSILON)highest=Math.max(highest,top);
                }
            }
            if(view.unknownRead)return new Support(currentY,StopReason.UNKNOWN_CHUNK);
            if(!Double.isFinite(highest))return new Support(currentY,StopReason.UNSUPPORTED);
            minimum=Math.min(minimum,highest);maximum=Math.max(maximum,highest);
        }
        if(maximum-minimum>MAX_RISE+EPSILON)return new Support(currentY,StopReason.UNSUPPORTED);
        return new Support(maximum,StopReason.NONE);
    }
    private static final class LoadedView implements net.minecraft.world.level.BlockGetter {
        private final ServerLevel level;boolean unknownRead;
        LoadedView(ServerLevel level){this.level=level;}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos pos){
            if(level.isOutsideBuildHeight(pos))return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null){unknownRead=true;return net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState();}
            return chunk.getBlockState(pos);
        }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){
            var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null){unknownRead=true;return null;}
            return chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        }
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinBuildHeight(){return level.getMinBuildHeight();}
    }
    private VehicleKinematics(){}
}
