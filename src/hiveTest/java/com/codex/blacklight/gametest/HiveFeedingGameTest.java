package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Isolated opt-in server tests; no player saves, commands or release assets are changed. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HiveFeedingGameTest {
    private record Scene(BlacklightInfectedHost host, BlacklightHive core, Mob prey, BlockPos home) {
        void cleanup(){DevourerFeedingSystem.release(host);host.discard();prey.discard();core.discard();
            var level=(net.minecraft.server.level.ServerLevel)host.level();
            for(int x=(home.getX()-42)>>4;x<=(home.getX()+42)>>4;x++)for(int z=(home.getZ()-42)>>4;z<=(home.getZ()+42)>>4;z++)level.setChunkForced(x,z,false);
        }
    }
    private static Scene scene(GameTestHelper h,int index) {
        var level=h.getLevel();level.getServer().setDifficulty(Difficulty.NORMAL,true);
        BlockPos home=h.absolutePos(new BlockPos(96+index*160,40,96));
        for(int x=(home.getX()-42)>>4;x<=(home.getX()+42)>>4;x++)for(int z=(home.getZ()-42)>>4;z<=(home.getZ()+42)>>4;z++)level.setChunkForced(x,z,true);
        for(int x=-42;x<=42;x++)for(int z=-42;z<=42;z++) {
            BlockPos pos=home.offset(x,-1,z);level.getChunk(pos);
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<7;y++)level.setBlock(pos.above(y+1),Blocks.AIR.defaultBlockState(),3);
        }
        for(int i=0;i<4;i++)level.setBlock(HiveFeedingMotion.node(home,i),ModBlocks.NUTRIENT_NODE.get().defaultBlockState(),3);
        var core=ModEntities.BLACKLIGHT_HIVE.get().create(level);
        core.moveTo(home.getX()+28.5,home.getY(),home.getZ()+8.5,0,0);
        core.getPersistentData().putBoolean("BlacklightColossalHive",true);
        core.getPersistentData().putLong("BlacklightHiveHome",home.asLong());level.addFreshEntity(core);
        var host=ModEntities.INFECTED_HOST.get().create(level);
        host.inherit(EntityType.IRON_GOLEM.create(level),false);
        host.moveTo(home.getX()+.5,home.getY(),home.getZ()+.5,0,0);
        host.getPersistentData().putBoolean("BlacklightDevourer",true);
        host.getPersistentData().putLong("BlacklightDevourHome",home.asLong());
        host.getPersistentData().putLong("BlacklightDevourNextFeed",0);level.addFreshEntity(host);
        Mob prey=EntityType.COW.create(level);
        prey.moveTo(home.getX()-15.5,home.getY(),home.getZ()+.5,0,0);
        prey.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);level.addFreshEntity(prey);
        return new Scene(host,core,prey,home);
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="hive_feeding_footprint")
    public static void fixedHiveFeedsOnceWithoutMovingBoss(GameTestHelper h){
        var s=scene(h,0);Vec3 start=s.host.position();
        h.startSequence().thenWaitUntil(()->h.assertTrue(DevourerFeedingSystem.active(s.host)&&s.host.feedingStarted()>=0,"waiting for actual feeding before timing stages"))
            .thenExecute(()->{
        h.runAfterDelay(35,()->{
            h.assertTrue(s.host.feedingNode()==0&&s.prey.isNoGravity(),"fixed western port must capture its nearby prey");
            h.assertTrue(DevourerFeedingSystem.biomass(s.host)==0,"no early biomass during capture");
            h.assertTrue(s.prey.getY()>s.home.getY()+.3,"prey must lift before the horizontal pull");
        });
        h.runAfterDelay(155,()->{
            h.assertTrue(!s.prey.isAlive(),"absorption must finish");
            h.assertTrue(DevourerFeedingSystem.meals(s.host)==1&&DevourerFeedingSystem.biomass(s.host)==2,"one cow credits exactly one meal and two biomass units");
            h.assertTrue(s.host.position().distanceToSqr(start)<.01,"boss must not chase or lift prey: displacement="+s.host.position().subtract(start));
            h.assertTrue(!DevourerFeedingSystem.active(s.host),"session must be released after completion");s.cleanup();h.succeed();
        });
        });
    }
    @GameTest(template="wall_test",timeoutTicks=160,batch="hive_feeding_footprint")
    public static void severedTendrilReleasesPreyWithoutReward(GameTestHelper h){
        var s=scene(h,1);
        // Forced chunks can activate after fixture creation. Cut during actual
        // feeding, not at an arbitrary global tick before the cable exists.
        h.startSequence().thenWaitUntil(()->h.assertTrue(DevourerFeedingSystem.active(s.host)&&s.host.feedingStarted()>=0,"waiting for live feeding cable"))
            .thenExecute(()->h.runAfterDelay(30,()->{
            var parts=h.getLevel().getEntitiesOfClass(HiveTendril.class,s.host.getBoundingBox().inflate(40));
            h.assertTrue(parts.size()==3,"three damageable tissue knots must exist: actual="+parts.size()+" feedingStarted="+s.host.feedingStarted()+" now="+h.getLevel().getGameTime());
            h.assertTrue(parts.get(0).hurt(parts.get(0).damageSources().generic(),2),"tendril hit must be accepted");
            h.assertTrue(s.prey.isAlive()&&!s.prey.isNoGravity(),"cut cable restores living prey gravity");
            h.assertTrue(!DevourerFeedingSystem.active(s.host)&&DevourerFeedingSystem.meals(s.host)==0,"cut must release claim without rewards");
            h.assertTrue(parts.stream().allMatch(HiveTendril::isRemoved),"all temporary hitboxes must be discarded");s.cleanup();h.succeed();
        }));
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="hive_feeding_footprint")
    public static void brokenActivePortAbortsEvenWithOtherNodes(GameTestHelper h){
        var s=scene(h,2);
        h.runAfterDelay(45,()->h.getLevel().setBlock(HiveFeedingMotion.node(s.home,0),Blocks.AIR.defaultBlockState(),3));
        h.runAfterDelay(48,()->{
            h.assertTrue(DevourerHabitat.liveNodes(s.host)==3,"remaining supply is still sufficient");
            h.assertTrue(s.prey.isAlive()&&!s.prey.isNoGravity()&&!DevourerFeedingSystem.active(s.host),"destroyed active mouth must release immediately");
            h.assertTrue(DevourerFeedingSystem.biomass(s.host)==0,"destroyed mouth grants no credit");s.cleanup();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=150,batch="hive_feeding_footprint")
    public static void solidWallPreventsAcquisition(GameTestHelper h){
        var s=scene(h,3);
        // Leave only western/eastern ports supplied; the eastern one is outside reach.
        h.getLevel().setBlock(HiveFeedingMotion.node(s.home,2),Blocks.AIR.defaultBlockState(),3);
        h.getLevel().setBlock(HiveFeedingMotion.node(s.home,3),Blocks.AIR.defaultBlockState(),3);
        for(int z=-4;z<=4;z++)for(int y=0;y<6;y++)h.getLevel().setBlock(s.home.offset(-21,y,z),Blocks.STONE.defaultBlockState(),3);
        h.runAfterDelay(55,()->{
            h.assertTrue(!DevourerFeedingSystem.active(s.host)&&s.prey.isAlive(),"port cannot acquire through a wall");
            h.assertTrue(!s.prey.isNoGravity()&&DevourerFeedingSystem.meals(s.host)==0,"occluded prey stays free");s.cleanup();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=150,batch="hive_feeding_footprint")
    public static void disappearingCoreRestoresExistingGravityFlag(GameTestHelper h){
        var s=scene(h,4);s.prey.setNoGravity(true);
        h.runAfterDelay(40,()->s.core.discard());
        h.runAfterDelay(43,()->{
            h.assertTrue(!DevourerFeedingSystem.active(s.host)&&s.prey.isAlive(),"removed core cancels feeding");
            h.assertTrue(s.prey.isNoGravity(),"release must preserve prey's pre-existing no-gravity flag");
            h.assertTrue(!s.prey.getPersistentData().contains("BlacklightDevourClaim"),"no stale saved claim may survive");s.cleanup();h.succeed();
        });
    }
}
