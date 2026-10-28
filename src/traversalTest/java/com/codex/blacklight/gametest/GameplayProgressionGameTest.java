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
        var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.addBiomass(750);state.addEvolutionPoints(1000);int biomassBefore=state.getBiomass();
        GrowthSystem.purchase(p,GrowthUpgrade.VITALITY);int balance=state.getEvolutionPoints();
        h.assertTrue(balance==800&&state.getBiomass()==biomassBefore&&p.getMaxHealth()==150,"vitality spends evolution points, preserves biomass and increases max health");
        GrowthSystem.purchase(p,GrowthUpgrade.VITALITY);h.assertTrue(state.getEvolutionPoints()==balance&&state.getBiomass()==biomassBefore&&p.getMaxHealth()==150,"repeat purchase cannot spend or stack");
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
        h.assertTrue(mob.getHealth()==hp-WeaponMove.HAMMER_DROP.damage,"landing contacts exactly once");
        h.assertTrue(!WeaponCombatSystem.busy(p),"landing recovery ends");mob.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hiveRewardIsSingleAndWaveBudgetSurvivesReload(GameTestHelper h){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"hive-qa"));var state=p.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        var hive=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());hive.setPos(h.absoluteVec(new Vec3(5,2,5)));h.getLevel().addFreshEntity(hive);
        var tag=new CompoundTag();hive.addAdditionalSaveData(tag);tag.putInt("BlacklightWaves",3);tag.putInt("BlacklightWaveCooldown",0);hive.readAdditionalSaveData(tag);
        var saved=new CompoundTag();hive.addAdditionalSaveData(saved);var restored=ModEntities.BLACKLIGHT_HIVE.get().create(h.getLevel());restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.waves()==3,"saved exhausted hive cannot reset waves");
        state.setPrototypeMode(true);
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
    @GameTest(template="wall_test",timeoutTicks=4000,batch="hive_real_wave_lifecycle")
    public static void hiveGeneratesBuildingAndStopsAfterThreeWaves(GameTestHelper h){
        var level=h.getLevel();var storage=level.getServer().overworld().getDataStorage();
        var original=com.codex.blacklight.infection.OutbreakData.get(level.getServer());
        var outbreak=new com.codex.blacklight.infection.OutbreakData();outbreak.start(level.getServer().overworld().getGameTime());storage.set("blacklight_outbreak",outbreak);
        var spawnRule=level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
        boolean oldSpawning=spawnRule.get();spawnRule.set(true,level.getServer());
        var column=h.absolutePos(new net.minecraft.core.BlockPos(64,0,64));
        var origin=new net.minecraft.core.BlockPos(column.getX(),220,column.getZ());
        var box=new net.minecraft.world.phys.AABB(origin).inflate(24);
        var forced=new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        var viewer=new net.minecraft.server.level.ServerPlayer[1];
        var core=new com.codex.blacklight.entity.BlacklightHive[1];
        var births=new java.util.HashSet<UUID>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> joined=e->{
            if(e.getLevel()==level&&!e.loadedFromDisk()&&core[0]!=null&&e.getEntity().getPersistentData().hasUUID("BlacklightHiveGuard")&&e.getEntity().getPersistentData().getUUID("BlacklightHiveGuard").equals(core[0].getUUID()))births.add(e.getEntity().getUUID());
        };
        Runnable cleanup=()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(joined);
            if(core[0]!=null){
                for(var mob:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,box,m->m.getPersistentData().hasUUID("BlacklightHiveGuard")&&m.getPersistentData().getUUID("BlacklightHiveGuard").equals(core[0].getUUID())))mob.discard();
                core[0].discard();
            }
            if(viewer[0]!=null)level.removePlayerImmediately(viewer[0],net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);
            spawnRule.set(oldSpawning,level.getServer());storage.set("blacklight_outbreak",original);
        };
        h.runAfterDelay(3900,cleanup);
        try {
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var cp=new net.minecraft.world.level.ChunkPos((origin.getX()>>4)+x,(origin.getZ()>>4)+z);
                if(!level.getForcedChunks().contains(cp.toLong())){forced.add(cp);level.setChunkForced(cp.x,cp.z,true);}level.getChunk(cp.x,cp.z);
            }
            // Publication waits for genuine entity storage and ticking; no manual AI ticks.
            h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(origin)&&level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(origin).toLong()),"waiting for the real hive arena"))
                .thenExecute(()->{
                    try {
                        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-1;y<15;y++)level.setBlock(origin.offset(x,y,z),(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
                        var feature=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE).get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","hive"));
                        h.assertTrue(feature!=null&&feature.place(level,level.getChunkSource().getGenerator(),level.random,origin),"configured hive places a building");
                        h.assertTrue(level.getBlockState(origin.offset(5,5,5)).is(Blocks.SMOOTH_STONE)||level.getBlockState(origin.offset(5,5,5)).is(Blocks.CRACKED_STONE_BRICKS),"upper floor is real world geometry");
                        com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Prepared actual hive regression arena at {}",origin);
                        var cores=level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightHive.class,box);h.assertTrue(cores.size()==1,"one core owns the encounter");core[0]=cores.getFirst();
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(joined);
                        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"hive-waves"));viewer[0]=p;p.moveTo(origin.getX(),origin.getY(),origin.getZ()-10,0,0);level.addNewPlayer(p);
                        // The shared outbreak quota uses world time. Separate genuine windows
                        // must admit all three waves; ticking only one mob cannot reset it.
                        h.runAfterDelay(850,()->{
                            try {
                                var hive=core[0];com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Actual hive regression waves={}, published={}",hive.waves(),births.size());h.assertTrue(hive.waves()==3,"three reinforcement waves finish during real server ticks; waves="+hive.waves());
                                int count=births.size();
                                h.assertTrue(count>=3&&count<=6,"three finite waves retain their actual common/hunter/mutant guards: "+count);
                                CompoundTag saved=new CompoundTag();hive.addAdditionalSaveData(saved);hive.readAdditionalSaveData(saved);
                                h.runAfterDelay(260,()->{try{h.assertTrue(hive.waves()==3&&births.size()==count,"reload cannot publish fresh guards beyond the three-wave budget; before="+count+" after="+births.size());}finally{cleanup.run();}h.succeed();});
                            }catch(Throwable failure){cleanup.run();throw failure;}
                        });
                    }catch(Throwable failure){cleanup.run();throw failure;}
                });
        }catch(Throwable failure){cleanup.run();throw failure;}
    }
}
