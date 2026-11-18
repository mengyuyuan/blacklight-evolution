package com.codex.blacklight.gametest;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HydraContactGameTest {
    private record Scene(BlacklightHydra actor,net.minecraft.world.entity.animal.Cow victim){}
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());m.setBodySize(2);m.setPos(h.absoluteVec(new Vec3(14,2,12)));m.setNoAi(true);m.setYRot(0);m.yBodyRot=0;h.getLevel().addFreshEntity(m);
        var v=h.spawn(EntityType.COW,new Vec3(14,2,22));v.setNoAi(true);v.setNoGravity(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(2000);return new Scene(m,v);
    }
    private static void start(GameTestHelper h,Scene s,int move){s.actor.setNoAi(false);h.assertTrue(s.actor.beginCombat(s.victim,move),"Hydra attack starts");}
    private static void atContact(Scene s,int move,float age,String joint){var b=HydraContact.boxes(move,age,joint,s.actor.position(),s.actor.getYRot(),s.actor.visualScale()).get(0);s.victim.setPos(b.center.add(0,-.6,0));s.victim.setDeltaMovement(Vec3.ZERO);}
    private static void end(GameTestHelper h,Scene s){s.actor.discard();s.victim.discard();h.succeed();}
    @GameTest(template="blackwatch_facility_arena",batch="hydra_sweep",timeoutTicks=85)public static void sweepWaitsForClawThenHitsOnce(GameTestHelper h){
        var s=scene(h);float[] after={0};h.runAfterDelay(5,()->start(h,s,9));
        h.runAfterDelay(27,()->{h.assertTrue(s.victim.getHealth()==2000,"windup has no damage");atContact(s,9,30,"Claw_L");});
        h.runAfterDelay(41,()->{after[0]=s.victim.getHealth();h.assertTrue(after[0]<2000,"moving claw hits its actual surface: "+s.actor.recoilStart()+" obstacle="+s.actor.obstaclePoint()+" root="+s.actor.position());h.assertTrue(2000-after[0]<=14.01,"overlapping claw records share one hit budget");});
        h.runAfterDelay(63,()->{h.assertTrue(s.victim.getHealth()==after[0],"no recovery damage");h.assertTrue(s.actor.groundImpacts()==0,"claw sweep is not a slam wave");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_side",timeoutTicks=105)public static void sideSweepHeadAndNeckDoNotMultiplyDamage(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,10));h.runAfterDelay(42,()->atContact(s,10,43,"Head"));
        h.runAfterDelay(70,()->{h.assertTrue(s.victim.getHealth()<2000,"moving head hits: "+s.actor.recoilStart()+" obstacle="+s.actor.obstaclePoint()+" root="+s.actor.position());h.assertTrue(2000-s.victim.getHealth()<=14.01,"seven source shapes share a blow");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_dodge",timeoutTicks=80)public static void DodgedClawDoesNotDamageTheOldRadius(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,9));h.runAfterDelay(27,()->s.victim.setPos(s.actor.position().add(0,0,2)));
        h.runAfterDelay(65,()->{h.assertTrue(s.victim.getHealth()==2000,"nearby but untouched target must miss");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_ground",timeoutTicks=95)public static void terrainWavesRequireActualDescentAndShareHitLedger(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,3));
        h.runAfterDelay(45,()->h.assertTrue(s.actor.groundImpacts()==0,"raised body has no ground eruption"));
        h.runAfterDelay(76,()->{h.assertTrue(s.actor.groundImpacts()==2,"body and head each contact terrain once: "+s.actor.groundImpacts()+" stop="+s.actor.recoilStart()+" obstacle="+s.actor.obstaclePoint()+" root="+s.actor.position());h.assertTrue(s.actor.lastGroundAge()>=49&&s.actor.lastGroundAge()<=60,"head lands after the body");h.assertTrue(s.actor.shockWaves()==3,"three source expansion windows");h.assertTrue(2000-s.victim.getHealth()<=14.01,"body and shock share one victim ledger");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_air",timeoutTicks=95)public static void noSupportMeansNoTerrainWave(GameTestHelper h){
        var s=scene(h);s.actor.setNoGravity(true);s.actor.setPos(s.actor.position().add(0,12,0));s.victim.setPos(s.victim.position().add(0,12,0));
        h.runAfterDelay(5,()->start(h,s,3));h.runAfterDelay(76,()->{h.assertTrue(s.actor.groundImpacts()==0&&s.actor.shockWaves()==0,"air slam cannot erupt distant floor");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_cancel",timeoutTicks=90)public static void interruptedAttackCannotLeaveDelayedDamage(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,3));h.runAfterDelay(40,()->{s.actor.cancelCombat();s.actor.setNoAi(true);});
        h.runAfterDelay(76,()->{h.assertTrue(s.victim.getHealth()==2000&&s.actor.groundImpacts()==0&&s.actor.shockWaves()==0,"interruption retires all contact events");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_immune",timeoutTicks=80)public static void damageImmunityIsRespected(GameTestHelper h){
        var s=scene(h);s.victim.setInvulnerable(true);h.runAfterDelay(5,()->start(h,s,9));h.runAfterDelay(27,()->atContact(s,9,30,"Claw_L"));
        h.runAfterDelay(65,()->{h.assertTrue(s.victim.getHealth()==2000,"contact cannot bypass damage immunity");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_wall",timeoutTicks=90)public static void wallInsertedAfterWindupBlocksContact(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,9));h.runAfterDelay(27,()->{
            atContact(s,9,30,"Claw_L");for(int x=0;x<32;x++)for(int y=2;y<24;y++)h.setBlock(x,y,19,Blocks.STONE);
        });h.runAfterDelay(65,()->{h.assertTrue(s.victim.getHealth()==2000,"actual attack cannot reach through an intervening solid wall");h.assertTrue(s.actor.wallImpacts()==1,"one physical obstacle contact");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_wall_recoil",timeoutTicks=145)public static void slammedHeadRecoilsWithoutCrossingTheWall(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,3));
        h.runAfterDelay(40,()->{for(int x=0;x<32;x++)for(int y=2;y<24;y++)h.setBlock(x,y,18,Blocks.STONE);});
        var corner=h.absoluteVec(new Vec3(0,2,18));var wall=new net.minecraft.world.phys.AABB(corner,corner.add(32,22,1));
        for(int t=41;t<105;t++){final int tick=t;h.runAfterDelay(t,()->{
            var boxes=HydraContact.body(s.actor.motionPose(0),s.actor.position(),s.actor.getYRot(),s.actor.visualScale());
            h.assertTrue(boxes.stream().noneMatch(b->b.touches(wall.deflate(.002))),"no mesh penetration at tick "+tick);
        });}
        h.runAfterDelay(110,()->{h.assertTrue(s.actor.wallImpacts()==1&&s.actor.action()==0,"contact recoils and recovers to idle");h.assertTrue(s.victim.getHealth()==2000&&s.actor.shockWaves()==0,"blocked attack cannot leave body hits or delayed waves");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_cancel_recoil",timeoutTicks=105)public static void interruptionRewindsPoseAndReturnsToIdle(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(5,()->start(h,s,3));
        float[] start={0};h.runAfterDelay(40,()->{start[0]=s.actor.combatPoseAge(0);s.actor.cancelCombat();s.actor.setNoAi(true);});
        h.runAfterDelay(50,()->h.assertTrue(s.actor.combatPoseAge(0)<start[0]&&s.actor.action()==3,"interruption visibly retracts instead of teleporting to idle"));
        h.runAfterDelay(95,()->{h.assertTrue(s.actor.action()==0&&s.actor.wallImpacts()==0&&s.actor.groundImpacts()==0&&s.actor.shockWaves()==0&&s.victim.getHealth()==2000,"clean interrupted recovery");end(h,s);});
    }
    @GameTest(template="blackwatch_facility_arena",batch="hydra_plant",timeoutTicks=30)public static void plantedGatewayCannotEnterWildCombat(GameTestHelper h){
        var s=scene(h);s.actor.plant(java.util.UUID.randomUUID());s.actor.setNoAi(false);
        h.assertTrue(!s.actor.beginCombat(s.victim,3)&&!s.actor.beginCombat(s.victim,9)&&!s.actor.beginCombat(s.victim,10),"player gateway must stay out of wild attack AI");end(h,s);
    }
}
