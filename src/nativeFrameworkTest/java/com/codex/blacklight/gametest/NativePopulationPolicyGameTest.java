package com.codex.blacklight.gametest;

import com.codex.blacklight.config.InfectionEcologyConfig;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativePopulationPolicyGameTest {
    @GameTest(template="wall_test",batch="native_sparse_population_policy")
    public static void sparseRegionStopsAtFiveAndHealthyResidentsCreateRealAdmissionRoom(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();var original=OutbreakData.get(level.getServer());
        boolean enabled=OutbreakConfig.ENABLED.get();int cap=InfectionEcologyConfig.WORLD_CAP.get(),dim=OutbreakConfig.DIMENSION_CAP.get();
        int local=OutbreakConfig.LOCAL_CAP.get(),batch=OutbreakConfig.BATCH.get(),interval=OutbreakConfig.INTERVAL.get();
        double share=InfectionEcologyConfig.WORLD_SHARE.get();var spawning=level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING);boolean oldSpawning=spawning.get();
        var owned=new ArrayList<Mob>();
        try{
            OutbreakConfig.ENABLED.set(true);spawning.set(true,level.getServer());InfectionEcologyConfig.WORLD_SHARE.set(.35D);
            InfectionEcologyConfig.WORLD_CAP.set(4096);OutbreakConfig.DIMENSION_CAP.set(1024);OutbreakConfig.LOCAL_CAP.set(128);
            OutbreakConfig.BATCH.set(16);OutbreakConfig.INTERVAL.set(20);var released=new OutbreakData();released.start(level.getGameTime());storage.set("blacklight_outbreak",released);
            var core=h.spawn(ModEntities.BLACKLIGHT_HIVE.get(),4,2,4);core.setNoAi(true);owned.add(core);
            for(int i=0;i<4;i++){var guard=h.spawn(ModEntities.COMMON_INFECTED.get(),3+i,2,6);guard.setNoAi(true);owned.add(guard);}
            h.assertTrue(!WorldOutbreakSystem.populationRoom(level,core.blockPosition()),"default 35 percent regional rule stops a core plus four guards in a sparse region");
            for(int i=0;i<24;i++){var resident=h.spawn(EntityType.COW,3+i%6,2,9+i/6);resident.setNoAi(true);resident.setInvulnerable(true);owned.add(resident);}
            h.assertTrue(WorldOutbreakSystem.populationRoom(level,core.blockPosition()),"actual healthy resident population admits the next guard without bypassing the regional rule");h.succeed();
        }finally{
            owned.forEach(Entity::discard);storage.set("blacklight_outbreak",original);spawning.set(oldSpawning,level.getServer());
            OutbreakConfig.ENABLED.set(enabled);InfectionEcologyConfig.WORLD_SHARE.set(share);InfectionEcologyConfig.WORLD_CAP.set(cap);
            OutbreakConfig.DIMENSION_CAP.set(dim);OutbreakConfig.LOCAL_CAP.set(local);OutbreakConfig.BATCH.set(batch);OutbreakConfig.INTERVAL.set(interval);
        }
    }
}
