package com.codex.blacklight.gametest;

import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class StreetInfectedGameTest {
    @GameTest(template="lost_convoy",batch="street_predators",timeoutTicks=240)
    public static void hunterFindsPatrolThenLosesItBehindCover(GameTestHelper h) {
        var level = h.getLevel();
        var floor = h.absolutePos(new BlockPos(16,32,16));
        for (var at : BlockPos.betweenClosed(floor.offset(-6,0,-5),floor.offset(6,0,19)))
            level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
        var hunter = h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),16,33,16);
        var soldier = h.spawn(ModEntities.BLACKWATCH_SOLDIER.get(),16,33,29);
        soldier.setNoAi(true);
        h.startSequence().thenWaitUntil(()->h.assertTrue(hunter.getTarget()==soldier,"hunter acquires a live patrol without being attacked"))
                .thenExecute(()->{
                    // Place cover between the two; keep an opening outside their sightline.
                    for (var at : BlockPos.betweenClosed(floor.offset(-5,1,9),floor.offset(5,9,9)))
                        level.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
                    soldier.moveTo(floor.getX()+4.5,floor.getY()+1,floor.getZ()+15.5,0,0);
                }).thenIdle(8).thenExecute(()->{
                    var path=hunter.getNavigation().getPath();
                    h.assertTrue(!hunter.hasLineOfSight(soldier),"cover blocks sight");
                    h.assertTrue(path==null || !path.getTarget().equals(soldier.blockPosition()),"hidden movements do not update pursuit waypoint");
                }).thenWaitUntil(()->h.assertTrue(hunter.getTarget()!=soldier,"loss of sight ends pursuit"))
                .thenExecute(()->{hunter.discard();soldier.discard();}).thenSucceed();
    }

    @GameTest(template="lost_convoy",batch="street_alarm",timeoutTicks=80)
    public static void woundedInfectedRalliesVisibleNeighbours(GameTestHelper h) {
        var first=h.spawn(ModEntities.COMMON_INFECTED.get(),15,24,15);
        var witness=h.spawn(ModEntities.COMMON_INFECTED.get(),18,24,16);
        var hidden=h.spawn(ModEntities.COMMON_INFECTED.get(),10,24,16);
        var attacker=h.spawn(EntityType.COW,16,24,18);
        for (int x=7;x<=21;x++) for (int z=12;z<=21;z++) h.setBlock(x,23,z,Blocks.STONE);
        for (int z=12;z<=21;z++) for(int y=24;y<=28;y++) h.setBlock(12,y,z,Blocks.STONE);
        // Disable independent target scans so only the injured creature's alert can recruit them.
        witness.targetSelector.removeAllGoals(g->true);
        hidden.targetSelector.removeAllGoals(g->true);
        hidden.goalSelector.removeAllGoals(g->true);
        attacker.setNoAi(true);
        h.runAfterDelay(4,()->first.hurt(h.getLevel().damageSources().mobAttack(attacker),1));
        h.startSequence().thenWaitUntil(()->h.assertTrue(witness.getTarget()==attacker,"idle witness joins retaliation"))
                .thenExecute(()->{
                    h.assertTrue(hidden.getTarget()==null,"alarm does not reveal someone through a wall");
                    first.discard();witness.discard();hidden.discard();attacker.discard();
                }).thenSucceed();
    }
}
