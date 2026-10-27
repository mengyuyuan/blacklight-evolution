package com.codex.blacklight.gametest;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.ClawActionPayload.Action;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class LockedGrabGameTest {
 static ServerPlayer player(GameTestHelper h,PowerForm form){
  for(int x=-5;x<=16;x++)for(int z=-5;z<=16;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
  var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"locked-grab"));p.setPos(h.absolutePos(new BlockPos(4,30,2)).getBottomCenter());p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
  var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(form);s.activate(form);s.unlock(PowerForm.MUSCLE_MASS);s.addEvolutionPoints(1000);s.buyCombatSkill(CombatSkill.BLOCK_THROW_1);s.buyCombatSkill(CombatSkill.BLOCK_THROW_2);s.buyCombatSkill(CombatSkill.BLOCK_THROW_3);p.setData(ModAttachments.CONSUMING,false);return p;
 }
 static void tick(ServerPlayer p,int n){for(int i=0;i<n;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}}
 static void clean(ServerPlayer p,Mob mob){ConsumeSystem.cancel(p);TerrainGrabSelection.cancel(p);var block=BlockThrowSystem.held(p);if(block!=null)block.drop();CombatTargeting.requestLock(p,-1);if(mob!=null)mob.discard();}
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_evolved_human")
 public static void grownEvolvedRequiresWeakeningAndUsesTheHumanCaptureInsteadOfMounting(GameTestHelper h){
  var body=EvolvedVariantsGameTest.host(h,EvolvedArchetype.ARMORED);var p=player(h,PowerForm.CLAWS);body.setPos(p.position().add(0,0,2));
  try{h.assertTrue(body.getBbHeight()>2.35&&!ConsumeSequence.large(body),"grown human retains its receiver skeleton instead of becoming a generic climbable monster");h.assertTrue(!ConsumeSystem.healthReady(p,body),"low original zombie health cannot bypass the super-elite weakening gate");var wounded=new net.minecraft.nbt.CompoundTag();body.saveWithoutId(wounded);wounded.putFloat("BlacklightCoreFracture",1);body.load(wounded);body.setHealth(body.getMaxHealth()*.08F);h.assertTrue(body.tissueCoreFracture()>=1&&ConsumeSystem.healthReady(p,body),"prepared broken-core low-health evolved remains absorbable");CombatTargeting.requestLock(p,body.getId());ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CAPTURE,"real grab intent enters human capture, never mount");}
  finally{clean(p,body);}h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_terrain")
 public static void aLockedNearbyWeakEvolvedCannotStealAnActualTerrainPickup(GameTestHelper h){
  var body=EvolvedVariantsGameTest.host(h,EvolvedArchetype.STRENGTH);var p=player(h,PowerForm.MUSCLE_MASS);body.setPos(p.position().add(2,0,1));body.setHealth(body.getMaxHealth()*.1F);p.setXRot(50);p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
  try{CombatTargeting.requestLock(p,body.getId());h.assertTrue(CombatTargeting.locked(p)==body&&TerrainGrabSelection.targetsTerrain(p),"actual lock and actual ground ray coexist");var eye=p.getEyePosition();var hit=p.level().clip(new net.minecraft.world.level.ClipContext(eye,eye.add(Vec3.directionFromRotation(50,0).scale(3.5)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
   ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&!body.getData(ModAttachments.CONSUME_VISUAL).active(),"ground selection never claims the off-ray locked victim");ClawCombatSystem.handle(p,Action.TERRAIN_GRAB_KEEPALIVE);ClawCombatSystem.handle(p,Action.TERRAIN_GRAB_RELEASE);
   h.assertTrue(BlockThrowSystem.held(p)!=null&&p.level().getBlockState(hit.getBlockPos()).isAir(),"release really extracts source terrain into held cargo while locked");h.assertTrue(CombatTargeting.locked(p)==body,"terrain pickup preserves the target for the later aimed throw");}
  finally{clean(p,body);}h.succeed();
 }
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_ray")
 public static void aCreatureDirectlyBeforeTheBlockRetainsPhysicalGrabPriority(GameTestHelper h){
  var p=player(h,PowerForm.MUSCLE_MASS);var mob=EntityType.ZOMBIE.create(h.getLevel());mob.setNoAi(true);mob.setPos(p.position().add(0,0,1.5));mob.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(mob);h.getLevel().setBlock(p.blockPosition().offset(0,1,3),Blocks.STONE.defaultBlockState(),2);
  try{h.assertTrue(!TerrainGrabSelection.targetsTerrain(p),"real creature surface is before the real wall");ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CAPTURE&&mob.getData(ModAttachments.CONSUME_VISUAL).active(),"the body is captured instead of tearing wall blocks through it");}
  finally{clean(p,mob);}h.succeed();
 }
 static Mob mounted(GameTestHelper h,ServerPlayer p){var mob=EntityType.IRON_GOLEM.create(h.getLevel());mob.setNoAi(true);mob.setHealth(5);mob.setPos(p.position().add(0,0,2));h.getLevel().addFreshEntity(mob);CombatTargeting.requestLock(p,mob.getId());ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT,"real large creature enters its intended mount");return mob;}
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_detach")
 public static void crouchEscapesTheRealMountedGrabBeforeBlockedMovementCanSwallowIt(GameTestHelper h){var p=player(h,PowerForm.CLAWS);var mob=mounted(h,p);try{com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,true);h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&p.getData(ModAttachments.GRAB_STAGE)==0&&!mob.getData(ModAttachments.CONSUME_VISUAL).active(),"crouch releases both claims and travel before the generic blocked-input return");}finally{clean(p,mob);}h.succeed();}
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_unlock")
 public static void explicitUnlockReleasesAPendingMountWithoutConsumingTheVictim(GameTestHelper h){var p=player(h,PowerForm.CLAWS);var mob=mounted(h,p);try{CombatTargeting.requestLock(p,-1);h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&!mob.getData(ModAttachments.CONSUME_VISUAL).active()&&mob.isAlive()&&CombatTargeting.locked(p)==null,"unlock really releases the independent attachment and leaves victim alive");}finally{clean(p,mob);}h.succeed();}
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_repress")
 public static void pressingGrabAgainCancelsThePendingCaptureWithoutQueuingAnotherTarget(GameTestHelper h){var p=player(h,PowerForm.CLAWS);var mob=EntityType.COW.create(h.getLevel());mob.setNoAi(true);mob.setPos(p.position().add(0,0,2));h.getLevel().addFreshEntity(mob);try{ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CAPTURE,"real first E starts capture");ClawCombatSystem.handle(p,Action.GRAB);h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&!mob.getData(ModAttachments.CONSUME_VISUAL).active(),"second E is a release intent during capture");}finally{clean(p,mob);}h.succeed();}
 @GameTest(template="wall_test",timeoutTicks=80,batch="lock_grab_commit")
 public static void theEscapeInputDoesNotAbortAnAlreadyCommittedConsume(GameTestHelper h){var p=player(h,PowerForm.CLAWS);var mob=EntityType.COW.create(h.getLevel());mob.setNoAi(true);mob.setPos(p.position().add(0,0,2));h.getLevel().addFreshEntity(mob);try{ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));h.assertTrue(ConsumeSystem.holding(p),"capture reaches its real hold");ClawCombatSystem.handle(p,Action.CONSUME);com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,true);h.assertTrue(p.getData(ModAttachments.CONSUMING)&&ConsumeVisual.consumeStage(p.getData(ModAttachments.GRAB_STAGE)),"ordinary detach cannot abort a committed reward scene");}finally{clean(p,mob);}h.succeed();}
}
