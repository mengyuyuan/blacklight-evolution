package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.registry.ModEntities;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.*;

/** Calls the real production entry even in RED, without replacing it with a test model. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleFleetGameTest {
    private static Object call(String method,Class<?>[] types,Object... values){
        try{return Class.forName("com.codex.blacklight.military.vehicle.VehicleFleetSystem").getMethod(method,types).invoke(null,values);}
        catch(InvocationTargetException failure){throw new GameTestAssertException("real fleet entry failed: "+method+": "+failure.getCause());}
        catch(ReflectiveOperationException failure){throw new GameTestAssertException("real fleet production entry is missing: "+method+": "+failure);}
    }
    @SuppressWarnings("unchecked") private static UUID create(ServerLevel level,List<MilitaryVehicle> vehicles,UUID officer){
        return ((Optional<UUID>)call("create",new Class<?>[]{ServerLevel.class,List.class,UUID.class},level,vehicles,officer)).orElseThrow(()->new GameTestAssertException("actual crewed fleet was rejected"));
    }
    private static MilitarySoldier soldier(MilitaryVehicleGameTest.Fixture f,boolean officer){
        var s=(MilitarySoldier)(officer?ModEntities.MILITARY_OFFICER.get():ModEntities.MILITARY_SOLDIER.get()).create(f.level);
        s.setAutomaticUnitJoin(false);s.setPersistenceRequired();s.setPos(f.x+.5,81,.5);f.level.addFreshEntity(s);f.entities.add(s);return s;
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=140)
    public static void actualCrewedFleetMovesWithoutSpendingLegacyFuelThroughProductionTick(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,220000);var v=f.truck();v.inventory().addFuel(1000);
        var driver=soldier(f,false);var officer=soldier(f,true);
        h.assertTrue(driver.startRiding(v)&&officer.startRiding(v),"existing real driver and officer physically board");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());var start=v.position();long fuel=v.fuelTicks();
            h.assertTrue((Boolean)call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"MOVE",new BlockPos(f.x,81,18)),"real fleet accepts independent MOVE mission");
            h.runAfterDelay(50,()->{try(var cleanup=new FleetCleanup(f)){
                h.assertTrue(v.position().distanceTo(start)>4,"actual registered server tick drives the real mechanical hull");
                h.assertTrue(v.fuelTicks()==fuel&&driver.getVehicle()==v&&officer.getVehicle()==v,"actual movement preserves dormant legacy fuel and original crew");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void playerOccupationSuspendsActualFleetWithoutCloningOrMovingCrew(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,221000);var v=f.truck();v.inventory().addFuel(1000);
        var driver=soldier(f,false);var officer=soldier(f,true);h.assertTrue(driver.startRiding(v)&&officer.startRiding(v),"actual crew boards");
        var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setPos(v.getX(),81,v.getZ());
        h.assertTrue(player.startRiding(v),"actual player boards an existing fleet vehicle");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());var start=v.position();long fuel=v.fuelTicks();
            call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"MOVE",new BlockPos(f.x,81,18));
            h.runAfterDelay(35,()->{try(var cleanup=new FleetCleanup(f)){h.assertTrue(v.position().equals(start)&&v.fuelTicks()==fuel,"player occupation parks actual AI without spending fuel");h.assertTrue(v.getPassengers().size()==3&&driver.getVehicle()==v&&officer.getVehicle()==v,"no copied crew, forced dismount, or replacement driver");}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=140)
    public static void deadFormalOfficerUsesActualLoadedHighestRankWithoutReplacingAppointment(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,222000);var v=f.truck();
        var driver=soldier(f,false);var officer=soldier(f,true);driver.setRank(com.codex.blacklight.military.MilitaryRank.CAPTAIN);
        h.assertTrue(driver.startRiding(v)&&officer.startRiding(v),"actual original crew boards");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());
            h.runAfterDelay(2,()->{
                h.assertTrue(officer.getUUID().equals(call("actingCommander",new Class<?>[]{ServerLevel.class,UUID.class},f.level,id)),"formal real officer keeps appointment while operational despite a higher-rank crew member");
                officer.setHealth(0);
                h.runAfterDelay(3,()->{try(var cleanup=new FleetCleanup(f)){h.assertTrue(driver.getUUID().equals(call("actingCommander",new Class<?>[]{ServerLevel.class,UUID.class},f.level,id)),"death elects actual loaded highest rank without spawning a replacement");}h.succeed();});
            });
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=150)
    public static void repeatedRealRoadFailureBlocksFleetAndKeepsActualUnknownChunksUnknown(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,223000);var v=f.truck();v.inventory().addFuel(1000);
        var driver=soldier(f,false);var officer=soldier(f,true);h.assertTrue(driver.startRiding(v)&&officer.startRiding(v),"actual original crew boards");
        for(int x=-10;x<=10;x++)for(int y=81;y<87;y++)f.level.setBlock(new BlockPos(f.x+x,y,8),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),2);
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());var forced=Set.copyOf(f.level.getForcedChunks());int unknownX=(f.x>>4)+20,unknownZ=80;
            h.assertTrue(f.level.getChunkSource().getChunkNow(unknownX,unknownZ)==null,"fixture has an actual unknown chunk");
            call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"MOVE",new BlockPos(f.x,81,18));
            h.runAfterDelay(75,()->{try(var cleanup=new FleetCleanup(f)){
                h.assertTrue("BLOCKED".equals(call("status",new Class<?>[]{ServerLevel.class,UUID.class},f.level,id)),"three actual bounded plan failures stop the production fleet");
                var after=Set.copyOf(f.level.getForcedChunks());var added=new HashSet<>(after);added.removeAll(forced);var removed=new HashSet<>(forced);removed.removeAll(after);
                h.assertTrue(v.getBoundingBox().maxZ<=8.001,"actual hull never crosses wall; bbox="+v.getBoundingBox());
                h.assertTrue(f.forced.keySet().stream().allMatch(after::contains),"planner preserves this fixture's own already-loaded cells; added="+added.size()+", removedOtherFixtures="+removed.size());
                h.assertTrue(f.level.getChunkSource().getChunkNow(unknownX,unknownZ)==null,"actual missing chunk remains missing");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=180)
    public static void actualClearMissionUsesRealGunnerFiniteAmmoAndExistingInfectedTargetRules(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,224000);var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);
        var driver=soldier(f,false);var gunner=soldier(f,false);var officer=soldier(f,true);
        h.assertTrue(driver.startRiding(v)&&gunner.startRiding(v)&&officer.startRiding(v),"three existing real personnel fill actual driver/gunner/officer seats");
        v.inventory().addAmmunition("autocannon_round",2);
        var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setPersistenceRequired();infected.setNoAi(true);infected.setPos(f.x+.5,81,19);f.level.addFreshEntity(infected);f.entities.add(infected);
        var ally=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);ally.setPersistenceRequired();ally.setAutomaticUnitJoin(false);ally.setNoAi(true);ally.setPos(f.x+5,81,11);f.level.addFreshEntity(ally);f.entities.add(ally);
        float initialHealth=infected.getHealth(),allyHealth=ally.getHealth();
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"CLEAR_AREA",new BlockPos(f.x,81,19));
            h.runAfterDelay(100,()->{try(var cleanup=new FleetCleanup(f)){
                h.assertTrue(infected.getHealth()<initialHealth&&v.inventory().ammunition("autocannon_round")==0,"actual AI gunner fires real finite shells and damages an actual permitted infected host; "+targetDiagnostic(v,infected)+"; ammo="+v.inventory().ammunition("autocannon_round"));
                h.assertTrue(ally.getHealth()==allyHealth&&driver.getVehicle()==v&&gunner.getVehicle()==v,"Army/BW alliance and actual crew identities remain intact");
                h.assertTrue(v.fuelTicks()==0,"actual APC combat never invents driving fuel");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=150)
    public static void threeActualFleetsUseProductionPlanningBudgetAndLoadedNodeCounters(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,225000);var vehicles=new ArrayList<MilitaryVehicle>();var officers=new ArrayList<MilitarySoldier>();
        for(int lane=-1;lane<=1;lane++){
            var v=f.truck();v.setPos(f.x+.5+lane*6,81,.5);v.inventory().addFuel(1000);var d=soldier(f,false);var o=soldier(f,true);
            h.assertTrue(d.startRiding(v)&&o.startRiding(v),"existing real lane crew boards");vehicles.add(v);officers.add(o);
        }
        h.runAfterDelay(20,()->{
            for(int i=0;i<vehicles.size();i++){var v=vehicles.get(i);var id=create(f.level,List.of(v),officers.get(i).getUUID());call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"MOVE",new BlockPos(f.x+(i-1)*6,81,18));}
            pollBudget(h,f,35,()->{try(var cleanup=new FleetCleanup(f)){
                var b=(com.codex.blacklight.military.vehicle.VehicleWorkBudget)call("budget",new Class<?>[]{ServerLevel.class},f.level);
                h.assertTrue(b.totalPlans()>=3&&b.maxSearchNodes()>0&&b.maxSearchNodes()<=2048,"actual production searches expose nonzero counters within 2048-node cap");
                for(var v:vehicles)h.assertTrue(v.getZ()>2&&v.fuelTicks()==1000,"each actual fleet receives fair work without spending dormant legacy fuel");
            }h.succeed();});
        });
    }
    private static void pollBudget(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,int remaining,Runnable done){
        var b=(com.codex.blacklight.military.vehicle.VehicleWorkBudget)call("budget",new Class<?>[]{ServerLevel.class},f.level);
        h.assertTrue(b.plansThisTick()<=2,"actual shared production planner never exceeds two searches in this server tick");
        if(remaining==0)done.run();else h.runAfterDelay(1,()->pollBudget(h,f,remaining-1,done));
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=130)
    public static void realPersonnelOrdersMapToIndependentFleetIntentWithoutChangingOldEnum(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,226000);var v=f.truck();v.inventory().addFuel(1000);var driver=soldier(f,false);var officer=soldier(f,true);
        var org=com.codex.blacklight.military.MilitaryOrganizationSystem.organization(f.level);var unit=UUID.randomUUID();
        h.assertTrue(org.create(unit,com.codex.blacklight.military.MilitaryFaction.MILITARY,com.codex.blacklight.military.MilitaryUnitType.TACTICAL_SQUAD,"fleet-order-qa",4,new com.codex.blacklight.military.MilitaryOrder.Point(f.x+.5,81,.5)),"actual personnel unit is registered");
        h.assertTrue(org.assign(unit,driver.getUUID(),driver.rank())&&org.assign(unit,officer.getUUID(),officer.rank()),"only actual personnel enter original unit roster");driver.setUnitId(unit);officer.setUnitId(unit);
        h.assertTrue(driver.startRiding(v)&&officer.startRiding(v),"existing unit personnel physically board");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());org.order(unit,new com.codex.blacklight.military.MilitaryOrder(com.codex.blacklight.military.MilitaryOrder.Mode.MOVE,new com.codex.blacklight.military.MilitaryOrder.Point(f.x+.5,81,18.5)));
            h.runAfterDelay(3,()->{
                h.assertTrue("MOVE".equals(actualMission(f.level,id)),"real changed personnel MOVE maps into independent fleet intent");org.order(unit,new com.codex.blacklight.military.MilitaryOrder(com.codex.blacklight.military.MilitaryOrder.Mode.RETURN,null));
                h.runAfterDelay(3,()->{
                    h.assertTrue("RETURN".equals(actualMission(f.level,id)),"real personnel RETURN maps to fleet return");org.order(unit,new com.codex.blacklight.military.MilitaryOrder(com.codex.blacklight.military.MilitaryOrder.Mode.PATROL,new com.codex.blacklight.military.MilitaryOrder.Point(f.x+.5,81,18.5)));
                    h.runAfterDelay(3,()->{try(var cleanup=new FleetCleanup(f)){h.assertTrue("PATROL".equals(actualMission(f.level,id)),"real personnel PATROL maps to fleet patrol");h.assertTrue(org.allMembers(unit).size()==2&&com.codex.blacklight.military.MilitaryOrder.Mode.values().length==5,"vehicle UUID is never added to personnel and saved old order enum remains unchanged");}org.removeUnit(unit);h.succeed();});
                });
            });
        });
    }
    @SuppressWarnings("unchecked") private static String actualMission(ServerLevel level,UUID id){
        var fleets=(List<com.codex.blacklight.military.vehicle.VehicleFleet>)call("fleets",new Class<?>[]{ServerLevel.class},level);
        return fleets.stream().filter(f->f.id().equals(id)).findFirst().orElseThrow().mission().name();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=140)
    public static void actualTwoVehicleFleetDoesNotTreatHalfBlockFormationWaitAsRoadFailure(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,227000);var first=f.truck();first.setPos(f.x+.5,81,10.5);var second=f.truck();second.setPos(f.x+.5,81,2);first.inventory().addFuel(1000);second.inventory().addFuel(1000);
        var d1=soldier(f,false);var o1=soldier(f,true);var d2=soldier(f,false);var o2=soldier(f,true);
        h.assertTrue(d1.startRiding(first)&&o1.startRiding(first)&&d2.startRiding(second)&&o2.startRiding(second),"two actual trucks keep their four original real crew");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(first,second),o1.getUUID());call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"MOVE",new BlockPos(f.x+8,81,-5));
            h.runAfterDelay(45,()->{try(var cleanup=new FleetCleanup(f)){h.assertTrue(!"BLOCKED".equals(call("status",new Class<?>[]{ServerLevel.class,UUID.class},f.level,id)),"a half-block correction behind a turning lead vehicle is a valid formation wait; lead="+first.position()+"/yaw="+first.getYRot()+"/stop="+first.stopReason()+"; tail="+second.position()+"/yaw="+second.getYRot()+"/stop="+second.stopReason());}h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=150)
    public static void actualDismountedClearTrooperKeepsFarFieldTargetWithoutChangingOriginalHomeOrUnit(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,228000);var v=f.truck();v.inventory().addFuel(1000);var driver=soldier(f,false);var officer=soldier(f,true);
        var trooper=ModEntities.MILITARY_SOLDIER.get().create(f.level);trooper.setPersistenceRequired();trooper.setAutomaticUnitJoin(false);trooper.setPos(f.x-128,81,.5);var originalHome=trooper.home();trooper.setPos(f.x+.5,81,.5);f.level.addFreshEntity(trooper);f.entities.add(trooper);
        var org=com.codex.blacklight.military.MilitaryOrganizationSystem.organization(f.level);var unit=UUID.randomUUID();
        h.assertTrue(org.create(unit,com.codex.blacklight.military.MilitaryFaction.MILITARY,com.codex.blacklight.military.MilitaryUnitType.TACTICAL_SQUAD,"fleet-far-field-qa",4,new com.codex.blacklight.military.MilitaryOrder.Point(f.x-128,81,.5))&&org.assign(unit,trooper.getUUID(),trooper.rank()),"original guard unit exists 128 blocks away");trooper.setUnitId(unit);
        org.order(unit,new com.codex.blacklight.military.MilitaryOrder(com.codex.blacklight.military.MilitaryOrder.Mode.GUARD,null));var originalOrder=org.effectiveOrder(unit);
        h.assertTrue(driver.startRiding(v)&&officer.startRiding(v)&&trooper.startRiding(v),"existing trooper boards a real passenger seat");
        var disabled=soldier(f,false);disabled.setNoAi(true);h.assertTrue(disabled.startRiding(v),"real disabled passenger retains a physical seat");
        var allied=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);allied.setPersistenceRequired();allied.setAutomaticUnitJoin(false);allied.setPos(v.position());f.level.addFreshEntity(allied);f.entities.add(allied);h.assertTrue(allied.startRiding(v),"independent allied faction can occupy its own real passenger seat");
        var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setPersistenceRequired();infected.setNoAi(true);infected.setInvulnerable(true);infected.setPos(f.x+.5,81,18);f.level.addFreshEntity(infected);f.entities.add(infected);
        h.assertTrue(!com.codex.blacklight.entity.BlackwatchSquadSystem.withinTerritory(trooper,infected.position()),"actual baseline personnel territory really excludes this far-field target");
        h.runAfterDelay(20,()->{
            UUID id=create(f.level,List.of(v),officer.getUUID());call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"CLEAR_AREA",new BlockPos(f.x,81,18));
            h.runAfterDelay(30,()->{try(var cleanup=new FleetCleanup(f)){
                h.assertTrue(!trooper.isPassenger(),"actual passenger physically dismounts for fleet clear; "+targetDiagnostic(v,infected));
                h.assertTrue(trooper.getTarget()==infected&&com.codex.blacklight.entity.BlackwatchSquadSystem.withinTerritory(trooper,infected.position()),"real distant dismounted trooper must retain its fleet target instead of clearing it and walking home");
                h.assertTrue(trooper.home().equals(originalHome)&&unit.equals(trooper.unitId())&&org.effectiveOrder(unit).equals(originalOrder),"temporary fleet deployment never rewrites original home, unit or saved guard order");
                h.assertTrue(disabled.getVehicle()==v&&allied.getVehicle()==v,"fleet never dismounts a disabled troop or commandeers an independent allied faction");
                trooper.setNoAi(true);h.assertTrue(!com.codex.blacklight.military.vehicle.VehicleFleetSystem.deploymentAllows(trooper,infected.position()),"a real disabled troop immediately loses its temporary deployment authority");
            }org.removeUnit(unit);h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=150)
    public static void actualAiGunnerPitchesRealMuzzleDownToSixteenBlockLowInfectedTarget(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,229000);var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x+.5,81,.5);f.level.addFreshEntity(tank);f.entities.add(tank);
        var driver=soldier(f,false);var gunner=soldier(f,false);h.assertTrue(driver.startRiding(tank)&&gunner.startRiding(tank),"actual AI driver and gunner occupy actual tank seats without a mock player");
        for(int x=-2;x<=2;x++)for(int z=14;z<=19;z++){f.level.setBlock(new BlockPos(f.x+x,80,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);f.level.setBlock(new BlockPos(f.x+x,79,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);}
        var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setPersistenceRequired();infected.setNoAi(true);infected.setPos(f.x+.5,80,16.5);f.level.addFreshEntity(infected);f.entities.add(infected);
        h.runAfterDelay(20,()->aimLowTarget(h,f,tank,gunner,infected,35));
    }
    private static void aimLowTarget(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle tank,MilitarySoldier gunner,net.minecraft.world.entity.LivingEntity target,int remaining){
        boolean aligned=tank.aimAiWeapon(gunner,target);
        if(remaining>0){h.runAfterDelay(1,()->aimLowTarget(h,f,tank,gunner,target,remaining-1));return;}
        try(var cleanup=new FleetCleanup(f)){
            var origin=new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(tank.getX(),tank.getY(),tank.getZ());var a=tank.definition().mountWorld("breech_primary",origin,tank.getYRot(),tank.turretYaw(),tank.gunPitch());var b=tank.definition().mountWorld("muzzle_primary",origin,tank.getYRot(),tank.turretYaw(),tank.gunPitch());
            var muzzle=new net.minecraft.world.phys.Vec3(b.x(),b.y(),b.z());var ray=muzzle.subtract(new net.minecraft.world.phys.Vec3(a.x(),a.y(),a.z())).normalize();
            h.assertTrue(aligned&&tank.gunPitch()>2,"actual AI must lower its cannon instead of leaving a horizontal ray above a low target; "+targetDiagnostic(tank,target)+"; aligned="+aligned+" pitch="+tank.gunPitch());
            h.assertTrue(target.getBoundingBox().inflate(.05).clip(muzzle,muzzle.add(ray.scale(32))).isPresent(),"actual breech/muzzle transformed ray intersects the real low target body at sixteen blocks");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=150)
    public static void actualFriendlyMechanicalHullBlocksAiShotWithoutSpendingFiniteShells(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,230000);var apc=ModEntities.MILITARY_APC.get().create(f.level);apc.setPos(f.x+.5,81,.5);f.level.addFreshEntity(apc);f.entities.add(apc);apc.inventory().addAmmunition("autocannon_round",2);
        var driver=soldier(f,false);var gunner=soldier(f,false);h.assertTrue(driver.startRiding(apc)&&gunner.startRiding(apc),"actual AI crew boards");
        var shield=f.truck();shield.setPos(f.x+.5,81,12);var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setPersistenceRequired();infected.setNoAi(true);infected.setPos(f.x+.5,81,21);f.level.addFreshEntity(infected);f.entities.add(infected);float health=infected.getHealth()/infected.getMaxHealth();
        h.runAfterDelay(20,()->{
            h.assertTrue(com.codex.blacklight.military.vehicle.VehicleThreatBridge.legalTarget(apc,infected),"underlying actual infection/ally/LOS rules allow this target before mechanical line-of-fire protection; "+targetDiagnostic(apc,infected));
            holdFireAtHull(h,f,apc,shield,infected,health,35);
        });
    }
    private static void holdFireAtHull(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitaryVehicle apc,MilitaryVehicle shield,net.minecraft.world.entity.LivingEntity target,float health,int remaining){
        com.codex.blacklight.military.vehicle.VehicleThreatBridge.engage(apc,target);
        if(remaining>0){h.runAfterDelay(1,()->holdFireAtHull(h,f,apc,shield,target,health,remaining-1));return;}
        try(var cleanup=new FleetCleanup(f)){
            var origin=new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(apc.getX(),apc.getY(),apc.getZ());var a=apc.definition().mountWorld("breech_primary",origin,apc.getYRot(),apc.turretYaw(),apc.gunPitch());var b=apc.definition().mountWorld("muzzle_primary",origin,apc.getYRot(),apc.turretYaw(),apc.gunPitch());var muzzle=new net.minecraft.world.phys.Vec3(b.x(),b.y(),b.z());var ray=muzzle.subtract(new net.minecraft.world.phys.Vec3(a.x(),a.y(),a.z())).normalize();
            h.assertTrue(target.getBoundingBox().inflate(.05).clip(muzzle,muzzle.add(ray.scale(32))).isPresent(),"real AI is actually aimed at the permitted target while the mechanical shield intercepts that ray");
            // Tissue development changes maximum HP while preserving the living health fraction.
            h.assertTrue(apc.inventory().ammunition("autocannon_round")==2&&shield.health()==shield.maxHealth()&&Math.abs(target.getHealth()/target.getMaxHealth()-health)<.0001,
                    "friendly/empty real chassis blocks firing before debit: ammo="+apc.inventory().ammunition("autocannon_round")+" shield="+shield.health()+"/"+shield.maxHealth()+" target="+target.getHealth()+"/"+target.getMaxHealth()+" expectedFraction="+health);
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=650)
    public static void actualClearAreaSpreadsThreeRealVehiclesAtFourteenBlockLateralIntervals(GameTestHelper h){
        clearFormation(h,false);
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=300)
    public static void actualClearAreaBlockedLateralTerrainParksWithoutTeleportOrForcedChunks(GameTestHelper h){
        clearFormation(h,true);
    }
    private static void clearFormation(GameTestHelper h,boolean blocked){
        var f=new MilitaryVehicleGameTest.Fixture(h,blocked?311000:310000);
        for(int cx=(f.x-48)>>4;cx<=(f.x+48)>>4;cx++)for(int cz=-2;cz<=2;cz++){
            long key=net.minecraft.world.level.ChunkPos.asLong(cx,cz);
            f.forced.putIfAbsent(key,f.level.getForcedChunks().contains(key));f.level.setChunkForced(cx,cz,true);
        }
        for(int dx=-36;dx<=36;dx++)for(int z=-28;z<=32;z++){
            f.level.setBlock(new BlockPos(f.x+dx,80,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            for(int y=81;y<87;y++)f.level.setBlock(new BlockPos(f.x+dx,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        }
        if(blocked)for(int dx:new int[]{-5,5})for(int z=-32;z<=40;z++)for(int y=81;y<87;y++)
            f.level.setBlock(new BlockPos(f.x+dx,y,z),net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(),2);
        var apc=ModEntities.MILITARY_APC.get().create(f.level);apc.setPos(f.x+.5,81,.5);f.level.addFreshEntity(apc);f.entities.add(apc);
        var truck=f.truck();truck.setPos(f.x+.5,81,-7.5);
        var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x+.5,81,-20);f.level.addFreshEntity(tank);f.entities.add(tank);
        h.assertTrue(tank.getBoundingBox().maxZ+1<truck.getBoundingBox().minZ,"fixture precondition: the enlarged tank barrel starts over one block behind the truck body");
        var vehicles=List.of(apc,truck,tank);for(var vehicle:vehicles)vehicle.inventory().addFuel(1000);
        apc.inventory().addAmmunition("autocannon_round",2);tank.inventory().addAmmunition("machine_gun_round",2);
        var crew=new ArrayList<MilitarySoldier>();var drivers=new ArrayList<MilitarySoldier>();
        for(var vehicle:vehicles){var driver=soldier(f,false);crew.add(driver);drivers.add(driver);h.assertTrue(driver.startRiding(vehicle),"original real driver boards");
            if(vehicle!=truck){var gunner=soldier(f,false);crew.add(gunner);h.assertTrue(gunner.startRiding(vehicle),"original real gunner boards");}}
        var officer=soldier(f,true);crew.add(officer);h.assertTrue(officer.startRiding(truck),"original real officer boards the truck officer seat");
        var trooper=soldier(f,false);crew.add(trooper);h.assertTrue(trooper.startRiding(truck),"original real transport passenger boards");
        h.assertTrue(truck.occupant(com.codex.blacklight.military.vehicle.VehicleDefinition.Job.PASSENGER)==trooper,"transport trooper occupies an actual passenger seat");
        var infected=ModEntities.INFECTED_HOST.get().create(f.level);infected.setPersistenceRequired();infected.setNoAi(true);infected.setInvulnerable(true);
        infected.setPos(f.x+.5,81,18.5);f.level.addFreshEntity(infected);f.entities.add(infected);
        var starts=vehicles.stream().map(MilitaryVehicle::position).toList();
        int unknownX=(f.x>>4)+20,unknownZ=80;
        h.assertTrue(f.level.getChunkSource().getChunkNow(unknownX,unknownZ)==null,"formation fixture has a real unknown chunk");
        h.runAfterDelay(1,new Runnable(){
            // The larger hull needs a longer collision-free turn from the separated rear start.
            UUID id;int waiting=80,remaining=blocked?140:560;
            final net.minecraft.world.phys.Vec3[] previous=starts.toArray(net.minecraft.world.phys.Vec3[]::new);
            final long[] fuel={1000,1000,1000};final int[] stationary=new int[3];
            @Override public void run(){try{
                if(id==null){
                    boolean ready=f.entities.stream().allMatch(e->f.level.getEntity(e.getUUID())==e&&e.tickCount>0&&f.level.isPositionEntityTicking(e.blockPosition()));
                    if(!ready){h.assertTrue(--waiting>0,"real formation entities must become tracked and ticking before behavior assertions");h.runAfterDelay(1,()->this.run());return;}
                    for(var vehicle:vehicles)h.assertTrue(com.codex.blacklight.military.vehicle.VehicleKinematics.placementReady(vehicle),"formation starts with real supported hull and barrel clearance: "+vehicle.chassisId());
                    h.assertTrue(com.codex.blacklight.military.vehicle.VehicleThreatBridge.legalTarget(apc,infected),"real clear target is legal before formation starts; "+targetDiagnostic(apc,infected));
                    id=create(f.level,vehicles,officer.getUUID());
                    h.assertTrue((Boolean)call("setMission",new Class<?>[]{ServerLevel.class,UUID.class,String.class,BlockPos.class},f.level,id,"CLEAR_AREA",new BlockPos(f.x,81,18)),"actual fleet accepts CLEAR_AREA");
                }
                var budget=com.codex.blacklight.military.vehicle.VehicleFleetSystem.budget(f.level);
                h.assertTrue(budget.plansThisTick()<=2&&budget.maxSearchNodes()<=2048,"combat formation shares the real global planning and node limits");
                for(int i=0;i<vehicles.size();i++){
                    var vehicle=vehicles.get(i);double step=vehicle.position().distanceTo(previous[i]);
                    h.assertTrue(step<=vehicle.definition().maxSpeed()+.0001,"formation must drive the real hull without a teleport; step="+step);
                    if(!trooper.isPassenger())h.assertTrue(step<.0001,"transport passenger deploys only after the fleet safely parks");
                    h.assertTrue(vehicle.fuelTicks()==fuel[i],"formation preserves exact dormant legacy fuel");
                    stationary[i]=step<.0001?stationary[i]+1:0;previous[i]=vehicle.position();fuel[i]=vehicle.fuelTicks();
                }
                if(--remaining>0){h.runAfterDelay(1,()->this.run());return;}
                try(var cleanup=new FleetCleanup(f)){
                    String positions=vehicles.stream().map(v->v.chassisId()+"="+v.position()).toList().toString();
                    if(blocked){
                        h.assertTrue("BLOCKED".equals(call("status",new Class<?>[]{ServerLevel.class,UUID.class},f.level,id)),"unreachable lateral combat slots must report BLOCKED; "+positions);
                        for(var vehicle:vehicles)h.assertTrue(vehicle.getBoundingBox().minX>=f.x-4-.001&&vehicle.getBoundingBox().maxX<=f.x+5+.001,"blocked combat hull never crosses either real wall");
                    }else{
                        var lateral=vehicles.stream().mapToDouble(MilitaryVehicle::getX).sorted().toArray();
                        h.assertTrue(Math.abs(lateral[1]-lateral[0]-14)<=1&&Math.abs(lateral[2]-lateral[1]-14)<=1,"CLEAR_AREA must spread adjacent real vehicles about 14 blocks laterally, not keep the original convoy parked; "+positions);
                        double depth=vehicles.stream().mapToDouble(MilitaryVehicle::getZ).max().orElseThrow()-vehicles.stream().mapToDouble(MilitaryVehicle::getZ).min().orElseThrow();
                        h.assertTrue(depth<=2,"combat vehicles form a horizontal line facing the contact; "+positions);
                        h.assertTrue(vehicles.stream().filter(v->v.position().distanceTo(starts.get(vehicles.indexOf(v)))>4&&v.fuelTicks()==1000).count()>=2,"at least two original vehicles actually drive without spending dormant legacy fuel");
                        h.assertTrue(!trooper.isPassenger(),"original transport passenger deploys after safe combat parking; "+positions+"; yaw="+vehicles.stream().map(MilitaryVehicle::getYRot).toList()+"; stationary="+Arrays.toString(stationary)+"; trooper="+trooper.commandRole()+"/"+MilitaryVehicle.crewOperational(trooper)+"/valid="+trooper.validTarget(infected)+"; "+com.codex.blacklight.military.vehicle.VehicleFleetSystem.diagnostic(f.level,id,tank.getUUID()));
                    }
                    for(int still:stationary)h.assertTrue(still>=20,"formation finishes safely parked for at least twenty ticks");
                    for(int i=0;i<vehicles.size();i++)h.assertTrue(vehicles.get(i).occupant(com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER)==drivers.get(i),"original real drivers remain aboard");
                    h.assertTrue(crew.get(1).getVehicle()==apc&&crew.get(4).getVehicle()==tank,"original real gunners remain aboard");
                    h.assertTrue(officer.getVehicle()==truck&&crew.stream().allMatch(s->f.level.getEntity(s.getUUID())==s),"formation preserves the original real officer and all personnel identities");
                    var area=new net.minecraft.world.phys.AABB(f.x-48,75,-48,f.x+48,96,48);
                    h.assertTrue(f.level.getEntitiesOfClass(MilitarySoldier.class,area).size()==7,"formation never duplicates personnel");
                    h.assertTrue(apc.inventory().ammunition("autocannon_round")<=2&&tank.inventory().ammunition("machine_gun_round")<=2,"combat firing uses only original finite ammunition");
                    h.assertTrue(budget.maxSearchNodes()>0,"combat deployment must use the actual bounded path planner");
                    h.assertTrue(f.forced.keySet().stream().allMatch(f.level.getForcedChunks()::contains)&&f.level.getChunkSource().getChunkNow(unknownX,unknownZ)==null,"formation preserves loaded fixture chunks and never loads the unknown probe");
                }h.succeed();
            }catch(GameTestAssertException failure){new FleetCleanup(f).close();throw failure;}}
        });
    }
    private static String targetDiagnostic(MilitaryVehicle vehicle,net.minecraft.world.entity.LivingEntity target){
        var actor=vehicle.weaponOperator();var living=actor instanceof net.minecraft.world.entity.LivingEntity l?l:null;
        return "targetAlive="+target.isAlive()+"/removed="+target.isRemoved()+"/registered="+(vehicle.level() instanceof ServerLevel level&&level.getEntity(target.getUUID())==target)+"/pos="+target.position()+"; actor="+(actor==null?"null":actor.getClass().getSimpleName())+"/operational="+MilitaryVehicle.crewOperational(actor)+"/valid="+(actor instanceof MilitarySoldier s&&s.validTarget(target))+"/LOS="+(living!=null&&living.hasLineOfSight(target))+"; infected="+com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(target)+"/locked="+vehicle.controlLocked()+"/distance="+Math.round(vehicle.distanceTo(target));
    }
    private record FleetCleanup(MilitaryVehicleGameTest.Fixture fixture) implements AutoCloseable {
        @Override public void close(){
            var ids=new HashSet<UUID>();for(var entity:fixture.entities)ids.add(entity.getUUID());
            for(var fleet:com.codex.blacklight.military.vehicle.VehicleFleetSystem.fleets(fixture.level))
                if(fleet.vehicles().stream().anyMatch(ids::contains))com.codex.blacklight.military.vehicle.VehicleFleetSystem.remove(fixture.level,fleet.id());
            fixture.close();
        }
    }
    private VehicleFleetGameTest(){}
}
