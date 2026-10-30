package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleRegistry;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleUsabilityGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6usability",timeoutTicks=100)
    public static void actualVanillaBareSummonCreatesOneLivingEmptyMechanicalChassis(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,176000);
        h.runAfterDelay(20,()->{
            var source=f.level.getServer().createCommandSourceStack().withLevel(f.level).withPosition(new net.minecraft.world.phys.Vec3(f.x+.5,81,.5)).withPermission(4);
            f.level.getServer().getCommands().performPrefixedCommand(source,"summon blacklight:military_apc ~ ~ ~ {Tags:[\"vehicle-empty-summon-qa\"]}");
            h.runAfterDelay(5,()->{try(f){
                var found=f.level.getEntitiesOfClass(MilitaryVehicle.class,new net.minecraft.world.phys.AABB(f.x-5,80,-5,f.x+5,86,5),v->v.getTags().contains("vehicle-empty-summon-qa"));
                f.entities.addAll(found);h.assertTrue(found.size()==1,"the real ordinary summon command must leave exactly one registered mechanical vehicle alive");
                var v=found.get(0);h.assertTrue(v.health()==v.maxHealth()&&v.fuelTicks()==0&&v.inventory().ammunition("autocannon_round")==0,"ordinary empty NBT uses constructor health and never adds fuel or ammunition");
            }h.succeed();});
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6usability",timeoutTicks=100)
    public static void malformedSavedVehicleCannotUseTheFreshSummonDefaults(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,177000)){
            var v=f.truck();v.inventory().addFuel(99);var tag=v.saveWithoutId(new CompoundTag());tag.remove("VehicleSchemaVersion");
            var copy=ModEntities.MILITARY_TRUCK.get().create(f.level);copy.load(tag);f.entities.add(copy);
            h.assertTrue(copy.health()==0&&copy.fuelTicks()==0,"a partial/corrupt persisted vehicle stays failed closed instead of becoming a fresh fully repaired issue");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6usability",timeoutTicks=100)
    public static void realFieldRepairConsumesActualPartsButNeverFullRemoteOrDeadVehicles(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,178000)){
            var v=f.truck();var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x-2,81,0);v.hurt(f.level.damageSources().generic(),40);
            var parts=new ItemStack(VehicleRegistry.item("vehicle_repair_parts"),3);p.setItemInHand(InteractionHand.MAIN_HAND,parts);
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND)==InteractionResult.CONSUME&&v.health()==80&&parts.getCount()==2,"one actual repair part restores exactly twenty existing durability");
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND)==InteractionResult.CONSUME&&v.health()==100&&parts.getCount()==1,"the second real part reaches the actual health cap");
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND)==InteractionResult.FAIL&&parts.getCount()==1,"full health cannot consume extra material");
            v.hurt(f.level.damageSources().generic(),7);p.setPos(f.x-30,81,0);
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND)==InteractionResult.FAIL&&v.health()==93&&parts.getCount()==1,"a remote real player cannot repair or spend another inventory");
            p.setPos(f.x-2,81,0);v.hurt(f.level.damageSources().generic(),10000);
            h.assertTrue(VehicleRegistry.supply(p,v,InteractionHand.MAIN_HAND)==InteractionResult.FAIL&&v.health()==0&&parts.getCount()==1,"actual destroyed chassis cannot be revived by a repair item");
        }h.succeed();
    }
}
