package com.codex.blacklight.gametest;

import com.codex.blacklight.config.GameplayBalanceConfig;
import com.codex.blacklight.city.CityEcology;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.state.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

/** Every temporary server configuration change is restored in the same test call. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GameplayBalanceGameTest {
    private static InfiniteProgress ranks(int rank){var map=new HashMap<String,Long>();for(var a:EvolutionProgression.CORE_ATTRIBUTES)map.put(a.id,(long)rank);return new InfiniteProgress(map);}
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_passive_gates")
    public static void legacyLearningWaitsForFiftyAndOneHundredFifty(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"ConfigQA");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setGameMode(GameType.SURVIVAL);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.addEvolutionPoints(10000);
        for(var s:List.of(CombatSkill.ADAPTATION,CombatSkill.TISSUE_REGENERATION,CombatSkill.ADAPTATION_PERCENT,CombatSkill.TISSUE_REGENERATION_PERCENT))state.buyCombatSkill(s);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.EVOLUTION_MILESTONES,new EvolutionMilestones(7));
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.ADAPTIVE_PASSIVES,new AdaptivePassiveState(0,90,20,0));
        try{
            h.assertTrue(PlayerEvolutionConfig.PASSIVE_LEVEL.get()==50&&PlayerEvolutionConfig.PERCENT_LEVEL.get()==150,"real loaded defaults are 50/150");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ranks(49));EvolutionProgression.refresh(p);
            h.assertTrue(!AdaptivePassiveSystem.active(p,1)&&!AdaptivePassiveSystem.active(p,2),"old 20/50 flags cannot bypass the new first tier");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ranks(50));EvolutionProgression.refresh(p);
            h.assertTrue(AdaptivePassiveSystem.active(p,1)&&AdaptivePassiveSystem.active(p,2)&&!AdaptivePassiveSystem.percentBased(p)&&!AdaptivePassiveSystem.percentRegeneration(p),"50 opens only both first-tier skills");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ranks(149));EvolutionProgression.refresh(p);
            h.assertTrue(!AdaptivePassiveSystem.percentBased(p)&&!AdaptivePassiveSystem.percentRegeneration(p),"149 cannot unlock either second tier");
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ranks(150));EvolutionProgression.refresh(p);
            h.assertTrue(AdaptivePassiveSystem.percentBased(p)&&AdaptivePassiveSystem.percentRegeneration(p),"150 activates both already-learned second tiers");
            h.assertTrue(AdaptivePassiveSystem.state(p).adaptation()==90&&AdaptivePassiveSystem.state(p).regeneration()==20,"migration preserves stack progress");
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_ecology")
    public static void configuredContactProbabilityChangesRealHits(GameTestHelper h){
        double chance=GameplayBalanceConfig.CONTACT_CHANCE.get();int delay=GameplayBalanceConfig.CONTACT_COOLDOWN.get();
        var actor=ModEntities.COMMON_INFECTED.get().create(h.getLevel());var prey=EntityType.COW.create(h.getLevel());
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);
        try{
            GameplayBalanceConfig.CONTACT_CHANCE.set(0D);GameplayBalanceConfig.CONTACT_COOLDOWN.set(1);
            prey.invulnerableTime=0;prey.hurt(prey.damageSources().mobAttack(actor),1);
            h.assertTrue(!BlacklightInfectionSystem.isInfected(prey),"zero prevents real contact infection");
            prey.getPersistentData().remove(InfectionRules.NEXT_CONTACT_EXPOSURE);GameplayBalanceConfig.CONTACT_CHANCE.set(1D);
            prey.invulnerableTime=0;prey.hurt(prey.damageSources().mobAttack(actor),1);
            h.assertTrue(BlacklightInfectionSystem.isInfected(prey),"one guarantees the next accepted eligible contact hit");
        }finally{GameplayBalanceConfig.CONTACT_CHANCE.set(chance);GameplayBalanceConfig.CONTACT_COOLDOWN.set(delay);actor.discard();prey.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_ecology")
    public static void configuredWeightsAndAreaChanceReachTheWorldScheduler(GameTestHelper h){
        var values=GameplayBalanceConfig.TIER_WEIGHTS[0];int[] before=Arrays.stream(values).mapToInt(ForgeConfigSpec.IntValue::get).toArray();
        boolean floors=GameplayBalanceConfig.LINEAGE_GUARANTEES.get();int green=GameplayBalanceConfig.GREEN_CHANCE.get();
        var pos=h.absolutePos(new BlockPos(4,2,4));
        try{
            GameplayBalanceConfig.LINEAGE_GUARANTEES.set(false);for(var v:values)v.set(0);values[3].set(1);
            h.assertTrue(WorldOutbreakSystem.tierForPopulation(h.getLevel(),pos,.9999)==OutbreakRules.Tier.HUNTER,"server scheduler uses the custom weight rather than hardcoded common births");
            GameplayBalanceConfig.GREEN_CHANCE.set(0);h.assertTrue(!CityEcology.ambientAllowed(h.getLevel(),pos,0),"zero area chance blocks a candidate");
            GameplayBalanceConfig.GREEN_CHANCE.set(100);h.assertTrue(CityEcology.ambientAllowed(h.getLevel(),pos,99),"100 percent permits a candidate");
        }finally{for(int i=0;i<5;i++)values[i].set(before[i]);GameplayBalanceConfig.LINEAGE_GUARANTEES.set(floors);GameplayBalanceConfig.GREEN_CHANCE.set(green);}h.succeed();
    }
    private static com.codex.blacklight.entity.BlacklightInfectedHost host(GameTestHelper h){
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.COW.create(h.getLevel()));host.setNoAi(true);host.setNoGravity(true);return host;
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="configured_fractional_growth")
    public static void slowGrowthAccumulatesFractionsAndReloadDoesNotResetThem(GameTestHelper h){
        double rate=GameplayBalanceConfig.GROWTH_SPEED.get(),acceleration=GameplayBalanceConfig.WORLD_ACCELERATION.get();int bonus=GameplayBalanceConfig.FED_BONUS_TICKS.get();
        var original=host(h);var restored=host(h);
        try{
            GameplayBalanceConfig.WORLD_ACCELERATION.set(0D);GameplayBalanceConfig.GROWTH_SPEED.set(.01D);GameplayBalanceConfig.FED_BONUS_TICKS.set(0);
            for(int i=0;i<50;i++)original.tick();var saved=new CompoundTag();original.saveWithoutId(saved);
            h.assertTrue(original.tissueAge()==0&&saved.getLong("BlacklightTissueAge")==0,"0.01x cannot be rounded up to a whole biological tick every second");
            restored.load(saved);for(int i=0;i<60;i++)restored.tick();restored.saveWithoutId(saved);
            h.assertTrue(restored.tissueAge()==1&&saved.getLong("BlacklightTissueAge")==1,"fractional work survives save/load and eventually advances instead of stopping forever");
        }finally{GameplayBalanceConfig.GROWTH_SPEED.set(rate);GameplayBalanceConfig.WORLD_ACCELERATION.set(acceleration);GameplayBalanceConfig.FED_BONUS_TICKS.set(bonus);original.discard();restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="configured_growth")
    public static void zeroGrowthPersistsAcrossSaveAndDoubleSpeedAdvancesTheHost(GameTestHelper h){
        double rate=GameplayBalanceConfig.GROWTH_SPEED.get(),acceleration=GameplayBalanceConfig.WORLD_ACCELERATION.get();int bonus=GameplayBalanceConfig.FED_BONUS_TICKS.get();
        var paused=host(h);var fast=host(h);
        try{
            GameplayBalanceConfig.WORLD_ACCELERATION.set(0D);GameplayBalanceConfig.GROWTH_SPEED.set(0D);GameplayBalanceConfig.FED_BONUS_TICKS.set(0);
            for(int i=0;i<30;i++)paused.tick();var saved=new CompoundTag();paused.saveWithoutId(saved);
            h.assertTrue(paused.tissueAge()==0&&saved.getLong("BlacklightTissueAge")==0,"paused growth cannot sneak forward through the pending save clock");
            GameplayBalanceConfig.GROWTH_SPEED.set(2D);for(int i=0;i<20;i++)fast.tick();
            h.assertTrue(fast.tissueAge()==40,"real host advances forty natural growth ticks in twenty ticks at rate two");
        }finally{GameplayBalanceConfig.GROWTH_SPEED.set(rate);GameplayBalanceConfig.WORLD_ACCELERATION.set(acceleration);GameplayBalanceConfig.FED_BONUS_TICKS.set(bonus);paused.discard();fast.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_ecology")
    public static void buildingTimersAndNutrientCostsUseConfiguredValuesInSaves(GameTestHelper h){
        int brood=GameplayBalanceConfig.BROOD_TICKS.get(),occupation=GameplayBalanceConfig.OCCUPATION_TICKS.get();double cost=GameplayBalanceConfig.BROOD_COST.get();
        int cityBrood=com.codex.blacklight.city.CityConfig.HIVE_BROOD_INTERVAL.get(),population=com.codex.blacklight.city.CityConfig.POPULATION_INTERVAL.get();
        try{
            GameplayBalanceConfig.BROOD_TICKS.set(2400);GameplayBalanceConfig.OCCUPATION_TICKS.set(200);GameplayBalanceConfig.BROOD_COST.set(2D);
            var data=new BuildingInfectionData();var c=new BuildingInfectionData.Colony(h.absolutePos(new BlockPos(4,2,4)));c.broodTicks=1800;c.occupationTicks=150;data.colonies.add(c);
            var restored=BuildingInfectionData.load(data.save(new CompoundTag()));
            h.assertTrue(restored.colonies.get(0).broodTicks==1800&&restored.colonies.get(0).occupationTicks==150,"configured intervals survive real NBT instead of being clamped to old 600/40 defaults");
            h.assertTrue(ColonySupply.broodCost(OutbreakRules.Tier.HUNTER)==128,"configured breeding fee reaches the colony's actual payment amount");
            com.codex.blacklight.city.CityConfig.HIVE_BROOD_INTERVAL.set(1800);com.codex.blacklight.city.CityConfig.POPULATION_INTERVAL.set(90);
            var city=new com.codex.blacklight.city.CityData();city.hiveBroodTicks[0]=1500;
            var copy=com.codex.blacklight.city.CityData.load(city.save(new CompoundTag()));
            h.assertTrue(copy.hiveBroodTicks[0]==1500&&com.codex.blacklight.city.CityConfig.populationIntervalTicks()==100,"city incubation survives save and population interval rounds to the actual 20-tick cadence");
        }finally{GameplayBalanceConfig.BROOD_TICKS.set(brood);GameplayBalanceConfig.OCCUPATION_TICKS.set(occupation);GameplayBalanceConfig.BROOD_COST.set(cost);com.codex.blacklight.city.CityConfig.HIVE_BROOD_INTERVAL.set(cityBrood);com.codex.blacklight.city.CityConfig.POPULATION_INTERVAL.set(population);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_ecology")
    public static void vialRadiusAndDirectChanceChangeRealBurstTargets(GameTestHelper h) throws Exception {
        double radius=GameplayBalanceConfig.VIAL_RADIUS.get(),direct=GameplayBalanceConfig.VIAL_DIRECT.get(),center=GameplayBalanceConfig.VIAL_CENTER.get(),edge=GameplayBalanceConfig.VIAL_EDGE.get();boolean enabled=OutbreakConfig.ENABLED.get();
        var level=h.getLevel();var at=h.absolutePos(new BlockPos(4,30,4));var hit=EntityType.COW.create(level);hit.setPos(at.getCenter());
        var far=EntityType.COW.create(level);far.setPos(at.east(20).getCenter());far.setNoAi(true);far.setNoGravity(true);level.getChunkAt(far.blockPosition());level.addFreshEntity(far);
        var burst=com.codex.blacklight.entity.BlacklightVirusVialEntity.class.getDeclaredMethod("onHit",net.minecraft.world.phys.HitResult.class);burst.setAccessible(true);
        try{
            OutbreakConfig.ENABLED.set(false);GameplayBalanceConfig.VIAL_RADIUS.set(1D);GameplayBalanceConfig.VIAL_DIRECT.set(0D);GameplayBalanceConfig.VIAL_CENTER.set(0D);GameplayBalanceConfig.VIAL_EDGE.set(0D);
            var first=new com.codex.blacklight.entity.BlacklightVirusVialEntity(level,at.getX(),at.getY(),at.getZ());burst.invoke(first,new net.minecraft.world.phys.EntityHitResult(hit));
            h.assertTrue(!BlacklightInfectionSystem.isInfected(hit)&&!BlacklightInfectionSystem.isInfected(far),"zero chances and short radius infect neither target");
            GameplayBalanceConfig.VIAL_RADIUS.set(32D);GameplayBalanceConfig.VIAL_DIRECT.set(1D);GameplayBalanceConfig.VIAL_CENTER.set(1D);GameplayBalanceConfig.VIAL_EDGE.set(1D);
            var second=new com.codex.blacklight.entity.BlacklightVirusVialEntity(level,at.getX(),at.getY(),at.getZ());burst.invoke(second,new net.minecraft.world.phys.EntityHitResult(hit));
            h.assertTrue(BlacklightInfectionSystem.isInfected(hit)&&BlacklightInfectionSystem.isInfected(far),"configured burst infects direct target and a creature twenty blocks away");
        }finally{GameplayBalanceConfig.VIAL_RADIUS.set(radius);GameplayBalanceConfig.VIAL_DIRECT.set(direct);GameplayBalanceConfig.VIAL_CENTER.set(center);GameplayBalanceConfig.VIAL_EDGE.set(edge);OutbreakConfig.ENABLED.set(enabled);hit.discard();far.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_player_attributes")
    public static void configuredAttributeEffectsAgreeWithRealHealthDamageAndHealing(GameTestHelper h){
        double hp=PlayerEvolutionConfig.HEALTH_PER_LEVEL.get(),attack=PlayerEvolutionConfig.ATTACK_PER_LEVEL.get(),recovery=PlayerEvolutionConfig.RECOVERY_PER_LEVEL.get();
        var profile=new GameProfile(UUID.randomUUID(),"AttributeQA");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setGameMode(GameType.SURVIVAL);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.EVOLUTION_MILESTONES,EvolutionMilestones.EMPTY.grant(EvolutionMilestones.INFINITE));com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.INFINITE_EVOLUTION,ranks(1));
        var prey=EntityType.COW.create(h.getLevel());
        try{
            PlayerEvolutionConfig.HEALTH_PER_LEVEL.set(20D);PlayerEvolutionConfig.ATTACK_PER_LEVEL.set(.5);PlayerEvolutionConfig.RECOVERY_PER_LEVEL.set(2D);
            GrowthSystem.updateHealth(p);h.assertTrue(Math.abs(p.getMaxHealth()-120)<.01,"one vitality rank gives configured 20 actual HP after conversion");
            h.assertTrue(Math.abs(InfiniteEvolutionSystem.damage(prey,p.damageSources().playerAttack(p),30)-45)<.01&&EvolutionAttribute.STRENGTH.effect(1)==50,"configured attack strength matches skill-page effect");
            p.setHealth(100);p.tickCount=20;InfiniteEvolutionSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(Math.abs(p.getHealth()-102)<.01,"configured recovery restores two actual HP once");
        }finally{PlayerEvolutionConfig.HEALTH_PER_LEVEL.set(hp);PlayerEvolutionConfig.ATTACK_PER_LEVEL.set(attack);PlayerEvolutionConfig.RECOVERY_PER_LEVEL.set(recovery);p.discard();prey.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="configured_ecology")
    public static void exportCompleteDocumentedServerDefaults(GameTestHelper h) throws Exception {
        var directory=Path.of(System.getProperty("blacklight.configExportDir","config-export"));Files.createDirectories(directory);
        var specs=new LinkedHashMap<String,ForgeConfigSpec>();
        specs.put("blacklight-gameplay-server.toml",GameplayBalanceConfig.SPEC);specs.put("blacklight-player-evolution-server.toml",PlayerEvolutionConfig.SPEC);
        specs.put("blacklight-outbreak-server.toml",OutbreakConfig.SPEC);specs.put("blacklight-evolution-server.toml",TissueCompetitionConfig.SPEC);
        specs.put("blacklight-building-infection-server.toml",BuildingInfectionConfig.SPEC);specs.put("blacklight-city-server.toml",com.codex.blacklight.city.CityConfig.SPEC);
        specs.put("blacklight-evolved-server.toml",EvolvedBalance.SPEC);specs.put("blacklight-combat-server.toml",com.codex.blacklight.config.CrossModCombatConfig.SPEC);
        for(var entry:specs.entrySet()){
            var defaults=com.electronwill.nightconfig.core.CommentedConfig.inMemory();entry.getValue().correct(defaults);
            var target=directory.resolve(entry.getKey());new com.electronwill.nightconfig.toml.TomlWriter().write(defaults,target,com.electronwill.nightconfig.core.io.WritingMode.REPLACE);
            var parsed=new com.electronwill.nightconfig.toml.TomlParser().parse(Files.readString(target));h.assertTrue(entry.getValue().isCorrect(parsed),"export round-trips through the real TOML parser: "+entry.getKey());
        }h.succeed();
    }
}
