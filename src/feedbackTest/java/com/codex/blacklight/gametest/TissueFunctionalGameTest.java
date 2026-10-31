package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.state.OrganResearch;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueFunctionalGameTest {
    @GameTest(template="wall_test",timeoutTicks=85,batch="organ_saddle_ai")
    public static void saddleSpringActuallyPreemptsBasicMeleeAndReleasesOneChargedStrike(GameTestHelper h){
        charged(h,TissueCore.SADDLE);
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="organ_club_ai")
    public static void clubActuallyWindsUpHitsOnceAndRecovers(GameTestHelper h){charged(h,TissueCore.CLUB);}
    private static void charged(GameTestHelper h,TissueCore core){
        var host=actor(h,core);var target=victim(h,6,5);host.setTarget(target);
        var hits=new java.util.concurrent.atomic.AtomicInteger();var started=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<com.codex.blacklight.combat.ForgeDamageEvent.Post> listener=e->{if(e.getEntity()==target&&e.getSource().getEntity()==host&&host.organActionCore()==core&&e.getNewDamage()>0)hits.incrementAndGet();};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        for(int tick=1;tick<=70;tick++)h.runAfterDelay(tick,()->{if(host.organActionCore()==core)started.set(true);});
        h.runAfterDelay(72,()->{
            try{h.assertTrue(started.get(),"actual AI, without manually starting a skill, selects its close-range "+core);
                h.assertTrue(hits.get()==1&&target.getHealth()<1000,"charge releases exactly one accepted organ hit; hits="+hits.get());
                h.assertTrue(host.organActionCore()==null,"organ releases control after its recovery");
            }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);host.discard();target.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=75,batch="organ_tongue")
    public static void ballisticTongueReallyCapturesAtRangePullsAndReleases(GameTestHelper h){
        var host=actor(h,TissueCore.TONGUE);var target=physicalVictim(h,9,5);double distance=host.distanceTo(target);start(h,host,target,TissueCore.TONGUE);
        h.runAfterDelay(24,()->{
            h.assertTrue(TissueGrapples.owner(target)==host,"extended tongue owns its actual distant target");
            h.assertTrue(host.distanceTo(target)<distance-.25,"the tether actually pulls a physical victim, independent of delayed damage");
        });
        h.runAfterDelay(54,()->{
            try{h.assertTrue(TissueGrapples.owner(target)==null&&host.organActionCore()==null,"tongue retracts without a permanent claim");
                h.assertTrue(host.distanceTo(target)<distance-.25&&target.getHealth()<1000,"real physics pull and closure damage both occur without pulling inside the host's large body; before="+distance+" after="+host.distanceTo(target)+" hp="+target.getHealth());
            }finally{host.discard();target.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="organ_tongue_obstruction")
    public static void tongueDropsItsClaimWhenThePathIsBlockedWithoutTeleportingTheVictim(GameTestHelper h){
        var host=actor(h,TissueCore.TONGUE);var target=physicalVictim(h,9,5);start(h,host,target,TissueCore.TONGUE);
        h.runAfterDelay(22,()->{
            h.assertTrue(TissueGrapples.owner(target)==host,"the unobstructed cast first establishes a real tether");
            for(int y=2;y<=6;y++)for(int z=3;z<=7;z++)h.setBlock(new BlockPos(7,y,z),Blocks.STONE);
        });
        h.runAfterDelay(42,()->{
            try{h.assertTrue(TissueGrapples.owner(target)==null,"blocking the tether releases ownership");
                h.assertTrue(target.getX()>=h.absolutePos(new BlockPos(8,2,5)).getX(),"the target stays on its own side of the solid wall");
                h.assertTrue(target.getHealth()==1000,"a disconnected tongue cannot apply its delayed closure damage through the wall");
            }finally{host.discard();target.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="organ_cnidocyte")
    public static void cnidocyteMakesActualContactDamageAndTemporarySlow(GameTestHelper h){
        var host=actor(h,TissueCore.CNIDOCYTE);var target=victim(h,6,5);start(h,host,target,TissueCore.CNIDOCYTE);
        h.runAfterDelay(18,()->h.assertTrue(target.getHealth()<1000&&target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"real discharge damages and briefly slows the contacted target"));
        h.runAfterDelay(60,()->{
            try{h.assertTrue(!target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&host.organActionCore()==null,"slow expires before another organ charge can begin");}
            finally{host.discard();target.discard();}h.succeed();
        });
    }
    private static BlacklightInfectedHost actor(GameTestHelper h,TissueCore core){
        for(int x=1;x<=13;x++)for(int z=1;z<12;z++){
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);for(int y=2;y<12;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.COW.create(h.getLevel()));
        var saved=new CompoundTag();host.saveWithoutId(saved);saved.getCompound("BlacklightHost").putString("NativeCore",core.key());
        saved.putInt("BlacklightTissueNutrition",100);host.readAdditionalSaveData(saved);host.advanceTissue(216000);
        host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);host.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,2,5))));
        host.setOnGround(true);h.getLevel().addFreshEntity(host);return host;
    }
    private static Mob victim(GameTestHelper h,int x,int z){
        var target=EntityType.HUSK.create(h.getLevel());target.setPersistenceRequired();target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        target.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,2,z))));h.getLevel().addFreshEntity(target);return target;
    }
    private static Mob physicalVictim(GameTestHelper h,int x,int z){
        var target=victim(h,x,z);
        // NoAI also disables vanilla's server-side travel. Preserve real travel and
        // collision, while preventing navigation from moving the test subject.
        target.setNoAi(false);target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        return target;
    }
    private static void start(GameTestHelper h,BlacklightInfectedHost host,Mob target,TissueCore expected){
        host.setTarget(target);int food=host.tissueNutrition();
        h.assertTrue(host.organAbilities().tick()&&host.organActionCore()==expected,"natural mature organ begins its own action: "+expected);
        h.assertTrue(host.tissueNutrition()==food-TissueOrganCycle.cycle(expected).cost(),"the server pays the organ's reserve cost exactly when loading starts");
    }
    private static void attack(GameTestHelper h,TissueCore core,int distance){
        var host=actor(h,core);var target=victim(h,4+distance,5);start(h,host,target,core);double originalX=host.getX();
        h.runAfterDelay(6,()->h.assertTrue(target.getHealth()==1000,"windup cannot deal contact damage"));
        h.runAfterDelay(38,()->{
            try{h.assertTrue(target.getHealth()<1000,"contact must really remove health for "+core+", health="+target.getHealth());
                if(core==TissueCore.TENDON||core==TissueCore.HORN)h.assertTrue(host.getX()>originalX+.5,"launch must change the actual server position");
                h.assertTrue(host.tissueTentacles()==0,"new grip organs preserve native walking support");
            }finally{host.discard();target.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void elasticTendonLoadsThenPounces(GameTestHelper h){attack(h,TissueCore.TENDON,4);}
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void hydraulicLimbActuallyExtendsAndHits(GameTestHelper h){attack(h,TissueCore.HYDRAULIC,3);}
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void hornChargeMovesAndDealsContactDamage(GameTestHelper h){attack(h,TissueCore.HORN,9);}
    private static void grip(GameTestHelper h,TissueCore core){
        var host=actor(h,core);var target=victim(h,6,5);start(h,host,target,core);
        h.runAfterDelay(TissueOrganCycle.cycle(core).contact()+2,()->h.assertTrue(TissueGrapples.owner(target)==host,"closed organ owns a real short tether for "+core));
        h.runAfterDelay(TissueOrganCycle.cycle(core).end()+2,()->{
            try{h.assertTrue(TissueGrapples.owner(target)==null,"retraction releases the target");
                h.assertTrue(target.getHealth()<1000,"closure/retraction must actually apply the attack once");
                h.assertTrue(host.organActionCore()==null,"the action ends rather than keeping a permanent grip");
            }finally{host.discard();target.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void chelaClampsCrushesAndReleases(GameTestHelper h){grip(h,TissueCore.CHELA);}
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void hydrostatCoilsPullsAndRetracts(GameTestHelper h){grip(h,TissueCore.HYDROSTAT);}
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void aquaticHydrostatReallyGripsWithoutNeedingGroundContact(GameTestHelper h){
        var host=actor(h,TissueCore.HYDROSTAT);var tag=new CompoundTag();host.saveWithoutId(tag);
        tag.getCompound("BlacklightHost").putString("Family","water");host.readAdditionalSaveData(tag);host.setNoGravity(true);
        for(int x=2;x<=11;x++)for(int z=2;z<=10;z++)for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        host.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4,3,5))));host.setOnGround(false);
        var target=victim(h,6,5);target.setNoGravity(true);target.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,3,5))));
        host.setTarget(target);
        h.runAfterDelay(21,()->h.assertTrue(host.isInWater()&&!host.onGround()&&TissueGrapples.owner(target)==host,"a real submerged host grips while floating, without a fake ground flag; water="+host.isInWater()+" ground="+host.onGround()+" action="+host.organActionCore()+" elapsed="+host.organActionElapsed(0)+" owner="+TissueGrapples.owner(target)+" host="+host.position()+" prey="+target.position()+" sight="+host.hasLineOfSight(target)+" strike="+host.tissueStrikeElapsed(0)));
        h.runAfterDelay(48,()->{try{h.assertTrue(target.getHealth()<1000&&TissueGrapples.owner(target)==null,"water grip damages once and releases");}finally{host.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="tissue_functional") public static void bladeCleaveIsDirectionalAndHasOneContact(GameTestHelper h){
        var host=actor(h,TissueCore.BLADE);var main=victim(h,6,5);var other=victim(h,6,6);var behind=victim(h,2,5);start(h,host,main,TissueCore.BLADE);
        h.runAfterDelay(22,()->{
            try{h.assertTrue(main.getHealth()<1000&&other.getHealth()<1000,"a true sweep contacts multiple visible targets in front");
                h.assertTrue(behind.getHealth()==1000,"a forward blade does not damage a target behind its owner");
            }finally{host.discard();main.discard();other.discard();behind.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=100,batch="tissue_functional") public static void pharyngealJawFinishesARealCaptureEarlierAndTransfersItsActualOrgan(GameTestHelper h){
        var host=actor(h,TissueCore.PHARYNX);var prey=actor(h,TissueCore.CHELA);
        prey.setPos(host.position().add(1.7,0,0));prey.setHealth(prey.getMaxHealth()*.1F);host.setTarget(prey);
        h.assertTrue(TissueCompetition.tryStart(host,prey),"a pharyngeal jaw requires an existing valid weakened capture");
        h.assertTrue(host.cannibalDuration()==72&&prey.cannibalDuration()==72,"both observers receive the shorter feeding clock");
        h.runAfterDelay(60,()->h.assertTrue(prey.isAlive()&&host.tissueInheritance().meals()==0,"the reward is not paid before the accepted death"));
        h.runAfterDelay(80,()->{
            try{h.assertTrue(!prey.isAlive()&&host.tissueInheritance().meals()==1,"actual jaw-assisted swallow completes before the legacy 100 ticks");
                h.assertTrue(host.tissueInheritance().organ(TissueCore.CHELA)!=null&&host.tissueInheritance().acquiredOrgans()==1,"the donor's real pincer is inherited once");
            }finally{TissueCompetition.release(host);host.discard();prey.discard();}h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="tissue_functional") public static void expandedResearchWireAndOldHostSaveMigrationAreLossless(GameTestHelper h){
        var research=OrganResearch.EMPTY;for(var definition:TissueOrganCatalog.ALL)research=research.discover(definition.id(),definition.number()%3+1);
        research=research.discover("future:module",3);var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{OrganResearch.STREAM_CODEC.encode(buf,research);h.assertTrue(research.equals(OrganResearch.STREAM_CODEC.decode(buf)),"73 independent stable IDs survive actual network encoding");}finally{buf.release();}
        var host=actor(h,TissueCore.HORN);var saved=new CompoundTag();host.saveWithoutId(saved);saved.getCompound("BlacklightHost").remove("NativeCore");
        host.readAdditionalSaveData(saved);h.assertTrue(host.nativeTissueCore()==TissueCore.forSeed(TissueOrgans.seed(host.getUUID())),"a legacy save uses the exact frozen five-core mapping");
        saved=new CompoundTag();host.saveWithoutId(saved);h.assertTrue(saved.getCompound("BlacklightHost").contains("NativeCore"),"migration becomes explicit and survives future catalogue additions");
        var dormant=Map.of("future:module",new TissueInheritance.Organ(150,50,1200,300,0,7,2));
        var genome=new TissueInheritance(Map.of(TissueCore.CHELA,new TissueInheritance.Organ(110,0,0,0,0,5,1)),1,110,false,null,1,1,dormant);
        var restored=TissueInheritanceCodec.read(TissueInheritanceCodec.write(genome));
        h.assertTrue(restored.dormant().equals(dormant)&&restored.organ(TissueCore.CHELA).integrated()==110,"unknown organs are preserved without replacing them with a random effect");
        saved.getCompound("BlacklightHost").putString("NativeCore","future:module");host.readAdditionalSaveData(saved);
        h.assertTrue(host.knownNativeTissueCore()==null&&java.util.Arrays.stream(TissueCore.values()).allMatch(c->host.organPower(c)==0),"an unavailable native organ cannot grant a fallback ability or sample");
        var future=new CompoundTag();host.saveWithoutId(future);h.assertTrue(future.getCompound("BlacklightHost").getString("NativeCore").equals("future:module"),"the unknown native identity remains saved");host.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="tissue_functional") public static void donorSpeciesSurviveHostSaveAndCompoundNetworkCodec(GameTestHelper h){
        var value=TissueInheritance.EMPTY;
        var donor=new TissueInheritance(Map.of(TissueCore.TENDON,new TissueInheritance.Organ(100,0,0,0,0,55,0)),0,0,false,null,0,0);
        for(String id:List.of("minecraft:zombie","minecraft:cow","other:unavailable_creature"))value=value.inherit(UUID.randomUUID(),donor,1,216000,42,504000,TissueInheritance.Rules.DEFAULT,0,TissueCore.MUSCLE,TissueCore.TENDON,id,"{Type:\""+id+"\",Humanoid:1b}");
        var encoded=TissueInheritanceCodec.write(value);var restored=TissueInheritanceCodec.read(encoded);
        h.assertTrue(restored.sources().equals(value.sources())&&restored.sources().size()==3,"species with the same weapon remain independent, including an absent mod's species");
        h.assertTrue(restored.sources().values().stream().allMatch(s->s.age()==504000&&s.authored().active()&&s.procedural().active()),"evolved donor age, native module and both actual development fields survive serialization");
        h.assertTrue(restored.sources().values().stream().allMatch(s->s.genes().get(TissueCore.TENDON).variant()==55&&s.appearance().contains("Humanoid")),"complete donor organ genes and appearance are retained, not reduced to five trait scores");
        var buf=new FriendlyByteBuf(Unpooled.buffer());
        try{buf.writeNbt(encoded);h.assertTrue(TissueInheritanceCodec.read(buf.readNbt()).sources().equals(value.sources()),"actual compound wire encoding preserves species, formation clock and variants");}finally{buf.release();}
        var legacy=encoded.copy();legacy.putInt("Schema",2);legacy.remove("BodySources");
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.SPIDER.create(h.getLevel()));host.setNoAi(true);host.advanceTissue(216000);float oldHeight=host.getBbHeight();var saved=new CompoundTag();host.saveWithoutId(saved);
        saved.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(value.digest(3600,true,1,216000,TissueInheritance.Rules.DEFAULT,TissueCore.MUSCLE)));
        host.readAdditionalSaveData(saved);h.assertTrue(host.getBbHeight()>oldHeight,"the real hittable body grows with the new upper torso; not just its visual culling box");host.discard();
        h.assertTrue(TissueInheritanceCodec.read(legacy).sources().isEmpty()&&TissueInheritanceCodec.read(legacy).acquiredOrgans()==3,"old saves keep powers without invented donor history");h.succeed();
    }
}
