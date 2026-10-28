package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.registry.ModEntities;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HydraFeedingBoundaryGameTest {
    private static void feed(GameTestHelper h,boolean cancel)throws Exception{
        var level=h.getLevel();var hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(level);
        hydra.plant(UUID.randomUUID());hydra.setPos(h.absolutePos(new BlockPos(3,5,3)).getBottomCenter());
        var prey=EntityType.COW.create(level);prey.setNoAi(true);prey.setHealth(1);prey.setPos(hydra.position().add(1,0,0));level.addFreshEntity(prey);
        var food=BlacklightHydra.class.getDeclaredField("food");food.setAccessible(true);food.set(hydra,prey);
        var action=BlacklightHydra.class.getDeclaredMethod("action",int.class);action.setAccessible(true);action.invoke(hydra,1);
        var reached=new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<LivingDeathEvent> veto=e->{if(e.getEntity()==prey){reached.set(true);if(cancel)e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(veto);
        try{
            for(int tick=0;tick<30;tick++)hydra.tick();
            h.assertTrue(reached.get(),"real prey damage reached the cancellable death event");
            h.assertTrue(hydra.feeds()==(cancel?0:1),cancel?"zero HP after a vetoed death must not count as an accepted feeding":"a genuine owned accepted kill still adds exactly one feed");
        }finally{NeoForge.EVENT_BUS.unregister(veto);hydra.discard();prey.discard();}
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="hydra_feeding_death_veto")
    public static void aVetoedPreyDeathCannotFeedTheDimensionConnector(GameTestHelper h)throws Exception{feed(h,true);}
    @GameTest(template="wall_test",timeoutTicks=20,batch="hydra_feeding_accepted_death")
    public static void anAcceptedPreyDeathStillFeedsTheDimensionConnector(GameTestHelper h)throws Exception{feed(h,false);}
}
