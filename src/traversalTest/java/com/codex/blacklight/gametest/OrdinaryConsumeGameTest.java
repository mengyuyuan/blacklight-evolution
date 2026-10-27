package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class OrdinaryConsumeGameTest {
    private static ServerPlayer player(GameTestHelper h, PowerForm form) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"form-consume"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3))); p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(form);s.activate(form);
        p.setData(ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p) { p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p)); }

    @GameTest(template="wall_test", timeoutTicks=30)
    public static void everyFormConsumesPassiveAndRewardsOnce(GameTestHelper h) {
        for (var form : new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.BLADE,PowerForm.WHIPFIST}) {
            var p=player(h,form);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
            ConsumeSystem.request(p);var visual=cow.getData(ModAttachments.CONSUME_VISUAL);
            h.assertTrue(visual.active() && visual.form()==form.ordinal(),"full-health cow accepted with form "+form);
            for(int i=0;i<ConsumeVisual.captureDuration(form)+80;i++)tick(p);
            h.assertTrue(ConsumeSystem.holding(p)&&cow.isAlive()&&p.getHealth()==10,"E must hold indefinitely without reward "+form);
            ConsumeSystem.consume(p);ConsumeSystem.consume(p);
            for(int i=0;i<ConsumeSequence.completionTick(form,false)-1;i++)tick(p);
            h.assertTrue(!cow.isRemoved()&&p.getHealth()==10,"no early award in "+form);
            tick(p);tick(p);
            h.assertTrue(cow.isRemoved()&&!p.getData(ModAttachments.CONSUMING),"complete releases target and player "+form);
            h.assertTrue(p.getHealth()==12&&p.getData(ModAttachments.PLAYER_STATE).getBiomass()==270
                    &&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==2,"cow reward once, not hunter reward "+form);
        }
        h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void hammerPairedSceneKeepsVictimUntilCompletion(GameTestHelper h) {
        var p=player(h,PowerForm.HAMMERFISTS);var pig=h.spawn(EntityType.PIG,3,2,5);pig.setNoAi(true);
        ConsumeSystem.request(p);
        for(int i=0;i<ConsumeVisual.captureDuration(PowerForm.HAMMERFISTS);i++)tick(p);
        h.assertTrue(ConsumeSystem.holding(p),"pig must reach held pose");
        double heldHeight=pig.getY();
        h.assertTrue(heldHeight>p.getY()+.3,"small target must visibly hang in the restraint");
        ConsumeSystem.consume(p);var origin=p.position();
        int complete=ConsumeSequence.completionTick(PowerForm.HAMMERFISTS,false);
        for(int i=1;i<complete;i++) {
            tick(p);
            h.assertTrue(pig.isAlive()&&p.getHealth()==10,"paired hammer receiver must survive until the completion cue");
            h.assertTrue(pig.position().distanceTo(origin)<8,"hammer paired scene stays local");
        }
        tick(p);tick(p);
        h.assertTrue(pig.isRemoved()&&!p.getData(ModAttachments.CONSUMING)&&p.getHealth()>10,"hammer completion releases receiver and pays once");
        h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void normalEnemyNeedsWeakeningAndClaimsAreExclusive(GameTestHelper h) {
        var p=player(h,PowerForm.BLADE);var z=h.spawn(EntityType.IRON_GOLEM,3,2,5);z.setNoAi(true);
        ConsumeSystem.request(p);h.assertTrue(!p.getData(ModAttachments.CONSUMING),"healthy hostile rejected");
        z.setHealth(4);ConsumeSystem.request(p);h.assertTrue(p.getData(ModAttachments.CONSUMING),"weak hostile accepted");
        var other=player(h,PowerForm.CLAWS);ConsumeSystem.request(other);
        h.assertTrue(!other.getData(ModAttachments.CONSUMING),"ordinary victims cannot be stolen");
        ConsumeSystem.cancel(p);z.discard();h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void captureDoesNotPersistNoAiAndDamageRestoresMob(GameTestHelper h) {
        var p=player(h,PowerForm.HAMMERFISTS);var cow=h.spawn(EntityType.COW,3,2,5);
        ConsumeSystem.request(p);var event=new EntityTickEvent.Pre(cow);ConsumeSystem.freeze(event);
        h.assertTrue(event.isCanceled(),"captured AI is frozen");
        var tag=new net.minecraft.nbt.CompoundTag();cow.saveWithoutId(tag);
        var restored=EntityType.COW.create(h.getLevel());restored.load(tag);
        h.assertTrue(!restored.isNoAi()&&!restored.getData(ModAttachments.CONSUME_VISUAL).active(),"reload must not save AI lock or visual capture");
        cow.hurt(cow.damageSources().generic(),1);
        h.assertTrue(p.getData(ModAttachments.CONSUMING)&&!cow.isNoAi(),"RC49 nonlethal damage preserves capture without persisting NoAI");
        cow.kill(); tick(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"death releases capture");
        event=new EntityTickEvent.Pre(cow);ConsumeSystem.freeze(event);
        h.assertTrue(!event.isCanceled()&&p.getHealth()==10,"normal ticks resume without reward");cow.discard();h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void whipPullUsesCollisionsAndCannotFinishAcrossWall(GameTestHelper h) {
        var p=player(h,PowerForm.WHIPFIST);var cow=h.spawn(EntityType.COW,3,2,8);cow.setNoAi(true);
        ConsumeSystem.request(p);h.assertTrue(p.getData(ModAttachments.CONSUMING),"whip reaches five blocks");
        Vec3 before=cow.position();for(int i=0;i<13;i++)tick(p);
        h.assertTrue(cow.position().distanceToSqr(before)<.0001,"victim must not slide before the whip arrives");
        for(int i=0;i<17;i++)tick(p);
        h.assertTrue(p.distanceToSqr(cow)<2,"whip reels victim into contact");ConsumeSystem.cancel(p);
        cow.setPos(h.absoluteVec(new Vec3(3,2,8)));ConsumeSystem.request(p);
        for(int y=2;y<5;y++)h.setBlock(3,y,5,net.minecraft.world.level.block.Blocks.STONE);
        tick(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&cow.isAlive()&&p.getHealth()==10,"wall interrupts, no remote reward");cow.discard();h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void whipGrabsAtTwelveBlocksButNotBeyondAndReelsInBeforeConsuming(GameTestHelper h) {
        var p=player(h,PowerForm.WHIPFIST);
        // Use an elevated isolated lane so neighbouring template walls cannot
        // accidentally make this a line-of-sight test instead of a range test.
        p.setPos(p.position().add(0,20,0));
        // World terrain can still cross this elevated lane. Explicitly clear
        // the small test corridor; obstruction behaviour has its own test.
        for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=14;z++)
            h.getLevel().setBlockAndUpdate(p.blockPosition().offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var lane=new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(p.position().add(0,0,12.6)));boolean forcedHere=!h.getLevel().getForcedChunks().contains(lane.toLong());if(forcedHere)h.getLevel().setChunkForced(lane.x,lane.z,true);h.getLevel().getChunk(lane.x,lane.z);
        h.runAfterDelay(4,()->{
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);
        cow.setPos(p.position().add(0,0,12.6));var chunk=new net.minecraft.world.level.ChunkPos(cow.blockPosition());h.getLevel().setChunkForced(chunk.x,chunk.z,true);h.getLevel().getChunk(chunk.x,chunk.z);h.getLevel().addFreshEntity(cow);
        ConsumeSystem.request(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"beyond twelve blocks must be rejected");
        cow.setPos(p.position().add(0,0,12));ConsumeSystem.request(p);
        h.assertTrue(p.getData(ModAttachments.CONSUMING),"twelve-block target must be captured; ground="+p.onGround()
                +" distance="+p.distanceToSqr(cow)+" eligible="+ConsumeSystem.eligible(cow)
                +" dot="+p.getLookAngle().dot(cow.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize())
                +" query="+p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(12)).size()
                +" ray="+p.level().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),cow.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getType());
        var before=cow.position();for(int i=0;i<13;i++)tick(p);
        h.assertTrue(cow.position().distanceToSqr(before)<.0001,"long-range target waits for whip contact");
        for(int i=13;i<36;i++)tick(p);
        h.assertTrue(ConsumeSystem.holding(p)&&p.distanceToSqr(cow)<2,"reel-in must finish inside the capture animation");
        ConsumeSystem.consume(p);for(int i=0;i<ConsumeSequence.duration(PowerForm.WHIPFIST,false);i++)tick(p);
        h.assertTrue(cow.isRemoved()&&p.getHealth()>10,"long-range capture can then be consumed at hand");
        if(forcedHere)h.getLevel().setChunkForced(lane.x,lane.z,false);h.succeed();
        });
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void petsAndMountedAnimalsAreProtectedWithoutSpeciesWhitelist(GameTestHelper h) {
        var wolf=h.spawn(EntityType.WOLF,3,2,5);wolf.setTame(true,true);
        var golem=h.spawn(EntityType.IRON_GOLEM,5,2,5);var skeleton=h.spawn(EntityType.SKELETON,6,2,5);
        var cow=h.spawn(EntityType.COW,3,2,6);var chicken=h.spawn(EntityType.CHICKEN,3,2,6);chicken.startRiding(cow,true);
        h.assertTrue(!ConsumeSystem.eligible(wolf)&&ConsumeSystem.eligible(golem)&&ConsumeSystem.eligible(skeleton)
                &&!ConsumeSystem.eligible(cow)&&!ConsumeSystem.eligible(chicken),"protect pets and mounted pairs while accepting current consumable species");
        wolf.discard();golem.discard();skeleton.discard();chicken.discard();cow.discard();h.succeed();
    }
    @GameTest(template="wall_test", timeoutTicks=30)
    public static void formChangeAndVictimRemovalCancelWithoutReward(GameTestHelper h) {
        var p=player(h,PowerForm.HAMMERFISTS);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);
        h.assertTrue(!PowerFormSystem.select(p,PowerForm.CLAWS,false),"cannot switch weapons while consuming");
        p.getData(ModAttachments.PLAYER_STATE).activate(PowerForm.CLAWS);tick(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&!cow.getData(ModAttachments.CONSUME_VISUAL).active(),"external form changes release claim");
        ConsumeSystem.request(p);cow.discard();tick(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&p.getHealth()==10,"removed victims pay nothing");h.succeed();
    }
}
