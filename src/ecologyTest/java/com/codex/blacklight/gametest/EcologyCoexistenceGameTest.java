package com.codex.blacklight.gametest;

import com.codex.blacklight.config.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EcologyCoexistenceGameTest {
    private static Mob cow(GameTestHelper h,int offset){
        var m=EntityType.COW.create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);m.setPersistenceRequired();
        m.setPos(h.absolutePos(new BlockPos(5,130,5)).getCenter().add(offset,0,0));h.getLevel().addFreshEntity(m);return m;
    }
    private static void outbreak(GameTestHelper h){
        h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,h.getLevel().getServer());
        OutbreakData.get(h.getLevel().getServer()).start(h.getLevel().getServer().overworld().getGameTime());
    }
    private static void check(GameTestHelper h,boolean value,String message){h.assertTrue(value,message);}
    private static ServerPlayer player(GameTestHelper h){
        return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"EcologyQA"));
    }
    @GameTest(template="wall_test",timeoutTicks=1100,batch="ecology_no_source")
    public static void healthyGreenAreaRemainsHealthyAcrossRealDirectorTicks(GameTestHelper h){
        outbreak(h);var p=player(h);p.setNoGravity(true);h.getLevel().addNewPlayer(p);p.setPos(h.absolutePos(new BlockPos(5,210,5)).getCenter());
        var natives=new ArrayList<Mob>();for(int i=0;i<4;i++){var mob=cow(h,i*2);mob.setPos(mob.position().add(0,80,0));natives.add(mob);}
        double base=GameplayBalanceConfig.EXPOSURE_BASE.get();int green=GameplayBalanceConfig.GREEN_CHANCE.get();
        GameplayBalanceConfig.EXPOSURE_BASE.set(1D);GameplayBalanceConfig.GREEN_CHANCE.set(100);
        h.runAfterDelay(1000,()->{try{
            check(h,natives.stream().noneMatch(BlacklightInfectionSystem::isInfected),"a global outbreak cannot spontaneously replace healthy mobs through 1000 real server ticks");
            check(h,!InfectionEcology.hasSource(h.getLevel(),natives.get(0).blockPosition()),"zone/global history is not a physical virus source");h.succeed();
        }finally{GameplayBalanceConfig.EXPOSURE_BASE.set(base);GameplayBalanceConfig.GREEN_CHANCE.set(green);natives.forEach(Entity::discard);h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="ecology_sources")
    public static void onlyRealNearbySourcesEnableNaturalExposure(GameTestHelper h){
        outbreak(h);var target=cow(h,0);var far=target.blockPosition().offset(200,0,0);InfectionEcology.released(h.getLevel(),far);
        try{check(h,!BlacklightInfectionSystem.infectNaturally(target,null),"a far-away vial does not infect this area");
            var pos=target.blockPosition().below();h.getLevel().setBlockAndUpdate(pos,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState());
            check(h,InfectionEcology.hasSource(h.getLevel(),target.blockPosition()),"real local tissue supplies virus");
            h.getLevel().removeBlock(pos,false);check(h,!InfectionEcology.hasSource(h.getLevel(),target.blockPosition()),"mining the last tissue removes its source");
            InfectionEcology.released(h.getLevel(),target.blockPosition());check(h,InfectionEcology.hasSource(h.getLevel(),target.blockPosition()),"a local explicit vial event is a temporary source even if terrain growth is denied");h.succeed();
        }finally{target.discard();}
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="ecology_incubation")
    public static void incubatingMobsAndManualOverflowStopNaturalReplenishment(GameTestHelper h){
        outbreak(h);var mobs=new ArrayList<Mob>();for(int i=0;i<8;i++)mobs.add(cow(h,i));
        h.runAfterDelay(41,()->{try{
            InfectionEcology.released(h.getLevel(),mobs.get(0).blockPosition());int accepted=0;
            for(var mob:mobs)if(BlacklightInfectionSystem.infectNaturally(mob,null))accepted++;
            check(h,accepted>0&&accepted<=5,"the regional soft budget includes infections before 48-tick conversion: "+accepted);
            var overflow=mobs.stream().filter(m->!BlacklightInfectionSystem.isInfected(m)).findFirst().orElseThrow();
            check(h,BlacklightInfectionSystem.infect(overflow,null),"manual exposure remains possible beyond the soft budget");
            check(h,!WorldOutbreakSystem.populationRoom(h.getLevel(),overflow.blockPosition()),"manual infections count against subsequent natural births");
            check(h,mobs.stream().allMatch(m->m.isAlive()&&!m.isRemoved()),"nothing is silently culled");h.succeed();
        }finally{mobs.forEach(Entity::discard);}});
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="ecology_reentrant")
    public static void effectCallbacksCannotDoubleSpendTheNaturalInfectionSlot(GameTestHelper h){
        outbreak(h);var a=cow(h,0);var b=cow(h,3);int batch=OutbreakConfig.BATCH.get();OutbreakConfig.BATCH.set(1);
        boolean[] nested={true};Consumer<NaturalInfectionEvent> listener=e->{if(e.getEntity()==a)nested[0]=BlacklightInfectionSystem.infectNaturally(b,null);};MinecraftForge.EVENT_BUS.addListener(listener);
        h.runAfterDelay(41,()->{try{
            InfectionEcology.released(h.getLevel(),a.blockPosition());check(h,BlacklightInfectionSystem.infectNaturally(a,null),"outer exposure succeeds");
            check(h,!nested[0]&&!BlacklightInfectionSystem.isInfected(b),"reentrant exposure cannot acquire the reserved slot");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);OutbreakConfig.BATCH.set(batch);a.discard();b.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="ecology_veto")
    public static void protectionVetoLeavesOriginalUntouchedAndReleasesItsSlot(GameTestHelper h){
        outbreak(h);var a=cow(h,0);var b=cow(h,3);int batch=OutbreakConfig.BATCH.get();OutbreakConfig.BATCH.set(1);
        Consumer<NaturalInfectionEvent> listener=e->{if(e.getEntity()==a)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(listener);
        h.runAfterDelay(41,()->{try{InfectionEcology.released(h.getLevel(),a.blockPosition());
            check(h,!BlacklightInfectionSystem.infectNaturally(a,null)&&!BlacklightInfectionSystem.isInfected(a),"a claim/quest adapter's explicit veto is honored before infection");
            check(h,BlacklightInfectionSystem.infectNaturally(b,null),"a veto cannot consume the only successful-exposure slot");h.succeed();
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);OutbreakConfig.BATCH.set(batch);a.discard();b.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="ecology_eligibility")
    public static void naturalProtectionIsSeparateFromManualExposure(GameTestHelper h){
        var named=cow(h,0);named.setCustomName(Component.literal("Quest NPC"));
        var wolf=EntityType.WOLF.create(h.getLevel());wolf.setOwnerUUID(UUID.randomUUID());wolf.setTame(true);wolf.setPos(named.position().add(2,0,0));h.getLevel().addFreshEntity(wolf);
        var trader=EntityType.VILLAGER.create(h.getLevel());trader.setPos(named.position().add(4,0,0));h.getLevel().addFreshEntity(trader);
        try{check(h,!InfectionEcology.naturalEligible(named)&&!InfectionEcology.naturalEligible(wolf)&&!InfectionEcology.naturalEligible(trader),"quest names, owned pets and traders are protected from automatic infection");
            check(h,BlacklightInfectionSystem.infect(named,null),"an explicitly aimed manual infection still follows manual eligibility");
            var immune=InfectionEcologyConfig.IMMUNE.get();try{InfectionEcologyConfig.IMMUNE.set(List.of("minecraft:*"));check(h,!BlacklightInfectionSystem.canInfect(wolf),"namespace immunity applies even to manual exposure");}finally{InfectionEcologyConfig.IMMUNE.set(immune);}h.succeed();
        }finally{named.discard();wolf.discard();trader.discard();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="ecology_dimension")
    public static void dimensionOverridesBlockNaturalButNotExplicitManualInfection(GameTestHelper h){
        outbreak(h);var target=cow(h,0);var old=InfectionEcologyConfig.EXCLUDED_DIMENSIONS.get();InfectionEcologyConfig.EXCLUDED_DIMENSIONS.set(List.of(h.getLevel().dimension().location().toString()));
        try{InfectionEcology.released(h.getLevel(),target.blockPosition());
            check(h,!BlacklightInfectionSystem.infectNaturally(target,null)&&!WorldOutbreakSystem.populationRoom(h.getLevel(),target.blockPosition()),"automatic routes obey the dimension override");
            check(h,BlacklightInfectionSystem.infect(target,null),"manual infection is explicit and still possible");h.succeed();
        }finally{InfectionEcologyConfig.EXCLUDED_DIMENSIONS.set(old);target.discard();}
    }
    @GameTest(template="wall_test",timeoutTicks=220,batch="ecology_native")
    public static void preservedOriginalKeepsUuidGearExternalModifiersAndSavedGrowth(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));
        var mob=cow(h,0);var uuid=mob.getUUID();mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        var external=new ResourceLocation("othermod","native_hp");CombatScale.modifier(mob.getAttribute(Attributes.MAX_HEALTH),external,.5,AttributeModifier.Operation.MULTIPLY_TOTAL);
        double base=mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue();BlacklightInfectionSystem.infect(mob,null);
        h.runAfterDelay(160,()->{try{
            check(h,h.getLevel().getEntity(uuid)==mob&&mob.getMainHandItem().is(Items.DIAMOND_SWORD),"onset finishes with exactly the same original and actual equipment");
            check(h,CombatScale.units(mob)==1&&mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue()==base&&mob.getAttribute(Attributes.MAX_HEALTH).getModifier(CombatScale.modifierId(external))!=null,"foreign attributes are not overwritten and fivefold units are not added");
            check(h,NativeInfection.age(mob)>0,"native carrier grows on real loaded ticks");
            float maximum=mob.getMaxHealth();for(int i=0;i<20;i++)NativeInfection.tick(mob,h.getLevel());check(h,Math.abs(mob.getMaxHealth()-maximum)<.01,"duplicate same-tick callbacks cannot compound bonuses or age");
            var data=new CompoundTag();mob.saveWithoutId(data);var restored=EntityType.COW.create(h.getLevel());restored.load(data);
            NativeInfection.tick(restored,h.getLevel());check(h,NativeInfection.age(restored)==NativeInfection.age(mob)&&Math.abs(restored.getMaxHealth()-maximum)<.01,"actual entity save/load retains genetics and stable modifiers");restored.discard();h.succeed();
        }finally{InfectionEcologyConfig.PRESERVE.set(old);mob.discard();}});
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="ecology_cure")
    public static void curingNativeCarrierRemovesOnlyOwnedGrowthModifiers(GameTestHelper h){
        var old=InfectionEcologyConfig.PRESERVE.get();InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));var m=cow(h,0);
        var foreign=new ResourceLocation("othermod","health");CombatScale.modifier(m.getAttribute(Attributes.MAX_HEALTH),foreign,.5,AttributeModifier.Operation.MULTIPLY_TOTAL);float original=m.getMaxHealth();
        try{BlacklightInfectionSystem.infect(m,null);NativeInfection.tick(m,h.getLevel());m.setHealth(m.getMaxHealth()*.5F);m.removeEffect(ModEffects.BLACKLIGHT_INFECTION.get());CombatScale.synchronizeMob(m);
            check(h,m.getAttribute(Attributes.MAX_HEALTH).getModifier(CombatScale.modifierId(foreign))!=null&&Math.abs(m.getMaxHealth()-original)<.01&&NativeInfection.age(m)==0,"curing keeps foreign modifiers and removes owned infection growth");h.succeed();
        }finally{m.discard();InfectionEcologyConfig.PRESERVE.set(old);}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="ecology_player_damage")
    public static void playerOwnedAttacksDoNotInheritNpcSuppression(GameTestHelper h){
        var p=player(h);var target=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());target.setNoAi(true);h.getLevel().addFreshEntity(target);
        var wolf=EntityType.WOLF.create(h.getLevel());wolf.setOwnerUUID(p.getUUID());wolf.setTame(true);h.getLevel().addNewPlayer(p);h.getLevel().addFreshEntity(wolf);
        try{p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(ModItems.BLACKLIGHT_SAMPLE.get()));
            var source=p.damageSources().playerAttack(p);check(h,CrossModCombat.defend(target,source,123)==123,"legal player damage is not reduced by namespace or elite NPC protection");
            check(h,CombatOwnership.player(wolf)==p,"owned summon/pet attribution resolves to its loaded player");
            var arrow=new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW,h.getLevel());arrow.setOwner(p);check(h,CombatOwnership.player(p.damageSources().arrow(arrow,null))==p,"projectile direct-owner fallback is recognized");arrow.discard();h.succeed();
        }finally{target.discard();wolf.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="ecology_rewards")
    public static void threatRewardsIncreaseWithoutCopyingNativeProgression(GameTestHelper h){
        var p=player(h);var weak=cow(h,0);var strong=cow(h,3);strong.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);strong.setHealth(strong.getMaxHealth());
        try{var a=AssimilationSystem.quote(p,weak);var b=AssimilationSystem.quote(p,strong);check(h,b.ep()>a.ep()&&b.biomass()>a.biomass(),"a high-native-threat absorption pays more than an ordinary animal");
            check(h,CombatRewards.fraction(b.ep(),.5)==Math.round(b.ep()*.5F),"kill reward remains half of this same absorption quote");h.succeed();
        }finally{weak.discard();strong.discard();p.discard();}
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="ecology_ultimates")
    public static void formalTrueDamageDoesNotDependOnBlanketCrossModOffense(GameTestHelper h){
        var p=player(h);var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        boolean old=CrossModCombatConfig.LEGACY_ADVANTAGE.get();CrossModCombatConfig.LEGACY_ADVANTAGE.set(false);
        try{check(h,CrossModCombat.bypassTitanLimit(DevastatorDamage.source(p)),"formal true devastators preserve their explicit mechanism bridge");
            check(h,DevastatorDamage.isTrueDamage(new net.minecraft.world.damagesource.DamageSource(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(BulletdiveDamage.TYPE),p)),"Bulletdive remains a formal true damage source");h.succeed();
        }finally{CrossModCombatConfig.LEGACY_ADVANTAGE.set(old);p.discard();}
    }

    @GameTest(template="wall_test",timeoutTicks=70,batch="ecology_city_guard")
    public static void legacyCityProducerCannotCreateAnOutbreakFromAnEmptyRedZone(GameTestHelper h){
        outbreak(h);var pos=h.absolutePos(new BlockPos(5,280,5));var previous=h.getLevel().getBlockState(pos.below());h.getLevel().setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        h.runAfterDelay(41,()->{try{
            var method=com.codex.blacklight.city.CitySystem.class.getDeclaredMethod("spawn",ServerLevel.class,EntityType.class,BlockPos.class,boolean.class);method.setAccessible(true);
            check(h,!(Boolean)method.invoke(null,h.getLevel(),ModEntities.COMMON_INFECTED.get(),pos,true),"legacy city births cannot bypass the local-source rule");
            InfectionEcology.released(h.getLevel(),pos);check(h,(Boolean)method.invoke(null,h.getLevel(),ModEntities.COMMON_INFECTED.get(),pos,true),"same real city producer can publish once a local source exists");h.succeed();
        }catch(Exception ex){throw new RuntimeException(ex);}finally{h.getLevel().getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(pos).inflate(4)).forEach(Entity::discard);h.getLevel().setBlockAndUpdate(pos.below(),previous);}});
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="ecology_dimension_cap")
    public static void dimensionCapAppliesToIncubationAndEveryProducer(GameTestHelper h){
        outbreak(h);var a=cow(h,0);var b=cow(h,3);int old=InfectionEcologyConfig.WORLD_CAP.get();InfectionEcologyConfig.WORLD_CAP.set(1);
        h.runAfterDelay(41,()->{try{InfectionEcology.released(h.getLevel(),a.blockPosition());
            check(h,BlacklightInfectionSystem.infectNaturally(a,null),"first incubating original acquires the dimension slot");
            check(h,!BlacklightInfectionSystem.infectNaturally(b,null)&&!WorldOutbreakSystem.populationRoom(h.getLevel(),b.blockPosition()),"another producer cannot get a separate full allowance");h.succeed();
        }finally{InfectionEcologyConfig.WORLD_CAP.set(old);a.discard();b.discard();}});
    }

    @GameTest(template="wall_test",timeoutTicks=390,batch="ecology_live_cohort")
    public static void actualNaturalExposureAndMaturationLeaveAHealthyNativePopulation(GameTestHelper h){
        outbreak(h);var oldPreserve=InfectionEcologyConfig.PRESERVE.get();double speed=GameplayBalanceConfig.GROWTH_SPEED.get(),base=GameplayBalanceConfig.EXPOSURE_BASE.get();int green=GameplayBalanceConfig.GREEN_CHANCE.get();
        InfectionEcologyConfig.PRESERVE.set(List.of("minecraft:cow"));GameplayBalanceConfig.GROWTH_SPEED.set(1000D);GameplayBalanceConfig.EXPOSURE_BASE.set(1D);GameplayBalanceConfig.GREEN_CHANCE.set(100);
        var origin=h.absolutePos(new BlockPos(5,130,5));var mobs=new ArrayList<Mob>();
        for(int i=0;i<16;i++){var mob=cow(h,0);mob.setPos((origin.getX()&~15)+2+(i%4)*3,origin.getY(),(origin.getZ()&~15)+2+(i/4)*3);mobs.add(mob);}
        var ids=mobs.stream().map(Entity::getUUID).toList();var p=player(h);p.setNoGravity(true);h.getLevel().addNewPlayer(p);p.setPos(mobs.get(0).position().add(0,4,0));
        BlacklightInfectionSystem.infect(mobs.get(0),null);InfectionEcology.released(h.getLevel(),mobs.get(0).blockPosition());
        long start=System.nanoTime();int first=h.getLevel().getServer().getTickCount();
        h.runAfterDelay(320,()->{try{
            long infected=mobs.stream().filter(BlacklightInfectionSystem::isInfected).count();long mature=mobs.stream().filter(m->NativeInfection.age(m)>=TissueGrowth.MATURITY_TICKS).count();
            check(h,infected>1&&infected<16&&mature>0,"real natural exposure advances and matures without consuming the whole native cohort: infected="+infected+", mature="+mature);
            check(h,java.util.stream.IntStream.range(0,mobs.size()).allMatch(i->h.getLevel().getEntity(ids.get(i))==mobs.get(i)&&mobs.get(i).isAlive()),"native identities and living population survive the whole progression");
            var sample=mobs.stream().filter(m->NativeInfection.age(m)>=TissueGrowth.MATURITY_TICKS).findFirst().orElseThrow();var quote=AssimilationSystem.quote(p,sample);
            check(h,!quote.samples().isEmpty(),"grown native organ can be researched through normal assimilation quote");
            var metrics=new java.util.LinkedHashMap<String,Object>();metrics.put("serverTicks",h.getLevel().getServer().getTickCount()-first);metrics.put("wallSeconds",(System.nanoTime()-start)/1e9);metrics.put("originalPopulation",16);metrics.put("infected",infected);metrics.put("healthy",16-infected);metrics.put("mature",mature);metrics.put("nativeAge",NativeInfection.age(sample));metrics.put("growthSpeedForTest",1000);metrics.put("scope","accelerated headless regression, not a production-server benchmark");
            for(String name:new String[]{"getAverageTickTime","getAverageTickTimeNanos"})try{var method=h.getLevel().getServer().getClass().getMethod(name);metrics.put("averageTickMilliseconds",((Number)method.invoke(h.getLevel().getServer())).doubleValue()/(name.endsWith("Nanos")?1e6:1));break;}catch(ReflectiveOperationException ignored){}
            java.nio.file.Files.writeString(java.nio.file.Path.of("ecology-cohort.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(metrics));System.out.println("ECOLOGY_COHORT "+metrics);h.succeed();
        }catch(java.io.IOException ex){throw new RuntimeException(ex);}finally{mobs.forEach(Entity::discard);h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);InfectionEcologyConfig.PRESERVE.set(oldPreserve);GameplayBalanceConfig.GROWTH_SPEED.set(speed);GameplayBalanceConfig.EXPOSURE_BASE.set(base);GameplayBalanceConfig.GREEN_CHANCE.set(green);}});
    }
}
