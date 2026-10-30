package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryLauncherGameTest {
    private static float healthFraction(LivingEntity entity){return entity.getHealth()/entity.getMaxHealth();}
    private static MilitarySoldier soldier(MilitaryVehicleGameTest.Fixture f){
        var s=ModEntities.MILITARY_SOLDIER.get().create(f.level);s.setAutomaticUnitJoin(false);s.setPersistenceRequired();s.setPos(f.x+.5,81,.5);f.level.addFreshEntity(s);f.entities.add(s);return s;
    }
    private static LivingEntity target(MilitaryVehicleGameTest.Fixture f,int distance){
        var target=ModEntities.INFECTED_HOST.get().create(f.level);target.setNoAi(true);target.setPersistenceRequired();target.setPos(f.x+.5,81,distance);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);f.level.addFreshEntity(target);f.entities.add(target);return target;
    }
    private static int rocketTotal(MilitarySoldier soldier){
        int magazine=0;for(var stack:java.util.List.of(soldier.getMainHandItem(),soldier.getOffhandItem()))if(stack.getItem()==FirearmsRegistry.weapon("rocket_launcher"))magazine+=FirearmItem.state(stack,0).magazine();
        return magazine+soldier.reserveAmmo("rocket_round");
    }
    private static void issue(GameTestHelper h,MilitaryVehicleGameTest.Fixture f,MilitarySoldier soldier,String assignment){
        if(!soldier.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR),assignment)){f.close();h.fail("real RPG carrier production loadout is not implemented");}
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=100)
    public static void actualNativeIssueStoresTwoRealFiniteGunStacksOnceAndLosingThemNeverReissues(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,261000)){
            var s=soldier(f);s.setNoAi(true);s.setRank(MilitaryRank.SERGEANT);
            h.assertTrue(s.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.ANTI_ARMOR),"qa-native"),"real explicit production loadout entry issues once");
            h.assertTrue(s.getMainHandItem().getItem()==FirearmsRegistry.weapon("military_carbine")&&s.getOffhandItem().getItem()==FirearmsRegistry.weapon("rocket_launcher"),"both weapons are real registered ItemStacks");
            h.assertTrue(s.magazineAmmo()==24&&FirearmItem.state(s.getOffhandItem(),0).magazine()==1&&s.reserveAmmo("rifle_round")==48&&s.reserveAmmo("rocket_round")==2,"actual gun components and typed reserves match the finite issue");
            h.assertTrue(s.rank()==MilitaryRank.SERGEANT&&!s.initializeNativeLoadout("other-task"),"changing a task cannot reissue or change rank");
            var primary=s.getMainHandItem();var secondary=s.getOffhandItem();long cooldown=f.level.getGameTime()+45;
            FirearmItem.store(secondary,new AmmoState(1,0,cooldown));s.setEquipped(false);
            h.assertTrue(s.getMainHandItem().isEmpty()&&s.getOffhandItem().isEmpty(),"uniform mode stows both actual firearms");
            var stowedCopy=ModEntities.MILITARY_SOLDIER.get().create(f.level);stowedCopy.load(s.saveWithoutId(new CompoundTag()));f.entities.add(stowedCopy);stowedCopy.setEquipped(true);
            h.assertTrue(stowedCopy.getOffhandItem().getItem()==FirearmsRegistry.weapon("rocket_launcher")&&FirearmItem.state(stowedCopy.getOffhandItem(),0).readyAt()==cooldown,"stowed secondary gun and its actual cooldown survive NBT");
            s.setEquipped(true);h.assertTrue(s.getMainHandItem()==primary&&s.getOffhandItem()==secondary,"uniform toggling moves the same two real gun objects");
            var saved=s.saveWithoutId(new CompoundTag());var copy=ModEntities.MILITARY_SOLDIER.get().create(f.level);copy.load(saved);f.entities.add(copy);
            h.assertTrue(copy.magazineAmmo()==24&&copy.reserveAmmo("rifle_round")==48&&copy.reserveAmmo("rocket_round")==2&&FirearmItem.state(copy.getOffhandItem(),0).magazine()==1&&FirearmItem.state(copy.getOffhandItem(),0).readyAt()==cooldown&&!copy.initializeNativeLoadout("reload"),"actual entity NBT preserves both gun stocks and one-time flag");
            s.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);s.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);s.tick();
            h.assertTrue(s.getMainHandItem().isEmpty()&&s.getOffhandItem().isEmpty()&&!s.initializeNativeLoadout("lost-guns"),"dropping actual guns never makes replacement guns or ammo");
            var lost=ModEntities.MILITARY_SOLDIER.get().create(f.level);lost.load(s.saveWithoutId(new CompoundTag()));f.entities.add(lost);lost.tick();
            h.assertTrue(lost.getMainHandItem().isEmpty()&&lost.getOffhandItem().isEmpty()&&!lost.initializeNativeLoadout("reload-lost"),"lost actual guns stay lost across the real NBT boundary");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=100)
    public static void actualOldThirtyPlusNinetyRifleMigratesWithoutConvertingReserveToRockets(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,262000)){
            var old=soldier(f);old.setNoAi(true);old.initializeWeapon();var saved=old.saveWithoutId(new CompoundTag());saved.remove("MilitaryLoadout");saved.putInt("MilitaryAmmoReserve",90);saved.putBoolean("MilitaryWeaponInitialized",true);
            var copy=ModEntities.MILITARY_SOLDIER.get().create(f.level);copy.load(saved);f.entities.add(copy);
            h.assertTrue(copy.magazineAmmo()==30&&copy.reserveAmmo()==90,"actual old magazine and legacy rifle reserve are preserved");
            copy.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(FirearmsRegistry.weapon("rocket_launcher")));
            h.assertTrue(copy.reserveAmmo()==0&&copy.reserveAmmo("rifle_round")==90&&copy.reserveAmmo("rocket_round")==0,"changing the actual gun never reinterprets ninety rifle rounds as rockets");
            h.assertTrue(copy.receiveAmmo(FirearmsRegistry.ammo("rifle_round"),90)==0,"a rifle-ammo box cannot refill a carried launcher");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualRpgCarrierUsesItsRealCarbineAtCloseRangeWithoutSpendingRockets(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,263000);var s=soldier(f);
        issue(h,f,s,"qa-near");
        var target=target(f,4);s.setTarget(target);float health=healthFraction(target);
        h.runAfterDelay(140,()->{try(f){h.assertTrue(s.getMainHandItem().getItem()==FirearmsRegistry.weapon("military_carbine")&&healthFraction(target)<health,"actual NPC goal shoots the real carbine at the close legal target");h.assertTrue(rocketTotal(s)==3,"safe-distance refusal does not consume or refill its three actual rockets");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=300)
    public static void actualNpcLaunchesFiniteRocketThenSwitchesSameCarbineWhenTargetCloses(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,264000);var s=soldier(f);
        issue(h,f,s,"qa-far");
        var rocket=s.getOffhandItem();var carbine=s.getMainHandItem();var target=target(f,16);s.setTarget(target);float health=healthFraction(target);boolean[] completed={false};
        for(int tick=1;tick<=240;tick++){final int at=tick;h.runAtTickTime(at,()->{
            if(completed[0])return;
            if(rocketTotal(s)<3&&healthFraction(target)<health){completed[0]=true;int remaining=rocketTotal(s);target.setPos(f.x+.5,81,4);
                h.runAfterDelay(35,()->{try(f){h.assertTrue(s.getMainHandItem()==carbine&&s.getOffhandItem()==rocket,"switching moves the same actual gun objects without cloning");h.assertTrue(rocketTotal(s)==remaining,"a close target cannot trigger more rockets or refill spent stock");}h.succeed();});
            }else if(at==240){f.close();h.fail("actual NPC never launched/damaged its safe distant target; rocketTotal="+rocketTotal(s)+", targetHealth="+healthFraction(target));}
        });}
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualAlliedSoldierInTheLinePreventsLauncherAndCarbineAmmoConsumption(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,265000);var s=soldier(f);
        issue(h,f,s,"qa-ally");
        var ally=ModEntities.BLACKWATCH_SOLDIER.get().create(f.level);ally.setNoAi(true);ally.setAutomaticUnitJoin(false);ally.setPos(f.x+.5,81,8);f.level.addFreshEntity(ally);f.entities.add(ally);
        var target=target(f,16);s.setTarget(target);float health=healthFraction(target);
        h.runAfterDelay(120,()->{try(f){h.assertTrue(rocketTotal(s)==3&&FirearmItem.state(s.getMainHandItem(),0).magazine()==24,"actual allied line obstruction prevents both weapon debits");h.assertTrue(Math.abs(healthFraction(target)-health)<1e-6,"no projectile passes through the actual allied soldier");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualEmptyLauncherSwitchesToSameCarbineWithoutInventingRockets(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,266000);var seed=soldier(f);issue(h,f,seed,"qa-empty");
        FirearmItem.store(seed.getOffhandItem(),new AmmoState(0,0,0));var saved=seed.saveWithoutId(new CompoundTag());
        saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt("rocket_round",0);seed.discard();
        var s=ModEntities.MILITARY_SOLDIER.get().create(f.level);s.load(saved);f.level.addFreshEntity(s);f.entities.add(s);
        var carbine=s.getMainHandItem();var rocket=s.getOffhandItem();s.setItemSlot(EquipmentSlot.MAINHAND,rocket);s.setItemSlot(EquipmentSlot.OFFHAND,carbine);
        var target=target(f,4);s.setTarget(target);float health=healthFraction(target);
        h.runAfterDelay(140,()->{try(f){h.assertTrue(s.getMainHandItem()==carbine&&s.getOffhandItem()==rocket&&healthFraction(target)<health,"the real empty launcher yields to the same carried carbine through the actual NPC goal");h.assertTrue(rocketTotal(s)==0,"no magazine or typed reserve is created for the empty launcher");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=140)
    public static void actualReloadDebitsMatchingTypedReserveAndSupplyDebitsOnlyAcceptedRealBoxItems(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,267000);var s=soldier(f);issue(h,f,s,"qa-reload");
        FirearmItem.store(s.getMainHandItem(),new AmmoState(0,0,0));var target=target(f,4);s.setTarget(target);float health=healthFraction(target);
        var boxPos=new net.minecraft.core.BlockPos(f.x+3,81,-4);f.level.setBlockAndUpdate(boxPos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var box=(net.minecraft.world.Container)f.level.getBlockEntity(boxPos);box.setItem(0,new ItemStack(FirearmsRegistry.ammo("rifle_round"),3));
        h.runAfterDelay(10,()->{
            try{
                h.assertTrue(s.magazineAmmo()==24&&s.reserveAmmo("rifle_round")==24&&s.reserveAmmo("rocket_round")==2,"actual reload transfers exactly twenty-four rifle rounds and no rocket rounds");
                h.assertTrue(s.receiveAmmo(FirearmsRegistry.ammo("machine_gun_round"),90)==0,"a real wrong ammunition item is refused");
                var stock=box.getItem(0);int accepted=s.receiveAmmo(stock.getItem(),stock.getCount());stock.shrink(accepted);box.setChanged();
                h.assertTrue(accepted==3&&box.getItem(0).isEmpty()&&s.reserveAmmo("rifle_round")==27&&s.reserveAmmo("rocket_round")==2,"matching three-item real box is debited by precisely the accepted typed transfer");
            }catch(Throwable failure){f.close();throw failure;}
        });
        h.runAfterDelay(90,()->{try(f){h.assertTrue(healthFraction(target)<health&&s.reserveAmmo("rifle_round")==27&&s.reserveAmmo("rocket_round")==2,"the reloaded actual carbine fires after its cooldown without more free reserves");}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=220)
    public static void actualGrenadierUsesItsFinitePhysicalLauncherOnTheSafeLowArc(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,272000);var s=soldier(f);
        if(!s.initializeLoadout(MilitaryLoadoutProfile.forDuty(MilitaryLoadoutProfile.Duty.GRENADIER),"qa-grenade")){f.close();h.fail("actual grenadier issue failed");}
        var target=target(f,16);s.setTarget(target);float fraction=healthFraction(target);
        h.runAfterDelay(160,()->{try(f){
            int remaining=FirearmItem.state(s.getMainHandItem(),0).magazine()+s.reserveAmmo("grenade_round");
            h.assertTrue(s.getMainHandItem().getItem()==FirearmsRegistry.weapon("grenade_launcher")&&remaining<18,"actual NPC launches a real finite grenade");
            h.assertTrue(healthFraction(target)<fraction,"the real low arc reaches and damages the legal distant target");
            h.assertTrue(s.reserveAmmo("rocket_round")==0&&s.getOffhandItem().isEmpty(),"grenadier never invents another launcher or backup gun");
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=100)
    public static void actualCorruptTypedSnapshotCannotFallbackToOldFreeReserve(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,273000)){
            var s=soldier(f);s.setNoAi(true);issue(h,f,s,"qa-corrupt");var saved=s.saveWithoutId(new CompoundTag());
            saved.getCompound("MilitaryLoadout").putInt("Schema",99);saved.putInt("MilitaryAmmoReserve",90);
            var copy=ModEntities.MILITARY_SOLDIER.get().create(f.level);copy.load(saved);f.entities.add(copy);copy.tick();
            h.assertTrue(copy.magazineAmmo()==24&&copy.reserveAmmo("rifle_round")==0&&copy.reserveAmmo("rocket_round")==0,"malformed typed inventory preserves actual gun components but grants no fallback reserve");
            h.assertTrue(!copy.initializeNativeLoadout("corrupt-retry"),"malformed typed loadout remains initialized and cannot compensate itself");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_loadout",timeoutTicks=100)
    public static void actualNativeAssignmentIssuesTheUuidWeightedPhysicalLoadoutOnceWithoutChangingIdentity(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,274000)){
            var s=soldier(f);s.setNoAi(true);s.setRank(MilitaryRank.SERGEANT);var unit=java.util.UUID.randomUUID();s.setUnitId(unit);var home=s.home();
            String assignment="qa-native-slot";var expected=MilitaryLoadoutProfile.forNative(s.getUUID(),assignment,s.faction(),s.heavy(),s.commandRole());
            h.assertTrue(s.initializeNativeLoadout(assignment),"actual native production entry initializes the real one-time loadout");
            h.assertTrue(s.getMainHandItem().getItem()==FirearmsRegistry.weapon(expected.primary().weaponId())&&s.magazineAmmo()==expected.primary().magazine(),"real UUID and assignment select the expected registered gun and actual finite chamber");
            h.assertTrue(s.rank()==MilitaryRank.SERGEANT&&unit.equals(s.unitId())&&home.equals(s.home())&&!s.initializeNativeLoadout("different"),"weapon assignment never edits original rank, organization, home or reissues for another task");
        }h.succeed();
    }
}
