package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleFuelDeactivationGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6fueldeactivation",timeoutTicks=140)
    public static void actualNewChassisDrivesAtZeroFuelWithoutIssuingAmmunition(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,296000);var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setPos(f.x+.5,81,-5);player.setYRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("military_apc_chassis")));
        var ground=new BlockPos(f.x,80,0);var context=new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(ground),Direction.UP,ground,false));
        h.assertTrue(player.getMainHandItem().getItem().useOn(context).consumesAction()&&player.getMainHandItem().isEmpty(),"fixture places and debits one actual chassis item");
        VehicleTestReadiness.run(h,f,()->{
            var vehicles=f.level.getEntitiesOfClass(MilitaryVehicle.class,new AABB(f.x-10,80,-10,f.x+10,87,10));
            h.assertTrue(vehicles.size()==1,"fixture must expose exactly one real placed chassis");var v=vehicles.get(0);f.entities.add(v);
            h.assertTrue(v.fuelTicks()==0&&v.inventory().snapshot().ammunition().values().stream().allMatch(count->count==0),"new chassis has zero saved fuel and empty ammunition");
            player.setPos(v.position());h.assertTrue(player.startRiding(v),"actual player boards the new driver seat");var lease=v.controlLease(player.getUUID());
            h.assertTrue(lease!=null&&v.acceptControl(player,lease,0,1,0,0,0,0),"actual driver submits authenticated throttle");double start=v.getZ();
            for(int i=0;i<12;i++)v.tick();
            h.assertTrue(v.getZ()>start+1,"fuel deactivation must let an actual new zero-fuel chassis drive through normal driver ticks");
            h.assertTrue(v.fuelTicks()==0&&v.inventory().snapshot().ammunition().values().stream().allMatch(count->count==0),"driving neither invents fuel nor grants ammunition");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6fueldeactivation",timeoutTicks=100)
    public static void actualFuelRegistrySurvivesWithoutCreativeRecipeOrLootAcquisition(GameTestHelper h) throws java.io.IOException {
        var fuel=VehicleRegistry.item("military_fuel_can");var id=new net.minecraft.resources.ResourceLocation("blacklight","military_fuel_can");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(fuel).equals(id),"legacy fuel registry ID still resolves to its actual item");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isEmpty(),"fuel deactivation must exclude the preserved fuel recipe from the actual recipe manager");
        var tab=net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.get(new net.minecraft.resources.ResourceLocation("blacklight","military_vehicles"));
        h.assertTrue(tab!=null,"actual vehicle creative tab remains registered");tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess()));
        h.assertTrue(tab.getDisplayItems().stream().noneMatch(stack->stack.is(fuel))&&tab.getSearchTabDisplayItems().stream().noneMatch(stack->stack.is(fuel)),"fuel deactivation must hide the retained item from creative display and search");
        for(String active:new String[]{"military_truck_chassis","military_apc_chassis","military_tank_chassis","blackwatch_truck_chassis","blackwatch_apc_chassis","blackwatch_tank_chassis","autocannon_round","tank_he_shell","tank_ap_shell","vehicle_repair_parts"}){
            h.assertTrue(tab.getDisplayItems().stream().anyMatch(stack->stack.is(VehicleRegistry.item(active))),"creative still exposes active vehicle item "+active);
            if(active.endsWith("_chassis"))assertTooltip(h,active,"tooltip.blacklight.vehicle.place","tooltip.blacklight.vehicle.supply");
            else assertTooltip(h,active,"tooltip.blacklight."+active);
        }
        assertTooltip(h,"military_fuel_can","tooltip.blacklight.military_fuel_can");
        for(var entry:h.getLevel().getServer().getResourceManager().listResources("loot_table",location->location.getNamespace().equals("blacklight")&&location.getPath().endsWith(".json")).entrySet())
            try(var reader=entry.getValue().openAsReader()){h.assertTrue(reader.lines().noneMatch(line->line.contains("blacklight:military_fuel_can")),"active loot resource cannot issue preserved fuel: "+entry.getKey());}
        h.succeed();
    }
    private static void assertTooltip(GameTestHelper h,String id,String... keys){
        var stack=new ItemStack(VehicleRegistry.item(id));var tooltip=stack.getTooltipLines(null,net.minecraft.world.item.TooltipFlag.NORMAL);
        for(String key:keys)h.assertTrue(tooltip.stream().anyMatch(line->line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&text.getKey().equals(key)),"actual item tooltip must expose its translated help: "+id+" / "+key);
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6fueldeactivation",timeoutTicks=100)
    public static void actualLegacyFuelNbtKeepsItsExactValueAcrossDrivingAndSaving(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,297000)){
            var original=f.truck();original.inventory().addFuel(137);var saved=original.saveWithoutId(new CompoundTag());original.discard();
            h.assertTrue(saved.getCompound("VehicleInventory").getLong("FuelTicks")==137,"fixture serializes the real legacy fuel value");
            var v=ModEntities.MILITARY_TRUCK.get().create(f.level);v.load(saved);f.level.addFreshEntity(v);f.entities.add(v);double start=v.getZ();
            for(int i=0;i<12;i++)v.moveVehicle(.28,0);
            h.assertTrue(v.getZ()>start+1,"restored legacy chassis really moves on supported terrain");
            h.assertTrue(v.fuelTicks()==137&&v.saveWithoutId(new CompoundTag()).getCompound("VehicleInventory").getLong("FuelTicks")==137,"fuel deactivation must preserve the exact legacy FuelTicks during movement and NBT save");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6fueldeactivation",timeoutTicks=100)
    public static void actualLegacyFuelCansStayInHandWhileAmmunitionAndRepairStillTransfer(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,298000)){
            var v=ModEntities.MILITARY_APC.get().create(f.level);v.setPos(f.x+.5,81,.5);f.level.addFreshEntity(v);f.entities.add(v);v.inventory().addFuel(137);
            var player=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(player);player.setPos(f.x-2,81,0);var cans=new ItemStack(VehicleRegistry.item("military_fuel_can"),3);player.setItemInHand(InteractionHand.MAIN_HAND,cans);
            h.assertTrue(!VehicleRegistry.supply(player,v,InteractionHand.MAIN_HAND).consumesAction(),"fuel deactivation must reject an actual old fuel-can supply action");
            h.assertTrue(player.getMainHandItem()==cans&&cans.getCount()==3&&v.fuelTicks()==137,"disabled legacy cans and saved fuel remain exact actual stock");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("autocannon_round"),7));
            h.assertTrue(VehicleRegistry.supply(player,v,InteractionHand.MAIN_HAND).consumesAction()&&player.getMainHandItem().isEmpty()&&v.inventory().ammunition("autocannon_round")==7,"matching ammunition still debits the real stack");
            h.assertTrue(v.hurt(f.level.damageSources().generic(),30),"fixture damages the real hull");float before=v.health();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VehicleRegistry.item("vehicle_repair_parts"),2));
            h.assertTrue(VehicleRegistry.supply(player,v,InteractionHand.MAIN_HAND).consumesAction()&&player.getMainHandItem().getCount()==1&&v.health()==before+20,"repair still spends one real part for twenty HP");
            h.assertTrue(v.fuelTicks()==137,"ammunition and repair leave dormant legacy fuel unchanged");
        }h.succeed();
    }
}
