package com.codex.blacklight.gametest;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BiomassSettlementCallbackGameTest {
    @GameTest(template="wall_test",timeoutTicks=50,batch="biomass_callback_death")
    public static void preyDeathCallbackCannotReviveADeadConsumer(GameTestHelper h) {
        check(h,true);
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="biomass_callback_cancel")
    public static void preyDeathCallbackCannotRestartACancelledTransformation(GameTestHelper h) {
        check(h,false);
    }
    private static void check(GameTestHelper h,boolean kill) {
        var p=FeedbackLifecycleGameTest.player(h);var target=EntityType.COW.create(h.getLevel());
        target.setNoAi(true);target.setPos(p.position());h.getLevel().addFreshEntity(target);
        int before=p.getData(ModAttachments.PLAYER_STATE).getBiomass();
        Consumer<LivingDeathEvent> listener=e->{
            if(e.getEntity()!=target)return;
            if(kill){p.setHealth(0);p.die(p.damageSources().genericKill());}
            else p.setData(ModAttachments.BIOMASS_FORM,BiomassState.EMPTY);
        };
        p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,
                p.level().getGameTime()-BiomassState.ABSORB_TICKS,true,"","claws",target.getId(),10));
        p.refreshDimensions();NeoForge.EVENT_BUS.addListener(listener);
        try {
            BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!target.isAlive(),"the prey really reached its normal accepted death callback");
            h.assertTrue(!BiomassSystem.active(p)&&(!kill||!p.isAlive()),
                    "an invalidated consumer cannot be healed or put back into reformation: hp="+p.getHealth()+" form="+BiomassSystem.state(p));
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getBiomass()==before,"an invalidated scene pays no biomass");
            h.succeed();
        } finally {NeoForge.EVENT_BUS.unregister(listener);target.discard();FeedbackLifecycleGameTest.cleanup(p);}
    }
}
