package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Real hulls driving over one-block steps and stair runs at arbitrary headings. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleStepTerrainGameTest {
    private static final int FLOOR=80;

    private static final class Field implements AutoCloseable {
        final ServerLevel level;final int x;final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<MilitaryVehicle> vehicles=new ArrayList<>();
        Field(GameTestHelper h,int x){level=h.getLevel();this.x=x;
            for(int cx=(x-40)>>4;cx<=(x+40)>>4;cx++)for(int cz=-4;cz<=4;cz++){var key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=-36;dx<=36;dx++)for(int z=-36;z<=60;z++){level.setBlock(new BlockPos(x+dx,FLOOR,z),Blocks.STONE.defaultBlockState(),2);for(int y=FLOOR+1;y<FLOOR+14;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState(),2);}
        }
        MilitaryVehicle spawn(Supplier<? extends EntityType<MilitaryVehicle>> type,double dx,double z,float yaw){
            var v=type.get().create(level);v.setPos(x+dx,FLOOR+1,z);v.setYRot(yaw);level.addFreshEntity(v);vehicles.add(v);return v;}
        void fill(int x0,int x1,int z0,int z1,int top){for(int bx=x0;bx<=x1;bx++)for(int bz=z0;bz<=z1;bz++)for(int y=FLOOR+1;y<=top;y++)level.setBlock(new BlockPos(x+bx,y,bz),Blocks.STONE.defaultBlockState(),2);}
        public void close(){vehicles.forEach(MilitaryVehicle::discard);forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    private static String describe(MilitaryVehicle v,VehicleKinematics.MoveResult r){return "pos="+v.position()+" yaw="+v.getYRot()+" result="+r;}
    private static VehicleKinematics.MoveResult drive(MilitaryVehicle v,double speed,int ticks){VehicleKinematics.MoveResult r=null;for(int i=0;i<ticks;i++)r=v.moveVehicle(speed,0);return r;}

    @GameTest(template="blackwatch_arena",batch="vehicleStep",timeoutTicks=200)
    public static void truckClimbsAndDescendsOneBlockStepAtAnAngle(GameTestHelper h){
        try(var f=new Field(h,191000)){
            // A whole half-plane rises one block; the truck meets its edge at 30 degrees.
            f.fill(-36,36,10,60,FLOOR+1);
            var v=f.spawn(ModEntities.MILITARY_TRUCK,0.5,-6,30);
            h.assertTrue(VehicleKinematics.placementReady(v),"fixture: truck starts clear "+v.position());
            var r=drive(v,.28,120);
            h.assertTrue(v.getY()==FLOOR+2,"angled truck climbs onto a one-block step: "+describe(v,r));
            r=drive(v,-.28,150);
            h.assertTrue(v.getY()==FLOOR+1&&v.getZ()<8,"angled truck reverses back down the one-block step: "+describe(v,r));
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicleStep",timeoutTicks=200)
    public static void truckDrivesUpAndDownAStairRun(GameTestHelper h){
        try(var f=new Field(h,192000)){
            // Four consecutive one-block risers, each one block deep, then a plateau.
            for(int i=0;i<4;i++)f.fill(-36,36,8+i,8+i,FLOOR+1+i);
            f.fill(-36,36,12,60,FLOOR+4);
            var v=f.spawn(ModEntities.MILITARY_TRUCK,0.5,-4,0);
            var r=drive(v,.28,140);
            h.assertTrue(v.getY()==FLOOR+5,"truck climbs four consecutive one-block steps: "+describe(v,r));
            r=drive(v,-.28,160);
            h.assertTrue(v.getY()==FLOOR+1,"truck reverses down four consecutive one-block steps: "+describe(v,r));
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicleStep",timeoutTicks=200)
    public static void tankDrivesUpAndDownAStairRunAtAnAngle(GameTestHelper h){
        try(var f=new Field(h,193000)){
            for(int i=0;i<4;i++)f.fill(-36,36,10+i,10+i,FLOOR+1+i);
            f.fill(-36,36,14,60,FLOOR+4);
            var v=f.spawn(ModEntities.MILITARY_TANK,0.5,-6,20);
            var r=drive(v,.16,260);
            h.assertTrue(v.getY()==FLOOR+5,"angled tank climbs four consecutive one-block steps: "+describe(v,r));
            r=drive(v,-.16,280);
            h.assertTrue(v.getY()==FLOOR+1,"angled tank reverses down four consecutive one-block steps: "+describe(v,r));
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicleStep",timeoutTicks=200)
    public static void apcDrivesOffAndBackOntoALedgeAtAnAngle(GameTestHelper h){
        try(var f=new Field(h,194000)){
            // Starts on a raised plateau and drives down its one-block edge diagonally.
            f.fill(-36,36,-36,6,FLOOR+1);
            var v=f.spawn(ModEntities.MILITARY_APC,0.5,-6,-35);
            v.setPos(v.getX(),FLOOR+2,v.getZ());
            h.assertTrue(VehicleKinematics.placementReady(v),"fixture: apc starts clear on the plateau "+v.position());
            var r=drive(v,.2,170);
            h.assertTrue(v.getY()==FLOOR+1,"angled apc drives down off a one-block ledge: "+describe(v,r));
            r=drive(v,-.2,190);
            h.assertTrue(v.getY()==FLOOR+2,"angled apc reverses back up the same ledge: "+describe(v,r));
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicleStep",timeoutTicks=200)
    public static void truckClimbsANarrowOneBlockBumpUnderItsMiddle(GameTestHelper h){
        try(var f=new Field(h,195000)){
            // A one-block-wide kerb narrower than the footprint sample spacing.
            f.fill(1,1,6,6,FLOOR+1);
            var v=f.spawn(ModEntities.MILITARY_TRUCK,0.5,-6,0);
            var r=drive(v,.28,90);
            h.assertTrue(v.getZ()>14,"truck drives over a narrow one-block kerb instead of sticking on it: "+describe(v,r));
            h.assertTrue(f.level.noCollision(v,v.getBoundingBox().deflate(.01))||v.getY()>=FLOOR+1,"truck never clips into the kerb");
        }h.succeed();
    }
    private VehicleStepTerrainGameTest(){}
}