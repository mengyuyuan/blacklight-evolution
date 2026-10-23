package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.state.OrganResearch;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueAssimilationGameTest {
    private static ServerPlayer player(GameTestHelper h){
        var profile=new GameProfile(UUID.randomUUID(),"OrganResearch");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,ClientInformation.createDefault()){
            @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
        };
        p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        p.setPos(h.absolutePos(new BlockPos(5,28,5)).getCenter().add(0,-.5,0));
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)h.getLevel().setBlock(p.blockPosition().offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        h.getLevel().addNewPlayer(p);var state=p.getData(ModAttachments.PLAYER_STATE);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.setOnGround(true);p.setYRot(0);p.setXRot(0);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.MUSCLE_MASS);state.activate(PowerForm.MUSCLE_MASS);
        return p;
    }
    private static void cleanup(ServerPlayer p){ConsumeSystem.cancel(p);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    private static BlacklightInfectedHost host(ServerPlayer p,long age,TissueCore core){
        var host=ModEntities.INFECTED_HOST.get().create(p.serverLevel());host.inherit(EntityType.CHICKEN.create(p.serverLevel()));
        long seed=1;while(TissueCore.forSeed(seed)!=core)seed++;
        host.setUUID(new UUID(0,seed));host.setNoAi(true);host.advanceTissue(age);
        host.setPos(p.position().add(0,0,1.2));p.serverLevel().addFreshEntity(host);return host;
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void matureTargetsHaveTheSameWeakeningGateForHumanAndSlime(GameTestHelper h){
        var p=player(h);var host=host(p,72000,TissueCore.GRASP);
        try{
            host.setPos(p.position());p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.CRAWLING,h.getLevel().getGameTime(),true,"","claws",-1,0));p.refreshDimensions();
            h.assertTrue(!ConsumeSystem.healthReady(host)&&!BiomassSystem.edible(p,host),"full-health matured tissue cannot be bypassed in slime form");
            host.setHealth(host.getMaxHealth()*.14F);
            h.assertTrue(ConsumeSystem.healthReady(host)&&BiomassSystem.edible(p,host),"both paths accept the same weakened host");
        }finally{host.discard();cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void highQualityCoreNeedsRealDamageAndSurvivesHostReload(GameTestHelper h){
        var p=player(h);var host=host(p,240000,TissueCore.CARAPACE);
        try{
            host.setHealth(host.getMaxHealth()*.05F);
            h.assertTrue(!ConsumeSystem.healthReady(host),"setting low health alone does not break protective tissue");
            host.setHealth(host.getMaxHealth());host.invulnerableTime=0;
            host.hurt(host.damageSources().genericKill(),host.getMaxHealth()*.6F/CombatScale.units(host));
            h.assertTrue(host.isAlive()&&host.tissueCoreFracture()>=1,"actual accepted damage breaks the organ's protective layer");
            host.setHealth(host.getMaxHealth()*.08F);
            h.assertTrue(ConsumeSystem.healthReady(host),"broken protection and low health permit execution");
            var tag=new CompoundTag();host.saveWithoutId(tag);var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.tissueCoreFracture()==1&&TissueCore.forSeed(TissueOrgans.seed(restored.getUUID()))==TissueCore.CARAPACE,"fracture and inherited strongest organ persist");restored.discard();
        }finally{host.discard();cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void settlementOnlyPaysOnceAndBetterSpecimensAdvanceResearch(GameTestHelper h){
        var p=player(h);var first=host(p,72000,TissueCore.GRASP);
        try{
            var reward=AssimilationSystem.quote(p,first);int ep=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(!AssimilationSystem.settle(p,first,reward,true),"a live, unfinished victim pays nothing");
            first.discard();h.assertTrue(AssimilationSystem.settle(p,first,reward,true),"successful completion pays");
            int paid=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(paid==ep+reward.ep()+40&&p.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.GRASP)==1,"the first mature specimen grants its organ and one discovery reward");
            h.assertTrue(!AssimilationSystem.settle(p,first,reward,true)&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==paid,"same victim cannot pay twice");
            var repeat=host(p,72000,TissueCore.GRASP);var repeatYield=AssimilationSystem.quote(p,repeat);repeat.discard();AssimilationSystem.settle(p,repeat,repeatYield,true);
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==paid+repeatYield.ep(),"repeated specimen gives ordinary resources without a discovery bonus");
            var better=host(p,816000,TissueCore.GRASP);var betterYield=AssimilationSystem.quote(p,better);better.discard();int before=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();AssimilationSystem.settle(p,better,betterYield,true);
            h.assertTrue(p.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.GRASP)==3&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==before+betterYield.ep()+200,"higher specimen grants only the two unseen ranks");
            var saved=new CompoundTag();p.saveWithoutId(saved);var restored=new ServerPlayer(p.server,h.getLevel(),new GameProfile(UUID.randomUUID(),"OrganReload"),ClientInformation.createDefault());restored.connection=p.connection;restored.load(saved);
            h.assertTrue(restored.getData(ModAttachments.ORGAN_RESEARCH).equals(p.getData(ModAttachments.ORGAN_RESEARCH)),"research survives player save and load");
            var respawn=new ServerPlayer(p.server,h.getLevel(),new GameProfile(UUID.randomUUID(),"OrganRespawn"),ClientInformation.createDefault());respawn.connection=p.connection;respawn.restoreFrom(p,false);
            h.assertTrue(respawn.getData(ModAttachments.ORGAN_RESEARCH).equals(p.getData(ModAttachments.ORGAN_RESEARCH)),"death-respawn clone preserves learned research");
        }finally{first.discard();cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void purchaseRequiresTheSpecimenAndOnlyBuysOneRankPerPayment(GameTestHelper h){
        var p=player(h);
        try{
            var state=p.getData(ModAttachments.PLAYER_STATE);state.addEvolutionPoints(5000);int ep=state.getEvolutionPoints();
            UpgradeSystem.purchase(p,TissueCore.CREST.id);h.assertTrue(state.getEvolutionPoints()==ep,"forged purchase intent without a specimen cannot spend EP");
            p.setData(ModAttachments.ORGAN_RESEARCH,OrganResearch.EMPTY.discover(TissueCore.CREST,2));
            UpgradeSystem.purchase(p,TissueCore.CREST.id);UpgradeSystem.purchase(p,TissueCore.CREST.id);UpgradeSystem.purchase(p,TissueCore.CREST.id);
            h.assertTrue(state.getEvolutionPoints()==ep-600&&p.getData(ModAttachments.ORGAN_RESEARCH).rank(TissueCore.CREST)==2,"two ranks cost 200+400 and the unresearched third rank is denied");
            h.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)-.2)<.00001,"purchased crest grants its real attribute");
            state.setPrototypeMode(false);AssimilationSystem.updateCrest(p);h.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)==0,"ordinary mode disables the combat effect");
        }finally{cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void humanConsumeFinishesWithThePrimaryOrganAndNoDuplicateRewards(GameTestHelper h){
        var p=player(h);var host=host(p,72000,TissueCore.JAW);
        try{
            host.setHealth(host.getMaxHealth()*.1F);var quoted=AssimilationSystem.quote(p,host);int before=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            ConsumeSystem.request(p);
            h.assertTrue(p.getData(ModAttachments.CONSUMING),"actual human capture starts on the weakened evolved receiver");
            for(int i=0;i<80;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}
            ConsumeSystem.consume(p);
            for(int i=0;i<220;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}
            h.assertTrue(host.isRemoved()&&p.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.JAW)==1,"the complete paired consumption yields its dominant organ");
            int paid=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            h.assertTrue(paid==before+quoted.ep()+40,"human path settles exactly its quoted reward plus discovery");
            ConsumeSystem.consume(p);ConsumeSystem.tick(new PlayerTickEvent.Post(p));h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==paid,"confirmation spam cannot repeat the reward");
        }finally{host.discard();cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void slimeAbsorptionActuallyExecutesAndSharesPrimaryOrganRewards(GameTestHelper h){
        var p=player(h);var host=host(p,72000,TissueCore.MUSCLE);
        try{
            host.setHealth(host.getMaxHealth()*.1F);host.setPos(p.position());var reward=AssimilationSystem.quote(p,host);int before=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();
            p.setData(ModAttachments.BIOMASS_FORM,new BiomassState(BiomassState.ABSORBING,h.getLevel().getGameTime()-BiomassState.ABSORB_TICKS,true,"","claws",host.getId(),10));p.refreshDimensions();
            BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(!host.isAlive()&&p.getData(ModAttachments.ORGAN_RESEARCH).sample(TissueCore.MUSCLE)==1,"a completed real slime execution discovers its primary organ");
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==before+reward.ep()+40&&p.getHealth()==p.getMaxHealth(),"slime shares loot and retains its existing body restoration");
            int paid=p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints();BiomassSystem.tick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==paid,"reformation ticks cannot repeat settlement");
        }finally{host.discard();cleanup(p);}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_assimilation")
    public static void boughtOrgansChangeActualMeleeDefenseRewardsAndGrabDistance(GameTestHelper h){
        var p=player(h);var level=h.getLevel();
        // A real ServerPlayer starts with sixty ticks of spawn protection.
        // Advance it normally instead of bypassing its damage path.
        for(int i=0;i<61;i++)p.tick();p.setOnGround(true);
        var a=EntityType.COW.create(level);var b=EntityType.COW.create(level);var grabbed=EntityType.COW.create(level);
        for(var cow:java.util.List.of(a,b,grabbed)){cow.setNoAi(true);cow.setPos(p.position().add(8,0,0));level.addFreshEntity(cow);}
        try{
            float before=a.getHealth();a.hurt(a.damageSources().playerAttack(p),2);float baseline=before-a.getHealth();
            p.setHealth(p.getMaxHealth());p.invulnerableTime=0;float playerBefore=p.getHealth();p.hurt(p.damageSources().generic(),2);float incoming=playerBefore-p.getHealth();
            var quote=AssimilationSystem.quote(p,b);grabbed.setPos(p.position().add(0,0,4.2));p.setOnGround(true);
            ConsumeSystem.request(p);h.assertTrue(!p.getData(ModAttachments.CONSUMING),"the baseline grab cannot reach the distant creature");
            var research=OrganResearch.EMPTY;
            for(var core:TissueCore.values()){research=research.discover(core,3).buy(core).buy(core).buy(core);}
            p.setData(ModAttachments.ORGAN_RESEARCH,research);
            float otherBefore=b.getHealth();b.hurt(b.damageSources().playerAttack(p),2);float enhanced=otherBefore-b.getHealth();
            h.assertTrue(baseline>0&&Math.abs(enhanced-baseline*1.15F)<.001,"muscle rank III raises actual direct melee damage by 15 percent");
            p.setHealth(p.getMaxHealth());p.invulnerableTime=0;playerBefore=p.getHealth();p.hurt(p.damageSources().generic(),2);
            h.assertTrue(incoming>0&&Math.abs((playerBefore-p.getHealth())-incoming*.85F)<.001,"carapace rank III reduces actual ordinary damage by 15 percent; baseline="+incoming+", enhanced="+(playerBefore-p.getHealth())+", health="+playerBefore+", mode="+p.gameMode.getGameModeForPlayer());
            var improved=AssimilationSystem.quote(p,b);
            h.assertTrue(Math.abs(improved.healing()-quote.healing()*1.3F)<.001&&improved.biomass()==Math.round(quote.biomass()*1.15F)&&improved.ep()==quote.ep(),"jaw changes healing and biomass without multiplying EP");
            p.setOnGround(true);ConsumeSystem.request(p);h.assertTrue(p.getData(ModAttachments.CONSUMING),"grasp rank III reaches the same creature beyond the baseline range");
        }finally{for(var cow:java.util.List.of(a,b,grabbed))cow.discard();cleanup(p);}h.succeed();
    }
}
