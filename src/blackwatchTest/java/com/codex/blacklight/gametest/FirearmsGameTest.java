package com.codex.blacklight.gametest;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class FirearmsGameTest {
    @GameTest(template="blackwatch_arena",timeoutTicks=70)
    public static void firearmsMissingOwnerCannotRemoveFriendlyProtection(GameTestHelper h){
        arena(h);var shooter=soldier(h,4,3);var ally=soldier(h,4,7);float health=ally.getHealth();
        for(int y=2;y<5;y++)h.setBlock(4,y,9,Blocks.STONE);
        FirearmsCombat.fire(shooter,WeaponCatalog.ROCKET,new Vec3(0,0,1),1);shooter.discard();
        h.runAfterDelay(20,()->{h.assertTrue(ally.getHealth()==health,"missing projectile owner must not disable friendly protection");ally.discard();h.succeed();});
    }
    private static void arena(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<7;y++)h.setBlock(x,y,z,Blocks.AIR);}
    }
    private static MilitarySoldier soldier(GameTestHelper h,int x,int z){
        var s=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());s.setPos(h.absoluteVec(new Vec3(x+.5,2,z+.5)));s.setNoAi(true);s.setPersistenceRequired();h.getLevel().addFreshEntity(s);s.initializeWeapon();return s;
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=30)
    public static void firearmsFiniteAmmoAndEquipmentRoundTrip(GameTestHelper h){
        arena(h);var s=soldier(h,4,4);var stack=s.getMainHandItem();
        h.assertTrue(stack.getItem() instanceof FirearmItem&&s.magazineAmmo()==30&&s.reserveAmmo()==90,"real initial finite rifle loadout");
        FirearmItem.store(stack,new AmmoState(2,0,123));
        s.setEquipped(false);h.assertTrue(s.getMainHandItem().isEmpty()&&s.getArmorValue()==0,"unequipped has no gun or armour");
        s.initializeWeapon();h.assertTrue(s.getMainHandItem().isEmpty(),"initialization cannot issue gun while unequipped");
        var tag=new CompoundTag();s.addAdditionalSaveData(tag);var restored=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());restored.readAdditionalSaveData(tag);
        h.assertTrue(!restored.equipped()&&restored.reserveAmmo()==90&&restored.magazineAmmo()==2,"saved equipment and finite reserve round trip");
        restored.setEquipped(true);h.assertTrue(restored.magazineAmmo()==2&&FirearmItem.state(restored.getMainHandItem(),0).readyAt()==123&&restored.getArmorValue()==2,"same gun and cooldown restored");
        restored.setEquipped(false);restored.setEquipped(true);h.assertTrue(restored.magazineAmmo()==2&&restored.reserveAmmo()==90,"repeated toggles never duplicate rounds");
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=30)
    public static void firearmsInventoryReloadAndSupplyAreFinite(GameTestHelper h){
        arena(h);var s=soldier(h,4,4);var tag=new CompoundTag();s.addAdditionalSaveData(tag);tag.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)s.getMainHandItem().getItem()).definition().ammoId(),5);s.readAdditionalSaveData(tag);
        h.assertTrue(s.reserveAmmo()==5,"finite supply fixture starts with five actual typed reserve rounds");
        FirearmItem.store(s.getMainHandItem(),new AmmoState(0,0,0));
        h.assertTrue(s.receiveAmmo(-1)==0&&s.receiveAmmo(200)==85&&s.reserveAmmo()==90&&s.magazineAmmo()==0,"finite supply respects reserve cap and never reloads gun");
        s.initializeWeapon();h.assertTrue(s.magazineAmmo()==0,"initialization cannot refill depleted magazine");
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=30)
    public static void firearmsHitscanStopsAtWallsAndAllies(GameTestHelper h){
        arena(h);var shooter=soldier(h,4,3);var ally=soldier(h,4,6);var target=EntityType.COW.create(h.getLevel());target.setPos(h.absoluteVec(new Vec3(4.5,2,10.5)));target.setNoAi(true);h.getLevel().addFreshEntity(target);
        float health=target.getHealth(),friendlyHealth=ally.getHealth();Vec3 aim=target.getBoundingBox().getCenter().subtract(shooter.getEyePosition().add(0,-.18,0)).normalize();
        FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,aim,1);
        h.assertTrue(target.getHealth()==health&&ally.getHealth()==friendlyHealth,"ally blocks ray without friendly fire");
        ally.discard();for(int y=2;y<5;y++)h.setBlock(4,y,7,Blocks.STONE);
        FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,aim,1);h.assertTrue(target.getHealth()==health,"solid wall blocks damage");
        for(int y=2;y<5;y++)h.setBlock(4,y,7,Blocks.AIR);
        FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,aim,1);h.assertTrue(target.getHealth()<health,"unobstructed ray damages real target");h.succeed();
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=30)
    public static void firearmsPlayerReloadPersistsAndDebitsInventory(GameTestHelper h){
        arena(h);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"firearms-fixture"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));
        var gun=new ItemStack(FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()));player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);player.getInventory().clearContent();player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,gun);
        player.getInventory().setItem(10,new ItemStack(FirearmsRegistry.ammo("rifle_round"),7));player.setShiftKeyDown(true);
        gun.getItem().use(h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(FirearmItem.state(gun,0).magazine()==7&&player.getInventory().getItem(10).isEmpty(),"real use transfers only inventory ammunition");
        var saved=gun.save(h.getLevel().registryAccess());var restored=ItemStack.parseOptional(h.getLevel().registryAccess(),(CompoundTag)saved);
        h.assertTrue(FirearmItem.state(restored,0).magazine()==7&&!FirearmItem.state(restored,0).canFire(h.getLevel().getGameTime()),"item save preserves magazine and reload gate");
        player.setShiftKeyDown(false);var state=FirearmItem.state(gun,0);gun.getItem().use(h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(FirearmItem.state(gun,0).equals(state),"reload cannot be bypassed with immediate use");h.succeed();
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=100)
    public static void firearmsOrdnanceHasOwnerBoundedRadiusAndTerrainSafety(GameTestHelper h){
        arena(h);var shooter=soldier(h,4,3);
        var near=EntityType.COW.create(h.getLevel());near.setPos(h.absoluteVec(new Vec3(4.5,2,7.5)));near.setNoAi(true);near.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);near.setHealth(100);h.getLevel().addFreshEntity(near);
        var far=EntityType.COW.create(h.getLevel());far.setPos(h.absoluteVec(new Vec3(12.5,2,7.5)));far.setNoAi(true);h.getLevel().addFreshEntity(far);
        h.setBlock(4,2,9,Blocks.STONE);h.setBlock(4,3,9,Blocks.STONE);
        Vec3 aim=new Vec3(0,0,1);FirearmsCombat.fire(shooter,WeaponCatalog.ROCKET,aim,1);
        var shots=h.getLevel().getEntitiesOfClass(OrdnanceProjectile.class,shooter.getBoundingBox().inflate(4));h.assertTrue(shots.size()==1&&shots.get(0).getOwner()==shooter,"server projectile records owner");
        h.runAfterDelay(15,()->{h.assertTrue(near.getHealth()<100&&far.getHealth()==far.getMaxHealth(),"explosion remains inside configured radius");h.assertTrue(h.getBlockState(new BlockPos(4,2,9)).is(Blocks.STONE),"ordnance does not bypass terrain protection");h.assertTrue(h.getLevel().getEntitiesOfClass(OrdnanceProjectile.class,shooter.getBoundingBox().inflate(32)).isEmpty(),"impact produces no recursive projectiles");h.succeed();});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=300)
    public static void firearmsNpcExhaustsFiniteStockWithoutTickRefill(GameTestHelper h){
        arena(h);var shooter=soldier(h,5,3);var tag=new CompoundTag();shooter.addAdditionalSaveData(tag);tag.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)shooter.getMainHandItem().getItem()).definition().ammoId(),5);shooter.readAdditionalSaveData(tag);
        h.assertTrue(shooter.reserveAmmo()==5,"exhaustion fixture starts with five actual typed reserve rounds");
        FirearmItem.store(shooter.getMainHandItem(),new AmmoState(1,0,0));shooter.setNoAi(false);
        var target=ModEntities.INFECTED_HOST.get().create(h.getLevel());target.inherit(EntityType.COW.create(h.getLevel()));target.setNoAi(true);target.setPersistenceRequired();target.setPos(h.absoluteVec(new Vec3(5.5,2,12.5)));target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);h.getLevel().addFreshEntity(target);float initialHealth=target.getHealth();shooter.setTarget(target);
        h.runAfterDelay(260,()->{h.assertTrue(shooter.reserveAmmo()==0&&shooter.magazineAmmo()<5,"NPC reload consumes real five-round reserve");h.assertTrue(target.getHealth()<initialHealth,"NPC actually fires registered finite weapon; health="+target.getHealth()+" initial="+initialHealth);int remaining=shooter.magazineAmmo();shooter.initializeWeapon();h.assertTrue(shooter.magazineAmmo()==remaining&&shooter.reserveAmmo()==0,"ticks and initialization never refill reserve");h.succeed();});
    }

    @GameTest(template="blackwatch_arena",timeoutTicks=30)
    public static void firearmsPrototypeInputAndCraftingRemainAvailable(GameTestHelper h){
        arena(h);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"firearms-prototype"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var state=player.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);player.setData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE,state);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id())));
        var input=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.world.InteractionHand.MAIN_HAND);
        com.codex.blacklight.mode.PrototypeSurvivalLock.onRightClickItem(input);
        h.assertTrue(!input.isCanceled(),"prototype can route firearm use through survival lock");
        for(var d:WeaponCatalog.ALL)h.assertTrue(h.getLevel().getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight",d.id())).isPresent(),"registered weapon crafting recipe exists: "+d.id());
        h.succeed();
    }

    @GameTest(template="blackwatch_arena",timeoutTicks=60)
    public static void firearmsUninfectedPlayersRemainNeutralAndCreativeProtected(GameTestHelper h){
        arena(h);var shooter=soldier(h,5,3);shooter.setNoAi(false);
        var normal=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"firearms-neutral"));normal.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);normal.setPos(h.absoluteVec(new Vec3(2.5,2,7.5)));h.getLevel().addNewPlayer(normal);
        var creative=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"firearms-creative"));creative.setGameMode(net.minecraft.world.level.GameType.CREATIVE);creative.setPos(h.absoluteVec(new Vec3(8.5,2,7.5)));h.getLevel().addNewPlayer(creative);
        float creativeHealth=creative.getHealth();FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,creative.getBoundingBox().getCenter().subtract(shooter.getEyePosition().add(0,-.18,0)).normalize(),1);
        h.assertTrue(creative.getHealth()==creativeHealth,"direct firearm ray respects creative immunity");
        h.onEachTick(()->h.assertTrue(shooter.getTarget()!=normal&&shooter.getTarget()!=creative,"ordinary uninfected and creative players never become automatic military enemies"));
        h.runAfterDelay(35,h::succeed);
    }

}
