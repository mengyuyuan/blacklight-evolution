package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.state.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class OutbreakEvolutionGameTest {
    @GameTest(template="wall_test",timeoutTicks=40,batch="outbreak_natural_guards")
    public static void NewNaturalHiveGuardsWaitForReleaseAndOldSavedGuardsArePreserved(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();var original=OutbreakData.get(level.getServer());storage.set("blacklight_outbreak",new OutbreakData());
        boolean spawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);var difficulty=level.getDifficulty();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var host=ModEntities.INFECTED_HOST.get().create(level);host.inherit(EntityType.ZOMBIE.create(level));host.getPersistentData().putBoolean("BlacklightColossalHive",true);host.setPos(h.absolutePos(new BlockPos(4,30,4)).getBottomCenter());
        try{
            var fresh=new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(host,level,false);WorldOutbreakSystem.naturalGuard(fresh);h.assertTrue(fresh.isCanceled(),"new roaming guards do not appear before the first vial");
            var saved=new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(host,level,true);WorldOutbreakSystem.naturalGuard(saved);h.assertTrue(!saved.isCanceled(),"legacy saved creatures are never deleted by the new progression");
            OutbreakData.get(level.getServer()).start(level.getServer().overworld().getGameTime());
            var active=new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(host,level,false);WorldOutbreakSystem.naturalGuard(active);h.assertTrue(!active.isCanceled(),"ordinary natural guards are accepted after release");
            host.getPersistentData().putBoolean("BlacklightDevourer",true);var elite=new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(host,level,false);WorldOutbreakSystem.naturalGuard(elite);h.assertTrue(elite.isCanceled(),"late hive devourers do not bypass the time gate");
        }finally{host.discard();storage.set("blacklight_outbreak",original);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning,level.getServer());level.getServer().setDifficulty(difficulty,true);}h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(java.util.UUID.randomUUID(),"EvolutionQA");var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault());
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;p.setGameMode(GameType.SURVIVAL);
        var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);state.addEvolutionPoints(1_000_000);
        p.setData(ModAttachments.PLAYER_STATE,state);GrowthSystem.updateHealth(p);p.setHealth(p.getMaxHealth());return p;
    }
    private static void completeBasics(ServerPlayer p){
        var state=p.getData(ModAttachments.PLAYER_STATE);state.addEvolutionPoints(Integer.MAX_VALUE);
        for(var form:PowerForm.values())if(form!=PowerForm.NONE)state.buy(form);
        for(var movement:MovementUpgrade.values())state.buyMovementUpgrade(movement);
        for(var claw:ClawUpgrade.values())state.buyClawUpgrade(claw);
        for(var skill:CombatSkill.values())if(!skill.adaptivePassive())state.buyCombatSkill(skill);
        var growth=GrowthProgress.EMPTY;for(var upgrade:GrowthUpgrade.values())growth=growth.buy(upgrade);
        p.setData(ModAttachments.GROWTH,growth);state.addEvolutionPoints(-Integer.MAX_VALUE);state.addEvolutionPoints(1_000_000);
        EvolutionProgression.refresh(p);GrowthSystem.updateHealth(p);p.setHealth(p.getMaxHealth());
    }
    private static InfiniteProgress ranks(long level){var map=new java.util.HashMap<String,Long>();for(var a:EvolutionProgression.CORE_ATTRIBUTES)map.put(a.id,level);return new InfiniteProgress(map);}
    @GameTest(template="wall_test",timeoutTicks=40,batch="common_infected_resources")
    public static void CommonInfectedAreDirectlyConsumableAndRestoreScalingBattleResourcesOnlyOnce(GameTestHelper h){
        var p=player(h);completeBasics(p);var common=ModEntities.COMMON_INFECTED.get().create(h.getLevel());
        common.setPos(h.absolutePos(new BlockPos(4,30,4)).getCenter());common.setNoAi(true);common.setNoGravity(true);h.getLevel().addFreshEntity(common);
        try{
            h.assertTrue(BlacklightInfectionSystem.isInfected(common)&&CombatScale.originalMaximum(common)==12,"ordinary infected retain their low authored health");
            h.assertTrue(ConsumeSystem.healthReady(p,common),"healthy ordinary infected can be grabbed without first weakening them");
            p.setHealth(p.getMaxHealth()*.25F);float before=p.getHealth();var reward=AssimilationSystem.quote(p,common);
            int balance=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(!AssimilationSystem.settle(p,common,reward,true),"living unconsumed enemies never pay healing or EP");
            common.hurt(common.damageSources().playerAttack(p),1000);
            h.assertTrue(!common.isAlive()&&AssimilationSystem.settle(p,common,reward,true),"actual finished kill can settle one consume reward");
            h.assertTrue(p.getHealth()-before>=p.getMaxHealth()*.10F,"one blood pack remains useful after health upgrades");
            float after=p.getHealth();int ep=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(ep>balance&&!AssimilationSystem.settle(p,common,reward,true)&&p.getHealth()==after&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==ep,"repeated settlement cannot farm a corpse");
        }finally{p.discard();common.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="city_ecology_identity")
    public static void ActualCityAtlasUsesSeparateDistrictHistoriesWithoutGeneratingRemoteChunks(GameTestHelper h){
        // GameTestServer intentionally creates its test level only. Atlas identity
        // is independent of dimension loading; native QA checks registration.
        var city=h.getLevel();int loaded=city.getChunkSource().getLoadedChunksCount();
        var shenzhen=new BlockPos(670,80,820);
        var la=new BlockPos((int)Math.round((360-118.2455-114.048)*102811.19191866614),180,(int)Math.round((22.552-34.0561)*111319.49079327358));
        var first=com.codex.blacklight.city.CityEcologyData.key(com.codex.blacklight.city.CityEcology.atlasCityId(shenzhen.getX(),shenzhen.getZ()),shenzhen.getX(),shenzhen.getZ());
        var second=com.codex.blacklight.city.CityEcologyData.key(com.codex.blacklight.city.CityEcology.atlasCityId(la.getX(),la.getZ()),la.getX(),la.getZ());
        h.assertTrue(first.city().equals("shenzhen")&&second.city().equals("los_angeles"),"frozen map indices resolve both real cities");
        var data=new com.codex.blacklight.city.CityEcologyData();data.advance(first,1,24000);data.advance(second,1,20);data.clear(first,600);
        var saved=com.codex.blacklight.city.CityEcologyData.load(data.save(new CompoundTag(),city.registryAccess()),city.registryAccess());
        h.assertTrue(saved.get(first).exposure()==24000&&saved.get(second).exposure()==20&&saved.get(first).suppression()==600&&saved.get(second).suppression()==0,"city exposure and hive clearing remain independent in real NBT");
        h.assertTrue(city.getChunkSource().getLoadedChunksCount()==loaded,"metadata and saved ecology never generate distant LA or Shenzhen terrain");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="qualification_basics_real")
    public static void UnfinishedBasicsBlockDirectInfiniteAndPassivePurchasesOnServer(GameTestHelper h){
        var p=player(h);try{
            p.setData(ModAttachments.INFINITE_EVOLUTION,ranks(100));int balance=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(!EvolutionProgression.infiniteUnlocked(p)&&!EvolutionProgression.missing(p).isEmpty(),"unfinished curriculum has visible missing requirements");
            h.assertTrue(!InfiniteEvolutionSystem.purchase(p,EvolutionAttribute.VITALITY)&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==balance,"direct purchase cannot bypass curriculum or consume EP");
            h.assertTrue(InfiniteEvolutionSystem.effectiveRank(p,EvolutionAttribute.VITALITY)==0,"old draft ranks cannot grant early power before qualification");
            UpgradeSystem.purchase(p,CombatSkill.ADAPTATION.id());h.assertTrue(!p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.ADAPTATION),"ordinary purchase route also blocks final passives");
            completeBasics(p);h.assertTrue(EvolutionProgression.infiniteUnlocked(p)&&p.getData(ModAttachments.EVOLUTION_MILESTONES).has(EvolutionMilestones.INFINITE),"all finite basics award a persistent qualification without requiring final passives");
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="qualification_passives")
    public static void EveryAttributeMustQualifyAndLegacyLearningIsRetained(GameTestHelper h){
        var p=player(h);int threshold=PlayerEvolutionConfig.PASSIVE_LEVEL.get();boolean keep=PlayerEvolutionConfig.KEEP_LEGACY_PASSIVES.get();
        try{
            PlayerEvolutionConfig.KEEP_LEGACY_PASSIVES.set(false);completeBasics(p);
            var learned=p.getData(ModAttachments.PLAYER_STATE);learned.buyCombatSkill(CombatSkill.ADAPTATION);learned.buyCombatSkill(CombatSkill.TISSUE_REGENERATION);
            var levels=new java.util.HashMap<>(ranks(threshold).ranks());levels.put(EvolutionAttribute.MOBILITY.id,threshold-1L);levels.put(EvolutionAttribute.VITALITY.id,1_000_000L);p.setData(ModAttachments.INFINITE_EVOLUTION,new InfiniteProgress(levels));
            p.setData(ModAttachments.ADAPTIVE_PASSIVES,new AdaptivePassiveState(0,90,20,0));p.setHealth(10);
            h.assertTrue(!EvolutionProgression.canLearn(p,CombatSkill.ADAPTATION)&&!AdaptivePassiveSystem.active(p,AdaptivePassiveState.ADAPTATION),"one missing attribute cannot be replaced by excess levels elsewhere");
            h.assertTrue(AdaptivePassiveSystem.finalDamage(p,p.damageSources().generic(),60)==60&&AdaptivePassiveSystem.state(p).adaptation()==90,"suspended learned passive preserves stacks and does not reduce damage");
            levels.put(EvolutionAttribute.MOBILITY.id,(long)threshold);p.setData(ModAttachments.INFINITE_EVOLUTION,new InfiniteProgress(levels));EvolutionProgression.refresh(p);
            h.assertTrue(AdaptivePassiveSystem.active(p,AdaptivePassiveState.ADAPTATION)&&AdaptivePassiveSystem.active(p,AdaptivePassiveState.REGENERATION),"reaching the final attribute automatically restores already learned passives");
            h.assertTrue(AdaptivePassiveSystem.finalDamage(p,p.damageSources().generic(),60)<60,"restored passive uses its real damage path");
            PlayerEvolutionConfig.PASSIVE_LEVEL.set(threshold+1);h.assertTrue(EvolutionProgression.canLearn(p,CombatSkill.ADAPTATION),"earned qualification survives a later threshold/catalog change");
            var saved=new CompoundTag();p.saveWithoutId(saved);var copy=player(h);try{copy.load(saved);h.assertTrue(copy.getData(ModAttachments.EVOLUTION_MILESTONES).has(EvolutionMilestones.PASSIVES),"player serialization keeps earned qualifications");}finally{copy.discard();}
        }finally{PlayerEvolutionConfig.PASSIVE_LEVEL.set(threshold);PlayerEvolutionConfig.KEEP_LEGACY_PASSIVES.set(keep);p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="qualification_percent")
    public static void PercentAdaptationNeedsTheHigherPerAttributeThreshold(GameTestHelper h){
        var p=player(h);try{
            completeBasics(p);p.setData(ModAttachments.INFINITE_EVOLUTION,ranks(PlayerEvolutionConfig.PASSIVE_LEVEL.get()));EvolutionProgression.refresh(p);
            UpgradeSystem.purchase(p,CombatSkill.ADAPTATION.id());UpgradeSystem.purchase(p,CombatSkill.ADAPTATION_PERCENT.id());
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.ADAPTATION)&&!p.getData(ModAttachments.PLAYER_STATE).hasCombatSkill(CombatSkill.ADAPTATION_PERCENT),"basic qualification does not unlock the advanced formula");
            int higher=EvolutionProgression.required(CombatSkill.ADAPTATION_PERCENT);var levels=new java.util.HashMap<>(ranks(higher).ranks());levels.put(EvolutionAttribute.RECOVERY.id,higher-1L);p.setData(ModAttachments.INFINITE_EVOLUTION,new InfiniteProgress(levels));
            h.assertTrue(!EvolutionProgression.canLearn(p,CombatSkill.ADAPTATION_PERCENT),"higher threshold also checks every attribute");
            levels.put(EvolutionAttribute.RECOVERY.id,(long)higher);p.setData(ModAttachments.INFINITE_EVOLUTION,new InfiniteProgress(levels));EvolutionProgression.refresh(p);UpgradeSystem.purchase(p,CombatSkill.ADAPTATION_PERCENT.id());
            h.assertTrue(AdaptivePassiveSystem.percentBased(p),"advanced qualification activates the actual percentage-based calculation");
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="infinite_purchases")
    public static void PurchasesUseTheSameCostAfterRank99AndPreserveHealthFraction(GameTestHelper h){
        var p=player(h);completeBasics(p);try{
            float initial=p.getMaxHealth();p.setHealth(initial*.5F);int price=PlayerEvolutionConfig.COST.get();
            for(int i=0;i<130;i++)h.assertTrue(InfiniteEvolutionSystem.purchase(p,EvolutionAttribute.VITALITY),"repeatable purchase accepted at rank "+i);
            h.assertTrue(InfiniteEvolutionSystem.rank(p,EvolutionAttribute.VITALITY)==130,"no gameplay rank 99 boundary");
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==1_000_000-130*price,"same fixed cost at rank zero and 129");
            h.assertTrue(Math.abs(p.getMaxHealth()-initial-1300)<.1&&Math.abs(p.getHealth()/p.getMaxHealth()-.5)<.001,"health is actual HP and purchases preserve the injured fraction beyond 1024 HP");
            var state=p.getData(ModAttachments.PLAYER_STATE);state.addEvolutionPoints(-Integer.MAX_VALUE);
            h.assertTrue(!InfiniteEvolutionSystem.purchase(p,EvolutionAttribute.VITALITY)&&InfiniteEvolutionSystem.rank(p,EvolutionAttribute.VITALITY)==130,"insufficient EP cannot mutate ranks");
            var encoded=new CompoundTag();p.saveWithoutId(encoded);var reloaded=player(h);try{reloaded.load(encoded);h.assertTrue(InfiniteEvolutionSystem.rank(reloaded,EvolutionAttribute.VITALITY)==130,"actual player NBT preserves repeatable ranks");}finally{reloaded.discard();}
        }finally{p.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=50,batch="infinite_effects")
    public static void UpgradesChangeRealAttackSkillHealingAndMovement(GameTestHelper h){
        var p=player(h);completeBasics(p);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);cow.setHealth(10000);
        cow.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);cow.setPos(h.absolutePos(new BlockPos(4,30,4)).getCenter());h.getLevel().addFreshEntity(cow);
        try{
            for(var a:EvolutionAttribute.values())h.assertTrue(InfiniteEvolutionSystem.purchase(p,a),"every attribute has a real purchase path");
            float before=cow.getHealth();cow.hurt(p.damageSources().playerAttack(p),10);
            h.assertTrue(Math.abs(before-cow.getHealth()-10.2)<.02,"ordinary attack applies strength exactly once");
            cow.invulnerableTime=0;before=cow.getHealth();DevastatorDamage.hurt(p,cow,2000);
            h.assertTrue(Math.abs(before-cow.getHealth()-2080)<.02,"native true damage receives real skill power");
            h.assertTrue(Math.abs(InfiniteEvolutionSystem.damage(p,p.damageSources().generic(),100)-100/1.025)<.01,"resilience has its own actual damage multiplier");
            p.setData(ModAttachments.INFINITE_EVOLUTION,p.getData(ModAttachments.INFINITE_EVOLUTION).buy(EvolutionAttribute.RECOVERY).buy(EvolutionAttribute.RECOVERY).buy(EvolutionAttribute.RECOVERY));
            p.setHealth(p.getMaxHealth()*.5F);before=p.getHealth();p.tickCount=20;InfiniteEvolutionSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(Math.abs(p.getHealth()-before-1)<.02,"four recovery ranks restore one actual HP, not five scaled HP");
            h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED)>.108,"mobility changes the real movement attribute");
        }finally{p.discard();cow.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=45,batch="external_caps")
    public static void ExternalShotsHaveSingleAndWindowCapsWhileNativeDevastatorsRemainUncapped(GameTestHelper h){
        var p=player(h);var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());hunter.setNoAi(true);hunter.setPos(h.absolutePos(new BlockPos(4,30,4)).getCenter());h.getLevel().addFreshEntity(hunter);hunter.setHealth(hunter.getMaxHealth());
        var source=new DamageSource(h.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath("blacklight_qa","external_impact"))));
        try{
            h.assertTrue(CrossModCombat.externalAttack(source),"registered foreign damage type is recognized through the production classifier");
            float max=hunter.getMaxHealth();hunter.hurt(source,100000);h.assertTrue(Math.abs(max-hunter.getHealth()-max*.06)<.1,"foreign one-shot is limited to six percent real max health");
            for(int i=0;i<5;i++){hunter.invulnerableTime=0;hunter.hurt(source,100000);}
            h.assertTrue(Math.abs(max-hunter.getHealth()-max*.15)<.1,"rapid repeat hits share the fifteen-percent window");
            float before=hunter.getHealth();DevastatorDamage.hurt(p,hunter,2000);
            h.assertTrue(Math.abs(before-hunter.getHealth()-2000)<.1,"native player Devastator ignores the external caps");
        }finally{p.discard();hunter.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="outbreak_vial")
    public static void ActualShatteredVialStartsSharedSavedWorldClockAndMatureSpawnsHaveOrgans(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();var original=OutbreakData.get(level.getServer());storage.set("blacklight_outbreak",new OutbreakData());
        boolean oldSpawning=level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        boolean oldGrief=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
        var oldDifficulty=level.getDifficulty();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,level.getServer());level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        var floor=h.absolutePos(new BlockPos(4,60,4));for(int x=-8;x<=12;x++)for(int z=-8;z<=12;z++)level.setBlock(floor.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(WorldOutbreakSystem.spawnAt(level,floor,OutbreakRules.Tier.FRESH)==null,"no new world infected before viral release");
        var vial=new BlacklightVirusVialEntity(level,floor.getX()+.5,floor.getY()+2,floor.getZ()+.5);vial.setDeltaMovement(0,-1,0);level.addFreshEntity(vial);
        h.runAfterDelay(12,()->{Mob mature=null;try{
            var data=OutbreakData.get(level.getServer());h.assertTrue(data.active()&&vial.isRemoved(),"real projectile ground collision starts the outbreak");
            long elapsed=data.elapsed(level.getServer().overworld().getGameTime());WorldOutbreakSystem.release(level,floor);
            h.assertTrue(data.elapsed(level.getServer().overworld().getGameTime())==elapsed,"second bottle does not restart progression");
            var copy=OutbreakData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());h.assertTrue(copy.active(),"saved world data retains activation");
            mature=WorldOutbreakSystem.spawnAt(level,floor.offset(5,0,0),OutbreakRules.Tier.MATURE);
            h.assertTrue(mature instanceof BlacklightInfectedHost host&&host.tissueAge()>=TissueGrowth.MATURITY_TICKS&&!host.isEvolved(),"mature birth uses real host organs without fabricating the six-organ evolved form; actual="+(mature instanceof BlacklightInfectedHost host?host.tissueAge()+" evolved="+host.isEvolved():mature));
            var occupied=mature.blockPosition();WorldOutbreakSystem.seed(level,occupied);
            h.assertTrue(level.getBlockState(occupied).isAir(),"terrain seeding never builds a core or patch inside the newborn creature");
            int loaded=level.getChunkSource().getLoadedChunksCount();
            h.assertTrue(WorldOutbreakSystem.spawnAt(level,new BlockPos(2_000_000,70,2_000_000),OutbreakRules.Tier.FRESH)==null&&level.getChunkSource().getLoadedChunksCount()==loaded,"unloaded spawn attempts never generate chunks");
            h.succeed();
        }finally{if(mature!=null)mature.discard();vial.discard();storage.set("blacklight_outbreak",original);level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(oldSpawning,level.getServer());level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(oldGrief,level.getServer());level.getServer().setDifficulty(oldDifficulty,true);}});
    }
}
