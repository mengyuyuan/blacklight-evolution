package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterPilotGameTest {
    static ServerPlayer player(GameTestHelper h,Fixture f,boolean prototype){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"air-qa");
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        f.entities.add(player);player.setPos(f.x-4,81,2.1);
        if(prototype){var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);player.setData(ModAttachments.PLAYER_STATE,state);}
        return player;
    }
    static final class Fixture implements AutoCloseable {
        final ServerLevel level;final int x;final List<Entity> entities=new ArrayList<>();final Map<Long,Boolean> forced=new HashMap<>();
        Fixture(GameTestHelper h,int x){this.level=h.getLevel();this.x=x;
            for(int cx=(x-32)>>4;cx<=(x+32)>>4;cx++)for(int cz=-2;cz<=2;cz++){long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
            for(var old:level.getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(x-24,75,-24,x+24,115,28),e->!(e instanceof net.minecraft.world.entity.player.Player)))old.discard();
            h.runAfterDelay(20,()->{for(var old:level.getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(x-24,75,-24,x+24,115,28),e->!(e instanceof net.minecraft.world.entity.player.Player)&& !entities.contains(e) && !(e instanceof net.neoforged.neoforge.entity.PartEntity<?> part && entities.contains(part.getParent()))))old.discard();});
            for(int dx=-20;dx<=20;dx++)for(int z=-20;z<=24;z++){
                level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                for(int y=81;y<=110;y++){var pos=new BlockPos(x+dx,y,z);if(!level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);}
            }
        }
        BlackwatchHelicopter craft(double height){var c=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);c.setPos(x,81+height,0);level.addFreshEntity(c);entities.add(c);return c;}
        List<BlackwatchSoldier> crew(BlackwatchHelicopter craft){
            var members=new ArrayList<BlackwatchSoldier>();for(int i=0;i<6;i++){
                var soldier=ModEntities.BLACKWATCH_SOLDIER.get().create(level);soldier.setPos(craft.position());soldier.setNoAi(true);soldier.setAutomaticUnitJoin(false);soldier.setPersistenceRequired();
                soldier.initializeNativeLoadout("pilot-test:"+i);soldier.getPersistentData().putBoolean("HelicopterTroop",i>=2);level.addFreshEntity(soldier);entities.add(soldier);
                if(!soldier.startRiding(craft,true))throw new IllegalStateException("crew boarding");members.add(soldier);
            }return members;
        }
        public void close(){for(var entity:entities)if(entity instanceof ServerPlayer p)level.getServer().getPlayerList().remove(p);else entity.discard();forced.forEach((key,value)->{var c=new net.minecraft.world.level.ChunkPos(key);level.setChunkForced(c.x,c.z,value);});}
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=300)
    public static void realPilotAscendsTurnsStopsOnStaleInputAndLands(GameTestHelper h){
        var f=new Fixture(h,251000);var craft=f.craft(0);var p=player(h,f,false);h.assertTrue(p.startRiding(craft,true),"actual player boards");
        int[] time={0};long[] seq={0};double[] high={81};
        h.onEachTick(()->{int t=++time[0];high[0]=Math.max(high[0],craft.getY());
            if(t>=90&&t<120&&t%2==0)h.assertTrue(craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),seq[0]++,0,0,0,0,VehicleControlAuthority.ASCEND),"lift accepted");
            if(t>=120&&t<132&&t%2==0)craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),seq[0]++,1,.3,0,0,0);
            if(t==155){h.assertTrue(craft.getDeltaMovement().length()<.01,"expired input brakes all axes");h.assertTrue(Math.abs(craft.getYRot())>2,"server steering rotates the same aircraft");}
            if(t>=165&&t<245&&t%2==0)craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),seq[0]++,0,0,0,0,VehicleControlAuthority.DESCEND);
        });
        h.runAfterDelay(250,()->{try(f){
            h.assertTrue(high[0]>86,"actual bounded input lifts the aircraft");h.assertTrue(Math.abs(craft.getY()-81)<.01&&craft.safeLanding(craft.position()),"descending reaches supported ground, without hovering or penetrating: "+craft.getY());
            h.assertTrue(craft.getControllingPassenger()==null,"vanilla position authority remains disabled");p.stopRiding();h.assertTrue(craft.pilot().lease(p.getUUID())==null,"dismount revokes control");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=190)
    public static void wholeHullStopsAtWallWhileManualInputContinues(GameTestHelper h){
        var f=new Fixture(h,252000);var craft=f.craft(0);var p=player(h,f,false);p.startRiding(craft,true);
        for(int x=-12;x<=12;x++)for(int y=81;y<=92;y++)f.level.setBlock(new BlockPos(f.x+x,y,10),Blocks.STONE.defaultBlockState(),2);
        int[] t={0};long[] sequence={0};h.onEachTick(()->{if(++t[0]>90&&t[0]%2==0)craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),sequence[0]++,1,0,0,0,0);});
        h.runAfterDelay(160,()->{try(f){h.assertTrue(craft.getZ()>2.8&&craft.getZ()<3.002,"rotor envelope reaches wall without tunneling: "+craft.getZ());h.assertTrue(craft.clearAt(craft.position(),craft.getYRot()),"full hull and rotor remain clear");}h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=100)
    public static void connectionSeatReplayAndNonfiniteInputsAreRejected(GameTestHelper h){
        try(var f=new Fixture(h,253000)){
            var craft=f.craft(0);var p=player(h,f,false);var other=player(h,f,false);p.startRiding(craft,true);UUID lease=craft.pilot().lease(p.getUUID());
            h.assertTrue(!craft.pilot().accept(other,lease,0,1,0,0,0,32),"another actual connection cannot reuse token");
            h.assertTrue(!craft.pilot().accept(p,lease,0,Double.NaN,0,0,0,0),"nonfinite movement rejected");
            h.assertTrue(craft.pilot().accept(p,lease,0,0,0,0,0,32),"actual driver lift accepted");h.assertTrue(!craft.pilot().accept(p,lease,0,0,0,0,0,32),"replayed sequence rejected");
            h.assertTrue(craft.pilot().switchSeat(p)&&craft.pilot().slot(p.getUUID())==1,"actual switch selects copilot");
            h.assertTrue(!craft.pilot().accept(p,lease,1,0,0,0,0,0),"old seat token revoked");UUID gunner=craft.pilot().lease(p.getUUID());
            h.assertTrue(!craft.pilot().accept(p,gunner,0,0,0,0,0,32),"copilot cannot alter lift");h.assertTrue(craft.pilot().accept(p,gunner,0,0,0,0,0,1),"copilot may fire");
        }h.succeed();
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=200)
    public static void actualManualShotsUseFiniteStoresAndPersistWithoutLeases(GameTestHelper h){
        var f=new Fixture(h,254000);var craft=f.craft(0);var p=player(h,f,false);p.startRiding(craft,true);craft.hurt(f.level.damageSources().generic(),23);
        var target=net.minecraft.world.entity.EntityType.COW.create(f.level);target.setPos(f.x,81,15);target.setNoAi(true);f.level.addFreshEntity(target);f.entities.add(target);
        int[] t={0};long[] sequence={0};h.onEachTick(()->{int now=++t[0];if(now>=100&&now<110&&now%2==0)craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),sequence[0]++,0,0,0,0,3);});
        h.runAfterDelay(122,()->{try(f){
            h.assertTrue(craft.combat().rounds()<240&&craft.combat().rockets()==7,"native manual gun and rocket debit finite stores");
            h.assertTrue(target.getHealth()<target.getMaxHealth()||!target.isAlive(),"actual bounded muzzle shot hits a real target");
            var tag=new CompoundTag();craft.saveWithoutId(tag);var restored=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);restored.load(tag);
            h.assertTrue(restored.health()==craft.health()&&restored.combat().rounds()==craft.combat().rounds()&&restored.combat().rockets()==craft.combat().rockets(),"save retains actual hull and expended ammo");
            h.assertTrue(restored.pilot().owner().equals(p.getUUID())&&restored.pilot().lease(p.getUUID())==null,"ownership persists but live network authority does not");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airPilotEmpty",timeoutTicks=210)
    public static void depletedPlayerStoresNeverInheritAiInfiniteAmmunition(GameTestHelper h){
        var f=new Fixture(h,256800);var craft=f.craft(0);var p=player(h,f,false);p.startRiding(craft,true);
        var tag=new CompoundTag();craft.saveWithoutId(tag);tag.getCompound("AirCombat").putInt("Rounds",0);tag.getCompound("AirCombat").putInt("Rockets",0);craft.load(tag);
        int[] t={0};long[] sequence={0};boolean[] accepted={false};
        h.onEachTick(()->{if(++t[0]>=100&&t[0]<160&&t[0]%2==0)accepted[0]|=craft.pilot().accept(p,craft.pilot().lease(p.getUUID()),sequence[0]++,0,0,0,0,3);});
        h.runAfterDelay(175,()->{try(f){
            h.assertTrue(accepted[0]&&craft.phase()==BlackwatchHelicopter.Phase.MANUAL&&craft.rotor()>=.9F,"actual operational player weapon input accepted");
            h.assertTrue(craft.combat().shots()==0&&craft.combat().rounds()==0&&craft.combat().rockets()==0,"empty manual weapons cannot fire or replenish from AI policy");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=180)
    public static void timedHijackKeepsSameCraftCrewAndIssuesFirstPlayerLoadout(GameTestHelper h){
        var f=new Fixture(h,255000);var craft=f.craft(0);var crew=f.crew(craft);var p=player(h,f,true);craft.hurt(f.level.damageSources().generic(),31);craft.combat().emptyPlayerStores();
        var ids=crew.stream().map(Entity::getUUID).toList();var reserve=crew.stream().map(MilitarySoldier::reserveAmmo).toList();
        h.runAfterDelay(40,()->{
            h.assertTrue(craft.pilot().hijack().request(p),"real prototype cockpit contact begins");h.assertTrue(craft.pilot().lease(p.getUUID())==null&&craft.getPassengers().size()==7,"exterior latch grants no driving authority");
            h.runAfterDelay(35,()->h.assertTrue(crew.stream().allMatch(e->e.getVehicle()==craft)&&craft.pilot().owner()==null,"paired pull precedes real extraction and ownership"));
            h.runAfterDelay(80,()->{try(f){
                h.assertTrue(p.getVehicle()==craft&&craft.pilot().slot(p.getUUID())==0&&!craft.pilot().hijack().active(),"same actual aircraft enters player control");
                h.assertTrue(craft.health()==329&&craft.combat().rounds()==240&&craft.combat().rockets()==8&&craft.combat().rapid().left()==75&&craft.combat().rapid().right()==75,"first completed capture issues the requested finite loadout without repairing hull damage");
                for(int i=0;i<crew.size();i++){var soldier=crew.get(i);h.assertTrue(f.level.getEntity(ids.get(i))==soldier&&soldier.isAlive()&&!soldier.isPassenger()&&soldier.reserveAmmo()==reserve.get(i),"each original soldier and inventory survive actual egress");for(int j=i+1;j<crew.size();j++)h.assertTrue(!soldier.getBoundingBox().intersects(crew.get(j).getBoundingBox()),"extracted bodies do not overlap");}
                h.assertTrue(craft.pilot().lease(p.getUUID())!=null,"only committed driver receives fresh authority");
            }h.succeed();});
        });
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=180)
    public static void airborneExtractionUsesActualFallingBodies(GameTestHelper h){
        var f=new Fixture(h,256000);var craft=f.craft(12);var crew=f.crew(craft);var p=player(h,f,true);p.setNoGravity(true);p.setPos(f.x-3,93,2.1);
        h.runAfterDelay(40,()->{
            crew.forEach(s->s.setNoAi(false));h.assertTrue(craft.pilot().hijack().request(p),"actual aerial contact latches");
            h.runAfterDelay(60,()->h.assertTrue(crew.stream().allMatch(s->!s.isPassenger()&&s.getY()>90&&s.getDeltaMovement().y<0),"crew exit near aircraft and start real gravity, without teleporting to ground: "+crew.stream().map(s->s.getY()+"/"+s.getDeltaMovement().y+"/riding="+s.isPassenger()).toList()));
            h.runAfterDelay(80,()->{try(f){h.assertTrue(craft.pilot().owner().equals(p.getUUID())&&craft.getY()>92,"aerial capture retains aircraft height");}h.succeed();});
        });
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=140)
    public static void detachedAttackerCancelsWithoutTransferringOrRemovingCrew(GameTestHelper h){
        var f=new Fixture(h,257000);var craft=f.craft(0);var crew=f.crew(craft);var p=player(h,f,true);
        h.runAfterDelay(40,()->{h.assertTrue(craft.pilot().hijack().request(p),"claim begins");h.runAfterDelay(12,p::stopRiding);
            h.runAfterDelay(24,()->{try(f){h.assertTrue(!craft.pilot().hijack().active()&&craft.pilot().owner()==null&&craft.pilot().lease(p.getUUID())==null,"detachment revokes claim");h.assertTrue(crew.stream().allMatch(s->s.getVehicle()==craft),"interrupted pull leaves all actual crew aboard");}h.succeed();});
        });
    }
    @GameTest(template="helicopter_arena",batch="airPilot",timeoutTicks=90)
    public static void playerOccupiedAircraftCannotBeForceCaptured(GameTestHelper h){
        try(var f=new Fixture(h,258000)){var craft=f.craft(0);var driver=player(h,f,false);driver.startRiding(craft,true);var attacker=player(h,f,true);
            h.assertTrue(!craft.pilot().hijack().request(attacker)&&driver.getVehicle()==craft,"real player occupant cannot be extracted by NPC hijack");
            var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(attacker,net.minecraft.world.InteractionHand.MAIN_HAND,craft);
            com.codex.blacklight.mode.PrototypeSurvivalLock.onEntityInteract(event);h.assertTrue(!event.isCanceled(),"prototype mode exposes actual helicopter interaction");
        }h.succeed();
    }
}
