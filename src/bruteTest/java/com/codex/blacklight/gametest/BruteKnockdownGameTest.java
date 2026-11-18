package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.registry.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BruteKnockdownGameTest {
    private record Scene(BlacklightMutant brute,Mob victim){}
    private static Scene scene(GameTestHelper h){
        h.getLevel().setDayTime(18000);
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<11;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var brute=ModEntities.BLACKLIGHT_BRUTE.get().create(h.getLevel());
        brute.setPos(h.absoluteVec(new Vec3(7.5,2,4.5)));brute.setPersistenceRequired();
        brute.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);brute.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(.05);h.getLevel().addFreshEntity(brute);
        var victim=EntityType.COW.create(h.getLevel());victim.setPos(brute.position().add(0,0,1.65));victim.setNoAi(true);victim.setPersistenceRequired();
        h.getLevel().addFreshEntity(victim);victim.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        h.runAfterDelay(4,()->h.assertTrue(ImpactControl.begin(brute,new ImpactProfile(HitReaction.DOWN,ImpactProfile.DOWN,18,0,0,true),0),"landed knockdown accepted"));
        return new Scene(brute,victim);
    }
    private static HitReaction hit(Scene s){return ModAttachments.peekData(s.brute,ModAttachments.HIT_REACTION,HitReaction.EMPTY);}
    private static void done(GameTestHelper h,Scene s){ImpactControl.cancel(s.brute);s.brute.discard();s.victim.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="brute_knockdown_clock",timeoutTicks=90)
    public static void controlsAttackUntilFullGetupCompletes(GameTestHelper h){
        var s=scene(h);int[] samples={0,0};
        for(int t=5;t<=52;t++)h.runAfterDelay(t,()->{
            var hit=hit(s);h.assertTrue(hit.kind()==HitReaction.DOWN||hit.kind()==HitReaction.GETUP,"phase stays down or getup: "+hit);
            samples[hit.kind()==HitReaction.DOWN?0:1]++;
            if(hit.kind()==HitReaction.GETUP)h.assertTrue(hit.duration()==BruteKnockdown.GETUP_TICKS,"Brute uses its 32 tick rise");
            h.assertTrue(ImpactControl.stunned(s.brute)&&!s.brute.isNoAi()&&!s.brute.beginAttack(MutantMove.BRUTE_HAMMER,s.victim),"no attack or NoAI while recovering");
        });
        h.runAfterDelay(60,()->{h.assertTrue(samples[0]>10&&samples[1]>=29&&!ImpactControl.stunned(s.brute)&&!s.brute.isNoAi(),"complete finite control");h.assertTrue(s.brute.beginAttack(MutantMove.BRUTE_HAMMER,s.victim),"accepts attack after getup");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_knockdown_down_hit",timeoutTicks=90)
    public static void standingHitDoesNotEraseDownPhase(GameTestHelper h){
        var s=scene(h);
        h.runAfterDelay(12,()->{var before=hit(s);ImpactControl.begin(s.brute,new ImpactProfile(HitReaction.STAGGER,ImpactProfile.RIGHT,8,0,0,false),0);h.assertTrue(hit(s).equals(before),"standing hit preserves knockdown clock");});
        h.runAfterDelay(30,()->h.assertTrue(hit(s).kind()==HitReaction.GETUP&&ImpactControl.stunned(s.brute),"still recovers through getup"));
        h.runAfterDelay(60,()->{h.assertTrue(!ImpactControl.stunned(s.brute)&&!s.brute.isNoAi(),"standing hit cannot lock AI");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_knockdown_rise_hit",timeoutTicks=80)
    public static void newHitAndCancelRetireTheOldGetupClock(GameTestHelper h){
        var s=scene(h);
        h.runAfterDelay(30,()->{h.assertTrue(hit(s).kind()==HitReaction.GETUP,"interrupt getup fixture");ImpactControl.begin(s.brute,new ImpactProfile(HitReaction.STAGGER,ImpactProfile.RIGHT,8,0,0,false),0);h.assertTrue(hit(s).kind()==HitReaction.STAGGER,"new standing hit owns phase");});
        h.runAfterDelay(35,()->h.assertTrue(ImpactControl.stunned(s.brute),"new short hit still controls"));
        h.runAfterDelay(42,()->{h.assertTrue(!ImpactControl.stunned(s.brute),"new hit deadline releases");ImpactControl.begin(s.brute,new ImpactProfile(HitReaction.DOWN,ImpactProfile.DOWN,18,0,0,true),0);ImpactControl.cancel(s.brute);});
        h.runAfterDelay(48,()->{h.assertTrue(hit(s).duration()==0&&!ImpactControl.stunned(s.brute)&&!s.brute.isNoAi(),"explicit cancel cannot resurrect old getup");h.assertTrue(s.brute.beginAttack(MutantMove.BRUTE_HAMMER,s.victim),"attack accepts after cancel");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_knockdown_death",timeoutTicks=65)
    public static void deathDuringGetupReleasesTransientControl(GameTestHelper h){
        var s=scene(h);
        h.runAfterDelay(30,()->{h.assertTrue(hit(s).kind()==HitReaction.GETUP,"death during getup fixture");s.brute.hurt(s.brute.damageSources().generic(),100000);});
        h.runAfterDelay(32,()->{h.assertTrue(!s.brute.isAlive()&&!ImpactControl.stunned(s.brute)&&!s.brute.isNoAi(),"death owns body without permanent AI mutation");h.assertTrue(!s.brute.beginAttack(MutantMove.BRUTE_HAMMER,s.victim),"dead Brute never restarts attack");done(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="brute_knockdown_late_land",timeoutTicks=130)
    public static void lateLandedPhaseStillGetsTheFullRise(GameTestHelper h){
        var s=scene(h);
        h.runAfterDelay(5,()->ImpactControl.begin(s.brute,new ImpactProfile(HitReaction.HEAVY,ImpactProfile.FALL,40,0,0,true),0));
        h.runAfterDelay(92,()->{var hit=hit(s);h.assertTrue(hit.kind()==HitReaction.GETUP&&hit.duration()==32&&ImpactControl.stunned(s.brute),"old 80 tick cap cannot truncate Brute getup: "+hit);});
        h.runAfterDelay(103,()->{h.assertTrue(!ImpactControl.stunned(s.brute)&&!s.brute.isNoAi(),"extended rise still has a bounded release");done(h,s);});
    }
}
