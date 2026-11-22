package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.item.GreeneTissueItem;
import com.codex.blacklight.registry.ModItems;
import com.codex.blacklight.mode.PrototypeSurvivalLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.codex.blacklight.network.ClawActionPayload.Action;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GrabInteractionGameTest {
    private static ServerPlayer player(GameTestHelper h) {
        for(int x=1;x<10;x++)for(int z=1;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"grab-input"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.activate(PowerForm.CLAWS);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p,int count) {for(int i=0;i<count;i++){p.tickCount++;ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}}
    private static Vec3 carryAnchor(ServerPlayer p,net.minecraft.world.entity.Mob victim) {
        var v=com.codex.blacklight.registry.ModAttachments.getData(victim, ModAttachments.CONSUME_VISUAL);
        return ConsumePlacementTrack.carryAnchor(p.position(),p.getYRot(),victim,GrappleMotion.key(p,v),GrappleMotion.progress(p,v,0)).orElseThrow();
    }
    @GameTest(template="wall_test",timeoutTicks=35)
    public static void livingTissueAcceptsEitherHandAndYieldsToGrab(GameTestHelper h) {
        var p = player(h);
        long seed = h.getLevel().getSeed();
        var den = com.codex.blacklight.world.GreeneHiveCave.site(seed,1);
        var above = den.above(30);
        h.assertTrue(com.codex.blacklight.world.GreeneHiveCave.nearest(seed,above).equals(den)
                && com.codex.blacklight.world.GreeneHiveCave.guidance(den,Vec3.atCenterOf(above)).y < -.99, "a player above the nearest hive is directed down");
        var tracker = ModItems.GREENE_TISSUE.get();
        h.assertTrue(tracker != ModItems.MOTHER_CORE.get(), "new loot has its own registry ID");
        for (var hand : InteractionHand.values()) {
            var stack = new ItemStack(hand == InteractionHand.MAIN_HAND ? tracker : ModItems.MOTHER_CORE.get());
            p.setItemInHand(hand, stack);
            var use = new PlayerInteractEvent.RightClickItem(p, hand);
            PrototypeSurvivalLock.onRightClickItem(use);
            h.assertTrue(!use.isCanceled(), "Prototype input permits both fresh and old tissue");
            h.assertTrue(p.gameMode.useItem(p, p.level(), stack, hand).consumesAction() && p.isUsingItem()
                    && p.getUsedItemHand() == hand, "normal item entry starts sensing");
            h.assertTrue(GreeneTissueItem.opening(0) == 0 && GreeneTissueItem.opening(8) == 1
                    && GreeneTissueItem.opening(21) == 2 && GreeneTissueItem.opening(32) == 0, "valves open and close");
            p.releaseUsingItem();
            h.assertTrue(!p.isUsingItem() && stack.getCount() == 1, "interrupted listening preserves tissue");
            var echo = stack.getItem().finishUsingItem(stack, p.level(), p);
            h.assertTrue(echo == stack && echo.getCount() == 1 && p.getCooldowns().isOnCooldown(stack.getItem()), "complete scan is reusable");
            p.setItemInHand(hand, ItemStack.EMPTY);
        }
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(tracker));
        var cow = h.spawn(EntityType.COW,3,2,5);
        cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        var duringHold = new PlayerInteractEvent.RightClickItem(p,InteractionHand.OFF_HAND);
        PrototypeSurvivalLock.onRightClickItem(duringHold);
        h.assertTrue(duringHold.isCanceled(), "a captive keeps ownership of the use input");
        ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
        ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
        tick(p,6);
        h.assertTrue(cow.isAlive() && !ConsumeSystem.holding(p) && cow.getDeltaMovement().length() > 1, "tissue in the other hand permits a primary throw");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeImpalesWithRightArmThenConsumesWithoutDroppingVictim(GameTestHelper h) {
        for(var type:new EntityType<?>[]{EntityType.CHICKEN,EntityType.COW}) {
            var p=player(h);var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
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
            tick(p,2);h.assertTrue(mob.isRemoved()&&p.getHealth()>10&&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING),"blade consumes once and unlocks");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeThrowReleasesOnRightArmSwingWithoutConsumeRewards(GameTestHelper h) {
        var p=player(h);var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.BLADE));ConsumeSystem.throwTarget(p);tick(p,5);
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.THROW,"retain body before release");
        tick(p,1);h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&p.getHealth()==10&&state.getEvolutionPoints()==0,"blade throw releases without reward");
        cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void smallVictimsStayAtPalmHeightAndFollowTurns(GameTestHelper h) {
        for(boolean baby:new boolean[]{false,true}) {
            var p=player(h);var chicken=h.spawn(EntityType.CHICKEN,3,2,5);chicken.setBaby(baby);chicken.setNoAi(true);
            ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()));
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
        h.assertTrue(!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING),"secondary intent without held target does not auto-grab");
        ClawCombatSystem.handle(p,Action.GRAB);ClawCombatSystem.handle(p,Action.CONSUME);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CONSUME&&cow.isAlive()&&p.getHealth()==10,"RC49 queued right click starts consume only after capture");
        tick(p,ConsumeSequence.completionTick(PowerForm.CLAWS,false,ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).variant())-1);
        h.assertTrue(cow.isAlive()&&p.getHealth()==10,"queue cannot shorten the consume sequence");
        tick(p,2);ClawCombatSystem.handle(p,Action.CONSUME);tick(p,20);
        h.assertTrue(cow.isRemoved()&&p.getHealth()==12&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints()==2,"queued consume and repeated input pay exactly once");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void grabKeyKeepsBodyAndPrimaryReleaseThrowsOnce(GameTestHelper h) {
        for (boolean early : new boolean[]{false,true}) {
            var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
            ClawCombatSystem.handle(p,Action.GRAB);
            if (!early) {
                tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
                ClawCombatSystem.handle(p,Action.GRAB);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_CANCEL);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
                h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE) == ConsumeVisual.HOLD,"repeated grab and cancelled release keep the body");
            }
            ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
            ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
            if (early) {
                h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE) == ConsumeVisual.CAPTURE,"tap during capture waits for the hand");
                tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
            }
            p.setXRot(75);
            tick(p,5);
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE)==ConsumeVisual.THROW,"throw waits for hand release");
            tick(p,1);ClawCombatSystem.handle(p,Action.THROW);ClawCombatSystem.handle(p,Action.CONSUME);
            h.assertTrue(cow.isAlive()&&com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING),"throw releases hold and begins free flight");
            var freeze=new com.codex.blacklight.combat.ForgeEntityTickEvent.Pre(cow);ConsumeSystem.freeze(freeze);
            h.assertTrue(!freeze.isCanceled(),"flight is not held by the capture freeze");
            h.assertTrue(cow.getDeltaMovement().z>1&&cow.getDeltaMovement().y>0,"victim launches in look direction");
            h.assertTrue(p.getHealth()==10&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getBiomass()==250&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints()==0,"throw and duplicate packets award no consumption resources");
            Vec3 launch=cow.position();
            for(int i=0;i<3;i++)ConsumeSystem.thrown(new com.codex.blacklight.combat.ForgeEntityTickEvent.Pre(cow));
            h.assertTrue(cow.position().distanceToSqr(launch)>4,"real flight displacement must work even for pre-existing NoAI victims");
            h.assertTrue(cow.isNoAi(),"flight must not change the original persistent NoAI setting");
            cow.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=65)
    public static void chargedPrimaryThrowsAlongDownwardReleaseAim(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
        h.runAfterDelay(10,()->ClawCombatSystem.handle(p,Action.GRAB_THROW_START));
        h.runAfterDelay(20,()->ClawCombatSystem.handle(p,Action.GRAB_THROW_START));
        h.runAfterDelay(31,()->{
            p.setXRot(70);
            ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
            p.setXRot(-70);
            tick(p,6);
            var velocity=cow.getDeltaMovement();
            h.assertTrue(velocity.y < -1.9 && velocity.z > .6,
                    "fully charged body keeps downward pitch from the button release: "+velocity);
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldTargetFollowsWalkingAndTurning(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()));Vec3 before=cow.position();
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
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()));
        for(var action:new Action[]{Action.GROUND_SPIKE_CHARGE_START,Action.CYCLE_FORM,Action.TOGGLE_CLAWS})ClawCombatSystem.handle(p,action);
        h.assertTrue(ConsumeSystem.holding(p)&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE,"held actions exclude combat and morph");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void carryingCanSprintTurnAndThrowWithoutStoppingOrHittingVictim(GameTestHelper h) {
        var p=player(h);p.setPos(h.absoluteVec(new Vec3(5,25,3)));
        var chicken=h.spawn(EntityType.CHICKEN,5,25,5);chicken.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()));float health=chicken.getHealth();
        double traveled=0;
        com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);
        p.setDeltaMovement(0,0,.6);
        for(int i=0;i<8;i++) {
            // Stay inside the bounded test arena while covering a full running turn.
            p.setYRot(i*45);p.setOnGround(true);p.setDeltaMovement(Vec3.directionFromRotation(0,p.getYRot()).scale(.6));
            com.codex.blacklight.movement.PrototypeMovementSystem.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(p.isSprinting()&&p.getDeltaMovement().horizontalDistance()>.45,"held state accepts actual sprint input and acceleration");
            traveled+=p.getDeltaMovement().horizontalDistance();p.setPos(p.position().add(p.getDeltaMovement().multiply(1,0,1)));tick(p,1);
            var anchor=carryAnchor(p,chicken);
            h.assertTrue(ConsumeSystem.holding(p)&&chicken.position().distanceTo(anchor)<.03,"sprinting and turning keeps victim at palm: step="+i+" held="+ConsumeSystem.holding(p)+" gap="+chicken.position().distanceTo(anchor)+" player="+p.position()+" target="+chicken.position()+" anchor="+anchor);
        }
        h.assertTrue(traveled>4&&chicken.getHealth()==health,"running neither drops nor body-checks the carried chicken");
        p.setYRot(0);p.setDeltaMovement(0,0,.7);ConsumeSystem.throwTarget(p);
        for(int i=0;i<6;i++){p.setPos(p.position().add(0,0,.7));tick(p,1);}
        h.assertTrue(!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&com.codex.blacklight.registry.ModAttachments.getData(chicken, ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT,"throw releases while carrier is moving");
        h.assertTrue(p.getDeltaMovement().z>.65&&chicken.getDeltaMovement().z>1.8,"throw preserves running speed and inherits carrier momentum");
        chicken.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void leavingPrototypeModeReleasesHeldBody(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()));com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).setPrototypeMode(false);tick(p,1);
        h.assertTrue(!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE)==0&&!com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).active()&&!cow.isNoAi(),"mode change frees target without persisting NoAI");
        h.assertTrue(p.getHealth()==10&&cow.isAlive(),"release is not consume");cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldBodyKeepsTheSharedGlideGripWithoutBreakingTheClaim(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(ConsumeSystem.holding(p),"fixture reaches a real held state");
        p.setPos(p.position().add(0,3,0));cow.setPos(cow.position().add(0,3,0));p.setOnGround(false);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.GLIDE_UNTIL,p.level().getGameTime()+100);
        for(int step=0;step<8;step++) {
            tick(p,1);var visual=com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL);
            var expected=GrappleMotion.carryAnchor(p,cow,visual,p.position(),p.getYRot(),0).orElseThrow();
            h.assertTrue(ConsumeSystem.holding(p)&&cow.position().distanceTo(expected)<.03,"gliding keeps the server's collision-resolved body on the shared anchor");
        }
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).carryPosture().sample(p.tickCount,true,1)==1,"glide grip transition completes");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
}
