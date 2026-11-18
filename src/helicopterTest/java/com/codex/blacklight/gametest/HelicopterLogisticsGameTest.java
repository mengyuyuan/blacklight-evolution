package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.military.aircraft.HelicopterSupplies;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

/** Real player block-use/entity-interact paths, including the one-time takeover loadout. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterLogisticsGameTest {
    private static BlockHitResult hit(HelicopterPilotGameTest.Fixture f){return new BlockHitResult(new Vec3(f.x+.5,81,.05),Direction.UP,new BlockPos(f.x,80,0),false);}
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void realPrototypePlacementUsesOrdinaryReachAndCreatesOwnedLoadedAircraft(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,981000);
        h.runAfterDelay(25,()->{try(f){
            var p=HelicopterPilotGameTest.player(h,f,true);p.setGameMode(GameType.SURVIVAL);p.setPos(f.x+.5,81,-3.9);p.setYRot(0);
            for(var form:new com.codex.blacklight.power.PowerForm[]{com.codex.blacklight.power.PowerForm.NONE,com.codex.blacklight.power.PowerForm.CLAWS,com.codex.blacklight.power.PowerForm.BLADE}){
                var state=com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);state.unlock(form);state.activate(form);com.codex.blacklight.registry.ModAttachments.setData(p,com.codex.blacklight.registry.ModAttachments.PLAYER_STATE,state);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("blackwatch_helicopter_chassis")));
                h.assertTrue(com.codex.blacklight.mode.PrototypeSurvivalLock.holdingPlacementItem(p)&&p.getEyePosition().distanceTo(hit(f).getLocation())<4.5,"actual held chassis owns ordinary reachable block use in "+form);
                var result=p.gameMode.useItemOn(p,f.level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit(f));
                var found=f.level.getEntitiesOfClass(BlackwatchHelicopter.class,new AABB(f.x-12,80,-9,f.x+12,90,20));f.entities.addAll(found);
                h.assertTrue(result.consumesAction()&&found.size()==1&&p.getMainHandItem().isEmpty(),"one actual aircraft and one survival chassis debit in "+form);
                var c=found.get(0);h.assertTrue(p.getUUID().equals(c.pilot().owner())&&c.safeLanding(c.position())&&c.getPassengers().isEmpty(),"owned supported aircraft, without fabricated crew");
                h.assertTrue(c.combat().rounds()==240&&c.combat().rockets()==8&&c.combat().rapid().left()==75&&c.combat().rapid().right()==75,"first player ownership supplies all three finite magazines");c.discard();
            }
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void firstBoardingFillsOnceButSeatsReboardingAndReloadCannotRefill(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,984000);
        h.runAfterDelay(25,()->{try(f){
            var c=f.craft(0);c.combat().emptyPlayerStores();var p=HelicopterPilotGameTest.player(h,f,false);
            h.assertTrue(p.startRiding(c,true),"actual first player boards an empty unowned aircraft");
            h.assertTrue(c.combat().rounds()==240&&c.combat().rockets()==8&&c.combat().rapid().left()==75&&c.combat().rapid().right()==75,"first boarding fills all finite stores");
            c.combat().emptyPlayerStores();c.combat().addPlayerAmmunition(HelicopterSupplies.CANNON,7);c.combat().addPlayerAmmunition(HelicopterSupplies.HELLFIRE,2);c.combat().addPlayerAmmunition(HelicopterSupplies.RAPID,3);
            for(int i=0;i<6;i++)h.assertTrue(c.pilot().switchSeat(p),"real seat switch succeeds");
            p.stopRiding();h.assertTrue(p.startRiding(c,true),"same player reboards");
            var saved=new CompoundTag();c.saveWithoutId(saved);c.load(saved);c.pilot().reconcile();
            p.stopRiding();var other=HelicopterPilotGameTest.player(h,f,false);h.assertTrue(other.startRiding(c,true),"another player takes the existing pilot seat");
            c.pilot().captured(other);
            h.assertTrue(c.combat().rounds()==7&&c.combat().rockets()==2&&c.combat().rapid().left()+c.combat().rapid().right()==3,"switching, reboarding, ownership handover and reload preserve exact remaining stores");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void groundedStartingRotorAllowsNearbyMechanicToLoadRealItems(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,985000);
        h.runAfterDelay(25,()->{
            var c=f.craft(0);var pilot=HelicopterPilotGameTest.player(h,f,false);h.assertTrue(pilot.startRiding(c,true),"actual pilot starts the rotor");c.combat().emptyPlayerStores();
            var mechanic=HelicopterPilotGameTest.player(h,f,false);mechanic.setGameMode(GameType.SURVIVAL);mechanic.setPos(f.x-3,81,2.1);
            mechanic.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item(HelicopterSupplies.CANNON),12));
            h.runAfterDelay(10,()->{try(f){
                h.assertTrue(c.rotor()>0&&c.rotor()<.9F&&c.getDeltaMovement().lengthSqr()<=.0004,"actual rotor is starting while the aircraft remains stationary");
                h.assertTrue(mechanic.getBoundingBox().intersects(c.getBoundingBox()),"service player overlaps the broad hull envelope");
                h.assertTrue(mechanic.interactOn(c,InteractionHand.MAIN_HAND).consumesAction(),"real nearby service interaction succeeds during startup");
                h.assertTrue(mechanic.getMainHandItem().isEmpty()&&c.combat().rounds()==12,"reload transfers and debits exactly twelve real rounds");
            }h.succeed();});
        });
    }
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void actualEntityInteractionTransfersEachMagazineAndPersistsExactRemainder(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,982000);
        h.runAfterDelay(25,()->{try(f){
            var c=f.craft(0);var p=HelicopterPilotGameTest.player(h,f,true);p.setGameMode(GameType.CREATIVE);c.pilot().captured(p);c.setFlightPhase(BlackwatchHelicopter.Phase.IDLE);c.combat().emptyPlayerStores();
            for(String id:new String[]{HelicopterSupplies.CANNON,HelicopterSupplies.HELLFIRE,HelicopterSupplies.RAPID}){
                int capacity=id.equals(HelicopterSupplies.CANNON)?240:id.equals(HelicopterSupplies.HELLFIRE)?8:150;
                int transferred=0;
                while(transferred<capacity){
                    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item(id),64));
                    h.assertTrue(com.codex.blacklight.mode.PrototypeSurvivalLock.holdingPlacementItem(p),"ammo retains right-click ownership in Prototype mode");
                    h.assertTrue(p.interactOn(c,InteractionHand.MAIN_HAND).consumesAction(),"real entity interaction supplies "+id);
                    int debit=Math.min(64,capacity-transferred);transferred+=debit;
                    h.assertTrue(p.getMainHandItem().getCount()==64-debit,"exact creative transfer remainder, including capacity clipping");
                }
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item(id),3));
                h.assertTrue(!p.interactOn(c,InteractionHand.MAIN_HAND).consumesAction()&&p.getMainHandItem().getCount()==3,"full magazine does not consume items");
            }
            h.assertTrue(c.combat().rounds()==240&&c.combat().rockets()==8&&c.combat().rapid().left()==75&&c.combat().rapid().right()==75,"all three real capacities reached independently");
            h.assertTrue(c.controlData().getInt("RapidRight")==75&&c.controlData().getInt("Rockets")==8,"same HUD state is synchronized immediately");
            var tag=new CompoundTag();c.saveWithoutId(tag);var copy=ModEntities.BLACKWATCH_HELICOPTER.get().create(f.level);copy.load(tag);
            h.assertTrue(copy.combat().rounds()==240&&copy.combat().rockets()==8&&copy.combat().rapid().left()==75&&copy.combat().rapid().right()==75,"save/load keeps actual stores");copy.discard();
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void obstructedPlacementAndMovingResupplyKeepRealItems(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,983000);
        h.runAfterDelay(25,()->{try(f){
            var p=HelicopterPilotGameTest.player(h,f,true);p.setGameMode(GameType.SURVIVAL);p.setPos(f.x+.5,81,-3.9);p.setYRot(0);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("blackwatch_helicopter_chassis")));
            var obstruction=new BlockPos(f.x,84,7);f.level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
            h.assertTrue(p.gameMode.useItemOn(p,f.level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit(f))==InteractionResult.FAIL&&p.getMainHandItem().getCount()==1,"real blocked use keeps chassis");
            f.level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),3);
            var c=f.craft(0);c.pilot().captured(p);c.combat().emptyPlayerStores();p.setPos(f.x-4,81,2.1);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item(HelicopterSupplies.CANNON),9));c.setDeltaMovement(.1,0,0);
            h.assertTrue(!p.interactOn(c,InteractionHand.MAIN_HAND).consumesAction()&&p.getMainHandItem().getCount()==9&&c.combat().rounds()==0,"moving aircraft refuses supply without debit");
            c.setDeltaMovement(Vec3.ZERO);c.setPos(c.position().add(0,2,0));
            h.assertTrue(!p.interactOn(c,InteractionHand.MAIN_HAND).consumesAction()&&p.getMainHandItem().getCount()==9,"hovering aircraft is not a landed service pad");
        }h.succeed();});
    }
    @GameTest(template="helicopter_arena",batch="airLogistics",timeoutTicks=140)
    public static void legacyOwnedEmptyAircraftReceivesLoadoutOnlyOnFirstActualControl(GameTestHelper h){
        var f=new HelicopterPilotGameTest.Fixture(h,986000);
        h.runAfterDelay(25,()->{try(f){
            var c=f.craft(0);var p=HelicopterPilotGameTest.player(h,f,false);c.pilot().captured(p);c.combat().emptyPlayerStores();
            var legacy=new CompoundTag();c.saveWithoutId(legacy);legacy.getCompound("AirPilot").remove("InitialStoresIssued");c.load(legacy);c.pilot().reconcile();
            h.assertTrue(c.pilot().owned()&&p.getUUID().equals(c.pilot().owner())&&c.getPassengers().isEmpty(),"actual loaded legacy airframe retains its owner without inventing a pilot");
            h.assertTrue(c.combat().rounds()==0&&c.combat().rockets()==0&&c.combat().rapid().left()+c.combat().rapid().right()==0,"loading the legacy save alone does not refill ammunition");
            h.assertTrue(p.startRiding(c,true)&&c.pilot().slot(p.getUUID())==0,"actual owner takes the loaded aircraft's pilot seat");
            h.assertTrue(c.combat().rounds()==240&&c.combat().rockets()==8&&c.combat().rapid().left()==75&&c.combat().rapid().right()==75,"first actual legacy takeover receives all three full magazines");
            c.combat().emptyPlayerStores();c.combat().addPlayerAmmunition(HelicopterSupplies.CANNON,7);c.combat().addPlayerAmmunition(HelicopterSupplies.HELLFIRE,2);c.combat().addPlayerAmmunition(HelicopterSupplies.RAPID,3);p.stopRiding();
            var migrated=new CompoundTag();c.saveWithoutId(migrated);h.assertTrue(migrated.getCompound("AirPilot").getBoolean("InitialStoresIssued"),"migration persists the consumed one-time loadout grant");c.load(migrated);
            h.assertTrue(p.startRiding(c,true),"owner reboards after migrated save reload");for(int i=0;i<6;i++)h.assertTrue(c.pilot().switchSeat(p),"actual seat switch after migration");
            h.assertTrue(c.combat().rounds()==7&&c.combat().rockets()==2&&c.combat().rapid().left()+c.combat().rapid().right()==3,"migrated reload, reboarding and seat switches preserve exact remaining ammunition");
        }h.succeed();});
    }
    private HelicopterLogisticsGameTest(){}
}
