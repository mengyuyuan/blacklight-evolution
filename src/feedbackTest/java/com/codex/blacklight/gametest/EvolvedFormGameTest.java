package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EvolvedFormGameTest {
    @GameTest(template="wall_test",timeoutTicks=500,batch="evolved_mapping_reload")
    public static void StoredLineageSurvivesAnActualServerDatapackReload(GameTestHelper h){
        var host=host(h);h.assertTrue(host.beginEvolvedMetamorphosis(),"registered lineage can enter its real metamorphosis");
        for(int tick=0;tick<200;tick++)host.tick();
        var choice=host.evolvedChoice().orElseThrow();var previous=com.codex.blacklight.infection.EvolvedMappingReload.current();
        var server=h.getLevel().getServer();var reload=server.reloadResources(server.getPackRepository().getSelectedIds());
        h.succeedWhen(()->{
            h.assertTrue(reload.isDone(),"waiting for the actual asynchronous datapack reload");
            try{
                reload.join();
                h.assertTrue(com.codex.blacklight.infection.EvolvedMappingReload.current()!=previous,"the real server reload applied a fresh mapping table");
                h.assertTrue(host.isEvolved()&&host.evolvedChoice().orElseThrow().equals(choice),"a data reload cannot reroll the stored form, seed, bias or score snapshot");
            }finally{host.discard();}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=130,batch="evolved_chase_edge")
    public static void visiblePartialPathCannotWalkTheEvolvedOffAnUnsupportedEdge(GameTestHelper h){
        var host=host(h);h.assertTrue(host.beginEvolvedMetamorphosis(),"edge fixture qualifies for its human body");for(int i=0;i<200;i++)host.tick();
        h.assertTrue(host.isEvolved(),"edge fixture completed metamorphosis");
        for(int x=3;x<=7;x++)for(int z=3;z<=7;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        host.setPos(h.absolutePos(new BlockPos(5,30,5)).getBottomCenter());host.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);host.setOnGround(true);
        var target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);
        target.setPos(host.position().add(20,0,0));target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        h.getLevel().addFreshEntity(target);host.setNoAi(false);host.setTarget(target);double floor=host.getY();
        h.runAfterDelay(110,()->{try{
            h.assertTrue(host.isAlive()&&host.getY()>=floor-.2,"direct visible-gap recovery retains support instead of falling off the small platform; y="+host.getY()+" floor="+floor+" action="+host.evolvedAction());
            h.assertTrue(host.distanceTo(target)>3,"unsupported air cannot be crossed by collision recovery");
        }finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=140,batch="evolved_ledge")
    public static void pressuredGroundDodgeDoesNotCarryItOffALedge(GameTestHelper h){
        var host=host(h);host.beginEvolvedMetamorphosis();for(int i=0;i<200;i++)host.tick();
        var data=new CompoundTag();host.saveWithoutId(data);data.putInt("BlacklightTissueNutrition",100);host.load(data);
        host.setPos(h.absolutePos(new BlockPos(5,30,5)).getBottomCenter());host.setOnGround(true);
        var target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setPos(host.position().add(1.2,0,0));
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);target.setHealth(100000);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(target);host.setNoAi(false);host.setTarget(target);
        double floor=host.getY();var dodged=new java.util.concurrent.atomic.AtomicBoolean();
        for(int t=1;t<=110;t++){
            final boolean pressure=t%4==0;
            h.runAfterDelay(t,()->{if(host.evolvedAction().startsWith("dodge"))dodged.set(true);if(pressure){host.invulnerableTime=0;host.hurt(host.damageSources().generic(),.1F);}});
        }
        h.runAfterDelay(115,()->{try{
            h.assertTrue(dodged.get(),"real combat pressure exercises a source dodge on the small raised platform");
            h.assertTrue(host.isAlive()&&host.getY()>=floor-.6,"ground attack root motion stops at unsupported terrain instead of falling; y="+host.getY());
        }finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="evolved_tier")
    public static void superEliteModifiersApplyOnceAndReloadKeepsInjuredHealth(GameTestHelper h){
        var host=host(h);BlacklightInfectedHost restored=null;
        try{
            float ordinary=host.getMaxHealth();double attack=host.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            host.setHealth(ordinary*.35F);host.beginEvolvedMetamorphosis();for(int i=0;i<200;i++)host.tick();
            var healthAttribute=host.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            var elite=healthAttribute.getModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","evolved_health"));
            h.assertTrue(elite!=null&&elite.amount()==5,"elite is a named sixfold total multiplier");
            healthAttribute.removeModifier(elite);double sameBody=healthAttribute.getValue();healthAttribute.addPermanentModifier(elite);
            h.assertTrue(Math.abs(host.getMaxHealth()/sameBody-6)<.001,"sixfold tier applies to the final human body, not the larger pre-metamorphosis frame");
            h.assertTrue(host.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)>attack*4.9,"attack receives its own elite tier");
            for(int i=0;i<100;i++)EvolvedBalance.apply(host);
            h.assertTrue(Math.abs(host.getHealth()/host.getMaxHealth()-.35F)<.001,"repeated updates do not refill health");
            float maximum=host.getMaxHealth();var tag=new CompoundTag();host.saveWithoutId(tag);
            restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(tag);
            for(int i=0;i<40;i++)restored.tick();
            h.assertTrue(Math.abs(restored.getMaxHealth()/maximum-1)<.01,"reload does not multiply the elite tier again");
            h.assertTrue(Math.abs(restored.getHealth()/restored.getMaxHealth()-.35F)<.001,"injury survives reload");
        }finally{host.discard();if(restored!=null)restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=170,batch="evolved_motion")
    public static void attackRootActuallyMovesTheEntityAndHitsWithoutTeleports(GameTestHelper h){
        var host=host(h);host.beginEvolvedMetamorphosis();for(int i=0;i<200;i++)host.tick();
        var data=new CompoundTag();host.saveWithoutId(data);data.putInt("BlacklightTissueNutrition",100);host.load(data);
        for(int x=-15;x<=25;x++)for(int z=-15;z<=25;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        host.setPos(h.absolutePos(new BlockPos(5,30,5)).getBottomCenter());host.setOnGround(true);
        var target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);target.setPos(host.position().add(7,0,0));
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(target);host.setNoAi(false);host.setTarget(target);
        var previous=new net.minecraft.world.phys.Vec3[]{host.position()};var action=new String[]{""};double[] during={0},largest={0};
        for(int tick=1;tick<=140;tick++)h.runAfterDelay(tick,()->{
            double movement=host.position().distanceTo(previous[0]);largest[0]=Math.max(largest[0],movement);
            if(!host.evolvedAction().isEmpty()&&host.evolvedAction().equals(action[0]))during[0]+=movement;
            previous[0]=host.position();action[0]=host.evolvedAction();
        });
        h.runAfterDelay(145,()->{try{
            h.assertTrue(during[0]>1,"source attack travel must move the collision entity, travelled="+during[0]);
            h.assertTrue(largest[0]<1.8,"travel uses bounded collision physics rather than teleporting across the arena; step="+largest[0]);
            h.assertTrue(target.getHealth()<10000,"moving attacks cause actual damage; action="+host.evolvedAction()+" gap="+host.distanceTo(target));
        }finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="evolved_grasp")
    public static void doesNotStartAnInheritedGraspOutsideItsContactReach(GameTestHelper h){
        var host=host(h);var target=EntityType.HUSK.create(h.getLevel());
        try{
            h.assertTrue(host.beginEvolvedMetamorphosis(),"sample has room to transform before testing grasp reach");for(int i=0;i<200;i++)host.tick();
            var data=new CompoundTag();host.saveWithoutId(data);data.putInt("BlacklightTissueNutrition",100);host.load(data);
            for(int x=-2;x<=12;x++)for(int z=-2;z<=12;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
            host.setPos(h.absolutePos(new BlockPos(5,30,5)).getBottomCenter());host.setOnGround(true);
            target.setNoAi(true);target.setPos(host.position().add(4.4,0,0));h.getLevel().addFreshEntity(target);
            host.setNoAi(false);host.setTarget(target);var combat=new EvolvedCombat(host);
            for(int i=0;i<13;i++)combat.tick();
            h.assertTrue(host.organActionCore()!=TissueCore.HYDROSTAT,"out-of-reach enemy cannot start a stationary grasp that will miss");
            h.assertTrue(host.evolvedAction().equals("rush")||host.evolvedAction().equals("leap")||!host.getNavigation().isDone(),"it closes the separation using a source entry, leap or navigation; action="+host.evolvedAction());
        }finally{host.discard();target.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=115,batch="evolved_combo")
    public static void sustainedLightAndHeavyHitsStillAllowARealCounterattack(GameTestHelper h){
        var host=host(h);host.beginEvolvedMetamorphosis();for(int i=0;i<200;i++)host.tick();
        for(int x=-2;x<=12;x++)for(int z=-2;z<=12;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        var target=EntityType.HUSK.create(h.getLevel());target.setNoAi(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        target.setPos(host.position().add(1.25,0,0));h.getLevel().addFreshEntity(target);
        host.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);host.setHealth(host.getMaxHealth());
        var damagingHits=new java.util.concurrent.atomic.AtomicInteger();host.setNoAi(false);host.setTarget(target);
        var accepted=new java.util.concurrent.atomic.AtomicInteger();
        for(int tick=0;tick<=76;tick+=4){final boolean heavy=tick%12==0;
            h.runAfterDelay(tick,()->{
                host.invulnerableTime=0;
                float before=host.getHealth();
                if(host.hurt(host.damageSources().generic(),2)){
                    if(host.getHealth()<before)damagingHits.incrementAndGet();
                    var profile=new com.codex.blacklight.combat.ImpactProfile(heavy?com.codex.blacklight.combat.HitReaction.HEAVY:com.codex.blacklight.combat.HitReaction.STAGGER,0,55,.1,0,false);
                    if(com.codex.blacklight.combat.ImpactControl.begin(host,profile,0))accepted.incrementAndGet();
                }
            });
        }
        h.runAfterDelay(105,()->{try{
            h.assertTrue(damagingHits.get()>0,"control resistance never prevents accepted damage, measured at contact before later growth rescaling");
            h.assertTrue(accepted.get()<=2,"twenty consecutive hits cannot repeatedly restart flinch");
            h.assertTrue(target.getHealth()<10000,"an actual source-timed counterattack lands during sustained hits");
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(host)&&!host.isNoAi(),"finite recovery restores decisions without changing NoAI");
        }finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="evolved_control")
    public static void launchedEvolvedRecoversAndConsumeOpeningCannotBeShortenedByACombo(GameTestHelper h){
        var host=host(h);host.beginEvolvedMetamorphosis();for(int i=0;i<200;i++)host.tick();host.setOnGround(false);
        var heavy=new com.codex.blacklight.combat.ImpactProfile(com.codex.blacklight.combat.HitReaction.LAUNCH,4,55,.1,.8,true);
        h.assertTrue(com.codex.blacklight.combat.ImpactControl.begin(host,heavy,0),"first heavy reaction is permitted");
        h.runAfterDelay(26,()->{
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(host),"launch and get-up together stay within the control budget");
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.begin(host,heavy,0),"another heavy cannot take away the counterattack interval");
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.consumeOpening(host,0),"consume opening remains available despite combat poise");
            var original=host.getData(com.codex.blacklight.registry.ModAttachments.HIT_REACTION);
            h.assertTrue(!com.codex.blacklight.combat.ImpactControl.begin(host,heavy,0),"combo cannot overwrite the explicit consume opening");
            h.assertTrue(host.getData(com.codex.blacklight.registry.ModAttachments.HIT_REACTION).equals(original),"the synchronized forty-tick opening is retained");
        });
        h.runAfterDelay(70,()->{try{h.assertTrue(!com.codex.blacklight.combat.ImpactControl.stunned(host),"consume opening also expires normally");}finally{host.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=280,batch="evolved_pursuit")
    public static void findsPursuesAndActuallyHitsAPlayerThenReleasesCreativeTargets(GameTestHelper h){
        var host=host(h);h.assertTrue(host.beginEvolvedMetamorphosis(),"qualified sample begins");for(int i=0;i<200;i++)host.tick();
        for(int x=-2;x<=22;x++)for(int z=-2;z<=12;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        host.setPos(h.absolutePos(new BlockPos(5,30,5)).getBottomCenter());host.setOnGround(true);
        var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EvolvedPursuit");
        var player=new net.minecraft.server.level.ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());
        player.connection=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),profile).connection;
        player.setPos(host.position().add(12,0,0));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);h.getLevel().addNewPlayer(player);
        var hits=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=event->{if(event.getEntity()==player&&event.getSource().getEntity()==host&&event.getNewDamage()>0)hits.incrementAndGet();};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);host.setNoAi(false);
        h.runAfterDelay(180,()->{
            try{
                h.assertTrue(host.distanceTo(player)<8,"real navigation reduces the twelve-block player separation");
                h.assertTrue(hits.get()>0,"real AI contact causes accepted player damage, not only a playing animation; distance="+host.distanceTo(player)+" action="+host.evolvedAction()+" elapsed="+host.evolvedActionElapsed(0)+" target="+host.getTarget()+" sight="+host.hasLineOfSight(player)+" health="+player.getHealth());
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                h.runAfterDelay(25,()->{try{h.assertTrue(host.getTarget()!=player&&host.evolvedAction().isEmpty(),"creative player stops being a combat target and no attack stays latched");h.succeed();}
                    finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);host.discard();h.getLevel().removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}});
            }catch(Throwable failure){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);host.discard();h.getLevel().removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);throw failure;}
        });
    }
    private static BlacklightInfectedHost host(GameTestHelper h){
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.ZOMBIE.create(h.getLevel()));
        var organs=new EnumMap<TissueCore,TissueInheritance.Organ>(TissueCore.class);
        for(var core:new TissueCore[]{TissueCore.MUSCLE,TissueCore.CARAPACE,TissueCore.JAW,TissueCore.GRASP,TissueCore.BLADE,TissueCore.HYDROSTAT})
            organs.put(core,new TissueInheritance.Organ(350,0,0,0,core==TissueCore.BLADE?1:0,core.ordinal()+71));
        long seed=TissueOrgans.seed(host.getUUID());var genes=new TissueInheritance(organs,6,2100,false,null,1,6).digest(1,true,seed,504000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE);
        var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",TissueCore.MUSCLE.key());tag.putLong("BlacklightTissueAge",504000);tag.putInt("BlacklightTissueNutrition",0);tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(genes));host.load(tag);
        for(int x=3;x<=7;x++)for(int z=3;z<=7;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        host.setNoAi(true);host.setPos(h.absolutePos(new BlockPos(5,30,5)).getCenter());h.getLevel().addFreshEntity(host);return host;
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="evolved_form")
    public static void sameEntityFinishesTheFullTransformationAndRetainsItsHistory(GameTestHelper h){
        var host=host(h);try{
            var id=host.getUUID();var genes=host.tissueInheritance();host.setHealth(host.getMaxHealth()*.4F);
            h.assertTrue(host.beginEvolvedMetamorphosis(),"qualified host begins actual ten-second transformation");
            for(int i=0;i<119;i++)host.tick();h.assertTrue(!host.isEvolved()&&host.isMetamorphosing(),"old body stays until the covered commit");
            for(int i=119;i<200;i++)host.tick();
            h.assertTrue(host.isEvolved()&&!host.isMetamorphosing(),"new body settles and unlocks at tick 200");
            var tag=new CompoundTag();host.saveWithoutId(tag);
            h.assertTrue(host.getUUID().equals(id)&&host.tissueInheritance().meals()==genes.meals()&&host.tissueInheritance().acquiredOrgans()==6,"transformation retains entity and earned history");
            h.assertTrue(tag.getCompound("BlacklightHost").getString("EvolvedOriginSpecies").equals("minecraft:zombie"),"origin survives the human appearance");
            h.assertTrue(Math.abs(host.getHealth()/host.getMaxHealth()-.4F)<.001,"transformation does not heal its injured body");
            h.assertTrue(host.tissueInheritance().organs().equals(genes.organs()),"the weapon and organ inventory is retained");
        }finally{host.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="evolved_form")
    public static void reloadResumesLoadedProgressAndClearsTheCombatAction(GameTestHelper h){
        var host=host(h);BlacklightInfectedHost restored=null;try{
            h.assertTrue(host.beginEvolvedMetamorphosis(),"start transformation");for(int i=0;i<65;i++)host.tick();host.setEvolvedAction("slash");
            var tag=new CompoundTag();host.saveWithoutId(tag);restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.isMetamorphosing()&&!restored.isEvolved()&&restored.evolvedProgress(0)==65,"reload preserves progress without offline completion");
            h.assertTrue(restored.evolvedAction().isEmpty(),"unloaded attack cannot replay a stale damage window");
            for(int i=65;i<200;i++)restored.tick();h.assertTrue(restored.isEvolved()&&!restored.isMetamorphosing(),"remaining loaded ticks complete the same transformation");
        }finally{host.discard();if(restored!=null)restored.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="evolved_form")
    public static void aSolidCeilingDefersTheHumanCommit(GameTestHelper h){
        var host=host(h);var ceiling=host.blockPosition().above(1);try{
            h.getLevel().setBlock(ceiling,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!host.beginEvolvedMetamorphosis()&&!host.isEvolved(),"blocked area cannot commit a body into blocks");
            h.getLevel().setBlock(ceiling,Blocks.AIR.defaultBlockState(),2);h.assertTrue(host.beginEvolvedMetamorphosis(),"free space permits retry");
        }finally{h.getLevel().setBlock(ceiling,Blocks.AIR.defaultBlockState(),2);host.discard();}h.succeed();
    }
}
