package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class OutbreakPopulationGameTest {
    private static BuildingAssimilationGameTest.Fixture fixture(GameTestHelper h){return new BuildingAssimilationGameTest.Fixture(h,h.getLevel().getServer().getLevel(Level.NETHER));}
    private static List<BlacklightCommonInfected> seed(BuildingAssimilationGameTest.Fixture f,int count){
        var result=new ArrayList<BlacklightCommonInfected>();
        for(int i=0;i<count;i++){
            // Keep every prepared body inside the already-simulating central chunk.
            var mob=ModEntities.COMMON_INFECTED.get().create(f.level);mob.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(f.origin.offset(-7+(i%6)*2,0,-4+(i/6)*2)));
            mob.tickCount=200;mob.getPersistentData().putBoolean("blacklight:outbreak_spawn",true);
            if(!f.level.addFreshEntity(mob))throw new AssertionError("ordinary preparation rejected");result.add(mob);f.born.add(mob);
        }
        f.level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,f.level.getServer());return result;
    }
    private static long population(BuildingAssimilationGameTest.Fixture f){return f.level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(f.origin).inflate(96),m->m.isAlive()&&BlacklightInfectionSystem.isInfected(m)).size();}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_early_mix")
    public static void actualEarlyPopulationAddsAGrowingAnimalLineageAfterItsFoodReserve(GameTestHelper h){var f=fixture(h);f.after(1,()->seed(f,6),()->{
        var tier=WorldOutbreakSystem.tierForPopulation(f.level,f.origin,0);h.assertTrue(tier==OutbreakRules.Tier.GROWING,"the real early neighborhood cannot keep selecting ordinary zombies after its six food slots");
        var mob=WorldOutbreakSystem.spawnFromBuilding(f.level,f.origin.east(5).north(2),tier);if(mob!=null)f.born.add(mob);
        h.assertTrue(mob instanceof BlacklightInfectedHost host&&host.tissueAge()==6000&&!host.isEvolved()&&host.tissueInheritance().meals()==0,"the actual shared producer creates a developing source rig, not a fake command-spawned evolved elite; result="+mob);
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_saturated")
    public static void aFullOrdinaryCohortCanDevelopWithoutAddingAnExtraPopulationSlot(GameTestHelper h){var f=fixture(h);List<BlacklightCommonInfected> old=new ArrayList<>();f.after(1,()->{old.addAll(seed(f,18));old.get(0).setHealth(old.get(0).getMaxHealth()*.5F);old.get(0).setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));},()->{
        h.assertTrue(!WorldOutbreakSystem.populationRoom(f.level,f.origin),"the genuine local cap is already full before development; population="+population(f)+" cap="+OutbreakConfig.LOCAL_CAP.get()+" difficulty="+f.level.getDifficulty()+" live="+old.stream().filter(Entity::isAlive).count());long before=population(f);var host=WorldOutbreakSystem.developSurplusCommon(f.level,f.origin);if(host!=null)f.born.add(host);
        h.assertTrue(host!=null&&population(f)==before&&old.get(0).isRemoved(),"a real same-body conversion repairs the saturated cohort without deletion or an extra final population slot");
        h.assertTrue(host.speciesId().equals("blacklight:common_infected")&&host.getMainHandItem().is(Items.IRON_SWORD)&&Math.abs(host.getHealth()/host.getMaxHealth()-.5)<.001,"promotion keeps the civilian source species, equipment and health fraction; species="+host.speciesId()+" item="+host.getMainHandItem()+" hp="+host.getHealth()+"/"+host.getMaxHealth()+" source="+old.get(0).getHealth()+"/"+old.get(0).getMaxHealth());
        h.assertTrue(host.tissueInheritance().meals()==0&&!host.isEvolved(),"population repair cannot invent a mature capture or skip the six-capture milestone");
        h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null,"one actual scheduled tick cannot promote the whole neighborhood");
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_join_veto")
    public static void vetoedCohortDevelopmentKeepsTheOriginalAndItsEquipment(GameTestHelper h){var f=fixture(h);List<BlacklightCommonInfected> old=new ArrayList<>();f.after(1,()->{old.addAll(seed(f,18));old.get(0).setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));},()->{
        Consumer<EntityJoinLevelEvent> veto=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof BlacklightInfectedHost)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto);
        try{h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null&&population(f)==18&&old.get(0).isAlive()&&old.get(0).getMainHandItem().is(Items.DIAMOND_SWORD),"actual entity-join protection leaves the old body and inventory intact; pop="+population(f)+" oldLive="+old.stream().filter(Entity::isAlive).count()+" first="+old.get(0).isAlive()+" equipment="+old.get(0).getMainHandItem());}finally{MinecraftForge.EVENT_BUS.unregister(veto);}
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_food_floor")
    public static void cohortDevelopmentCannotConsumeTheLastOrdinaryFoodReserve(GameTestHelper h){var f=fixture(h);f.after(1,()->seed(f,6),()->{
        h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null&&population(f)==6,"the six actual food bodies are kept, not forcibly replaced to make elites");
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_reentry")
    public static void actualJoinCallbacksCannotPromoteTheSameOriginalTwice(GameTestHelper h){var f=fixture(h);List<BlacklightCommonInfected> old=new ArrayList<>();f.after(1,()->old.addAll(seed(f,18)),()->{
        BlacklightInfectedHost[] nested={null};Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof BlacklightInfectedHost){nested[0]=old.get(0).promote();h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null,"the scheduled conversion reservation is also held through the actual join callback");}};MinecraftForge.EVENT_BUS.addListener(callback);
        try{var host=WorldOutbreakSystem.developSurplusCommon(f.level,f.origin);if(host!=null)f.born.add(host);h.assertTrue(host!=null&&nested[0]==null&&population(f)==18,"only one real replacement body survives an external reentrant join; host="+host+" nested="+nested[0]+" population="+population(f)+" common="+old.stream().filter(Entity::isAlive).count());}finally{MinecraftForge.EVENT_BUS.unregister(callback);}
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_source_lost")
    public static void removingTheOriginalInsideAJoinCallbackCannotLeaveAGhostConversion(GameTestHelper h){var f=fixture(h);List<BlacklightCommonInfected> old=new ArrayList<>();f.after(1,()->old.addAll(seed(f,18)),()->{
        Consumer<EntityJoinLevelEvent> callback=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof BlacklightInfectedHost)old.get(0).discard();};MinecraftForge.EVENT_BUS.addListener(callback);
        try{h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null&&population(f)==17,"source removal during publication cannot leave a replacement for a nonexistent creature");}finally{MinecraftForge.EVENT_BUS.unregister(callback);}
    });}

    @GameTest(template="wall_test",timeoutTicks=600,batch="population_rotation")
    public static void oneProtectedOriginalCannotPermanentlyStarveTheRestOfItsCohort(GameTestHelper h){var f=fixture(h);List<BlacklightCommonInfected> old=new ArrayList<>();Consumer<EntityJoinLevelEvent>[] veto=new Consumer[1];
        f.after(2,()->{
            old.addAll(seed(f,18));veto[0]=e->{if(e.getLevel()==f.level&&e.getEntity() instanceof BlacklightInfectedHost&&e.getEntity().blockPosition().equals(old.get(0).blockPosition()))e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(veto[0]);
            h.assertTrue(WorldOutbreakSystem.developSurplusCommon(f.level,f.origin)==null,"a genuine veto keeps the first original");
        },()->{
            try{var host=WorldOutbreakSystem.developSurplusCommon(f.level,f.origin);if(host!=null)f.born.add(host);h.assertTrue(host!=null&&old.get(0).isAlive()&&old.get(1).isRemoved()&&population(f)==18,"a later scheduled attempt rotates to another actual original without bypassing the protected one or adding population");}finally{MinecraftForge.EVENT_BUS.unregister(veto[0]);}
        });}
}
