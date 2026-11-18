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
public final class JuggernautContactGameTest {
    private record Scene(EncounterMob actor,net.minecraft.world.entity.animal.Pig victim){}
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<13;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var m=EncounterEntities.CREATURES.get(EncounterKind.JUGGERNAUT).get().create(h.getLevel());m.setPos(h.absoluteVec(new Vec3(8,2,5)));m.setPersistenceRequired();
        m.goalSelector.removeAllGoals(g->true);m.targetSelector.removeAllGoals(g->true);m.setYRot(0);m.yBodyRot=0;h.getLevel().addFreshEntity(m);
        var v=h.spawn(EntityType.PIG,new Vec3(8,2,8));v.setNoAi(true);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);v.setHealth(2000);return new Scene(m,v);
    }
    private static void end(GameTestHelper h,Scene s){s.actor.discard();s.victim.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="j_combo",timeoutTicks=120)public static void twoHandsHitOnceEach(GameTestHelper h){
        var s=scene(h);float[] first={0},second={0};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.victim),"combo starts in actual reach"));
        h.runAfterDelay(38,()->h.assertTrue(s.victim.getHealth()==2000,"windup cannot damage"));
        // Move the target to the second hand's visible path; do not force damage or reset i-frames.
        h.runAfterDelay(48,()->{first[0]=s.victim.getHealth();h.assertTrue(first[0]<2000,"first hand hit");var p=JuggernautContact.fist(EncounterMove.HAMMER,39,false,s.actor.position(),s.actor.getYRot()).center();s.victim.setPos(p.x,p.y-.35,p.z);s.victim.setNoGravity(true);});
        h.runAfterDelay(72,()->{second[0]=s.victim.getHealth();h.assertTrue(second[0]<first[0],"second hand hit");h.assertTrue(Math.abs((2000-first[0])-(first[0]-second[0]))<.01,"one accepted hit per release");});
        h.runAfterDelay(104,()->{h.assertTrue(s.victim.getHealth()==second[0],"recovery cannot repeat damage");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="j_slam",timeoutTicks=120)public static void slamGroundWaitsForFistsAndDoesNotRepeat(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.SLAM,s.victim),"slam starts"));
        h.runAfterDelay(65,()->h.assertTrue(s.actor.groundImpacts()==0&&s.victim.getHealth()==2000,"raised fists have no ground effect"));
        h.runAfterDelay(70,()->h.assertTrue(h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,s.actor.getBoundingBox().inflate(6)).isEmpty(),"ground debris is presented on contact frame, not spawned ahead as a server display"));
        h.runAfterDelay(105,()->{h.assertTrue(s.actor.groundImpacts()==1,"one ground impact: "+s.actor.groundImpacts());h.assertTrue(s.actor.lastGroundAge()>=42&&s.actor.lastGroundAge()<=48,"impact follows descent: "+s.actor.lastGroundAge());h.assertTrue(s.victim.getHealth()<2000,"grounded nearby target reacts; at="+s.actor.lastGroundPoint()+" victim="+s.victim.position()+" ground="+s.victim.onGround()+" enemy="+s.actor.enemy(s.victim)+" sight="+s.actor.hasLineOfSight(s.victim));end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="j_air",timeoutTicks=120)public static void noTerrainNoGroundShock(GameTestHelper h){
        var s=scene(h);s.actor.setNoGravity(true);s.victim.setNoGravity(true);s.actor.setPos(s.actor.position().add(0,5,0));s.victim.setPos(s.victim.position().add(0,5,0));
        long[] started={-1};boolean[] ended={false};
        h.onEachTick(()->{if(ended[0]||s.actor.tickCount<25)return;
            if(started[0]<0){h.assertTrue(s.actor.begin(EncounterMove.SLAM,s.victim),"air slam starts after actor warmup, ticks="+s.actor.tickCount);started[0]=h.getLevel().getGameTime();}
            if(h.getLevel().getGameTime()-started[0]>=80){ended[0]=true;h.assertTrue(s.actor.groundImpacts()==0,"no artificial ground event in air");end(h,s);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="j_cancel",timeoutTicks=120)public static void interruptionCancelsFutureHands(GameTestHelper h){
        var s=scene(h);long[] started={-1};boolean[] ended={false};
        h.onEachTick(()->{if(ended[0]||s.actor.tickCount<25)return;
            if(started[0]<0){h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.victim),"combo begins after actor warmup");started[0]=h.getLevel().getGameTime();}
            long elapsed=h.getLevel().getGameTime()-started[0];if(elapsed==11)s.actor.cancelAttack();
            if(elapsed>=80){ended[0]=true;h.assertTrue(s.victim.getHealth()==2000&&s.actor.move()==EncounterMove.NONE,"cancelled attack has no delayed hits");end(h,s);}
        });
    }
    @GameTest(template="blackwatch_arena",batch="j_dodge",timeoutTicks=120)public static void dodgingBothHandsMisses(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.victim),"combo begins"));
        h.runAfterDelay(37,()->s.victim.setPos(s.victim.position().add(4,0,0)));
        h.runAfterDelay(105,()->{h.assertTrue(s.victim.getHealth()==2000,"old wide arc does not grant damage");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="j_guard",timeoutTicks=150)public static void frontalBlockReactsAndBreakOpensPunishWindow(GameTestHelper h){
        var s=scene(h);float[] before={0};h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.GUARD,s.victim),"guard starts"));
        h.runAfterDelay(41,()->{before[0]=s.actor.getHealth();h.assertTrue(s.actor.hurt(s.actor.damageSources().mobAttack(s.victim),40),"frontal hit accepted");h.assertTrue(before[0]-s.actor.getHealth()<20*com.codex.blacklight.power.CombatScale.units(s.actor),"guard reduces frontal damage in converted health units");h.assertTrue(s.actor.guardHitAge(0)==0,"block hit drives native reaction clock");});
        h.runAfterDelay(53,()->{h.assertTrue(s.actor.hurt(s.actor.damageSources().mobAttack(s.victim),40),"second hit accepted");h.assertTrue(s.actor.exposed()&&s.actor.move()==EncounterMove.NONE,"poise break cancels guard");h.assertTrue(!s.actor.begin(EncounterMove.HAMMER,s.victim),"cannot attack in punish window");});
        h.runAfterDelay(126,()->{h.assertTrue(!s.actor.exposed(),"bounded stun recovers");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="j_rear",timeoutTicks=65)public static void guardDoesNotProtectTheBack(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.GUARD,s.victim),"guard starts"));
        h.runAfterDelay(41,()->{s.victim.setPos(s.actor.position().add(0,0,-3));float before=s.actor.getHealth();
            h.assertTrue(s.actor.hurt(s.actor.damageSources().mobAttack(s.victim),10),"back hit accepted");
            h.assertTrue(before-s.actor.getHealth()>7.5*com.codex.blacklight.power.CombatScale.units(s.actor),"rear bypasses frontal protection");
            h.assertTrue(s.actor.guardHitAge(0)>100,"rear hit does not play frontal block reaction");end(h,s);
        });
    }
    @GameTest(template="blackwatch_arena",batch="j_invulnerable",timeoutTicks=120)public static void immunityRejectsBothNativeFistWindows(GameTestHelper h){
        var s=scene(h);h.runAfterDelay(25,()->{h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.victim),"combo starts");s.victim.setInvulnerable(true);});
        h.runAfterDelay(105,()->{h.assertTrue(s.victim.getHealth()==2000,"native contact never bypasses damage cancellation");end(h,s);});
    }
}
