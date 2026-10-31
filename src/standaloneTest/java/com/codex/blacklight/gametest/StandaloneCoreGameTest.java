package com.codex.blacklight.gametest;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Production gameplay runs in Minecraft with the independent renderer's prerequisites only. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class StandaloneCoreGameTest {
    private static ServerPlayer player(GameTestHelper h,String name,PowerForm form) {
        return player(h,name,form,false);
    }
    private static ServerPlayer player(GameTestHelper h,String name,PowerForm form,boolean damageable) {
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)h.setBlock(x,1,z,Blocks.STONE);
        var profile=new GameProfile(UUID.randomUUID(),name);
        ServerPlayer p;
        if(damageable) {
            // NeoForge FakePlayer is permanently invulnerable and its tick is a no-op.
            // Exercise the real ServerPlayer hurt pipeline, with networking isolated.
            p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);
            // This fixture invokes no inbound movement or interaction handlers.
            p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
            for(int tick=0;tick<61;tick++)p.tick();
        } else p=FakePlayerFactory.get(h.getLevel(),profile);
        p.setGameMode(GameType.SURVIVAL);p.setPos(h.absoluteVec(new Vec3(3,2,3)));
        p.setYRot(0);p.setXRot(0);p.yBodyRot=0;p.setOnGround(true);p.tickCount=100;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        for(var power:PowerForm.values())state.unlock(power);
        state.activate(form);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);
        com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT).reset();com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).reset();
        return p;
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void ownActionsAndSkeletonLoadWithoutCombatPrerequisites(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("epicfight")&&!ModList.get().isLoaded("wom"),
                "standalone runtime cannot load the removed combat prerequisites");
        MotionLibrary.requireInstalled();var skeleton=BlacklightSkeleton.get();
        for(var action:PlayerAction.values()) {
            String id=MotionLibrary.resolve(action);var clip=MotionLibrary.clip(id);
            h.assertTrue(clip.duration()>0,"independent action contains keyframes: "+action);
            var pose=MotionLibrary.pose(id,clip.duration()*.5F,false);
            for(var bone:skeleton.bones()) {
                var matrix=skeleton.getBoundTransformFor(pose,bone);
                h.assertTrue(Float.isFinite(matrix.m30())&&Float.isFinite(matrix.m31())&&Float.isFinite(matrix.m32()),
                        "own skeleton binds "+id+" "+bone.getName());
            }
        }
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void allFormsArmorShieldAndSensesRetainServerSelection(GameTestHelper h) {
        var p=player(h,"standalone-forms",PowerForm.NONE);
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,
                PowerForm.MUSCLE_MASS,PowerForm.BLADE,PowerForm.NONE}) {
            p.tickCount+=100;
            h.assertTrue(PowerFormSystem.select(p,form,false),"unlocked form can be selected: "+form);
            h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()==form,"server retains selected form: "+form);
        }
        p.tickCount+=100;PowerFormSystem.select(p,PowerForm.BLADE,false);p.tickCount+=100;
        double normalSpeed=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        h.assertTrue(PowerFormSystem.select(p,PowerForm.ARMOR,false)&&UtilityPowerSystem.active(p,PowerForm.ARMOR),"armor remains an independently activated mode");
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getActivePower()==PowerForm.BLADE,"armor coexists with blade");
        h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED)<normalSpeed
                &&p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)>=.6,"armor weight and stability remain authoritative");
        p.tickCount+=100;PowerFormSystem.select(p,PowerForm.THERMAL_VISION,false);p.tickCount+=100;
        h.assertTrue(UtilityPowerSystem.active(p,PowerForm.THERMAL_VISION)&&UtilityPowerSystem.active(p,PowerForm.ARMOR),"armor and thermal sense coexist");
        PowerFormSystem.select(p,PowerForm.SHIELD,false);p.tickCount+=100;
        h.assertTrue(UtilityPowerSystem.shieldReady(p)&&!UtilityPowerSystem.active(p,PowerForm.ARMOR),"shield replaces armor without replacing blade");
        PowerFormSystem.select(p,PowerForm.INFECTED_VISION,false);
        h.assertTrue(UtilityPowerSystem.active(p,PowerForm.INFECTED_VISION)&&!UtilityPowerSystem.active(p,PowerForm.THERMAL_VISION),"infected sense switches independently of shield and blade");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void armorReductionAndShieldDurabilityWorkWithoutCombatCapabilities(GameTestHelper h) {
        var p=player(h,"standalone-defense",PowerForm.BLADE,true);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.setHealth(p.getMaxHealth());
        var enemy=h.spawn(EntityType.ZOMBIE,3,2,5);enemy.setNoAi(true);enemy.setPos(p.position().add(0,0,2));
        var source=p.damageSources().mobAttack(enemy);
        float before=p.getHealth();p.hurt(source,8);
        float baseline=before-p.getHealth();
        h.assertTrue(baseline>0,"mob attack fixture must apply measurable unprotected damage");
        p.setHealth(p.getMaxHealth());p.invulnerableTime=0;p.setAbsorptionAmount(0);
        UtilityPowerSystem.toggle(p,PowerForm.ARMOR);before=p.getHealth();
        p.hurt(source,8);
        h.assertTrue(Math.abs(before-p.getHealth()-baseline*.525F)<.02,
                "armor preserves its actual damage ratio against the same mob source: baseline="+baseline+" protected="+(before-p.getHealth()));
        UtilityPowerSystem.toggle(p,PowerForm.SHIELD);p.invulnerableTime=0;
        p.setDeltaMovement(Vec3.ZERO);p.yBodyRot=0;before=p.getHealth();
        float shield=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE).shield();
        p.hurt(p.damageSources().mobAttack(enemy),4);
        h.assertTrue(p.getHealth()==before&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.UTILITY_STATE).shield()<shield,"front shield absorbs damage and spends durability");
        enemy.discard();h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void synchronizedActionsSeekForLateObserversAndClearWithMode(GameTestHelper h) {
        var p=player(h,"standalone-clock",PowerForm.CLAWS);
        PlayerActions.get().play(p,PlayerAction.CLAW_AUTO_1,0);
        var action=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_ACTION);
        h.assertTrue(!action.clipId().isEmpty()&&action.revision()>0,"server publishes own action");
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            NativeActionState.CODEC.encode(buffer,action);var observer=NativeActionState.CODEC.decode(buffer);
            h.assertTrue(observer.equals(action),"attachment protocol preserves clip identity and shared clock");
            h.assertTrue(Math.abs(observer.seconds(action.started()+5.5)-.275F)<.0001,"late observer seeks into existing action instead of restarting");
        } finally {buffer.release();}
        var enemy=h.spawn(EntityType.COW,3,2,5);enemy.setNoAi(true);CombatTargeting.requestLock(p,enemy.getId());
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.TARGET_LOCK_ID)==enemy.getId(),"target facing is synchronized by Blacklight");
        PlayerActions.get().synchronizeMode(p,false);
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_ACTION).clipId().isEmpty()
                &&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.TARGET_LOCK_ID)==-1,"ordinary mode clears transient action and target lock");
        enemy.discard();h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void serverClawRootStepStopsAtRealWorldCollision(GameTestHelper h) {
        var p=player(h,"standalone-collision",PowerForm.CLAWS);
        for(int x=0;x<9;x++)for(int y=2;y<6;y++)h.setBlock(x,y,4,Blocks.STONE);
        var combat=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CLAW_COMBAT);combat.begin(ClawCombatState.Action.AUTO_1);
        combat.setAttackDirection(0,1);PlayerActions.get().play(p,PlayerAction.CLAW_AUTO_1,0);
        double start=p.getZ(),wall=h.absoluteVec(new Vec3(0,0,4)).z;
        for(int tick=0;tick<10;tick++){combat.advance();NativeRootMotion.claw(p,combat);}
        h.assertTrue(p.getZ()>start+.05,"server genuinely executes the authored lunge");
        h.assertTrue(p.getBoundingBox().maxZ<=wall+.00001,"collision body cannot pass through a stone wall");
        combat.reset();NativePlayerActionBackend.clear(p);h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void muscleCaptureConsumeAndSuperArmorKeepTheirOwnLifecycle(GameTestHelper h) {
        var p=player(h,"standalone-consume",PowerForm.MUSCLE_MASS);p.setHealth(p.getMaxHealth()*.5F);
        var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);cow.setPos(p.position().add(0,0,1.5));
        ConsumeSystem.request(p);
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&com.codex.blacklight.registry.ModAttachments.getData(cow, ModAttachments.CONSUME_VISUAL).active(),"muscle capture claims actor and receiver");
        p.setDeltaMovement(Vec3.ZERO);p.knockback(2,1,0);
        h.assertTrue(p.getDeltaMovement().horizontalDistanceSqr()<.000001,"native capture super armor suppresses knockback");
        for(int tick=0;tick<ConsumeVisual.captureDuration(PowerForm.MUSCLE_MASS)+5;tick++) {
            p.tickCount++;ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        }
        h.assertTrue(ConsumeSystem.holding(p)&&cow.isAlive(),"capture reaches holding without consuming automatically");
        int biomass=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getBiomass(),ep=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints();
        ConsumeSystem.consume(p);ConsumeSystem.consume(p);
        for(int tick=0;tick<ConsumeVisual.duration(PowerForm.MUSCLE_MASS)+2;tick++) {
            p.tickCount++;ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        }
        h.assertTrue(cow.isRemoved()&&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING),"confirmed muscle consume completes and releases both actors");
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getBiomass()>biomass
                &&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints()>ep,"consume retains biomass and evolution rewards");
        int paid=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints();
        ConsumeSystem.consume(p);ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE).getEvolutionPoints()==paid,"repeated confirmation cannot award twice");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void wallRunStandsOnWallWithAuthoredStrideAndPlaneClearance(GameTestHelper h) {
        String run=com.codex.blacklight.movement.TraversalMotionSelector.wall(true,.55,0);
        String climb=com.codex.blacklight.movement.TraversalMotionSelector.wall(false,.14,0);
        h.assertTrue(run.equals("basic_wall_run_up")&&climb.equals("basic_wall_climb_up")&&!run.equals(climb),
                "RUN and CLIMB use distinct motion families: run="+run+" climb="+climb);
        var skeleton=BlacklightSkeleton.get();var runClip=MotionLibrary.clip(run);var climbClip=MotionLibrary.clip(climb);
        org.joml.Vector3f firstFoot=null;double maximumFootTravel=0,maximumPoseDifference=0;
        for(int frame=0;frame<24;frame++) {
            float phase=frame/24F;
            var pose=MotionLibrary.pose(run,runClip.duration()*phase,false);
            var beforeHead=skeleton.getBoundTransformFor(pose,skeleton.head);
            var beforeRoot=skeleton.getBoundTransformFor(pose,skeleton.rootJoint);
            var originalAxis=beforeHead.getTranslation(new org.joml.Vector3f()).sub(beforeRoot.getTranslation(new org.joml.Vector3f()));
            WallSurfacePose.orientRun(pose,skeleton,0,.55,.3F);
            var head=skeleton.getBoundTransformFor(pose,skeleton.head);
            var root=skeleton.getBoundTransformFor(pose,skeleton.rootJoint);
            var axis=head.getTranslation(new org.joml.Vector3f()).sub(root.getTranslation(new org.joml.Vector3f()));
            h.assertTrue(axis.z/axis.length()>.72F&&Math.abs(axis.length()-originalAxis.length())<.002
                            &&Math.abs(root.m31()-skeleton.rootJoint.getLocalTransform().m31())<.002,
                    "wall-running torso stands away from the wall without stretching or sinking: phase="+phase);
            var stopped=MotionLibrary.pose(run,runClip.duration()*phase,false);
            WallSurfacePose.orientRun(stopped,skeleton,0,
                    WallSurfacePose.normalizeRunVertical(0,-.0784,true,false),.3F);
            var stoppedHead=skeleton.getBoundTransformFor(stopped,skeleton.head);
            var stoppedRoot=skeleton.getBoundTransformFor(stopped,skeleton.rootJoint);
            h.assertTrue(stoppedHead.m31()-stoppedRoot.m31()>0&&stoppedHead.m32()-stoppedRoot.m32()>.2,
                    "stopped gravity cannot turn the complete runtime RUN pose upside down: phase="+phase);
            var foot=skeleton.getBoundTransformFor(pose,skeleton.legR).transformPosition(new org.joml.Vector3f(0,.43F,0));
            if(firstFoot==null)firstFoot=new org.joml.Vector3f(foot);
            maximumFootTravel=Math.max(maximumFootTravel,Math.hypot(foot.x-firstFoot.x,foot.y-firstFoot.y));
            var climbPose=MotionLibrary.pose(climb,climbClip.duration()*phase,false);
            var runningHand=skeleton.getBoundTransformFor(pose,skeleton.handR);
            var climbingHand=skeleton.getBoundTransformFor(climbPose,skeleton.handR);
            maximumPoseDifference=Math.max(maximumPoseDifference,Math.hypot(runningHand.m30()-climbingHand.m30(),runningHand.m31()-climbingHand.m31()));
            WallSurfacePose.clearSkin(pose,skeleton,.3F);
            h.assertTrue(WallSurfacePose.skinMinimumZ(pose,skeleton)>=-.296F,"head, hands and legs remain outside wall without pinning the stride");
        }
        h.assertTrue(maximumFootTravel>.08,"running feet must visibly cycle along the wall: travel="+maximumFootTravel);
        h.assertTrue(maximumPoseDifference>.08,"running counterbalance must differ from climbing hands: difference="+maximumPoseDifference);
        com.codex.blacklight.BlacklightNeoMod.LOGGER.info(
                "Standalone wall pose QA: RUN key={} seconds={} CLIMB key={} seconds={} footCycle={} handDifference={}",
                run,runClip.duration(),climb,climbClip.duration(),maximumFootTravel,maximumPoseDifference);
        h.succeed();
    }
}
