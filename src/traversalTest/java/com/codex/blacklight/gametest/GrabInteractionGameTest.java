package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.network.ClawActionPayload.Action;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GrabInteractionGameTest {
    private static ServerPlayer player(GameTestHelper h) {
        for(int x=1;x<10;x++)for(int z=1;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"grab-input"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.activate(PowerForm.CLAWS);
        p.setData(ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p,int count) {for(int i=0;i<count;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}}
    private static Vec3 carryAnchor(ServerPlayer p,net.minecraft.world.entity.Mob victim) {
        var v=victim.getData(ModAttachments.CONSUME_VISUAL);
        return ConsumePlacementTrack.carryAnchor(p.position(),p.getYRot(),victim,GrappleMotion.key(p,v),GrappleMotion.progress(p,v,0)).orElseThrow();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeImpalesWithRightArmThenConsumesWithoutDroppingVictim(GameTestHelper h) {
        for(var type:new EntityType<?>[]{EntityType.CHICKEN,EntityType.COW}) {
            var p=player(h);var state=p.getData(ModAttachments.PLAYER_STATE);
            state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
            var mob=(net.minecraft.world.entity.Mob)h.spawn(type,3,2,5);mob.setNoAi(true);
            var before=mob.position();ConsumeSystem.request(p);tick(p,5);
            h.assertTrue(mob.position().distanceToSqr(before)<.0001,"wind-up must not drag the victim before blade contact");
            tick(p,ConsumeVisual.captureDuration(PowerForm.BLADE)-5);h.assertTrue(ConsumeSystem.holding(p),"blade capture reaches a decision hold");
            for(float yaw:new float[]{0,90,180,270})for(float pitch:new float[]{-60,0,60}) {
                p.setYRot(yaw);p.setXRot(pitch);tick(p,5);
                var expected=carryAnchor(p,mob);
                h.assertTrue(mob.position().distanceTo(expected)<.03,"right blade body anchor follows view");
            }
            p.setXRot(0);tick(p,5);var origin=p.position();ConsumeSystem.consume(p);
            int completion=ConsumeSequence.completionTick(PowerForm.BLADE,false);
            for(int t=1;t<completion;t++) {
                tick(p,1);
                h.assertTrue(mob.isAlive()&&p.getHealth()==10,"paired blade scene cannot pay before completion");
                h.assertTrue(mob.position().distanceTo(origin)<8,"paired receiver remains within the consume scene");
            }
            tick(p,2);h.assertTrue(mob.isRemoved()&&p.getHealth()>10&&!p.getData(ModAttachments.CONSUMING),"blade consumes once and unlocks");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeThrowReleasesOnRightArmSwingWithoutConsumeRewards(GameTestHelper h) {
        var p=player(h);var state=p.getData(ModAttachments.PLAYER_STATE);
        state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.BLADE));ConsumeSystem.throwTarget(p);tick(p,5);
        h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.THROW,"retain body before release");
        tick(p,1);h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&p.getHealth()==10&&state.getEvolutionPoints()==0,"blade throw releases without reward");
        cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void smallVictimsStayAtPalmHeightAndFollowTurns(GameTestHelper h) {
        for(boolean baby:new boolean[]{false,true}) {
            var p=player(h);var chicken=h.spawn(EntityType.CHICKEN,3,2,5);chicken.setBaby(baby);chicken.setNoAi(true);
            ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));
            h.assertTrue(ConsumeSystem.holding(p),"chicken must be held");
            tick(p,1);
            h.assertTrue(chicken.position().distanceTo(carryAnchor(p,chicken))<.03,
                    "small bodies meet palm height instead of hovering below it");
            for(float yaw:new float[]{90,180,270,0}) for(float pitch:new float[]{75,-75,0}) {
                p.setYRot(yaw);p.setXRot(pitch);tick(p,5);
                var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw);
                var contact=chicken.position().add(0,chicken.getBbHeight()*.6,0).subtract(forward.scale(chicken.getBbWidth()*.4));
                var hand=GrabPlacement.palm(p.position(),yaw,p.getXRot());
                h.assertTrue(chicken.position().distanceTo(carryAnchor(p,chicken))<.03&&ConsumeSystem.holding(p),"turning, looking up or down must not detach the held chicken");
            }
            ConsumeSystem.cancel(p);chicken.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void consumeCannotAcquireButCanQueueWithinExistingCapture(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.CONSUME);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"secondary intent without held target does not auto-grab");
        ClawCombatSystem.handle(p,Action.GRAB);ClawCombatSystem.handle(p,Action.CONSUME);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CONSUME&&cow.isAlive()&&p.getHealth()==10,"RC49 queued right click starts consume only after capture");
        tick(p,ConsumeSequence.completionTick(PowerForm.CLAWS,false)-1);
        h.assertTrue(cow.isAlive()&&p.getHealth()==10,"queue cannot shorten the consume sequence");
        tick(p,2);ClawCombatSystem.handle(p,Action.CONSUME);tick(p,20);
        h.assertTrue(cow.isRemoved()&&p.getHealth()==12&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==2,"queued consume and repeated input pay exactly once");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void primaryAndRepeatedGrabThrowAtReleaseFrameWithoutRewards(GameTestHelper h) {
        for(var action:new Action[]{Action.THROW,Action.GRAB}) {
            var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
            ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));ClawCombatSystem.handle(p,action);tick(p,5);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.THROW,"throw waits for hand release");
            tick(p,1);ClawCombatSystem.handle(p,Action.THROW);ClawCombatSystem.handle(p,Action.CONSUME);
            h.assertTrue(cow.isAlive()&&cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&!p.getData(ModAttachments.CONSUMING),"throw releases hold and begins free flight");
            var freeze=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(cow);ConsumeSystem.freeze(freeze);
            h.assertTrue(!freeze.isCanceled(),"flight is not held by the capture freeze");
            h.assertTrue(cow.getDeltaMovement().z>1&&cow.getDeltaMovement().y>0,"victim launches in look direction");
            h.assertTrue(p.getHealth()==10&&p.getData(ModAttachments.PLAYER_STATE).getBiomass()==250&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==0,"throw and duplicate packets award no consumption resources");
            Vec3 launch=cow.position();
            for(int i=0;i<3;i++)ConsumeSystem.thrown(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(cow));
            h.assertTrue(cow.position().distanceToSqr(launch)>4,"real flight displacement must work even for pre-existing NoAI victims");
            h.assertTrue(cow.isNoAi(),"flight must not change the original persistent NoAI setting");
            cow.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldTargetFollowsWalkingAndTurning(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));Vec3 before=cow.position();
        // Forward travel and a turn cannot cancel each other at the new left-palm pivot.
        for(int i=0;i<20;i++){p.setPos(p.position().add(0,0,.07));p.setYRot(i*4);tick(p,1);}
        h.assertTrue(ConsumeSystem.holding(p),"turning while walking must preserve the held state");
        Vec3 forward=Vec3.directionFromRotation(0,p.getYRot());
        Vec3 expected=carryAnchor(p,cow);
        h.assertTrue(expected.distanceToSqr(before)>.01,"walking and turning must produce a different contact point");
        h.assertTrue(cow.position().distanceToSqr(expected)<.0025,"held body follows the rotated palm after walking; gap="+cow.position().distanceTo(expected));ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldClicksDoNotStartGroundspikeOrClawAttack(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));
        for(var action:new Action[]{Action.GROUND_SPIKE_CHARGE_START,Action.CYCLE_FORM,Action.TOGGLE_CLAWS})ClawCombatSystem.handle(p,action);
        h.assertTrue(ConsumeSystem.holding(p)&&p.getData(ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE,"held actions exclude combat and morph");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void carryingCanSprintTurnAndThrowWithoutStoppingOrHittingVictim(GameTestHelper h) {
        var p=MovingCombatGameTest.player(h,PowerForm.CLAWS);p.setPos(p.position().add(2,0,0));
        var chicken=h.spawn(EntityType.CHICKEN,5,25,5);chicken.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));float health=chicken.getHealth();
        double traveled=0;
        com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);
        p.setDeltaMovement(0,0,.6);
        for(int i=0;i<8;i++) {
            // Stay inside the bounded test arena while covering a full running turn.
            p.setYRot(i*45);p.setOnGround(true);p.setDeltaMovement(Vec3.directionFromRotation(0,p.getYRot()).scale(.6));
            com.codex.blacklight.movement.PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.isSprinting()&&p.getDeltaMovement().horizontalDistance()>.45,"held state accepts actual sprint input and acceleration");
            traveled+=p.getDeltaMovement().horizontalDistance();p.setPos(p.position().add(p.getDeltaMovement().multiply(1,0,1)));tick(p,1);
            var anchor=carryAnchor(p,chicken);
            h.assertTrue(ConsumeSystem.holding(p)&&chicken.position().distanceTo(anchor)<.03,"sprinting and turning keeps victim at palm: step="+i+" held="+ConsumeSystem.holding(p)+" gap="+chicken.position().distanceTo(anchor)+" player="+p.position()+" target="+chicken.position()+" anchor="+anchor);
        }
        h.assertTrue(traveled>4&&chicken.getHealth()==health,"running neither drops nor body-checks the carried chicken");
        p.setYRot(0);p.setDeltaMovement(0,0,.7);ConsumeSystem.throwTarget(p);
        for(int i=0;i<6;i++){p.setPos(p.position().add(0,0,.7));tick(p,1);}
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&chicken.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT,"throw releases while carrier is moving");
        h.assertTrue(p.getDeltaMovement().z>.65&&chicken.getDeltaMovement().z>1.8,"throw preserves running speed and inherits carrier momentum");
        chicken.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void leavingPrototypeModeReleasesHeldBody(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));p.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(false);tick(p,1);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&p.getData(ModAttachments.GRAB_STAGE)==0&&!cow.getData(ModAttachments.CONSUME_VISUAL).active()&&!cow.isNoAi(),"mode change frees target without persisting NoAI");
        h.assertTrue(p.getHealth()==10&&cow.isAlive(),"release is not consume");cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldBodyKeepsTheSharedGlideGripWithoutBreakingTheClaim(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(ConsumeSystem.holding(p),"fixture reaches a real held state");
        p.setPos(p.position().add(0,3,0));cow.setPos(cow.position().add(0,3,0));p.setOnGround(false);
        p.setData(ModAttachments.GLIDE_UNTIL,p.level().getGameTime()+100);
        for(int step=0;step<8;step++) {
            tick(p,1);var visual=cow.getData(ModAttachments.CONSUME_VISUAL);
            var expected=GrappleMotion.carryAnchor(p,cow,visual,p.position(),p.getYRot(),0).orElseThrow();
            h.assertTrue(ConsumeSystem.holding(p)&&cow.position().distanceTo(expected)<.03,"gliding keeps the server's collision-resolved body on the shared anchor");
        }
        h.assertTrue(p.getData(ModAttachments.MOVEMENT_STATE).carryPosture().sample(p.tickCount,true,1)==1,"glide grip transition completes");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
}
