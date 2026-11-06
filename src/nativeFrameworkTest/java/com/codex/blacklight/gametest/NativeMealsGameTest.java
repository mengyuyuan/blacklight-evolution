package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeMealsGameTest {
    @GameTest(template="wall_test",timeoutTicks=40,batch="native_meal_actual_receipts")
    public static void actualDeathPaysAfterOverridesButCancelledRestoredOrLostSourcesNeverPay(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        var actors=new Mob[4];var victims=new Mob[4];
        for(int i=0;i<4;i++){
            actors[i]=h.spawn(EntityType.COW,3+i*2,2,3);actors[i].setNoAi(true);
            h.assertTrue(BlacklightInfectionSystem.infect(actors[i],null),"native source infected");
            var data=new CompoundTag();data.putLong("Age",72000);data.putString("Core",TissueCore.MUSCLE.key());data.putInt("Nutrition",0);
            actors[i].getPersistentData().put("BlacklightNativeInfection",data);NativeInfection.tick(actors[i],h.getLevel());
            victims[i]=h.spawn(EntityType.PIG,3+i*2,2,5);victims[i].setNoAi(true);
        }
        Consumer<LivingDeathEvent> veto=event->{if(event.getEntity()==victims[1]){event.setCanceled(true);victims[1].setHealth(victims[1].getMaxHealth());}};
        NeoForge.EVENT_BUS.addListener(veto);
        Runnable cleanup=()->{NeoForge.EVENT_BUS.unregister(veto);for(var actor:actors)actor.discard();for(var victim:victims)victim.discard();InfectionEcologyConfig.PRESERVE.set(old);};
        try{
            for(int i=0;i<4;i++)victims[i].hurt(victims[i].damageSources().mobAttack(actors[i]),10000);
            for(int i=0;i<3;i++)h.assertTrue(NativeTissue.nutrition(actors[i])==0,"a cancellable callback never pays prematurely");
            h.assertTrue(!victims[0].isAlive()&&victims[0].getKillCredit()==actors[0]&&victims[1].isAlive(),"fixture has one accepted and one vetoed real death");
            // Model an outer foreign override which restores HP after super.hurt
            // returned. Reward must observe that final outcome, not the base call.
            victims[2].setHealth(victims[2].getMaxHealth());actors[3].discard();
            h.runAfterDelay(3,()->{
                try{
                    h.assertTrue(NativeTissue.nutrition(actors[0])>0&&victims[0].getPersistentData().getBoolean("BlacklightTissueMealSettled"),"only actual accepted death pays after the outer override completed");
                    h.assertTrue(NativeTissue.nutrition(actors[1])==0&&NativeTissue.nutrition(actors[2])==0,"vetoed or later-restored health pays no meal");
                    h.assertTrue(!victims[3].getPersistentData().getBoolean("BlacklightTissueMealSettled"),"an unloaded/invalidated source cannot receive or consume a queued reward");
                    int paid=NativeTissue.nutrition(actors[0]);NativeTissueMeals.death(new LivingDeathEvent(victims[0],victims[0].damageSources().mobAttack(actors[0])));
                    h.runAfterDelay(3,()->{try{h.assertTrue(NativeTissue.nutrition(actors[0])==paid,"repeated notification of the same accepted corpse cannot mint nutrition");h.succeed();}finally{cleanup.run();}});
                }catch(Throwable failure){cleanup.run();throw failure;}
            });
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
