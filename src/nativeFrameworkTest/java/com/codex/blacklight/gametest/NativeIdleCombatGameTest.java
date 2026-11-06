package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeIdleCombatGameTest {
    private static void noPulse(GameTestHelper h,TissueCore core){
        var old=InfectionEcologyConfig.PRESERVE.get();
        var arena=NativeTestArena.open(h,0,15);
        InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        Mob actor=null,prey=null;
        try{
            for(int x=2;x<=8;x++)for(int y=2;y<=6;y++)for(int z=2;z<=8;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            actor=h.spawn(EntityType.COW,4,2,4);actor.setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actor,null),"test retains the actual native cow");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",core.key());
            data.putInt("Nutrition",0);data.putInt("Recovery",10000);
            actor.getPersistentData().put("BlacklightNativeInfection",data);
            actor.tickCount=1;NativeInfection.tick(actor,h.getLevel());
            actor.setHealth(actor.getMaxHealth()*.5F);
            prey=h.spawn(EntityType.PIG,5,2,4);prey.setNoAi(true);
            prey.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
            actor.setTarget(prey);
            h.assertTrue(actor.hasLineOfSight(prey)&&actor.distanceToSqr(prey)<9,"real nearby target satisfies the former pulse conditions");
            float before=actor.getHealth()/actor.getMaxHealth(),victim=prey.getHealth();
            actor.tickCount=100;NativeInfection.tick(actor,h.getLevel());
            h.assertTrue(TissueActions.core(actor)==null&&NativeTissue.nutrition(actor)==0,"no AI action or food was supplied");
            h.assertTrue(Math.abs(actor.getHealth()/actor.getMaxHealth()-before)<.00001F,
                    "idle "+core+" cannot heal merely because a target is nearby; fraction="+before+" -> "+actor.getHealth()/actor.getMaxHealth());
            h.assertTrue(prey.getHealth()==victim,"idle "+core+" cannot hurt without a native attack or organ contact; health="+victim+" -> "+prey.getHealth());
            h.succeed();
        }finally{
            if(actor!=null)actor.discard();if(prey!=null)prey.discard();
            InfectionEcologyConfig.PRESERVE.set(old);arena.close();
        }
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_idle_jaw")
    public static void idleJawHasNoFreeTargetHealing(GameTestHelper h){noPulse(h,TissueCore.JAW);}
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_idle_muscle")
    public static void idleMuscleHasNoInvisiblePeriodicHit(GameTestHelper h){noPulse(h,TissueCore.MUSCLE);}
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_idle_grasp")
    public static void idleGraspHasNoInvisiblePeriodicHit(GameTestHelper h){noPulse(h,TissueCore.GRASP);}
}
