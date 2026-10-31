package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EvolvedHealingCallbackGameTest {
    @GameTest(template="wall_test",timeoutTicks=65,batch="audit_mend_death")
    public static void aHealingCallbackCannotMakeADeadCasterHealMoreAllies(GameTestHelper h){check(h,true);}
    @GameTest(template="wall_test",timeoutTicks=65,batch="audit_mend_interrupt")
    public static void anInterruptedHealingCallbackCannotInvalidateThePatientIterator(GameTestHelper h){check(h,false);}
    private static void check(GameTestHelper h,boolean kill){
        var body=EvolvedVariantsGameTest.host(h,EvolvedArchetype.REGENERATOR);
        var enemy=EvolvedVariantsGameTest.enemy(h,body,5);
        var control=new EvolvedSpecials(body);
        var patients=new BlacklightInfectedHost[2];
        for(int i=0;i<patients.length;i++){
            var ally=ModEntities.INFECTED_HOST.get().create(h.getLevel());ally.inherit(EntityType.COW.create(h.getLevel()));
            ally.setNoAi(true);ally.setNoGravity(true);ally.setPos(body.position().add(i==0?-2:2,0,0));
            h.getLevel().addFreshEntity(ally);ally.setHealth(ally.getMaxHealth()*.25F);patients[i]=ally;
        }
        int[] callbacks={0};boolean[] casting={false};
        Consumer<LivingHealEvent> listener=e->{
            if(!casting[0])return; // Natural tissue repair is unrelated to this cast.
            if(e.getEntity()!=patients[0]&&e.getEntity()!=patients[1])return;
            if(++callbacks[0]!=1)return;
            if(kill){body.setHealth(0);body.die(body.damageSources().genericKill());}
            else {control.interrupt();body.setEvolvedAction("");}
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        h.assertTrue(control.tick(enemy,true)&&body.evolvedAction().equals(EvolvedSpecialMove.MEND.key),"paid mend starts with two wounded allies");
        h.runAfterDelay(EvolvedSpecialMove.MEND.contact,()->{
            try{
                casting[0]=true;
                control.tick(enemy,false);
                h.assertTrue(callbacks[0]==1,"the invalidated cast must stop after the first actual heal callback: "+callbacks[0]);
                h.succeed();
            }finally{
                casting[0]=false;
                MinecraftForge.EVENT_BUS.unregister(listener);control.interrupt();body.discard();enemy.discard();
                for(var ally:patients)ally.discard();
            }
        });
    }
}
