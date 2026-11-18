package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.military.aircraft.HelicopterSupport;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class UavReconGameTest {
    private static final class Fixture {
        final GameTestHelper helper;final ServerLevel level;final int x;final Map<Long,Boolean> forced=new LinkedHashMap<>();final List<Entity> actors=new ArrayList<>();
        MilitaryUav drone,second;net.minecraft.world.entity.LivingEntity infected;long start;int calls,mountedAt=-1,stableSamples;Vec3 previousDrone,aircraftBefore;boolean initialized,done,changed;BlackwatchHelicopter aircraft;final Set<UUID> deliveredCrew=new HashSet<>(),handedOff=new HashSet<>();
        Fixture(GameTestHelper h,int index){helper=h;level=h.getLevel();x=440000+index*256;
            for(int cx=(x>>4)-3;cx<=(x>>4)+3;cx++)for(int cz=-3;cz<=3;cz++){long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            // GameTest runs without a wall-clock tick delay. Finish generation before its
            // timeout clock races pending chunk workers; normal entity ticking is still required.
            for(int cx=(x>>4)-4;cx<=(x>>4)+4;cx++)for(int cz=-4;cz<=4;cz++)level.getChunk(cx,cz);
            if(index!=4)for(int dx=-26;dx<=26;dx++)for(int z=-26;z<=26;z++)level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            else for(int dx=-1;dx<=1;dx++)for(int z=4;z<=6;z++)level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
        }
        boolean ready(){
            if(++calls%100==0)com.codex.blacklight.BlacklightNeoMod.LOGGER.info("UAV_FIXTURE x={} calls={} initialized={} age={} centerTicking={} edgeTicking={} drone={} scan={} signal={} support={} aircraft={}",x,calls,initialized,initialized?age():-1,level.isPositionEntityTicking(new BlockPos(x,86,0)),level.isPositionEntityTicking(new BlockPos(x+48,100,0)),drone==null?null:drone.position(),drone==null?-1:drone.scanProgress(),drone==null?-1:drone.signalProgress(),drone==null?-1:drone.supportCooldown(),aircraft().stream().map(c->c.phase()+":"+c.position()+":"+c.getPassengers().size()+":"+c.combat().targetId()).toList());
            if(initialized)return true;
            if(!level.isPositionEntityTicking(new BlockPos(x,86,0))||!level.isPositionEntityTicking(new BlockPos(x+48,100,0))){
                // Accelerated GameTest ticks otherwise starve asynchronous entity-chunk I/O.
                // Yield only during fixture setup; scan/flight assertions use native tick ages.
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);return false;
            }
            infected=ModEntities.COMMON_INFECTED.get().create(level);((Mob)infected).setNoAi(true);((Mob)infected).setPersistenceRequired();infected.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);infected.setHealth(100000);infected.setPos(x,81,5);level.addFreshEntity(infected);actors.add(infected);
            drone=drone(0);start=level.getGameTime();initialized=true;return true;
        }
        MilitaryUav drone(double offset){var d=EncounterEntities.UAV.get().create(level);d.setPersistenceRequired();d.setPos(x+offset,86,0);level.addFreshEntity(d);actors.add(d);return d;}
        int age(){return (int)(level.getGameTime()-start);}
        List<BlackwatchHelicopter> aircraft(){return level.getEntitiesOfClass(BlackwatchHelicopter.class,new AABB(x-96,65,-96,x+96,128,96));}
        void wall(){for(int y=81;y<=104;y++)for(int d=-2;d<=2;d++){level.setBlock(new BlockPos(x-2,y,5+d),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(x+2,y,5+d),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(x+d,y,3),Blocks.STONE.defaultBlockState(),2);level.setBlock(new BlockPos(x+d,y,7),Blocks.STONE.defaultBlockState(),2);}for(int a=-2;a<=2;a++)for(int b=3;b<=7;b++)level.setBlock(new BlockPos(x+a,104,b),Blocks.STONE.defaultBlockState(),2);}
        void close(){done=true;actors.forEach(e->{if(e instanceof net.minecraft.server.level.ServerPlayer p)level.getServer().getPlayerList().remove(p);else e.discard();});for(var e:level.getEntitiesOfClass(Entity.class,new AABB(x-110,60,-110,x+110,140,110),e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();forced.forEach((key,value)->{var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
        void pass(){close();helper.succeed();}
    }
    private static void run(GameTestHelper h,int index,Consumer<Fixture> body){var f=new Fixture(h,index);h.onEachTick(()->{if(f.done)return;try{if(f.ready())body.accept(f);}catch(RuntimeException e){f.close();throw e;}});}
    private static net.minecraft.server.level.ServerPlayer player(Fixture f){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"viral-qa");
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new net.minecraft.server.level.ServerPlayer(f.level.getServer(),f.level,profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        f.level.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        f.actors.add(player);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(f.x,81,5);return player;
    }
    private static net.minecraft.server.level.ServerPlayer infectedPlayer(Fixture f){
        f.infected.discard();var player=player(f);f.infected=player;
        var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);player.setData(ModAttachments.PLAYER_STATE,state);return player;
    }
    private static BlackwatchHelicopter boardHelicopter(Fixture f,net.minecraft.server.level.ServerPlayer player,Vec3 at){
        var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);craft.setPos(at);f.level.addFreshEntity(craft);f.actors.add(craft);
        f.helper.assertTrue(player.startRiding(craft,true),"native player helicopter seat");craft.positionRider(player);return craft;
    }
    @GameTest(template="blackwatch_arena",batch="uavEnclosed",timeoutTicks=450)
    public static void unknownHelicopterOccupantDoesNotEnterSeeingUntilDismount(GameTestHelper h){run(h,8,f->{
        if(!f.changed){var player=infectedPlayer(f);f.aircraft=boardHelicopter(f,player,new Vec3(f.x,94,5));f.changed=true;}
        if(f.age()<120){h.assertTrue(f.drone.scanProgress()==0&&f.drone.warningLamp()==0&&f.drone.signalProgress()==0,"helicopter occupant cannot start viral seeing");h.assertTrue(!HelicopterSupport.request(f.drone,f.infected),"enclosed contact cannot forge a support request");}
        if(f.age()==120){f.infected.stopRiding();f.infected.setPos(f.x,81,5);f.aircraft.discard();}
        if(f.age()==210){h.assertTrue(f.drone.scanTargetId()==f.infected.getId()&&f.drone.scanProgress()==60,"dismounted infected player becomes a normal viral target");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavTank",timeoutTicks=450)
    public static void enteringTankCancelsIncompleteScanWithoutThroughArmorDetection(GameTestHelper h){run(h,9,f->{
        if(!f.changed){infectedPlayer(f);f.changed=true;}
        if(f.mountedAt<0&&f.drone.scanProgress()>=30){
            h.assertTrue(f.drone.scanProgress()<60,"fixture boards before recognition");
            var tank=ModEntities.BLACKWATCH_TANK.get().create(f.level);tank.setPos(f.x,81,5);f.level.addFreshEntity(tank);f.actors.add(tank);
            h.assertTrue(f.infected.startRiding(tank,true),"native tank seat");f.mountedAt=f.age();
        }
        if(f.mountedAt>=0&&f.age()>f.mountedAt+2){h.assertTrue(f.drone.scanProgress()==0&&f.drone.signalProgress()==0&&f.drone.warningLamp()==0,"incomplete scan is cancelled inside the tank");h.assertTrue(!HelicopterSupport.request(f.drone,f.infected),"tank occupant cannot complete an old transmission");}
        if(f.mountedAt>=0&&f.age()==f.mountedAt+100){h.assertTrue(f.aircraft().isEmpty(),"no ground sortie is summoned through armor");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavAirTarget",timeoutTicks=1250)
    public static void recognizedHelicopterUsesWideStandoffAndDropsOccludedContact(GameTestHelper h){run(h,10,f->{
        if(!f.changed){infectedPlayer(f);f.changed=true;}
        if(f.mountedAt<0&&f.drone.scanProgress()==60){f.aircraft=boardHelicopter(f,(net.minecraft.server.level.ServerPlayer)f.infected,new Vec3(f.x+8,91,5));f.mountedAt=f.age();}
        if(f.mountedAt>=0&&f.age()>f.mountedAt+2&&f.age()<f.mountedAt+900){
            h.assertTrue(f.drone.scanTargetId()==f.infected.getId()&&f.drone.warningLamp()==2&&f.drone.scanProgress()==60,"previously recognized contact stays red in the helicopter");
            h.assertTrue(f.drone.signalProgress()==0&&!HelicopterSupport.request(f.drone,f.infected)&&f.aircraft().size()==1,"following a helicopter does not create a ground transport");
            double range=f.drone.position().subtract(f.aircraft.position()).horizontalDistance();
            if((f.age()<f.mountedAt+400||f.age()>f.mountedAt+470)&&range>=19.5&&range<=35.5&&f.previousDrone!=null&&f.drone.position().distanceToSqr(f.previousDrone)<.0001)f.stableSamples++;
            f.previousDrone=f.drone.position();
        }
        if(f.mountedAt>=0&&f.age()==f.mountedAt+400){
            h.assertTrue(f.stableSamples>=10,"scanner arrives and hovers at source helicopter standoff, samples="+f.stableSamples+" drone="+f.drone.position()+" craft="+f.aircraft.position());
            f.aircraftBefore=f.aircraft.position();f.stableSamples=0;
        }
        if(f.mountedAt>=0&&f.age()>=f.mountedAt+400&&f.age()<f.mountedAt+440){
            var player=(net.minecraft.server.level.ServerPlayer)f.infected;
            h.assertTrue(f.aircraft.pilot().accept(player,f.aircraft.pilot().lease(player.getUUID()),f.age(),.2,0,0,0,0),"real driver input advances the observed aircraft");
        }
        if(f.mountedAt>=0&&f.age()==f.mountedAt+470)h.assertTrue(f.aircraft.position().distanceTo(f.aircraftBefore)>3,"target aircraft actually moves through native pilot control");
        if(f.mountedAt>=0&&f.age()==f.mountedAt+900){
            h.assertTrue(f.stableSamples>=10,"scanner settles at the relocated helicopter, hover samples="+f.stableSamples);
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("UAV_AIR_TARGET_VERIFIED aircraftTravel={} postMoveHoverSamples={} scanner={} aircraft={}",f.aircraft.position().distanceTo(f.aircraftBefore),f.stableSamples,f.drone.position(),f.aircraft.position());
            var center=f.aircraft.blockPosition();for(int y=-3;y<=8;y++)for(int d=-5;d<=5;d++){f.level.setBlock(center.offset(-5,y,d),Blocks.STONE.defaultBlockState(),2);f.level.setBlock(center.offset(5,y,d),Blocks.STONE.defaultBlockState(),2);f.level.setBlock(center.offset(d,y,-5),Blocks.STONE.defaultBlockState(),2);f.level.setBlock(center.offset(d,y,5),Blocks.STONE.defaultBlockState(),2);}for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)f.level.setBlock(center.offset(x,8,z),Blocks.STONE.defaultBlockState(),2);
        }
        if(f.mountedAt>=0&&f.age()==f.mountedAt+930){h.assertTrue(f.drone.scanProgress()==0&&f.drone.scanTargetId()==-1&&f.drone.warningLamp()==0,"occluded aircraft does not retain a live recognized tracker");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavViral",timeoutTicks=350)
    public static void sustainedVirusScanExposesDisguiseAndIgnoresNearerHealthyMob(GameTestHelper h){run(h,6,f->{
        if(!f.changed){
            f.infected.discard();var player=player(f);f.infected=player;
            var identity=com.codex.blacklight.disguise.DisguiseIdentity.capture(ModEntities.BLACKWATCH_SOLDIER.get().create(f.level));
            var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.captureIdentity(identity.type());state.setDisguised(true);
            player.setData(ModAttachments.PLAYER_STATE,state);player.setData(ModAttachments.DISGUISE_IDENTITY,identity);
            var healthy=EntityType.PIG.create(f.level);healthy.setNoAi(true);healthy.setPersistenceRequired();healthy.setPos(f.x,81,0);f.level.addFreshEntity(healthy);f.actors.add(healthy);
            h.assertTrue(com.codex.blacklight.disguise.DisguiseSystem.active(player),"valid native Blackwatch disguise");f.changed=true;
        }
        var player=(net.minecraft.server.level.ServerPlayer)f.infected;var observer=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);
        if(f.age()==50)h.assertTrue(com.codex.blacklight.disguise.DisguiseSystem.fooled(observer,player),"incomplete scan does not expose the identity");
        if(f.age()==80){h.assertTrue(f.drone.scanTargetId()==player.getId()&&f.drone.scanProgress()==60,"actual infected player is acquired instead of nearer healthy pig");h.assertTrue(!com.codex.blacklight.disguise.DisguiseSystem.fooled(observer,player)&&player.getPersistentData().getCompound("BlacklightDisguiseAwareness").getInt("Identity")>0,"native scanner reports viral exposure through disguise");h.assertTrue(f.aircraft().isEmpty(),"exposure precedes completed transmission");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavSortie",timeoutTicks=1200)
    public static void confirmedScanCallsOneRealCrewAndHandsTheTargetToAllFourDeliveredTroops(GameTestHelper h){run(h,0,f->{
        if(f.age()<50)h.assertTrue(!HelicopterSupport.request(f.drone,f.infected)&&f.aircraft().isEmpty(),"incomplete scan cannot bypass the request boundary");
        if(f.aircraft==null&&!f.aircraft().isEmpty()){
            f.aircraft=f.aircraft().get(0);h.assertTrue(f.aircraft.getPassengers().size()==6,"two pilots and four finite real troops");
            h.assertTrue(f.aircraft.getPassengers().stream().allMatch(e->e instanceof MilitarySoldier s&&s.hasBattleAmmunition()),"real finite issued inventories");
            f.aircraft.getPassengers().stream().filter(e->e.getPersistentData().getBoolean("HelicopterTroop")).forEach(e->f.deliveredCrew.add(e.getUUID()));
        }
        if(f.aircraft!=null)for(var troop:f.level.getEntitiesOfClass(MilitarySoldier.class,new AABB(f.x-96,65,-96,f.x+96,128,96)))if(f.deliveredCrew.contains(troop.getUUID())&&!troop.isPassenger()&&troop.getTarget()==f.infected)f.handedOff.add(troop.getUUID());
        if(f.handedOff.size()==4||f.age()==750){h.assertTrue(f.aircraft!=null&&f.drone.supportCooldown()>2900,"one completed request starts the saved sortie lease");h.assertTrue(f.handedOff.size()==4,"all four actual unloaded troops acquire the detected target: "+f.handedOff.size());h.assertTrue(f.aircraft().size()<=1,"no recurring duplicate aircraft");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavReservation",timeoutTicks=1200)
    public static void scannerAlreadyAtNewReservationEdgeRetreatsAndAllowsRealTroopDelivery(GameTestHelper h){run(h,7,f->{
        if(f.aircraft==null&&!f.aircraft().isEmpty()){
            f.aircraft=f.aircraft().get(0);var pad=f.aircraft.landing();
            // A newly reserved pad catches a nearby scanner at its margin: it must be
            // able to leave, while retaining the helicopter's full collision checks.
            f.drone.setPos(pad.add(-7.8,3,0));
            f.aircraft.getPassengers().stream().filter(e->e.getPersistentData().getBoolean("HelicopterTroop")).forEach(e->f.deliveredCrew.add(e.getUUID()));
        }
        for(UUID id:f.deliveredCrew)if(f.level.getEntity(id) instanceof MilitarySoldier s&&!s.isPassenger()&&s.getTarget()==f.infected)f.handedOff.add(id);
        if(f.handedOff.size()==4||f.age()==750){h.assertTrue(f.handedOff.size()==4,"scanner retreats from a new reservation and all four troops deploy");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavOcclusion",timeoutTicks=350)
    public static void wallInterruptsTransmissionBeforeAnySupportIsCreated(GameTestHelper h){run(h,1,f->{
        if(!f.changed&&f.drone.signalProgress()>=20){f.wall();f.changed=true;}
        if(f.age()==190){h.assertTrue(f.changed&&!f.drone.hasLineOfSight(f.infected),"fixture interrupts a real acquired transmission");h.assertTrue(f.drone.signalProgress()==0&&f.aircraft().isEmpty(),"wall prevents delayed dispatch");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavDamage",timeoutTicks=350)
    public static void acceptedDamageAndDestructionCancelTheSignal(GameTestHelper h){run(h,2,f->{
        if(!f.changed&&f.drone.signalProgress()>=20){h.assertTrue(f.drone.hurt(f.level.damageSources().generic(),1),"native accepted hit");h.assertTrue(f.drone.signalProgress()==0&&f.drone.scanProgress()==0,"accepted damage cancels both acquisition and transmission");f.changed=true;f.drone.hurt(f.level.damageSources().generic(),10000);}
        if(f.age()==190){h.assertTrue(f.changed&&!f.drone.isAlive()&&f.aircraft().isEmpty(),"destroyed scanner cannot finish a delayed call");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavBudget",timeoutTicks=400)
    public static void simultaneousScannersShareTheExistingLocalAirBudgetAndKeepSavedLeases(GameTestHelper h){run(h,3,f->{
        if(f.second==null)f.second=f.drone(1.5);
        if(f.age()==180){h.assertTrue(f.aircraft().size()==1,"two completed scanners produce one local aircraft");
            var caller=f.drone.supportCooldown()>1000?f.drone:f.second;h.assertTrue(caller.supportCooldown()>3400,"successful scanner has the long sortie lease");
            var tag=new net.minecraft.nbt.CompoundTag();caller.addAdditionalSaveData(tag);var copy=EncounterEntities.UAV.get().create(f.level);copy.readAdditionalSaveData(tag);
            h.assertTrue(copy.supportCooldown()==caller.supportCooldown()&&copy.scanProgress()==0&&copy.signalProgress()==0,"load preserves the support lease but cannot resume a stale signal");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavUnavailable",timeoutTicks=350)
    public static void noSupportedLandingDoesNotSpawnTroopsOrRetryEveryTick(GameTestHelper h){run(h,4,f->{
        if(f.age()==150){h.assertTrue(f.drone.scanProgress()==60&&f.aircraft().isEmpty(),"virus is detected even when support cannot land");
            h.assertTrue(f.drone.supportCooldown()>100&&f.drone.getPersistentData().getString("AirSupportLastResult").equals("no_supported_landing"),"unavailable terrain starts bounded retry delay");
            h.assertTrue(f.level.getEntitiesOfClass(MilitarySoldier.class,new AABB(f.x-96,65,-96,f.x+96,128,96)).isEmpty(),"no unsupported infantry teleport");f.pass();}
    });}
    @GameTest(template="blackwatch_arena",batch="uavDisabled",timeoutTicks=350)
    public static void disabledScannerCannotDispatchAndHealthyTargetsCannotForgeARequest(GameTestHelper h){run(h,5,f->{
        if(!f.changed&&f.drone.signalProgress()>=20){var healthy=EntityType.PIG.create(f.level);healthy.setPos(f.infected.position());h.assertTrue(!HelicopterSupport.request(f.drone,healthy),"healthy target cannot replace the confirmed infection");f.drone.setNoAi(true);f.changed=true;}
        if(f.age()==180){h.assertTrue(f.changed&&f.drone.scanProgress()==0&&f.drone.signalProgress()==0&&f.aircraft().isEmpty(),"disabled recon cannot finish or dispatch");f.pass();}
    });}
}
