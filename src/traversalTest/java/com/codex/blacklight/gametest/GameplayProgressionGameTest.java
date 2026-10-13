package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GameplayProgressionGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void biologicalGrowthCostsOnceAndMutantsUnlockGenes(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"growth-qa"));
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.addBiomass(750);
        GrowthSystem.purchase(p,GrowthUpgrade.VITALITY);int balance=state.getBiomass();
        h.assertTrue(balance==800&&p.getMaxHealth()==150,"vitality spends biomass and increases max health");
        GrowthSystem.purchase(p,GrowthUpgrade.VITALITY);h.assertTrue(state.getBiomass()==balance&&p.getMaxHealth()==150,"repeat purchase cannot spend or stack");
        var types=java.util.List.of(ModEntities.BLACKLIGHT_STALKER,ModEntities.BLACKLIGHT_SPITTER,ModEntities.BLACKLIGHT_BRUTE);
        for(var type:types){var mob=type.get().create(h.getLevel());GrowthSystem.consumed(p,mob);mob.discard();}
        h.assertTrue(state.hasUnlocked(PowerForm.BLADE)&&state.hasUnlocked(PowerForm.WHIPFIST)&&state.hasUnlocked(PowerForm.HAMMERFISTS),"three genes unlock three weapons");
        h.assertTrue(p.getData(ModAttachments.GROWTH).absorbed()==3,"absorption count is retained");
        state.cure();p.tickCount=200;GrowthSystem.tick(new PlayerTickEvent.Post(p));h.assertTrue(p.getMaxHealth()==20,"cured player loses active bonus");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void airborneHammerWaitsForLandingAndHitsOnce(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"slam-qa"));
        p.setPos(h.absoluteVec(new Vec3(4,8,4)));p.setYRot(0);p.setOnGround(false);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.HAMMERFISTS);state.activate(PowerForm.HAMMERFISTS);
        for(int x=1;x<10;x++)for(int z=1;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var mob=h.spawn(EntityType.IRON_GOLEM,4,2,6);mob.setNoAi(true);float hp=mob.getHealth();
        WeaponCombatSystem.request(p,true);
        for(int t=0;t<35;t++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(WeaponCombatSystem.busy(p)&&mob.getHealth()==hp,"long descent stays committed without early damage");
        p.setPos(h.absoluteVec(new Vec3(4,2,4)));p.setOnGround(true);
        for(int t=0;t<WeaponMove.HAMMER_DROP.duration+2;t++){p.tickCount++;WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));}
        h.assertTrue(mob.getHealth()==hp-WeaponMove.HAMMER_DROP.damage*5,"landing contacts exactly once");
        h.assertTrue(!WeaponCombatSystem.busy(p),"landing recovery ends");mob.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hiveRewardIsSingleAndWaveBudgetSurvivesReload(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"hive-qa"));var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();
        var hive=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());hive.setPos(h.absoluteVec(new Vec3(5,2,5)));h.getLevel().addFreshEntity(hive);
        var tag=new CompoundTag();hive.addAdditionalSaveData(tag);tag.putInt("BlacklightWaves",3);tag.putInt("BlacklightWaveCooldown",0);hive.readAdditionalSaveData(tag);
        var saved=new CompoundTag();hive.addAdditionalSaveData(saved);var restored=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.waves()==3,"saved exhausted hive cannot reset waves");
        hive.hurt(p.damageSources().playerAttack(p),1000);int ep=state.getEvolutionPoints(),bio=state.getBiomass();hive.die(p.damageSources().playerAttack(p));
        h.assertTrue(ep==180&&bio==500&&state.getEvolutionPoints()==ep&&state.getBiomass()==bio,"one clear produces exactly one reward");hive.discard();restored.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void survivalRecipeAndGuidanceAreLoaded(GameTestHelper h){
        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","blacklight_sample");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(id).isEmpty(),"RC18 removes the obsolete virus sample crafting recipe");
        for(String name:new String[]{"root","awakening","first_consume","first_evolution","hive_breaker"})h.assertTrue(h.getLevel().getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight",name))!=null,"growth guide loads "+name);
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=60)
    public static void utilityPowersCoexistAndResetWithNormalMode(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"utility-qa"));p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(PowerForm.BLADE);s.activate(PowerForm.BLADE);
        h.assertTrue(!PowerFormSystem.select(p,PowerForm.ARMOR,false),"locked defence cannot be selected");
        s.unlock(PowerForm.ARMOR);s.unlock(PowerForm.SHIELD);s.unlock(PowerForm.INFECTED_VISION);
        h.assertTrue(PowerFormSystem.select(p,PowerForm.ARMOR,false),"unlocked armour equips");p.tickCount+=20;
        h.assertTrue(s.getActivePower()==PowerForm.BLADE&&UtilityPowerSystem.active(p,PowerForm.ARMOR)&&p.getArmorValue()==0,"armour preserves weapon without adding legacy vanilla armour");
        PowerFormSystem.select(p,PowerForm.INFECTED_VISION,false);p.tickCount+=20;
        h.assertTrue(UtilityPowerSystem.active(p,PowerForm.ARMOR)&&UtilityPowerSystem.active(p,PowerForm.INFECTED_VISION),"vision and armour coexist");
        PowerFormSystem.select(p,PowerForm.SHIELD,false);
        h.assertTrue(!UtilityPowerSystem.active(p,PowerForm.ARMOR)&&UtilityPowerSystem.active(p,PowerForm.SHIELD)&&p.getArmorValue()==0,"shield replaces armour and removes its attributes");
        s.setPrototypeMode(false);p.tickCount=200;UtilityPowerSystem.tick(new PlayerTickEvent.Post(p));h.assertTrue(p.getData(ModAttachments.UTILITY_POWERS)==0,"normal mode clears utility powers");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void hiveGeneratesBuildingAndStopsAfterThreeWaves(GameTestHelper h){
        var level=h.getLevel();var origin=new net.minecraft.core.BlockPos(1500,220,1500);
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-1;y<15;y++)level.setBlock(origin.offset(x,y,z),(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        var feature=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE).get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","hive"));
        h.assertTrue(feature!=null&&feature.place(level,level.getChunkSource().getGenerator(),level.random,origin),"configured hive places a building");
        h.assertTrue(level.getBlockState(origin.offset(5,5,5)).is(Blocks.SMOOTH_STONE)||level.getBlockState(origin.offset(5,5,5)).is(Blocks.CRACKED_STONE_BRICKS),"upper floor is real world geometry");
        var box=new net.minecraft.world.phys.AABB(origin).inflate(20);var cores=level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightHive.class,box);h.assertTrue(cores.size()==1,"one core owns the encounter");
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"hive-waves"));p.moveTo(1500,220,1490,0,0);level.addNewPlayer(p);
        var hive=cores.getFirst();
        // A blocked random spawn point legitimately delays a wave by another cooldown.
        // Allow those retries; the required wave count and reinforcement budget stay strict.
        for(int t=0;t<2400&&hive.waves()<3;t++){hive.tickCount++;hive.tick();}
        h.assertTrue(hive.waves()==3,"three reinforcement waves finish");
        // Each wave starts with a hunter; random blocked secondary positions may
        // reduce mutants without reducing the completed wave count.
        java.util.function.Predicate<net.minecraft.world.entity.Mob> guard=mob->
                (mob instanceof com.codex.blacklight.entity.BlacklightMutant||mob instanceof com.codex.blacklight.entity.BlacklightHunter)
                &&mob.getPersistentData().hasUUID("BlacklightHiveGuard")
                &&mob.getPersistentData().getUUID("BlacklightHiveGuard").equals(hive.getUUID());
        int count=level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,box,guard).size();
        h.assertTrue(count>=3&&count<=6,"wave budget caps active hunter and mutant reinforcements: guards="+count);
        CompoundTag saved=new CompoundTag();hive.addAdditionalSaveData(saved);hive.readAdditionalSaveData(saved);
        for(int t=0;t<300;t++){hive.tickCount++;hive.tick();}
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,box,guard).size()==count,"reload does not refill wave budget");
        for(var mob:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,box))mob.discard();p.discard();h.succeed();
    }
}
