package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EvolvedDistrictGameTest {
    @GameTest(template="wall_test",timeoutTicks=380,batch="evolved_finite_combo")
    public static void actualConnectedAttacksFormAFiniteCombinationAndReleaseRecovery(GameTestHelper h){
        var host=EvolvedFormGameTest.host(h);h.assertTrue(host.beginEvolvedMetamorphosis(),"the real six-organ fixture qualifies");for(int i=0;i<200;i++)host.tick();
        for(int x=-10;x<=20;x++)for(int z=-10;z<=20;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        host.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,30,5))));host.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);host.setOnGround(true);
        var target=EntityType.HUSK.create(h.getLevel());target.setPersistenceRequired();target.setNoAi(true);target.setPos(host.position().add(2,0,0));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);target.setHealth(100000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(target);
        host.setNoAi(false);host.setTarget(target);
        var moves=new java.util.ArrayList<String>();var recovery=new java.util.concurrent.atomic.AtomicInteger();var maximumPause=new java.util.concurrent.atomic.AtomicInteger();
        for(int tick=1;tick<=300;tick++)h.runAfterDelay(tick,()->{
            String move=host.evolvedAction();
            if(!move.isEmpty()&&(moves.isEmpty()||!moves.get(moves.size()-1).equals(move)))moves.add(move);
            if(move.isEmpty()&&moves.contains("slash"))maximumPause.accumulateAndGet(recovery.incrementAndGet(),Math::max);else recovery.set(0);
        });
        h.runAfterDelay(305,()->{try{
            h.assertTrue(moves.contains("thrust_left")&&moves.contains("thrust_right")&&moves.contains("slash"),"actual AI performed the connected source moves: "+moves);
            h.assertTrue(target.getHealth()<99999,"the sequence caused accepted real damage, rather than visual-only swings");
            h.assertTrue(maximumPause.get()>=12,"a completed combination leaves a recovery window, pause="+maximumPause.get()+" moves="+moves);
            h.assertTrue(host.isAlive()&&host.isEvolved()&&host.tissueInheritance().meals()==6,"combat does not fabricate extra meals or alter the earned form");
        }finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="evolved_water_navigation")
    public static void theCompletedHumanBodyCanFloatWithoutRetainingAquaticNavigation(GameTestHelper h){
        var host=EvolvedFormGameTest.host(h);try{
            h.assertTrue(host.beginEvolvedMetamorphosis(),"qualified body starts");for(int i=0;i<200;i++)host.tick();
            h.assertTrue(host.isEvolved()&&host.getNavigation() instanceof GroundPathNavigation&&host.getNavigation().canFloat()&&!host.isNoGravity(),"the completed evolved uses ground pursuit with swimming escape enabled");
        }finally{host.discard();}h.succeed();
    }
}
