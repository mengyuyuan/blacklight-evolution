package com.codex.blacklight.gametest;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.city.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.UUID;
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class PostBiomassAuditGameTest {
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_removed_transit")
 public static void removedPlayerCannotRemainInHydraTransit(GameTestHelper h){
  var p=player(h,"RemovedTransit");var level=h.getLevel();
  var hydra=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);
  h.assertTrue(hydra.beginArrival(p)&&HydraTransport.carrier(p)==hydra,"live arrival owns the player");
  var position=p.position();level.removePlayerImmediately(p,Entity.RemovalReason.KILLED);p.invalidateCaps();
  h.assertTrue(!ModAttachments.available(p),"removed player has no live capability");
  h.assertTrue(!HydraTransport.active(p),"removed player is no longer a carrier participant");
  HydraTransport.place(p);CityTravel.transitLanding(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
  h.assertTrue(p.position().equals(position)&&!ModAttachments.available(p),"post-death tick neither moves the corpse nor revives capability state");
  hydra.discard();h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_input")
 public static void hydraMustRejectWeaponInput(GameTestHelper h){
  var level=h.getLevel();var p=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"AuditInput"));
  p.moveTo(h.absolutePos(new BlockPos(4,22,4)).getCenter());level.addNewPlayer(p);
  var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);
  WeaponCombatSystem.request(p,false);
  boolean control=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec()!=null;
  ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE).beginCharge();
  var hydra=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);
  boolean held=hydra.beginArrival(p)&&HydraTransport.active(p);
  h.assertTrue(ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE).chargeTicks()==0,"capture clears latent charged-jump movement");
  h.assertTrue(!WeaponCombatSystem.busy(p),"capture cancels an already running attack");
  WeaponCombatSystem.acceptInput(p,true,false,false,false);
  boolean attack=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec()!=null;
  WeaponCombatSystem.cancel(p);hydra.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  h.assertTrue(control&&held,"AUDIT fixture: ordinary attack must work, then hydra must hold player");
  h.assertTrue(!attack,"AUDIT BUG: held player started weapon action during hydra cinematic");h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_recovery")
 public static void failedSafeRecoveryMustRetainRescue(GameTestHelper h){
  var level=h.getLevel();var origin=h.absolutePos(new BlockPos(4,22,4));
  var p=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"AuditRescue")){
   @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
  };
  p.moveTo(origin.getCenter());level.addNewPlayer(p);
  var hydra=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);
  boolean held=hydra.beginArrival(p)&&p.getPersistentData().contains("BlacklightHydraRescue");
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
   level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   level.setBlock(new BlockPos(origin.getX()+x,level.getMaxBuildHeight()-1,origin.getZ()+z),Blocks.STONE.defaultBlockState(),2);
  }
  boolean unsafe=CityTravel.safePosition(level,origin)==null;
  HydraTransport.place(p);hydra.hurt(level.damageSources().generic(),1);
  boolean retained=p.getPersistentData().contains("BlacklightHydraRescue");
  hydra.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
   level.setBlock(origin.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
   level.setBlock(new BlockPos(origin.getX()+x,level.getMaxBuildHeight()-1,origin.getZ()+z),Blocks.AIR.defaultBlockState(),2);
  }
  h.assertTrue(held&&unsafe,"AUDIT fixture: rescue exists and no candidate is safe");
  h.assertTrue(retained,"AUDIT BUG: failed safe recovery erased persistent rescue marker");h.succeed();
 }

 private static FakePlayer player(GameTestHelper h,String name){
  var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),name)){
   @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
  };
  p.setPos(h.absolutePos(new BlockPos(4,22,4)).getCenter());h.getLevel().addNewPlayer(p);
  var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);
  return p;
 }
 private static void tickSlime(FakePlayer p){BiomassSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_slime_recall")
 public static void voluntarySlimeRecallPreservesHealthAndRemovesOnlyItsShell(GameTestHelper h){
  var p=player(h,"SlimeRecall");p.setHealth(7);
  h.assertTrue(BiomassSystem.begin(p,false),"split starts");
  var shell=h.getLevel().getEntity(UUID.fromString(BiomassSystem.state(p).shell()));
  p.setPos(p.position().add(2,0,0));
  boolean recalled=BiomassSystem.cancel(p);
  boolean valid=recalled&&!BiomassSystem.active(p)&&shell.isRemoved()&&p.getHealth()==7&&ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.CLAWS;
  h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  h.assertTrue(valid,"recall restores form without healing, leaving a shell or bursting terrain");h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_slime_consume")
 public static void fatalSlimeCannotRecallAndOneMealCommitsOnce(GameTestHelper h){
  var p=player(h,"SlimeFatal");var level=h.getLevel();int before=ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass();
  h.assertTrue(BiomassSystem.begin(p,true),"fatal split has enough reserve");
  h.assertTrue(!BiomassSystem.cancel(p)&&BiomassSystem.state(p).shell().isEmpty(),"death slime has no recall or shell");
  h.assertTrue(ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass()==before-level.getGameRules().getInt(BiomassRules.COST),"reserve charged once");
  var cow=h.spawn(net.minecraft.world.entity.EntityType.COW,4,22,4);cow.setPos(p.position());
  ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).absorb(cow.getId(),level.getGameTime()-BiomassState.ABSORB_TICKS));
  tickSlime(p);
  h.assertTrue(!cow.isAlive()&&BiomassSystem.state(p).consumed(),"confirmed prey death commits reform");
  int biomass=ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass();int age=BiomassSystem.state(p).age();
  BiomassSystem.login(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));tickSlime(p);
  h.assertTrue(BiomassSystem.state(p).consumed()&&BiomassSystem.state(p).age()==age&&ModAttachments.getData(p,ModAttachments.PLAYER_STATE).getBiomass()==biomass,"relogin preserves committed kill and cannot award it twice");
  cow.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_slime_claim")
 public static void concurrentSlimesCannotClaimTheSamePrey(GameTestHelper h){
  var p=player(h,"SlimeFirst");var q=player(h,"SlimeSecond");var cow=h.spawn(net.minecraft.world.entity.EntityType.COW,4,22,4);cow.setPos(p.position());
  h.assertTrue(BiomassSystem.begin(p,true)&&BiomassSystem.begin(q,true),"both forms start");
  ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).absorb(cow.getId(),h.getLevel().getGameTime()));
  boolean protectedClaim=BiomassSystem.edible(p,cow)&&!BiomassSystem.edible(q,cow);
  ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);
  boolean freed=BiomassSystem.edible(q,cow);
  cow.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.getLevel().removePlayerImmediately(q,Entity.RemovalReason.DISCARDED);
  h.assertTrue(protectedClaim&&freed,"one live claim excludes other players and releases immediately when form ends");h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_slime_ceiling")
 public static void consumedSlimeWaitsForStandingRoom(GameTestHelper h){
  var p=player(h,"SlimeRoom");long now=h.getLevel().getGameTime();var ceiling=p.blockPosition().above();
  h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),2);
  ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.REFORMING,now-BiomassState.REFORM_TICKS,true,"","claws",-1,250));
  tickSlime(p);boolean waits=BiomassSystem.state(p).phase()==BiomassState.READY;
  h.getLevel().setBlock(ceiling,Blocks.AIR.defaultBlockState(),2);
  ModAttachments.setData(p,ModAttachments.BIOMASS_FORM,BiomassSystem.state(p).ready(now-BiomassState.REFORM_TICKS));tickSlime(p);
  boolean restored=!BiomassSystem.active(p);
  h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  h.assertTrue(waits&&restored,"blocked reform waits, then restores at the same position without another prey");h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_arrival_space")
 public static void newGatewaysDoNotShareAnOccupiedLanding(GameTestHelper h){
  var level=h.getLevel();var origin=h.absolutePos(new BlockPos(4,22,4));
  for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
  var first=CityTravel.arrivalPosition(level,origin,2.4F);h.assertTrue(first!=null,"first arrival has a safe floor");
  var hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(level);hydra.setBodySize(2.4F);hydra.moveTo(first.getX()+.5,first.getY(),first.getZ()+.5,0,0);level.addFreshEntity(hydra);
  var second=CityTravel.arrivalPosition(level,origin,2.4F);hydra.discard();
  h.assertTrue(second!=null&&second.distSqr(first)>=36,"permanent connectors receive distinct separated landing positions");h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_throw_chunks")
 public static void throwPredictionDoesNotLoadMissingTerrain(GameTestHelper h) throws Exception {
  var p=player(h,"ThrowNoLoad");var level=h.getLevel();var origin=new net.minecraft.world.phys.Vec3(2000000,90,2000000);
  int cx=net.minecraft.util.Mth.floor(origin.x)>>4,cz=net.minecraft.util.Mth.floor(origin.z)>>4;
  h.assertTrue(level.getChunkSource().getChunkNow(cx,cz)==null,"fixture chunk is unloaded");
  var method=BlockThrowAim.class.getDeclaredMethod("clear",net.minecraft.server.level.ServerPlayer.class,net.minecraft.world.phys.Vec3.class,BlockThrowBallistics.Shot.class);method.setAccessible(true);
  boolean clear=(boolean)method.invoke(null,p,origin,new BlockThrowBallistics.Shot(new net.minecraft.world.phys.Vec3(1,0,0),1));
  boolean stillMissing=level.getChunkSource().getChunkNow(cx,cz)==null;
  h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  h.assertTrue(!clear&&stillMissing,"prediction rejects unloaded flight volume without creating a chunk");h.succeed();
 }

 @GameTest(template="wall_test",timeoutTicks=700,batch="audit_unloaded_return")
 public static void legacyReturnPreparesUnloadedOriginalChunks(GameTestHelper h){
  var original=h.getLevel();var city=original.getServer().getLevel(CityTravel.CITY);
  h.assertTrue(city!=null,"city dimension is installed");
  // Fake network isolation: the real ticket/safe-position/commit code still executes.
  var p=new FakePlayer(city,new GameProfile(UUID.randomUUID(),"ReturnChunks")){
   @Override public void teleportTo(net.minecraft.server.level.ServerLevel level,double x,double y,double z,float yaw,float pitch){setServerLevel(level);setPos(x,y,z);}
  };
  p.setPos(670.5,64,820.5);
  int x=400000+((UUID.randomUUID().hashCode()&4095)<<4),z=400000;
  h.assertTrue(original.getChunkSource().getChunkNow(x>>4,z>>4)==null,"return chunk begins unloaded");
  var back=new net.minecraft.nbt.CompoundTag();back.putString("dimension",original.dimension().location().toString());
  back.putDouble("x",x+.5);back.putDouble("y",64);back.putDouble("z",z+.5);p.getPersistentData().put("BlacklightCityReturn",back);
  h.assertTrue(CityTravel.travel(p)==1,"return request accepted without loaded destination");
  h.assertTrue(p.serverLevel()==city&&p.getPersistentData().contains("BlacklightCityReturn"),"return coordinates remain until completion");
  h.succeedWhen(()->{
   // GameTest advances ticks without the normal 50 ms server pacing; allow real chunk I/O.
   java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
   // The return contract requires a known safe home. Random generated terrain may be ocean.
   // Provision the fixture only after asynchronous loading, never by forcing a chunk read.
   if(original.getChunkSource().getChunkNow(x>>4,z>>4)!=null){
    original.setBlock(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState(),2);
    original.setBlock(new BlockPos(x,64,z),Blocks.AIR.defaultBlockState(),2);
    original.setBlock(new BlockPos(x,65,z),Blocks.AIR.defaultBlockState(),2);
   }
   CityTravel.transitLanding(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
   h.assertTrue(p.serverLevel()==original,"pending return: loaded="+(original.getChunkSource().getChunkNow(x>>4,z>>4)!=null)+", safe="+CityTravel.safePosition(original,new BlockPos(x,64,z)));
   h.assertTrue(!p.getPersistentData().contains("BlacklightCityReturn")&&Math.abs(p.getX()-x)<9,"return commits once at safe original coordinates");
   p.discard();
  });
 }

 @GameTest(template="wall_test",timeoutTicks=100,batch="audit_shell_world")
 public static void remoteShellEruptsInItsOwnWorldOnce(GameTestHelper h){
  var p=player(h,"RemoteShell");var level=h.getLevel();var at=p.blockPosition();
  var husk=ModEntities.BIOMASS_HUSK.get().create(level);husk.initialize(p);level.addFreshEntity(husk);
  level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
  var cow=h.spawn(net.minecraft.world.entity.EntityType.COW,4,22,4);cow.setPos(p.position().add(2,0,0));float health=cow.getHealth();
  var remote=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(remote!=null,"other dimension installed");
  p.setServerLevel(remote);
  try{
   husk.burst(p);int debris=husk.debrisCount();
   h.assertTrue(husk.erupted()&&debris>0,"remote-owner shell grows actual tissue in the shell world");
   h.assertTrue(!level.getBlockState(at.below()).is(Blocks.STONE),"eruption changes original terrain");
   h.assertTrue(cow.getHealth()<health,"local victims receive owned eruption damage across owner travel");
   husk.burst(p);h.assertTrue(husk.debrisCount()==debris,"eruption is committed only once");
  }finally{p.setServerLevel(level);husk.discard();cow.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
  h.succeed();
 }

 @GameTest(template="wall_test",timeoutTicks=60,batch="audit_fatal_transport")
 public static void fatalSlimeReleasesHydraAnimationImmediately(GameTestHelper h){
  var p=player(h,"FatalTransport");var level=h.getLevel();var floor=p.blockPosition().below();
  level.setBlock(floor,Blocks.STONE.defaultBlockState(),2);
  var hydra=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);
  h.assertTrue(hydra.beginArrival(p)&&HydraTransport.active(p),"fixture enters paired arrival animation");
  p.setHealth(0);
  boolean transformed=BiomassSystem.begin(p,true);
  boolean released=!HydraTransport.active(p)&&hydra.heldId()<0&&hydra.scenePlayerId()<0;
  boolean fatal=BiomassSystem.active(p)&&BiomassSystem.state(p).fatal()&&p.isAlive()&&!BiomassSystem.cancel(p);
  hydra.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
  level.setBlock(floor,Blocks.AIR.defaultBlockState(),2);
  h.assertTrue(transformed&&released&&fatal,"death slime survives but no longer inherits the carrier or cinematic lock");h.succeed();
 }
}
