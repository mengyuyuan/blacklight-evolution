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
    /** Ground this far under a footprint sample still carries it, so the hull spans a hill. */
    static final double SLOPE_PROBE = 4;
    /** On a one-block-per-block hillside the downhill end of the hull hangs about its own length above ground. */
    static double probe(MilitaryVehicle vehicle){return Math.max(SLOPE_PROBE,Math.ceil(Math.max(vehicle.definition().length(),vehicle.definition().width()))+1);}

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
        // A blocked turn only cancels the turn. Throttle still drives the hull, so a
        // vehicle pressed against a wall can always reverse or slide out while steering.
        StopReason turn = yaw == 0 ? StopReason.NONE : rotate(vehicle,shape,yaw);
        float turned = turn == StopReason.NONE ? yaw : 0;
        if (distance == 0) return result(vehicle,0,turned,turn);
        int steps = Math.max(1,(int)Math.ceil(Math.abs(distance)/MAX_STEP));
        double stride = distance/steps, completed=0;
        double radians = Math.toRadians(vehicle.getYRot());
        for (int step=0;step<steps;step++) {
            if (stride == 0) break;
            Vec3 start = vehicle.position();
            double x=start.x+Math.sin(radians)*stride,z=start.z+Math.cos(radians)*stride;
            var support = support(vehicle,x,start.y,z,vehicle.getYRot());
            if (support.reason != StopReason.NONE) { setMotion(vehicle,before); return result(vehicle,completed,turned,support.reason); }
            Vec3 destination = new Vec3(x,support.height,z);
            // A step rises before forward motion; a descent moves above the edge before lowering.
            Vec3 bend = destination.y > start.y ? new Vec3(start.x,destination.y,start.z) : new Vec3(destination.x,start.y,destination.z);
            StopReason reason = clearTranslation(vehicle,shape,start,bend,vehicle.getYRot());
            if (reason == StopReason.NONE) reason = clearTranslation(vehicle,shape,bend,destination,vehicle.getYRot());
            if (reason != StopReason.NONE) { setMotion(vehicle,before); return result(vehicle,completed,turned,reason); }
            vehicle.setPos(destination.x,destination.y,destination.z);completed+=stride;
        }
        for (var passenger:vehicle.getPassengers()) vehicle.positionRider(passenger);
        // Still report a refused turn so AI drivers treat their manoeuvre as failed.
        setMotion(vehicle,before);return result(vehicle,completed,turned,turn);
    }
    private static StopReason rotate(MilitaryVehicle vehicle,VehicleHullShape shape,float yaw) {
        float initialYaw = vehicle.getYRot(), targetYaw = initialYaw+yaw;
        StopReason reason = clearRotation(vehicle,shape,vehicle.position(),initialYaw,targetYaw);
        if (reason != StopReason.NONE) return reason == StopReason.BLOCKED ? StopReason.ROTATION_BLOCKED : reason;
        int samples = Math.max(1,(int)Math.ceil(shape.radius()*Math.toRadians(Math.abs(yaw))/.125));
        for (int i=1;i<=samples;i++) {
            var support = support(vehicle,vehicle.getX(),vehicle.getY(),vehicle.getZ(),initialYaw+yaw*i/samples);
            if (support.reason != StopReason.NONE || support.height > vehicle.getY()+EPSILON)
                return support.reason == StopReason.NONE ? StopReason.UNSUPPORTED : support.reason;
        }
        vehicle.setYRot(net.minecraft.util.Mth.wrapDegrees(targetYaw));
        return StopReason.NONE;
    }    public static boolean placementReady(MilitaryVehicle vehicle){
        if(!(vehicle.level() instanceof ServerLevel))return false;
        var shape=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
        if(clear(vehicle,pose(shape,vehicle.position(),vehicle.getYRot(),0))!=StopReason.NONE)return false;
        var support=support(vehicle,vehicle.getX(),vehicle.getY(),vehicle.getZ(),vehicle.getYRot());
        return support.reason==StopReason.NONE&&Math.abs(support.height-vehicle.getY())<EPSILON;
    }
    private static void setMotion(MilitaryVehicle vehicle,Vec3 before) { vehicle.setDeltaMovement(vehicle.position().subtract(before)); vehicle.hasImpulse=true; }
    private static MoveResult result(MilitaryVehicle vehicle,double distance,float yaw,StopReason reason) {
        vehicle.setStopReason(reason);vehicle.syncVehicleState();return new MoveResult(distance,yaw,reason);
    }
    /**
     * A hull box rotated by the body yaw. The axis-aligned envelope of a turned hull is much
     * larger than the hull itself: its corners caught steps and kerbs the real hull never
     * touched, so an angled vehicle stuck on any one-block rise. Terrain is tested exactly.
     */
    private record Oriented(double cx,double cz,double ux,double uz,double vx,double vz,
                            double minA,double maxA,double minB,double maxB,double minY,double maxY,boolean terrain) {
        static Oriented of(VehicleHullShape.Bounds local,double x,double y,double z,double yaw,double inflate) {return of(local,x,y,z,yaw,inflate,true);}
        /** terrain=false marks a gun barrel: it passes through blocks (as in the original game) but still meets entities. */
        static Oriented of(VehicleHullShape.Bounds local,double x,double y,double z,double yaw,double inflate,boolean terrain) {
            double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);
            // rotateYaw: local (a,b) -> world (a*c+b*s, b*c-a*s)
            return new Oriented(x,z,c,-s,s,c,local.minX()-inflate+EPSILON,local.maxX()+inflate-EPSILON,
                    local.minZ()-inflate+EPSILON,local.maxZ()+inflate-EPSILON,y+local.minY()+EPSILON,y+local.maxY()-EPSILON,terrain);
        }
        AABB aabb() {
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(double a:new double[]{minA,maxA})for(double b:new double[]{minB,maxB}){
                double wx=cx+a*ux+b*vx,wz=cz+a*uz+b*vz;
                minX=Math.min(minX,wx);maxX=Math.max(maxX,wx);minZ=Math.min(minZ,wz);maxZ=Math.max(maxZ,wz);
            }
            return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
        }
        boolean intersects(AABB box) {
            return box.maxY>minY && box.minY<maxY && overlapsFootprint(box);
        }
        boolean overlapsFootprint(AABB box) {
            double bx=(box.minX+box.maxX)/2-cx,bz=(box.minZ+box.maxZ)/2-cz,hx=(box.maxX-box.minX)/2,hz=(box.maxZ-box.minZ)/2;
            // Separating axes: world X, world Z, hull right (u), hull forward (v).
            for(int axis=0;axis<4;axis++){
                double ax=axis==0?1:axis==1?0:axis==2?ux:vx, az=axis==0?0:axis==1?1:axis==2?uz:vz;
                double centre=bx*ax+bz*az, boxR=hx*Math.abs(ax)+hz*Math.abs(az);
                double pa1=minA*(ux*ax+uz*az),pa2=maxA*(ux*ax+uz*az),pb1=minB*(vx*ax+vz*az),pb2=maxB*(vx*ax+vz*az);
                double lo=Math.min(pa1,pa2)+Math.min(pb1,pb2),hi=Math.max(pa1,pa2)+Math.max(pb1,pb2);
                if(centre+boxR<=lo || centre-boxR>=hi)return false;
            }
            return true;
        }
    }
    private static List<Oriented> pose(VehicleHullShape shape,Vec3 at,double yaw,double inflate) {
        var result=new ArrayList<Oriented>();
        var parts=shape.localParts();
        for(int i=0;i<parts.size();i++)result.add(Oriented.of(parts.get(i),at.x,at.y,at.z,yaw,inflate,i==0));
        return result;
    }
    /** Exact swept volume for a move along a hull axis or vertically (the only moves made). */
    public static StopReason clearTranslation(MilitaryVehicle vehicle,VehicleHullShape shape,Vec3 from,Vec3 to,double yaw) {
        var delta=to.subtract(from);
        if(delta.lengthSqr()>32*32)return StopReason.INVALID;
        double r=Math.toRadians(yaw),c=Math.cos(r),s=Math.sin(r);
        double da=delta.x*c-delta.z*s,db=delta.x*s+delta.z*c;
        var swept=new ArrayList<Oriented>();
        var parts=shape.localParts();
        for(int i=0;i<parts.size();i++){var part=parts.get(i);
            var local=new VehicleHullShape.Bounds(Math.min(part.minX(),part.minX()+da),Math.min(part.minY(),part.minY()+delta.y),Math.min(part.minZ(),part.minZ()+db),
                    Math.max(part.maxX(),part.maxX()+da),Math.max(part.maxY(),part.maxY()+delta.y),Math.max(part.maxZ(),part.maxZ()+db));
            swept.add(Oriented.of(local,from.x,from.y,from.z,yaw,0,i==0));
        }
        return clear(vehicle,swept);
    }
    /** Turning sweep: sampled exact poses, each widened by the largest arc deviation between samples. */
    public static StopReason clearRotation(MilitaryVehicle vehicle,VehicleHullShape shape,Vec3 at,double startYaw,double endYaw) {
        double delta=net.minecraft.util.Mth.wrapDegrees(endYaw-startYaw);
        int samples=Math.max(1,Math.min(720,(int)Math.ceil(shape.radius()*Math.toRadians(Math.abs(delta))/(1D/16))));
        double margin=shape.radius()*Math.toRadians(Math.abs(delta))/samples/2+1e-6;
        var poses=new ArrayList<Oriented>();
        for(int i=0;i<=samples;i++)poses.addAll(pose(shape,at,startYaw+delta*i/samples,i==0?0:margin));
        return clear(vehicle,poses);
    }
    /** Turret/gun articulation against terrain using the oriented hull, not its world envelope. */
    public static StopReason clearArticulation(MilitaryVehicle vehicle,double startTurretYaw,double endTurretYaw,double startPitch,double endPitch) {
        var poses=new ArrayList<Oriented>();
        var locals=VehicleHullShape.sweepArticulationLocal(vehicle.definition(),startTurretYaw,endTurretYaw,startPitch,endPitch);
        for(int i=0;i<locals.size();i++)
            poses.add(Oriented.of(locals.get(i),vehicle.getX(),vehicle.getY(),vehicle.getZ(),vehicle.getYRot(),0,i==0));
        return clear(vehicle,poses);
    }
    private static StopReason clear(MilitaryVehicle vehicle,List<Oriented> shapes) {
        AABB envelope=null,box=null;
        // Blocks and fluids only meet the hull body; barrels skip terrain so an aimed-down gun never wedges the hull.
        for(var shape:shapes){var a=shape.aabb();envelope=envelope==null?a:envelope.minmax(a);if(shape.terrain())box=box==null?a:box.minmax(a);}
        if(envelope==null)return StopReason.NONE;
        if (!vehicle.level().getWorldBorder().isWithinBounds(envelope)) return StopReason.BLOCKED;
        if(box!=null){
        if (!loaded(vehicle.level(),box)) return StopReason.UNKNOWN_CHUNK;
        var view=new LoadedView((ServerLevel)vehicle.level());
        var context=net.minecraft.world.phys.shapes.CollisionContext.of(vehicle);
        for (int x=net.minecraft.util.Mth.floor(box.minX)-1;x<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxX))+1;x++)
            for(int y=net.minecraft.util.Mth.floor(box.minY)-1;y<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxY))+1;y++)
                for(int z=net.minecraft.util.Mth.floor(box.minZ)-1;z<=net.minecraft.util.Mth.floor(Math.nextDown(box.maxZ))+1;z++){
                    var pos=new BlockPos(x,y,z);var state=view.getBlockState(pos);
                    var collisions=state.getCollisionShape(view,pos,context);
                    if(view.unknownRead)return StopReason.UNKNOWN_CHUNK;
                    for(var collision:collisions.toAabbs()){var world=collision.move(x,y,z);if(!world.intersects(box))continue;for(var shape:shapes)if(shape.terrain()&&shape.intersects(world))return StopReason.BLOCKED;}
                    if(!state.getFluidState().isEmpty()){var cell=new AABB(x,y,z,x+1,y+1,z+1);for(var shape:shapes)if(shape.terrain()&&shape.intersects(cell))return StopReason.WATER;}
                }
        }
        for(var entity:vehicle.level().getEntityCollisions(vehicle,envelope))
            for(var collision:entity.toAabbs())for(var shape:shapes)if(shape.intersects(collision))return StopReason.BLOCKED;
        return StopReason.NONE;
    }
    /** Highest block top under the exact rotated body footprint within the climb/probe window. */
    private static double footprintTop(MilitaryVehicle vehicle,LoadedView view,double x,double currentY,double z,double yaw) {
        var body=new VehicleHullShape.Bounds(-vehicle.definition().width()/2,0,-vehicle.definition().length()/2,vehicle.definition().width()/2,1,vehicle.definition().length()/2);
        var foot=Oriented.of(body,x,0,z,yaw,0);var area=foot.aabb();
        double highest=Double.NEGATIVE_INFINITY,probe=probe(vehicle);
        for(int bx=net.minecraft.util.Mth.floor(area.minX);bx<=net.minecraft.util.Mth.floor(Math.nextDown(area.maxX));bx++)
            for(int bz=net.minecraft.util.Mth.floor(area.minZ);bz<=net.minecraft.util.Mth.floor(Math.nextDown(area.maxZ));bz++)
                for(int by=net.minecraft.util.Mth.floor(currentY+MAX_RISE);by>=net.minecraft.util.Mth.floor(currentY-probe)-1&&by+1>highest;by--){
                    var pos=new BlockPos(bx,by,bz);boolean hit=false;
                    for(AABB local:view.getBlockState(pos).getCollisionShape(view,pos).toAabbs()){
                        double top=by+local.maxY;
                        if(top<=highest || top>currentY+MAX_RISE+EPSILON || top<currentY-probe-EPSILON)continue;
                        if(foot.overlapsFootprint(local.move(bx,by,bz))){highest=top;hit=true;}
                    }
                    if(hit)break;
                }
        return highest;
    }    private static VehicleDefinition.Vec3 point(Vec3 position) { return new VehicleDefinition.Vec3(position.x,position.y,position.z); }
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
    /**
     * Ground under a grid of footprint samples (about one per block). The hull rests on its highest
     * contact; other samples count as carried only while each neighbour step stays within one block,
     * so a hillside or stair run carries the hull but a pit or cliff under one end stops it.
     */
    private static Support support(MilitaryVehicle vehicle,double x,double currentY,double z,double yaw) {
        var view=new LoadedView((ServerLevel)vehicle.level());double probe=probe(vehicle);
        var definition=vehicle.definition();double halfWidth=definition.width()/2-EPSILON,halfLength=definition.length()/2-EPSILON;
        int cols=Math.max(3,(int)Math.ceil(definition.width())+1),rows=Math.max(3,(int)Math.ceil(definition.length())+1);
        double radians=Math.toRadians(yaw),c=Math.cos(radians),s=Math.sin(radians);
        double[][] ground=new double[rows][cols];
        for(int r=0;r<rows;r++)for(int col=0;col<cols;col++){
            double dx=-halfWidth+2*halfWidth*col/(cols-1),dz=-halfLength+2*halfLength*r/(rows-1);
            double px=x+dx*c+dz*s,pz=z+dz*c-dx*s;
            // Clamp to the build range: a hull resting near bedrock or the ceiling is still on loaded terrain.
            AABB column=new AABB(px-.001,Math.max(vehicle.level().getMinBuildHeight(),currentY-probe-.01),pz-.001,
                    px+.001,Math.min(vehicle.level().getMaxBuildHeight(),currentY+MAX_RISE+.01),pz+.001);
            if(!loaded(vehicle.level(),column))return new Support(currentY,StopReason.UNKNOWN_CHUNK);
            double highest=Double.NEGATIVE_INFINITY;
            int bx=net.minecraft.util.Mth.floor(px),bz=net.minecraft.util.Mth.floor(pz);
            // Top-down: the first solid contact is the column's ground; water lying above it floods the hull.
            for(int by=net.minecraft.util.Mth.floor(currentY+MAX_RISE);by>=net.minecraft.util.Mth.floor(currentY-probe)-1&&!Double.isFinite(highest);by--){
                var pos=new BlockPos(bx,by,bz);
                var state=view.getBlockState(pos);
                if(!state.getFluidState().isEmpty())return new Support(currentY,StopReason.WATER);
                for(AABB local:state.getCollisionShape(view,pos).toAabbs()){
                    double top=by+local.maxY;
                    if(top>currentY+MAX_RISE+EPSILON || top<currentY-probe-EPSILON)continue;
                    if(px>=bx+local.minX-EPSILON && px<=bx+local.maxX+EPSILON && pz>=bz+local.minZ-EPSILON && pz<=bz+local.maxZ+EPSILON)highest=Math.max(highest,top);
                }
            }
            if(view.unknownRead)return new Support(currentY,StopReason.UNKNOWN_CHUNK);
            ground[r][col]=highest;
        }
        // Neighbouring samples on a turned hull sit diagonally and can straddle two one-block rises.
        double rowStep=2*halfLength/(rows-1),colStep=2*halfWidth/(cols-1);
        double maximum=carriedTop(ground,crossings(rowStep*s,rowStep*c)*MAX_DROP,crossings(colStep*c,colStep*s)*MAX_DROP);
        if(!Double.isFinite(maximum)||maximum<currentY-MAX_DROP-EPSILON)return new Support(currentY,StopReason.UNSUPPORTED);
        // The sample grid can still miss a one-block kerb between points; the hull rests on whatever
        // its full footprint covers, or the exact collision test would wedge it on that kerb.
        double exact=footprintTop(vehicle,view,x,currentY,z,yaw);
        if(view.unknownRead)return new Support(currentY,StopReason.UNKNOWN_CHUNK);
        return new Support(Math.max(maximum,exact),StopReason.NONE);
    }
    /**
     * Height the hull rests on, or -infinity when it is not carried. Starting from the highest sample,
     * ground joins the carried patch through neighbours at most one block apart. Both end rows must
     * touch that patch and at least two thirds of all samples must belong to it.
     */
    /** Block boundaries a segment with this world offset can cross; each may add one block of height. */
    static int crossings(double dx,double dz){return Math.max(1,(int)Math.ceil(Math.abs(dx)-1e-6)+(int)Math.ceil(Math.abs(dz)-1e-6));}
    static double carriedTop(double[][] ground){return carriedTop(ground,MAX_DROP,MAX_DROP);}
    static double carriedTop(double[][] ground,double rowLimit,double colLimit){
        int rows=ground.length,cols=ground[0].length,total=rows*cols,startR=-1,startC=-1;double top=Double.NEGATIVE_INFINITY;
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++)if(ground[r][c]>top){top=ground[r][c];startR=r;startC=c;}
        if(!Double.isFinite(top))return Double.NEGATIVE_INFINITY;
        boolean[][] carried=new boolean[rows][cols];var queue=new ArrayDeque<int[]>();
        carried[startR][startC]=true;queue.add(new int[]{startR,startC});int count=1;
        while(!queue.isEmpty()){
            int[] at=queue.poll();
            for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
                int r=at[0]+d[0],c=at[1]+d[1];
                if(r<0||c<0||r>=rows||c>=cols||carried[r][c]||!Double.isFinite(ground[r][c]))continue;
                if(Math.abs(ground[r][c]-ground[at[0]][at[1]])>(d[0]!=0?rowLimit:colLimit)+EPSILON)continue;
                carried[r][c]=true;count++;queue.add(new int[]{r,c});
            }
        }
        boolean front=false,back=false;
        for(int c=0;c<cols;c++){front|=carried[rows-1][c];back|=carried[0][c];}
        return front&&back&&count*3>=total*2?top:Double.NEGATIVE_INFINITY;
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
