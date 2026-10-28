package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Real NeoForge breathe events, air supply, paid reserves, and actual combat cadence. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueVitalsGameTest {
    private static void floor(GameTestHelper h){
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=9;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
    }
    private static void pond(GameTestHelper h){
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++)for(int y=2;y<=7;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
    }
    private static BlacklightInfectedHost actor(GameTestHelper h,EntityType<? extends Mob> type,TissueCore core,int x,int z){
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());long id=host.getId();host.setUUID(new UUID(id,id^42));host.inherit(type.create(h.getLevel()));
        var t=new CompoundTag();host.saveWithoutId(t);t.getCompound("BlacklightHost").putString("NativeCore",core==null?"future_inert":core.key());
        t.putInt("BlacklightTissueNutrition",100);host.readAdditionalSaveData(t);host.advanceTissue(216000);
        host.setHealth(host.getMaxHealth());host.setNoAi(true);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);host.moveTo(h.absolutePos(new BlockPos(x,2,z)).getBottomCenter(),0,0);host.setOnGround(true);
        h.getLevel().addFreshEntity(host);return host;
    }
    private static String state(BlacklightInfectedHost h){return "air="+h.getAirSupply()+"/"+h.getMaxAirSupply()+" state="+h.vitals().write()+" env="+TissueVitalSystem.environment(h,false);}
    private static void clean(BlacklightInfectedHost...hosts){for(var host:hosts)host.discard();}

    @GameTest(template="wall_test",timeoutTicks=90,batch="vitals_native_traits")
    public static void infectedFishAndDolphinKeepTheirDifferentRealBreathingTraits(GameTestHelper h){
        floor(h);var cow=actor(h,EntityType.COW,null,2,5);var fish=actor(h,EntityType.COD,null,5,5);var dolphin=actor(h,EntityType.DOLPHIN,null,8,5);
        h.assertTrue(dolphin.getMaxAirSupply()==4800&&fish.getMaxAirSupply()==300,"native air capacities survive infection, including the air-breathing dolphin");
        for(var host:List.of(cow,fish,dolphin))host.setAirSupply(100);
        h.runAfterDelay(15,()->{
            h.assertTrue(cow.getAirSupply()>100&&dolphin.getAirSupply()>100&&fish.getAirSupply()<100,"actual dry-land respiration distinguishes all three: "+state(cow)+state(fish)+state(dolphin));
            pond(h);for(var host:List.of(cow,fish,dolphin))host.setAirSupply(100);
        });
        h.runAfterDelay(35,()->{try{
            h.assertTrue(fish.getAirSupply()>100&&cow.getAirSupply()<100&&dolphin.getAirSupply()<100,"actual water events reverse fish breathing without making dolphins aquatic gills: "+state(cow)+state(fish)+state(dolphin));
        }finally{clean(cow,fish,dolphin);}h.succeed();});
    }

    @GameTest(template="wall_test",timeoutTicks=370,batch="vitals_paid_gill")
    public static void acquiredGillsActuallySpendOneDosePersistItAndEventuallyRunOut(GameTestHelper h){
        floor(h);pond(h);var host=actor(h,EntityType.COW,TissueCore.GILL_LAMELLA,4,5);host.spendTissueNutrition(99);host.setAirSupply(20);
        h.runAfterDelay(15,()->{
            h.assertTrue(host.getAirSupply()>20&&host.tissueNutrition()==0&&host.vitals().route()==TissueVitals.Route.GILL,"a real acquired gill breathes water using one existing nutrient: "+state(host));
            var t=new CompoundTag();host.saveWithoutId(t);int dose=t.getCompound("BlacklightTissueVitals").getInt("GillCharge");host.readAdditionalSaveData(t);
            h.assertTrue(host.vitals().write().getInt("GillCharge")==dose&&host.tissueNutrition()==0,"reload preserves remaining paid dose and cannot refill nutrients");
        });
        h.runAfterDelay(300,()->{h.assertTrue(host.vitals().write().getInt("GillCharge")==0,"the actual dose expires under water");host.setAirSupply(15);});
        h.runAfterDelay(330,()->{try{h.assertTrue(host.getAirSupply()<15&&host.vitals().route()==TissueVitals.Route.SEALED&&host.tissueNutrition()==0,"empty gills return to real air consumption instead of permanent water immunity: "+state(host));}finally{host.discard();}h.succeed();});
    }

    @GameTest(template="wall_test",timeoutTicks=380,batch="vitals_lung")
    public static void lungsIncreaseTheActualDiveCapacityWithoutEnablingWaterBreathing(GameTestHelper h){
        floor(h);var plain=actor(h,EntityType.COW,null,3,5);var lung=actor(h,EntityType.COW,TissueCore.LUNG,7,5);
        h.assertTrue(lung.getMaxAirSupply()>plain.getMaxAirSupply(),"bronchial lungs change the actual vanilla air capacity");plain.setAirSupply(20);lung.setAirSupply(20);
        h.runAfterDelay(10,()->{
            h.assertTrue(lung.getAirSupply()>plain.getAirSupply(),"actual air refills faster through the mature lung");
            pond(h);plain.setAirSupply(plain.getMaxAirSupply());lung.setAirSupply(lung.getMaxAirSupply());
        });
        h.runAfterDelay(325,()->{try{h.assertTrue(plain.getAirSupply()<=0&&lung.getAirSupply()>40&&lung.vitals().route()==TissueVitals.Route.SEALED,"both consume air, but the larger real lung reservoir sustains a longer dive: "+state(plain)+state(lung));}finally{clean(plain,lung);}h.succeed();});
    }

    private static void workPair(GameTestHelper h,TissueCore core,boolean activeBetter){
        floor(h);var resting=actor(h,EntityType.COW,core,2,3);var moving=actor(h,EntityType.COW,core,6,7);var start=moving.position();
        h.runAfterDelay(4,()->{resting.setAirSupply(0);moving.setAirSupply(0);moving.setNoAi(false);});
        for(int tick=5;tick<=19;tick++)h.runAfterDelay(tick,()->moving.setDeltaMovement(new Vec3(.09,0,0)));
        h.runAfterDelay(20,()->{try{
            h.assertTrue(moving.distanceToSqr(start)>.02,"the work subject actually moved under entity physics");
            h.assertTrue(activeBetter?moving.getAirSupply()>resting.getAirSupply():resting.getAirSupply()>moving.getAirSupply(),"real motion changes the exchange rate in its own direction for "+core+": resting "+state(resting)+" moving "+state(moving));
        }finally{clean(resting,moving);}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="vitals_airflow")
    public static void avianAirflowRespondsToActualLocomotion(GameTestHelper h){workPair(h,TissueCore.AIRFLOW,true);}
    @GameTest(template="wall_test",timeoutTicks=45,batch="vitals_book_lung")
    public static void bookLungExchangeIsStrongerDuringActualDryRest(GameTestHelper h){workPair(h,TissueCore.BOOK_LUNG,false);}

    @GameTest(template="wall_test",timeoutTicks=90,batch="vitals_trachea")
    public static void realExposedBodyVentsCanSupplyASubmergedHeadButNotAFullyFloodedBody(GameTestHelper h){
        floor(h);var host=actor(h,EntityType.COW,TissueCore.TRACHEA,5,5);
        h.runAfterDelay(3,()->{
            // An open gate blocks fluid spread while retaining an empty collision shape.
            // The head is in actual source water; the lower thoracic vents remain in real air.
            var eye=BlockPos.containing(host.getX(),host.getEyeY(),host.getZ());
            h.getLevel().setBlockAndUpdate(eye.below(),Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN,true));
            for(int y=0;y<=1;y++){for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)h.getLevel().setBlockAndUpdate(eye.above(y).relative(d),Blocks.GLASS.defaultBlockState());h.getLevel().setBlockAndUpdate(eye.above(y),Blocks.WATER.defaultBlockState());}
            host.setAirSupply(20);
        });
        h.runAfterDelay(18,()->{
            var env=TissueVitalSystem.environment(host,false);
            h.assertTrue(env.headWater()&&env.ventAir()&&host.vitals().route()==TissueVitals.Route.VENT&&host.getAirSupply()>20,"the adapter resolves real head water and uncovered lower vents: "+state(host));
            pond(h);host.setAirSupply(100);
        });
        h.runAfterDelay(40,()->{try{h.assertTrue(!TissueVitalSystem.environment(host,false).ventAir()&&host.getAirSupply()<100&&host.vitals().route()==TissueVitals.Route.SEALED,"flooding all exposed vents ends respiration even with a paid dose left: "+state(host));}finally{host.discard();}h.succeed();});
    }

    @GameTest(template="wall_test",timeoutTicks=560,batch="vitals_heart_combat")
    public static void thePaidHeartActuallyShortensRealMeleeIntervalsInsteadOfOnlyAnUnusedCounter(GameTestHelper h){
        floor(h);var host=actor(h,EntityType.COW,TissueCore.HEART,3,3);var control=actor(h,EntityType.COW,TissueCore.HEART,3,7);
        var target=passive(h,host.position().add(4,0,0));var other=passive(h,control.position().add(4,0,0));
        var hits=new ArrayList<Long>();var normal=new ArrayList<Long>();var contacts=new ArrayList<Integer>();var normalContacts=new ArrayList<Integer>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=e->{
            if(e.getNewDamage()<=0)return;
            if(e.getEntity()==target&&e.getSource().getEntity()==host){hits.add(h.getLevel().getGameTime());contacts.add((int)host.tissueStrikeElapsed(0));}
            if(e.getEntity()==other&&e.getSource().getEntity()==control){normal.add(h.getLevel().getGameTime());normalContacts.add((int)control.tissueStrikeElapsed(0));}
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);var before=new int[]{0};
        h.runAfterDelay(300,()->{
            h.assertTrue(host.vitals().oxygen()>=50&&control.vitals().oxygen()>=50,"actual air exchange fills both work buffers: "+state(host)+state(control));
            before[0]=host.tissueNutrition();control.spendTissueNutrition(control.tissueNutrition());
            target.setPos(host.position().add(1.7,0,0));other.setPos(control.position().add(1.7,0,0));host.setNoAi(false);control.setNoAi(false);host.setTarget(target);control.setTarget(other);
        });
        for(int tick=301;tick<490;tick++)h.runAfterDelay(tick,()->{host.setTarget(target);control.setTarget(other);});
        h.runAfterDelay(490,()->{try{
            var paid=intervals(strikes(hits,contacts));var baseline=intervals(strikes(normal,normalContacts));
            h.assertTrue(paid.size()>=2&&baseline.size()>=2&&Collections.min(paid)<Collections.min(baseline)&&host.tissueNutrition()<before[0],"accepted real attack cycles shorten only with paid recovery: paid="+hits+" control="+normal+" host="+host.position()+" prey="+target.position()+" living="+target.isAlive()+" "+state(host));
            h.assertTrue(contacts.stream().anyMatch(t->t==TissueOrgans.HIT)&&normalContacts.stream().anyMatch(t->t==TissueOrgans.HIT),"both real strikes retain the same source contact frame: "+contacts+" / "+normalContacts);
        }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);clean(host,control);target.discard();other.discard();}h.succeed();});
    }
    private static List<Long> strikes(List<Long> times,List<Integer> contacts){var result=new ArrayList<Long>();for(int i=0;i<times.size();i++)if(contacts.get(i)==TissueOrgans.HIT)result.add(times.get(i));return result;}
    private static List<Long> intervals(List<Long> times){var result=new ArrayList<Long>();for(int i=1;i<times.size();i++)result.add(times.get(i)-times.get(i-1));return result;}
    private static Mob passive(GameTestHelper h,Vec3 position){var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setHealth(10000);target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);target.moveTo(position);h.getLevel().addFreshEntity(target);return target;}

    @GameTest(template="wall_test",timeoutTicks=200,batch="vitals_branchial_heart")
    public static void branchialCirculationWorksDuringRealWaterExchangeAndNotOnDryLand(GameTestHelper h){
        floor(h);var land=actor(h,EntityType.COW,TissueCore.BRANCHIAL_HEART,3,3);var fish=actor(h,EntityType.COD,TissueCore.BRANCHIAL_HEART,7,7);
        for(int x=6;x<=9;x++)for(int z=6;z<=9;z++)for(int y=2;y<=6;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        // Keep the test's small water basin bounded rather than flooding the land control.
        for(int q=5;q<=9;q++)for(int y=2;y<=6;y++){h.setBlock(new BlockPos(5,y,q),Blocks.GLASS);h.setBlock(new BlockPos(q,y,5),Blocks.GLASS);}
        h.runAfterDelay(140,()->{try{
            int a=land.tissueNutrition(),b=fish.tissueNutrition();
            h.assertTrue(land.recoverTissueAction(100)==100&&land.tissueNutrition()==a,"branchial pumps do not give a universal land cooldown bonus: "+state(land));
            h.assertTrue(fish.vitals().route()==TissueVitals.Route.WATER&&fish.recoverTissueAction(100)<100&&fish.tissueNutrition()==b-1,"the native fish's real water exchange enables one paid recovery: "+state(fish));
        }finally{clean(land,fish);}h.succeed();});
    }

    @GameTest(template="wall_test",timeoutTicks=850,batch="vitals_spleen")
    public static void spleenBloodChargesThroughActualRestAndFoodAndItsDrowningRescueIsFinite(GameTestHelper h){
        floor(h);var host=actor(h,EntityType.COW,TissueCore.SPLEEN,4,5);h.assertTrue(host.vitals().spleen()==0,"the mature organ does not start with free stock");
        h.runAfterDelay(700,()->{
            int stock=host.vitals().spleen();h.assertTrue(stock>0&&host.tissueNutrition()<100,"actual resting ticks pay for stored blood: "+state(host));
            var t=new CompoundTag();host.saveWithoutId(t);host.readAdditionalSaveData(t);h.assertTrue(host.vitals().spleen()==stock,"real entity save/load preserves paid blood without advancing it");
            host.spendTissueNutrition(host.tissueNutrition());pond(h);host.setAirSupply(0);
        });
        h.runAfterDelay(715,()->{h.assertTrue(host.vitals().spleen()==0&&host.getAirSupply()>0,"the finite resting reserve releases actual air during drowning: "+state(host));host.setAirSupply(0);});
        h.runAfterDelay(745,()->{try{h.assertTrue(host.vitals().spleen()==0&&host.getAirSupply()<=0&&host.tissueNutrition()==0,"used blood and empty food cannot cause repeated emergency refills: "+state(host));}finally{host.discard();}h.succeed();});
    }

    @GameTest(template="wall_test",timeoutTicks=35,batch="vitals_migrate")
    public static void oldHostSavesReconstructNativeTraitsWithoutChangingOrgansOrMintingVitals(GameTestHelper h){
        floor(h);var fish=actor(h,EntityType.COD,TissueCore.GILL_LAMELLA,3,5);var dolphin=actor(h,EntityType.DOLPHIN,TissueCore.LUNG,7,5);
        for(var host:List.of(fish,dolphin)){
            var t=new CompoundTag();host.saveWithoutId(t);var data=t.getCompound("BlacklightHost");String core=data.getString("NativeCore");
            data.remove("NativeAirResp");data.remove("NativeWaterResp");data.remove("NativeMaxAir");t.remove("BlacklightTissueVitals");host.readAdditionalSaveData(t);
            h.assertTrue(host.hostData().getString("NativeCore").equals(core)&&host.vitals().oxygen()==0&&host.vitals().spleen()==0,"old host migration preserves identity without filling oxygen or stock");
        }
        try{h.assertTrue(fish.hostData().getBoolean("NativeWaterResp")&&!fish.hostData().getBoolean("NativeAirResp")&&dolphin.hostData().getBoolean("NativeAirResp")&&!dolphin.hostData().getBoolean("NativeWaterResp")&&dolphin.hostData().getInt("NativeMaxAir")==4800,"actual source types recover their original fish and dolphin traits");}finally{clean(fish,dolphin);}h.succeed();
    }
}
