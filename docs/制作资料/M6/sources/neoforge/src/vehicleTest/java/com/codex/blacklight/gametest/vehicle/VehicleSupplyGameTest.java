package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleSupplyGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=120)
    public static void realChassisItemPlacesOneEmptyVehicleAndDebitsActualStack(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,191000);var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setPos(f.x+.5,81,-5);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("military_truck_chassis")));
        var ground=new BlockPos(f.x,80,0);var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ground),Direction.UP,ground,false));
        h.assertTrue(player.getMainHandItem().getItem().useOn(context).consumesAction(),"actual chassis item use succeeds on clear supported ground");
        h.assertTrue(player.getMainHandItem().isEmpty(),"placing one chassis debits one actual item");
        h.runAfterDelay(20,()->{try(f){
            var vehicles=f.level.getEntitiesOfClass(MilitaryVehicle.class,new AABB(f.x-10,80,-10,f.x+10,87,10));
            h.assertTrue(vehicles.size()==1,"exactly one real tracked chassis was placed");var v=vehicles.getFirst();f.entities.add(v);
            h.assertTrue(v.fuelTicks()==0&&v.inventory().snapshot().ammunition().values().stream().allMatch(count->count==0),"player chassis placement never issues free fuel or ammunition");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void supplyDebitsExactlySevenRealAutocannonRounds(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,192000)){
            var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);
            var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("autocannon_round"),7));
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND).consumesAction(),"actual matching supply is accepted");
            h.assertTrue(v.inventory().ammunition("autocannon_round")==7&&p.getMainHandItem().isEmpty(),"one actual seven-round stack moves exactly once");
            VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND);h.assertTrue(v.inventory().ammunition("autocannon_round")==7,"repeating an empty-hand request cannot duplicate rounds");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void oneRealLegacyFuelCanStaysUnused(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,193000)){
            var v=f.truck();var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("military_fuel_can")));
            h.assertTrue(!VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND).consumesAction(),"legacy fuel can is inactive");
            h.assertTrue(v.fuelTicks()==0&&p.getMainHandItem().getCount()==1,"disabled fuel leaves the real can and zero saved fuel unchanged");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void wrongAmmunitionAndRemoteSupplyNeverChangeEitherInventory(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,194000)){
            var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);
            var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("tank_he_shell"),7));
            h.assertTrue(!VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND).consumesAction()&&p.getMainHandItem().getCount()==7,"HE shells cannot be converted to APC ammunition");
            p.setPos(f.x+30,81,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("autocannon_round"),7));
            h.assertTrue(!VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND).consumesAction()&&p.getMainHandItem().getCount()==7&&v.inventory().ammunition("autocannon_round")==0,"remote intent cannot transfer or manufacture ammunition");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6",timeoutTicks=100)
    public static void allTenActiveRecipesLoadAndProduceTheirRegisteredItems(GameTestHelper h){
        for(String id:new String[]{"military_truck_chassis","military_apc_chassis","military_tank_chassis","blackwatch_truck_chassis","blackwatch_apc_chassis","blackwatch_tank_chassis","autocannon_round","tank_he_shell","tank_ap_shell","vehicle_repair_parts"}){
            var recipe=h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight",id));
            h.assertTrue(recipe.isPresent(),"actual recipe manager loaded "+id);
            var result=recipe.orElseThrow().value().getResultItem(h.getLevel().registryAccess());
            h.assertTrue(result.getItem()==VehicleRegistry.item(id)&&result.getCount()==(id.equals("autocannon_round")?8:1),"actual registered result and finite yield match "+id);
        }h.succeed();
    }
}
