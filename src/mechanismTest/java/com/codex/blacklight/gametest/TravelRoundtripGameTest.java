package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.entity.BlacklightHydra;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TravelRoundtripGameTest {
    private static final class Veto {
        final ServerPlayer player;Veto(ServerPlayer player){this.player=player;}
        @SubscribeEvent public void travel(net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event){if(event.getEntity()==player)event.setCanceled(true);}
    }
    private static final class RemoveArrival {
        final ServerPlayer player;final BlacklightHydra endpoint;
        RemoveArrival(ServerPlayer player,BlacklightHydra endpoint){this.player=player;this.endpoint=endpoint;}
        @SubscribeEvent public void changed(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){
            if(event.getEntity()==player&&event.getTo()==Level.NETHER)endpoint.discard();
        }
    }
    @GameTest(template="wall_test",timeoutTicks=2400,batch="travel_actual_roundtrip")
    public static void realCrossDimensionVetoAndRoundtripPreservePlayer(GameTestHelper h)throws Exception{
        var origin=h.getLevel();var target=origin.getServer().getLevel(Level.NETHER);
        h.assertTrue(target!=null&&target!=origin,"fixture uses an actual second server world");
        var p=new net.neoforged.neoforge.common.util.FakePlayer(origin,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"HydraRoundtripQA"));
        // NeoForge cross-world hooks inspect negotiated channel attributes even for
        // FakePlayers. Supply a local QA transport; no socket/client is created.
        var connectionField=net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
        connectionField.setAccessible(true);
        var transport=(net.minecraft.network.Connection)connectionField.get(p.connection);
        var channel=transport.channel()==null?new io.netty.channel.embedded.EmbeddedChannel(transport):null;
        var from=h.absolutePos(new BlockPos(4,20,4));var to=new BlockPos(8,170,8);
        p.moveTo(from.getCenter());origin.addNewPlayer(p);
        // QA fixture preparation, outside production arrival polling. GameTest can
        // advance thousands of virtual ticks before asynchronous terrain generation
        // gets CPU time; prewarm the fixture, then test the real ticket readiness.
        var centre=new net.minecraft.world.level.ChunkPos(to);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)target.getChunk(centre.x+x,centre.z+z);
        var sourceTicket=new HydraDestination();var targetTicket=new HydraDestination();
        var deadline=origin.getGameTime()+1800;
        class Poll implements Runnable{
            BlacklightHydra a,b;boolean done;Object expectedPower;long lastTargetTime=target.getGameTime();
            void cleanup(){if(a!=null)a.discard();if(b!=null)b.discard();sourceTicket.release();targetTicket.release();p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);if(channel!=null)channel.finishAndReleaseAll();}
            void stress(int iteration,boolean returning){
                try{
                    // FakePlayer dimension changes can happen between world ticks.
                    // Keep the otherwise empty QA dimensions awake during stress;
                    // real player and client arrival are verified independently.
                    origin.resetEmptyTime();target.resetEmptyTime();
                    if(iteration==8){
                        System.out.println("HYDRA_STRESS_PASSED extraRoundtrips=8 source="+p.serverLevel().dimension().location());
                        done=true;cleanup();h.succeed();return;
                    }
                    // A recovering gateway must reject instant recapture. Wait for
                    // its real recovery animation instead of resetting production state.
                    var endpoint=returning?a:b;
                    if(!sourceTicket.prepare(origin,from)||!targetTicket.prepare(target,to)||!endpoint.availableForArrival(p)){
                        h.assertTrue(origin.getGameTime()<deadline,"gateway recovery/tickets settle before repeated travel: iteration="+iteration+" returning="+returning+" sourceAction="+a.action()+":"+a.actionTime()+" targetAction="+b.action()+":"+b.actionTime()+" sourceTicket="+sourceTicket.diagnostics()+" targetTicket="+targetTicket.diagnostics());
                        java.util.concurrent.locks.LockSupport.parkNanos(2_000_000);
                        h.runAfterDelay(4,()->stress(iteration,returning));return;
                    }
                    h.assertTrue(CityTravel.transferFromHydra(p,returning?b:a,returning?to:from,1.6F),"repeat handoff "+iteration+" return="+returning);
                    h.assertTrue(p.serverLevel()==(returning?origin:target)&&HydraTransport.carrier(p)==endpoint,"repeat handoff owns correct world/grip");
                    h.assertTrue(p.getData(ModAttachments.PLAYER_STATE)==expectedPower,"repeat handoff preserves the original progression object");
                    endpoint.abortTransport(p);
                    h.runAfterDelay(4,()->stress(iteration+(returning?1:0),!returning));
                }catch(Throwable failure){failure.printStackTrace();cleanup();throw failure instanceof RuntimeException r?r:new RuntimeException(failure);}
            }
            void finishInitialReturn(com.codex.blacklight.state.BlacklightState power){
                try{
                    b.abortTransport(p);
                    h.assertTrue(CityTravel.transferFromHydra(p,b,to,1.6F),"same transaction supports real return travel");
                    h.assertTrue(p.serverLevel()==origin&&HydraTransport.carrier(p)==a&&!p.isPassenger(),"return gateway restores the original world without vanilla riding");
                    h.assertTrue(p.getData(ModAttachments.PLAYER_STATE)==power&&power.isInfected(),"roundtrip must preserve live attachment identity");
                    System.out.println("HYDRA_ACTUAL_ROUNDTRIP outbound="+target.dimension().location()+" return="+origin.dimension().location()+" uuid="+p.getUUID());
                    a.abortTransport(p);expectedPower=power;stress(0,false);
                }catch(Throwable failure){cleanup();throw failure instanceof RuntimeException r?r:new RuntimeException(failure);}
            }
            public void run(){
                try{
                    // NeoForge's GameTest runner advances the primary test world;
                    // keep the actual secondary fixture world/entity storage ticking.
                    if(target.getGameTime()==lastTargetTime)target.tick(()->true);
                    lastTargetTime=target.getGameTime();
                    boolean sourceReady=sourceTicket.prepare(origin,from),targetReady=targetTicket.prepare(target,to);
                    if(!sourceReady||!targetReady){
                        // The accelerated test runner does not wait 50 ms per tick.
                        // Yield bounded wall time to the real asynchronous chunk/IO workers.
                        java.util.concurrent.locks.LockSupport.parkNanos(5_000_000);
                        if(origin.getGameTime()%200==0){
                            var dm=target.getChunkSource().chunkMap.getDistanceManager();
                            System.out.println("HYDRA_READY_POLL "+targetTicket.diagnostics()+" range="+dm.inEntityTickingRange(centre.toLong())+" storageTick="+target.isNaturalSpawningAllowed(to));
                        }
                        h.assertTrue(origin.getGameTime()<deadline,"actual cross-world chunk/entity tickets become ready: "+targetTicket.diagnostics());
                        h.runAfterDelay(2,()->run());return;
                    }
                    for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                        origin.setBlockAndUpdate(from.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                        target.setBlockAndUpdate(to.offset(x,-1,z),Blocks.STONE.defaultBlockState());
                    }
                    a=ModEntities.BLACKLIGHT_HYDRA.get().create(origin);b=ModEntities.BLACKLIGHT_HYDRA.get().create(target);
                    a.moveTo(from.getCenter());b.moveTo(to.getCenter());
                    a.connect(p.getUUID(),new HydraLink(target.dimension(),to,b.getUUID()));
                    b.connect(p.getUUID(),new HydraLink(origin.dimension(),from,a.getUUID()));
                    var wait=BlacklightHydra.class.getDeclaredField("waitForDeparture");wait.setAccessible(true);wait.setBoolean(a,true);wait.setBoolean(b,true);
                    origin.addFreshEntity(a);target.addFreshEntity(b);
                    var power=p.getData(ModAttachments.PLAYER_STATE);power.infect();power.setPrototypeMode(true);
                    var identity=p.getUUID();var previous=new net.minecraft.nbt.CompoundTag();previous.putString("dimension","minecraft:overworld");previous.putDouble("x",99);
                    p.getPersistentData().put("BlacklightCityReturn",previous.copy());
                    var veto=new Veto(p);NeoForge.EVENT_BUS.register(veto);
                    try{h.assertTrue(!CityTravel.transferFromHydra(p,a,from,1.6F),"foreign dimension veto must abort cleanly");}
                    finally{NeoForge.EVENT_BUS.unregister(veto);}
                    h.assertTrue(p.serverLevel()==origin&&!HydraTransport.active(p)&&!b.isRemoved(),"veto preserves source player and existing destination portal");
                    h.assertTrue(p.getPersistentData().getCompound("BlacklightCityReturn").equals(previous),"veto cannot commit return coordinates");
                    var remover=new RemoveArrival(p,b);NeoForge.EVENT_BUS.register(remover);
                    try{h.assertTrue(!CityTravel.transferFromHydra(p,a,from,1.6F),"endpoint removed inside arrival hook rejects and rolls back");}
                    finally{NeoForge.EVENT_BUS.unregister(remover);}
                    h.assertTrue(p.serverLevel()==origin&&!HydraTransport.active(p)&&b.isRemoved(),"failed arrival leaves player free in source world");
                    h.assertTrue(p.getPersistentData().getCompound("BlacklightCityReturn").equals(previous),"removed endpoint cannot rewrite return record");
                    b=ModEntities.BLACKLIGHT_HYDRA.get().create(target);b.moveTo(to.getCenter());
                    b.connect(p.getUUID(),new HydraLink(origin.dimension(),from,a.getUUID()));wait.setBoolean(b,true);target.addFreshEntity(b);
                    a.connect(p.getUUID(),new HydraLink(target.dimension(),to,b.getUUID()));
                    h.assertTrue(CityTravel.transferFromHydra(p,a,from,1.6F),"real outbound handoff succeeds after veto removal");
                    h.assertTrue(p.serverLevel()==target&&HydraTransport.carrier(p)==b&&!p.isPassenger(),"actual second-world gateway owns paired animation");
                    h.assertTrue(p.getData(ModAttachments.PLAYER_STATE)==power&&p.getUUID().equals(identity),"same player and progression survive outbound transfer");
                    // A real traveler stays in the destination cinematic for many ticks.
                    // Yield here too: an empty dimension suspends entity ticks even
                    // while its prepared chunks remain entity-ticking. An instant
                    // same-tick roundtrip never wakes that world's idle counter.
                    h.runAfterDelay(2,()->finishInitialReturn(power));

                }catch(Throwable failure){failure.printStackTrace();cleanup();throw failure instanceof RuntimeException r?r:new RuntimeException(failure);}
            }
        }
        new Poll().run();
    }
}
