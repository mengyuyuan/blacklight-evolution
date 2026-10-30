package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleKinematics;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryVehicleGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void actualMountedSoldierBodyFacesTheTurningVehicle(GameTestHelper h){
        try(var f=new Fixture(h,179000)){
            var v=f.truck();var soldier=ModEntities.MILITARY_SOLDIER.get().create(f.level);soldier.setNoAi(true);soldier.setAutomaticUnitJoin(false);soldier.setPos(v.position());f.level.addFreshEntity(soldier);f.entities.add(soldier);
            h.assertTrue(soldier.startRiding(v),"actual soldier physically boards before vehicle turns");
            soldier.setYBodyRot(13);v.setYRot(90);v.positionRider(soldier);
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(soldier.yBodyRot+v.getYRot()))<.001,"actual passenger torso follows the custom vehicle heading with native Minecraft yaw conversion");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void independentMechanicalIdentityHealthEmptyInventoryAndSave(GameTestHelper h){
        try(var f=new Fixture(h,180000)){
            double[][] sizes={{2.9,7.5,3.7},{3.3,7,3.3},{4,8,3.4}};int i=0;
            for(var type:List.of(ModEntities.MILITARY_TRUCK.get(),ModEntities.MILITARY_APC.get(),ModEntities.MILITARY_TANK.get(),ModEntities.BLACKWATCH_TRUCK.get(),ModEntities.BLACKWATCH_APC.get(),ModEntities.BLACKWATCH_TANK.get())){
                var v=type.create(f.level);f.entities.add(v);v.setPos(f.x+.5,81,.5);
                var size=sizes[i++%3];
                h.assertTrue(Math.abs(type.getWidth()-size[0])<1e-6&&Math.abs(type.getHeight()-size[2])<1e-6,"installed entity registration retains the literal enlarged width and height: "+type);
                h.assertTrue(v.definition().width()==size[0]&&v.definition().length()==size[1]&&v.definition().height()==size[2],"installed entity uses the literal enlarged body dimensions: "+type);
                h.assertTrue(!(((Entity)v) instanceof LivingEntity),"mechanical chassis must never be a living infection/consume/identity target");
                h.assertTrue(v.health()==v.maxHealth()&&v.fuelTicks()==0,"new empty chassis has real full HP and zero fuel");
                for(var ammo:v.definition().ammunitionCapacities().keySet())h.assertTrue(v.inventory().ammunition(ammo)==0,"empty chassis never spawns free ammunition");
                h.assertTrue(v.hurt(f.level.damageSources().generic(),7)&&v.health()==v.maxHealth()-7,"real damage reduces mechanical HP");
                var saved=v.saveWithoutId(new CompoundTag());var copy=type.create(f.level);copy.load(saved);
                h.assertTrue(copy.health()==v.health()&&copy.fuelTicks()==0&&copy.getUUID().equals(v.getUUID()),"actual Entity serialization preserves chassis UUID, damage and zero legacy fuel");copy.discard();
            }
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void realTruckMovesAlongNineWideRoadAndStopsAtTwoWideDoor(GameTestHelper h){
        try(var f=new Fixture(h,181000)){
            var v=f.truck();assertClearFixture(h,f,v,"nine-wide road");
            for(int i=0;i<24;i++)v.moveVehicle(.28,0);
            h.assertTrue(v.getZ()>6,"actual 7.5-block truck moves on actual nine-wide road");
            h.assertTrue(12-v.getBoundingBox().maxZ>=1,"fixture precondition: the new two-wide door starts at least one block beyond the actual front");
            for(int x=-5;x<=5;x++)if(x<0||x>1)for(int y=81;y<86;y++)f.level.setBlock(new BlockPos(f.x+x,y,12),Blocks.STONE.defaultBlockState(),2);
            assertClearFixture(h,f,v,"two-wide door");
            for(int i=0;i<40;i++)v.moveVehicle(.28,0);
            h.assertTrue(v.getBoundingBox().maxZ<=12.001&&v.getZ()<9,"full long hull cannot pass the actual two-block door");
            h.assertTrue(f.level.noCollision(v,v.getBoundingBox()),"stopped actual truck never penetrates the door wall");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void realTruckClimbsOneStepAndRefusesThreeDeepPit(GameTestHelper h){
        try(var f=new Fixture(h,182000)){
            var v=f.truck();
            h.assertTrue(v.getZ()==.5&&Math.abs(v.getBoundingBox().maxZ-4.25)<1e-9,"fixture precondition: the 7.5-block truck really starts with its front at z=4.25");
            int stepStart=(int)Math.ceil(v.getBoundingBox().maxZ+1),pitStart=stepStart+10;
            h.assertTrue(stepStart==6&&pitStart==16,"fixture precondition: enlarged step and pit retain their independently planned positions");
            for(int x=-5;x<=5;x++)for(int z=stepStart;z<pitStart;z++)f.level.setBlock(new BlockPos(f.x+x,81,z),Blocks.STONE.defaultBlockState(),2);
            assertClearFixture(h,f,v,"one-block step");
            for(int i=0;i<34;i++)v.moveVehicle(.28,0);
            h.assertTrue(v.getY()==82&&v.getZ()>5&&v.getBoundingBox().minZ>=stepStart,"actual truck climbs one block with its whole hull beyond the step front: position="+v.position()+" box="+v.getBoundingBox()+" stop="+v.stopReason());
            h.assertTrue(f.level.noCollision(v,v.getBoundingBox()),"actual climbed truck never clips into the step");
            h.assertTrue(pitStart-v.getBoundingBox().maxZ>=1,"fixture precondition: the pit is cut at least one block beyond the actual climbed front");
            for(int x=-6;x<=6;x++)for(int z=pitStart;z<pitStart+8;z++){
                f.level.setBlock(new BlockPos(f.x+x,78,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=79;y<=81;y++)f.level.setBlock(new BlockPos(f.x+x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            h.assertTrue(v.getY()==82&&f.level.getBlockState(new BlockPos(f.x,78,pitStart)).is(Blocks.STONE)
                &&f.level.getBlockState(new BlockPos(f.x,79,pitStart)).isAir()&&f.level.getBlockState(new BlockPos(f.x,80,pitStart)).isAir()
                &&f.level.getBlockState(new BlockPos(f.x,81,pitStart)).isAir(),"fixture precondition: platform top 82 to actual pit floor top 79 is exactly three blocks");
            assertClearFixture(h,f,v,"three-deep pit approach");
            double approachZ=v.getZ();VehicleKinematics.MoveResult result=null;for(int i=0;i<50;i++)result=v.moveVehicle(.28,0);
            h.assertTrue(v.getZ()>approachZ&&result.reason()==VehicleKinematics.StopReason.UNSUPPORTED&&v.getBoundingBox().maxZ<=pitStart+.001&&v.getY()==82,"three-deep unsupported terrain safely stops the whole actual rigid hull before its edge: result="+result+" position="+v.position()+" box="+v.getBoundingBox());
            h.assertTrue(f.level.noCollision(v,v.getBoundingBox()),"actual truck remains outside the platform and pit walls");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void rotationChecksIntermediateCornerAndPreservesRealPose(GameTestHelper h){
        try(var f=new Fixture(h,183000)){
            var v=f.truck();v.setYRot(0);
            // At x=2,z=2 the initial narrow X footprint and final narrow Z footprint are clear,
            // but the long front-right corner crosses the thin pillar during a 90-degree turn.
            var pillar=new BlockPos(f.x+2,81,2);f.level.setBlock(pillar,Blocks.IRON_BARS.defaultBlockState(),2);
            var post=f.level.getBlockState(pillar).getCollisionShape(f.level,pillar).bounds().move(pillar);
            var end=new AABB(f.x-3.25,81,-.95,f.x+4.25,84.7,1.95);
            h.assertTrue(post.equals(new AABB(f.x+2.4375,81,2.4375,f.x+2.5625,82,2.5625))&&!v.getBoundingBox().intersects(post)&&!end.intersects(post),"fixture precondition: actual thin pillar is clear of both literal enlarged 0/90-degree endpoints");
            assertClearFixture(h,f,v,"intermediate-corner pillar");
            boolean blocked=false;for(int i=0;i<30;i++)if(v.moveVehicle(0,3).reason()==com.codex.blacklight.military.vehicle.VehicleKinematics.StopReason.ROTATION_BLOCKED)blocked=true;
            h.assertTrue(blocked&&v.getYRot()<85,"rotation sweep catches the intermediate long-car corner, not just two clear endpoints");
            h.assertTrue(f.level.noCollision(v,v.getBoundingBox()),"actual entity bounding box remains outside the real pillar");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void actualPassengersSeatsAndOldControlLeaseAreExclusive(GameTestHelper h){
        try(var f=new Fixture(h,184000)){
            var v=f.truck();var first=mockPlayer(h);var second=mockPlayer(h);f.entities.add(first);f.entities.add(second);
            first.setPos(v.getX(),81,v.getZ());second.setPos(v.getX(),81,v.getZ());
            h.assertTrue(first.startRiding(v)&&second.startRiding(v),"two real passengers board physical seats");
            String driver=v.definition().seats().stream().filter(seat->seat.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER).findFirst().orElseThrow().id();
            h.assertTrue(driver.equals(v.seatOf(first.getUUID()))&&!v.switchSeat(second,driver),"a second real player cannot claim an occupied driver seat");
            var lease=v.controlLease(first.getUUID());h.assertTrue(lease!=null,"actual driver receives a server lease");
            h.assertTrue(v.acceptControl(first,lease,0,1,0,0,0,0),"real driver input enters server authority");
            h.assertTrue(!v.acceptControl(first,lease,0,1,0,0,0,0),"duplicate sequence is rejected");
            first.stopRiding();h.assertTrue(v.controlLease(first.getUUID())==null&&!v.acceptControl(first,lease,1,1,0,0,0,0),"dismount immediately revokes the actual old lease");
            h.assertTrue(v.switchSeat(second,driver),"vacated driver seat can be atomically assigned to actual other passenger");
            h.assertTrue(!lease.equals(v.controlLease(second.getUUID())),"new driver receives a new control lease");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void noActualDriverAndDisabledSoldierNeverMoveVehicle(GameTestHelper h){
        try(var f=new Fixture(h,185000)){
            var v=f.truck();var start=v.position();v.tick();
            h.assertTrue(start.equals(v.position()),"a chassis without actual driver stays parked with fuel disabled");
            var soldier=ModEntities.MILITARY_SOLDIER.get().create(f.level);soldier.setAutomaticUnitJoin(false);soldier.setNoAi(true);soldier.setPos(v.getX(),81,v.getZ());f.level.addFreshEntity(soldier);f.entities.add(soldier);
            h.assertTrue(soldier.startRiding(v)&&!MilitaryVehicle.crewOperational(soldier),"NoAI is a real disabled crew state, independent of riding disabling infantry tactics");
            v.tick();h.assertTrue(start.equals(v.position()),"disabled real driver cannot move a vehicle or create an invisible replacement");
            var saved=v.saveWithoutId(new CompoundTag());var copy=ModEntities.MILITARY_TRUCK.get().create(f.level);copy.load(saved);f.entities.add(copy);
            h.assertTrue(copy.getPassengers().isEmpty()&&copy.controlLease(soldier.getUUID())==null,"loading vehicle identity alone never spawns duplicate crew or restores a missing control lease");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void heldNextSeatDoesNotRepeatWhenActualPlayerReceivesNewLease(GameTestHelper h){
        try(var f=new Fixture(h,188000)){
            var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);
            var player=mockPlayer(h);f.entities.add(player);player.setPos(v.getX(),81,v.getZ());
            h.assertTrue(player.startRiding(v)&&"driver".equals(v.seatOf(player.getUUID())),"actual APC player starts in driver seat");
            var old=v.controlLease(player.getUUID());h.assertTrue(v.acceptControl(player,old,0,0,0,0,0,8),"first NEXT_SEAT edge is accepted through real control authority");
            var next=v.controlLease(player.getUUID());h.assertTrue("gunner".equals(v.seatOf(player.getUUID()))&&next!=null&&!next.equals(old),"actual player switches once to gunner and receives a new lease");
            h.assertTrue(v.acceptControl(player,next,1,0,0,0,0,8),"held bit in a new sequence is a valid authenticated input");
            h.assertTrue("gunner".equals(v.seatOf(player.getUUID())),"holding NEXT_SEAT across the same real passenger's new lease must not repeat the seat transition");
            h.assertTrue(v.acceptControl(player,next,2,0,0,0,0,0)&&v.acceptControl(player,next,3,0,0,0,0,8),"release and a new edge are accepted");
            h.assertTrue(!"gunner".equals(v.seatOf(player.getUUID())),"a later real edge may advance to the next free actual seat");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void actualSevenWideGatePassesAndWaterEdgeStopsHull(GameTestHelper h){
        try(var f=new Fixture(h,186000)){
            var v=f.truck();
            h.assertTrue(7-v.getBoundingBox().maxZ>=1,"fixture precondition: seven-wide gate starts beyond the actual enlarged front");
            for(int x=-5;x<=5;x++)if(x< -3||x>3)for(int y=81;y<86;y++)f.level.setBlock(new BlockPos(f.x+x,y,7),Blocks.STONE.defaultBlockState(),2);
            assertClearFixture(h,f,v,"seven-wide gate");
            for(int i=0;i<40;i++)v.moveVehicle(.28,0);
            h.assertTrue(v.getZ()>10,"actual truck passes seven-wide base gate on a nine-wide road");
            h.assertTrue(17-v.getBoundingBox().maxZ>=1,"fixture precondition: water is added at least one block beyond the actual moved front");
            for(int x=-5;x<=5;x++)for(int z=17;z<=20;z++)f.level.setBlock(new BlockPos(f.x+x,81,z),Blocks.WATER.defaultBlockState(),2);
            assertClearFixture(h,f,v,"water-edge approach");
            VehicleKinematics.MoveResult result=null;for(int i=0;i<40;i++)result=v.moveVehicle(.28,0);
            h.assertTrue(v.getBoundingBox().maxZ<=17.001&&result.reason()==VehicleKinematics.StopReason.WATER,"actual full hull stops before water and does not claim amphibious support: "+result);
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void actualUnknownNeighborStopsWithoutLoadingAnyChunk(GameTestHelper h){
        try(var f=new Fixture(h,187000)){
            int unknown=3;while(unknown<32&&f.level.getChunkSource().getChunkNow(f.x>>4,unknown)!=null)unknown++;
            h.assertTrue(unknown<32,"fixture has an actually unknown neighboring full chunk");int edge=unknown*16;
            for(int dx=-4;dx<=4;dx++)for(int z=edge-10;z<edge;z++){
                h.assertTrue(f.level.getChunkSource().getChunkNow((f.x+dx)>>4,z>>4)!=null,"fixture only edits already loaded cells");
                var known=f.level.getChunkSource().getChunkNow((f.x+dx)>>4,z>>4);
                known.setBlockState(new BlockPos(f.x+dx,80,z),Blocks.STONE.defaultBlockState(),false);for(int y=81;y<87;y++)known.setBlockState(new BlockPos(f.x+dx,y,z),Blocks.AIR.defaultBlockState(),false);
            }
            h.assertTrue(f.level.getChunkSource().getChunkNow(f.x>>4,unknown)==null,"fixture edits preserve the actually unknown neighboring full chunk");
            var v=f.truck();int edgeChunks=f.level.getChunkSource().getLoadedChunksCount();
            var grazing=new com.codex.blacklight.military.vehicle.VehicleHullShape.Bounds(f.x-.25,81,edge-1,f.x+.25,82,edge+.0000015);
            var grazingResult=com.codex.blacklight.military.vehicle.VehicleKinematics.checkEnvelope(v,grazing);
            h.assertTrue(grazingResult==com.codex.blacklight.military.vehicle.VehicleKinematics.StopReason.UNKNOWN_CHUNK&&f.level.getChunkSource().getChunkNow(f.x>>4,unknown)==null&&f.level.getChunkSource().getLoadedChunksCount()==edgeChunks,
                "edge preflight must cover exactly the cells it will query without loading: result="+grazingResult+" unknown="+(f.level.getChunkSource().getChunkNow(f.x>>4,unknown)==null)+" chunksBefore="+edgeChunks+" chunksAfter="+f.level.getChunkSource().getLoadedChunksCount());
            v.setPos(f.x+.5,81,edge-6);
            h.assertTrue(Math.abs(v.getBoundingBox().minZ-(edge-9.75))<1e-9&&Math.abs(v.getBoundingBox().maxZ-(edge-2.25))<1e-9,"fixture precondition: the enlarged hull starts wholly on the ten-block known approach with query padding");
            assertClearFixture(h,f,v,"unknown-neighbor approach");double startZ=v.getZ();int chunks=f.level.getChunkSource().getLoadedChunksCount();
            com.codex.blacklight.military.vehicle.VehicleKinematics.MoveResult result=null;for(int i=0;i<12;i++)result=v.moveVehicle(.28,0);
            h.assertTrue(result.reason()==com.codex.blacklight.military.vehicle.VehicleKinematics.StopReason.UNKNOWN_CHUNK,"unknown actual neighbor is reported as a conservative stop: result="+result+" position="+v.position()+" box="+v.getBoundingBox()+" edge="+edge+" unknown="+(f.level.getChunkSource().getChunkNow(f.x>>4,unknown)==null)+" chunksBefore="+chunks+" chunksAfter="+f.level.getChunkSource().getLoadedChunksCount());
            h.assertTrue(v.getZ()>startZ&&v.getBoundingBox().maxZ<edge&&f.level.getChunkSource().getLoadedChunksCount()==chunks&&f.level.getChunkSource().getChunkNow(f.x>>4,unknown)==null,"actual hull first moves on known support, remains in loaded cells and never loads its unknown neighbor");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void prototypePlayerRetainsRealDrivingAuthority(GameTestHelper h){
        try(var f=new Fixture(h,188000)){
            var v=f.truck();var player=mockPlayer(h);f.entities.add(player);player.setPos(f.x+.5,81,-1);
            var state=com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE,state);
            h.assertTrue(player.startRiding(v),"actual prototype player can enter the empty driving seat");
            h.assertTrue(com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(player),"fixture really has the protagonist infection identity");
            h.assertTrue(MilitaryVehicle.crewOperational(player),"prototype identity grants powers and must not disable player driving like an infected NPC");
            var lease=v.controlLease(player.getUUID());
            h.assertTrue(lease!=null&&v.acceptControl(player,lease,0,1,0,0,0,0),"actual protagonist receives a usable driving lease");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void actualLongHullRemainsQueryableAcrossEntitySections(GameTestHelper h){
        var f=new Fixture(h,189008);var v=f.truck();v.setPos(f.x+15,81,15);
        h.runAfterDelay(20,()->{try(f){
            h.assertTrue(f.level.getEntity(v.getUUID())==v,"query fixture must have a genuinely tracked parent before testing section lookup");
            var probe=new net.minecraft.world.phys.AABB(f.x+14.9,81,18.02,f.x+15.1,82,18.08);
            h.assertTrue(v.getBoundingBox().intersects(probe),"real long truck hull reaches beyond its center section");
            h.assertTrue(f.level.getEntities((Entity)null,probe,entity->entity.is(v)).stream().anyMatch(entity->entity.is(v)),"native narrow query must see the actual long vehicle parent or native part across the section boundary");
            h.assertTrue(!f.level.getEntityCollisions(null,probe).isEmpty(),"native collision query must see the same long mechanical hull");
        }h.succeed();});
    }
    private static void assertClearFixture(GameTestHelper h,Fixture f,MilitaryVehicle v,String scene){
        h.assertTrue(VehicleKinematics.placementReady(v),"fixture precondition: "+scene+" must start clear on loaded support; position="+v.position()+" box="+v.getBoundingBox());
    }
    static net.minecraft.server.level.ServerPlayer mockPlayer(GameTestHelper h){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"vehicle-qa");
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.minecraftforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        return player;
    }
    static final class Fixture implements AutoCloseable {
        final ServerLevel level;final int x;final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<Entity> entities=new ArrayList<>();
        Fixture(GameTestHelper h,int x){this.level=h.getLevel();this.x=x;
            for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=-2;cz<=2;cz++){var key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(int dx=-10;dx<=10;dx++)for(int z=-10;z<=24;z++){level.setBlock(new BlockPos(x+dx,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<87;y++)level.setBlock(new BlockPos(x+dx,y,z),Blocks.AIR.defaultBlockState(),2);}
        }
        MilitaryVehicle truck(){var v=ModEntities.MILITARY_TRUCK.get().create(level);v.setPos(x+.5,81,.5);level.addFreshEntity(v);entities.add(v);return v;}
        public void close(){entities.forEach(entity->{if(entity instanceof net.minecraft.server.level.ServerPlayer player)level.getServer().getPlayerList().remove(player);else entity.discard();});forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    private MilitaryVehicleGameTest(){}
}
