package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HunterContactGameTest {
    private record Scene(BlacklightHunter hunter,Mob victim) {}
    private static Scene scene(GameTestHelper h,double distance){
        for(int x=1;x<20;x++)for(int z=1;z<20;z++)h.setBlock(x,1,z,Blocks.STONE);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),new Vec3(9,2,7));
        hunter.goalSelector.removeAllGoals(g->true);hunter.targetSelector.removeAllGoals(g->true);
        hunter.setNoGravity(true);hunter.setPersistenceRequired();
        var victim=h.spawn(EntityType.COW,new Vec3(9,2,7+distance));victim.setNoAi(true);victim.setNoGravity(true);
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);victim.setHealth(1000);
        victim.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return new Scene(hunter,victim);
    }
    private static void finish(GameTestHelper h,Scene s){s.hunter.discard();s.victim.discard();h.succeed();}
    private static void connects(GameTestHelper h,CreatureAttack move){
        var s=scene(h,1.5);h.assertTrue(s.hunter.beginAttack(move,s.victim),"start "+move);
        h.runAfterDelay(8,()->h.assertTrue(s.victim.getHealth()==1000,"telegraph is harmless"));
        h.runAfterDelay(move.ticks+2,()->{h.assertTrue(s.victim.getHealth()<1000,"visible wrist must connect: "+move);h.assertTrue(!s.hunter.committedAttack(),"recovery completes");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_swipe",timeoutTicks=65)public static void swipeConnectsAtVisibleClaw(GameTestHelper h){connects(h,CreatureAttack.SWIPE);}
    @GameTest(template="blackwatch_arena",batch="h_barrage",timeoutTicks=80)public static void barrageConnectsAtVisibleClaws(GameTestHelper h){connects(h,CreatureAttack.BARRAGE);}
    @GameTest(template="blackwatch_arena",batch="h_slam",timeoutTicks=65)public static void slamConnectsAtVisibleFist(GameTestHelper h){connects(h,CreatureAttack.SLAM);}
    @GameTest(template="blackwatch_arena",batch="h_autonomous",timeoutTicks=200)public static void autonomousHunterClosesTheGapBeforeStriking(GameTestHelper h){
        var scene=scene(h,3.3);var start=scene.hunter.position();scene.hunter.discard();
        var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());hunter.setPos(start);h.getLevel().addFreshEntity(hunter);
        boolean[] done={false};h.onEachTick(()->{
            if(done[0]||scene.victim.getHealth()>=1000)return;
            done[0]=true;h.assertTrue(hunter.position().distanceTo(start)>.6,"AI must approach beyond the old distance-only trigger");
            hunter.discard();scene.victim.discard();h.succeed();
        });
    }
    @GameTest(template="blackwatch_arena",batch="h_ground",timeoutTicks=65)public static void slamGroundPulseWaitsForTerrainContact(GameTestHelper h){
        var s=scene(h,1.5);h.assertTrue(s.hunter.beginAttack(CreatureAttack.SLAM,s.victim),"slam starts");
        h.runAfterDelay(11,()->h.assertTrue(s.hunter.groundImpactPoint()==null,"no ground pulse during windup"));
        h.runAfterDelay(22,()->{h.assertTrue(s.hunter.groundImpactPoint()!=null,"fist finds the actual fixture floor");h.assertTrue(Math.abs(s.hunter.groundImpactPoint().y-s.hunter.getY())<.1,"material contact stays on the terrain surface");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_miss",timeoutTicks=65)public static void oldRangeConeCannotDamageBeyondTheClaw(GameTestHelper h){
        var s=scene(h,3.3);h.assertTrue(s.hunter.beginAttack(CreatureAttack.SWIPE,s.victim),"commits while in old cone");
        h.runAfterDelay(40,()->{h.assertTrue(s.victim.getHealth()==1000,"no remote melee damage");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_evade",timeoutTicks=65)public static void targetCanEvadeTheCommittedSweep(GameTestHelper h){
        var s=scene(h,1.5);h.assertTrue(s.hunter.beginAttack(CreatureAttack.SWIPE,s.victim),"commits");
        h.runAfterDelay(7,()->s.victim.setPos(s.hunter.position().add(0,0,-1.5)));
        h.runAfterDelay(40,()->{h.assertTrue(s.victim.getHealth()==1000,"no tracking through the back during release");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_wall",timeoutTicks=65)public static void newWallBlocksACommittedSweep(GameTestHelper h){
        var s=scene(h,2.1);s.hunter.setPos(s.hunter.position().add(0,0,.4));s.victim.setPos(s.victim.position().add(0,0,.4));
        h.assertTrue(s.hunter.beginAttack(CreatureAttack.SWIPE,s.victim),"commits with sight");
        h.runAfterDelay(5,()->{for(int x=6;x<=12;x++)for(int y=2;y<=6;y++)h.setBlock(x,y,8,Blocks.STONE);});
        h.runAfterDelay(40,()->{h.assertTrue(s.victim.getHealth()==1000,"fresh wall blocks wrist damage");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_interrupt",timeoutTicks=80)public static void interruptionCancelsAllFiveWindows(GameTestHelper h){
        var s=scene(h,1.5);h.assertTrue(s.hunter.beginAttack(CreatureAttack.BARRAGE,s.victim),"barrage starts");
        h.runAfterDelay(5,()->s.hunter.setNoAi(true));
        h.runAfterDelay(60,()->{h.assertTrue(s.victim.getHealth()==1000&&!s.hunter.committedAttack(),"no queued damage after cancel");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="h_immune",timeoutTicks=80)public static void acceptedHitStillHonorsVictimInvulnerability(GameTestHelper h){
        var s=scene(h,1.5);h.assertTrue(s.hunter.beginAttack(CreatureAttack.BARRAGE,s.victim),"barrage starts");s.victim.setInvulnerable(true);
        h.runAfterDelay(60,()->{h.assertTrue(s.victim.getHealth()==1000,"contact never bypasses invulnerability");h.assertTrue(s.hunter.damageContact()==null,"transient wound location is cleared");finish(h,s);});
    }
}
