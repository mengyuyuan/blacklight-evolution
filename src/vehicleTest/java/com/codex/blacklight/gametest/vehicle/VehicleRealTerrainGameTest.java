package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** Player-like driving: guns aimed at the ground ahead, steering while climbing, uneven natural ground. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleRealTerrainGameTest {
    private static final int FLOOR=80;
    private static final class Field implements AutoCloseable {
        final ServerLevel level;final int x;final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<MilitaryVehicle> vehicles=new ArrayList<>();
        Field(GameTestHelper h,int x){level=h.getLevel();this.x=x;
            for(int cx=(x-48)>>4;cx<=(x+48)>>4;cx++)for(int cz=-5;cz<=5;cz++){var key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=-44;dx<=44;dx++)for(int z=-44;z<=72;z++){level.setBlock(new BlockPos(x+dx,FLOOR,z),Blocks.STONE.defaultBlockState(),2);for(int y=FLOOR+1;y<FLOOR+16;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState(),2);}
        }
        MilitaryVehicle spawn(Supplier<? extends EntityType<MilitaryVehicle>> type,double dx,double y,double z,float yaw,float pitch){
            var v=type.get().create(level);v.setPos(x+dx,y,z);v.setYRot(yaw);
            var saved=v.saveWithoutId(new CompoundTag());saved.putFloat("VehicleTurretYaw",0);saved.putFloat("VehicleGunPitch",pitch);v.load(saved);v.setPos(x+dx,y,z);v.setYRot(yaw);
            level.addFreshEntity(v);vehicles.add(v);return v;}
        void column(int bx,int bz,int top){for(int y=FLOOR+1;y<=top;y++)level.setBlock(new BlockPos(x+bx,y,bz),Blocks.GRASS_BLOCK.defaultBlockState(),2);}
        void fill(int x0,int x1,int z0,int z1,int top){for(int bx=x0;bx<=x1;bx++)for(int bz=z0;bz<=z1;bz++)column(bx,bz,top);}
        public void close(){vehicles.forEach(MilitaryVehicle::discard);forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    private static String d(MilitaryVehicle v,VehicleKinematics.MoveResult r){return "pos="+v.position()+" yaw="+v.getYRot()+" pitch="+v.gunPitch()+" result="+r;}
    private static VehicleKinematics.MoveResult drive(MilitaryVehicle v,double speed,float steer,int ticks){VehicleKinematics.MoveResult r=null;for(int i=0;i<ticks;i++){r=v.moveVehicle(speed,steer);}return r;}

    private static void stairs(GameTestHelper h,int x,Supplier<? extends EntityType<MilitaryVehicle>> type,float pitch,float yaw,double speed){
        try(var f=new Field(h,x)){
            for(int i=0;i<4;i++)f.fill(-44,44,10+i,10+i,FLOOR+1+i);
            f.fill(-44,44,14,72,FLOOR+4);
            var v=f.spawn(type,0.5,FLOOR+1,-8,yaw,pitch);
            h.assertTrue(VehicleKinematics.placementReady(v),"fixture "+d(v,null));
            var r=drive(v,speed,0,(int)(40/speed));
            h.assertTrue(v.getY()==FLOOR+5,type.get().getDescriptionId()+" pitch "+pitch+" climbs four steps: "+d(v,r));
            r=drive(v,-speed,0,(int)(44/speed));
            h.assertTrue(v.getY()==FLOOR+1,type.get().getDescriptionId()+" pitch "+pitch+" reverses down four steps: "+d(v,r));
        }
    }
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void tankGunDownClimbsStairs(GameTestHelper h){stairs(h,301000,ModEntities.MILITARY_TANK,15,0,.16);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void tankGunDownClimbsStairsAngled(GameTestHelper h){stairs(h,302000,ModEntities.MILITARY_TANK,15,25,.16);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void apcGunDownClimbsStairs(GameTestHelper h){stairs(h,303000,ModEntities.MILITARY_APC,15,0,.2);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void tankGunUpClimbsStairs(GameTestHelper h){stairs(h,304000,ModEntities.MILITARY_TANK,-25,0,.16);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void truckSteersUpAndDownStairs(GameTestHelper h){
        try(var f=new Field(h,305000)){
            for(int i=0;i<4;i++)f.fill(-44,44,10+i,10+i,FLOOR+1+i);
            f.fill(-44,44,14,72,FLOOR+4);
            var v=f.spawn(ModEntities.MILITARY_TRUCK,0.5,FLOOR+1,-8,0,0);
            VehicleKinematics.MoveResult r=null;
            for(int i=0;i<200&&v.getY()<FLOOR+5;i++)r=v.moveVehicle(.28,(i/20)%2==0?3:-3);
            h.assertTrue(v.getY()==FLOOR+5,"truck steering left/right climbs four steps: "+d(v,r));
            for(int i=0;i<260&&v.getY()>FLOOR+1;i++)r=v.moveVehicle(-.28,(i/20)%2==0?3:-3);
            h.assertTrue(v.getY()==FLOOR+1,"truck steering reverses down four steps: "+d(v,r));
        }h.succeed();
    }
    private static void hills(GameTestHelper h,int x,Supplier<? extends EntityType<MilitaryVehicle>> type,double speed,float pitch){
        try(var f=new Field(h,x)){
            // Rolling terrain: per-axis slope stays below one block per block, so rounded neighbours differ by at most one.
            int[][] hgt=new int[89][117];
            for(int i=0;i<89;i++)for(int j=0;j<117;j++)
                hgt[i][j]=FLOOR+5+(int)Math.round(2.6*Math.sin(i*.17+x%7)+2.4*Math.cos(j*.15)+.9*Math.sin((i-j)*.08));
            for(int i=0;i<89;i++)for(int j=0;j<117;j++)f.column(i-44,j-44,hgt[i][j]);
            var v=f.spawn(type,0.5,FLOOR+1,-9.5,0,pitch);int top=0;
            for(int y=FLOOR+12;y>=FLOOR+1;y--){v.setPos(x+.5,y,-9.5);if(VehicleKinematics.placementReady(v)){top=y;break;}}            double travelled=0;int stuck=0;VehicleKinematics.MoveResult r=null;Set<String> stops=new TreeSet<>();
            for(int i=0;i<700;i++){
                float steer=(float)(3*Math.sin(i*.035));
                var before=v.position();r=v.moveVehicle(speed,steer);
                double moved=v.position().subtract(before).horizontalDistance();travelled+=moved;
                if(r.reason()!=VehicleKinematics.StopReason.NONE)stops.add(r.reason().name());
                stuck=moved<1e-4?stuck+1:0;
                h.assertTrue(stuck<40,type.get().getDescriptionId()+grid(v)+" stuck on rolling one-block terrain at tick "+i+" travelled="+travelled+" "+d(v,r)+" stops="+stops);
                if(Math.abs(v.getX()-x)>36||v.getZ()<-36||v.getZ()>64)break;
            }
            h.assertTrue(travelled>20,type.get().getDescriptionId()+" crosses rolling terrain: travelled="+travelled+" "+d(v,r)+" stops="+stops);
        }
    }
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void truckCrossesRollingTerrain(GameTestHelper h){hills(h,306000,ModEntities.MILITARY_TRUCK,.28,0);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void tankCrossesRollingTerrain(GameTestHelper h){hills(h,307000,ModEntities.MILITARY_TANK,.16,10);h.succeed();}
    @GameTest(template="blackwatch_arena",batch="vehicleReal",timeoutTicks=200)
    public static void apcCrossesRollingTerrain(GameTestHelper h){hills(h,308000,ModEntities.MILITARY_APC,.2,10);h.succeed();}
    static String grid(MilitaryVehicle v){
        var def=v.definition();double hw=def.width()/2-1e-6,hl=def.length()/2-1e-6;
        int cols=Math.max(3,(int)Math.ceil(def.width())+1),rows=Math.max(3,(int)Math.ceil(def.length())+1);
        double r=Math.toRadians(v.getYRot()),c=Math.cos(r),s=Math.sin(r);var sb=new StringBuilder(" grid[");
        for(int row=0;row<rows;row++){sb.append(row==0?"":" | ");for(int col=0;col<cols;col++){
            double dx=-hw+2*hw*col/(cols-1),dz=-hl+2*hl*row/(rows-1);double px=v.getX()+dx*c+dz*s,pz=v.getZ()+dz*c-dx*s;
            int bx=net.minecraft.util.Mth.floor(px),bz=net.minecraft.util.Mth.floor(pz);String top="-";
            for(int by=(int)v.getY()+1;by>=(int)v.getY()-12;by--){var st=v.level().getBlockState(new BlockPos(bx,by,bz));if(!st.getCollisionShape(v.level(),new BlockPos(bx,by,bz)).isEmpty()){top=(by+1)+"";break;}}
            sb.append(top).append(col<cols-1?",":"");}}
        return sb.append("]").toString();
    }
    private VehicleRealTerrainGameTest(){}
}