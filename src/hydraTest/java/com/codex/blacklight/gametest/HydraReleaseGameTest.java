package com.codex.blacklight.gametest;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.city.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HydraReleaseGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void hydraSizePersistsAndScalesGripAndCollision(GameTestHelper h){
        var level=h.getLevel();var e=ModEntities.BLACKLIGHT_HYDRA.get().create(level);
        h.assertTrue(e.bodySize()>=1.6F&&e.bodySize()<=2.4F,"new hydras use the enlarged size range");
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HydraScaleQA"));
        e.moveTo(h.absolutePos(new BlockPos(4,22,4)).getCenter());
        e.setBodySize(1.6F);var small=e.gripPosition(p,0).subtract(e.position()).add(0,p.getBbHeight()*.5,0);float width=e.getBbWidth();
        e.setBodySize(2.4F);var large=e.gripPosition(p,0).subtract(e.position()).add(0,p.getBbHeight()*.5,0);
        h.assertTrue(large.distanceTo(small.scale(1.5))<.0001,"server grip scales with rendered horns");
        h.assertTrue(Math.abs(e.getBbWidth()/width-1.5)<.0001,"collision scales with body");
        var saved=new CompoundTag();e.addAdditionalSaveData(saved);
        var restored=ModEntities.BLACKLIGHT_HYDRA.get().create(level);restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.bodySize()==e.bodySize(),"reloading must not reroll body size");
        e.setBodySize(Float.NaN);h.assertTrue(Float.isFinite(e.getBbWidth()),"malformed size cannot poison collision");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=300)
    public static void arrivalCarriesThrowsAndRemovesTemporaryHydra(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HydraTossQA"));
        var origin=h.absolutePos(new BlockPos(4,22,4));p.moveTo(origin.getCenter());level.addNewPlayer(p);
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)h.setBlock(x,21,z,Blocks.STONE);
        var e=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);e.beginArrival(p);h.assertTrue(e.scenePlayerId()==p.getId(),"arrival owns only its participant camera");
        h.runAfterDelay(40,()->h.assertTrue(e.action()==7&&HydraTransport.carrier(p)==e&&!p.isPassenger(),"emerge must retain passenger: action="+e.action()+" time="+e.actionTime()+" carrier="+HydraTransport.carrier(p)+" alive="+p.isAlive()+" carrier="+e.isAlive()));
        h.runAfterDelay(100,()->h.assertTrue(e.action()==8&&HydraTransport.carrier(p)==e&&!p.isPassenger(),"original toss windup retains grip"));
        h.runAfterDelay(145,()->{
            h.assertTrue(!HydraTransport.active(p)&&!p.isPassenger()&&p.getPersistentData().contains("BlacklightHydraLanding"),"toss releases once with landing protection");
            h.assertTrue(Double.isFinite(p.getX())&&Double.isFinite(p.getY())&&Double.isFinite(p.getZ()),"finite grip positions");
            h.assertTrue(e.scenePlayerId()==p.getId(),"camera keeps its shot through toss recovery after grip releases");
        });
        h.runAfterDelay(265,()->{boolean gone=e.isRemoved();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.assertTrue(gone,"temporary arrival hydra must withdraw and disappear");h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80)
    public static void interruptedArrivalReleasesAtSafeSurface(GameTestHelper h){
        var level=h.getLevel();
        // FakePlayer's network handler deliberately ignores teleports. Model its
        // server-side position here so this checks the chosen safe landing point.
        var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new GameProfile(UUID.randomUUID(),"HydraAbortQA")){
            @Override public void teleportTo(double x,double y,double z){setPos(x,y,z);}
        };
        var origin=h.absolutePos(new BlockPos(4,22,4));p.moveTo(origin.getCenter());level.addNewPlayer(p);
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)h.setBlock(x,21,z,Blocks.STONE);
        var e=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,22,4);e.beginArrival(p);h.assertTrue(e.scenePlayerId()==p.getId(),"arrival owns only its participant camera");
        h.runAfterDelay(10,()->{
            e.hurt(level.damageSources().generic(),1);
            h.assertTrue(e.scenePlayerId()==-1,"interruption releases cinematic camera immediately");
            h.assertTrue(!HydraTransport.active(p)&&!p.isPassenger()&&p.getY()>=origin.getY(),"interruption must release above ground");
        });
        h.runAfterDelay(55,()->{boolean gone=e.isRemoved();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.assertTrue(gone,"interrupted carrier also removed");h.succeed();});
    }
    // Explicitly missing endpoint: loader-specific GameTest dimension registries differ.
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void missingDestinationNeverCapturesOrTeleports(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HydraTravelQA"));
        var origin=h.absolutePos(new BlockPos(4,2,4));p.moveTo(origin.getCenter());
        var missing=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","missing_qa_endpoint"));
        h.assertTrue(level.getServer().getLevel(missing)==null,"fixture must use a genuinely missing dimension");
        var destination=new HydraDestination();
        h.assertTrue(!destination.prepare(null,origin),"unavailable dimension must not report ready");
        var entrance=ModEntities.BLACKLIGHT_HYDRA.get().create(level);entrance.moveTo(origin.getCenter());
        entrance.connect(p.getUUID(),new HydraLink(missing,origin,UUID.randomUUID()));
        h.assertTrue(!CityTravel.transferFromHydra(p,entrance,origin,1.6F)&&p.serverLevel()==level&&!p.isPassenger(),"failed entry leaves player free in source world");
        destination.release();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void tissuePlantsOnceAndSurvivesReload(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HydraPlantQA"));
        p.moveTo(h.absolutePos(new BlockPos(3,2,3)).getCenter());
        var floor=h.absolutePos(new BlockPos(4,1,4));level.setBlock(floor,Blocks.COPPER_BLOCK.defaultBlockState(),3);
        level.setBlock(floor.above(),Blocks.AIR.defaultBlockState(),3);level.setBlock(floor.above(2),Blocks.AIR.defaultBlockState(),3);
        var stack=ModItems.LIVING_HIVE_TISSUE.get().getDefaultInstance();stack.setCount(2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var use=new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(floor.getCenter().add(0,.5,0),Direction.UP,floor,false));
        h.assertTrue(stack.useOn(use).consumesAction(),"tissue must plant on copper");
        var list=level.getEntitiesOfClass(BlacklightHydra.class,new AABB(floor).inflate(3));
        h.assertTrue(list.size()==1&&stack.getCount()==1,"exactly one seed/entity");
        h.assertTrue(!stack.useOn(use).consumesAction()&&stack.getCount()==1,"overlap consumes no item");
        var e=list.getFirst();var save=new CompoundTag();e.addAdditionalSaveData(save);
        save.putInt("GrowthAge",1800);save.putInt("Feeds",3);
        var restored=ModEntities.BLACKLIGHT_HYDRA.get().create(level);restored.readAdditionalSaveData(save);
        h.assertTrue(restored.planted()&&restored.feeds()==3&&restored.growthAge()==1800&&Math.abs(restored.growth()-.6F)<.001,"growth survives reload");
        h.assertTrue(restored.scenePlayerId()==-1,"reload never restores a camera lock");
        h.assertTrue(restored.action()==0&&restored.getPassengers().isEmpty(),"transient capture never resumes locked");
        var route=new HydraLink(level.dimension(),originForLink(e),UUID.randomUUID());
        e.connect(p.getUUID(),route);var linkedSave=new CompoundTag();e.addAdditionalSaveData(linkedSave);
        restored.readAdditionalSaveData(linkedSave);
        h.assertTrue(route.equals(restored.link())&&restored.growth()==1,"paired endpoint identity and maturity survive reload");
        h.assertTrue(!restored.beginCombat(p,3),"gateway cannot run wild combat AI");
        e.discard();h.succeed();
    }
    private static BlockPos originForLink(BlacklightHydra h){return h.blockPosition().offset(50,0,0);}
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void hunterAttacksAndConsumptionInterruptsItsSwipe(GameTestHelper h){
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)h.setBlock(x,1,z,Blocks.STONE);
        var hunter=h.spawn(ModEntities.BLACKLIGHT_HUNTER.get(),4,2,4);
        // Other simultaneously running templates contain survival FakePlayers.
        // Keep this close-combat AI test from acquiring their higher-priority targets.
        hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(3);
        var cow=h.spawn(EntityType.COW,5,2,4);cow.setNoAi(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);cow.setHealth(100);
        // Infection legitimately raises max health after a successful bite.
        // Check accepted damage rather than comparing against pre-infection HP.
        var damaged=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> damageListener=event->{
            if(event.getEntity()==cow&&event.getSource().getEntity()==hunter&&event.getNewDamage()>0)damaged.set(true);
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(damageListener);
        hunter.setTarget(cow);
        h.runAfterDelay(32,()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(damageListener);
            h.assertTrue(damaged.get(),"hunter must deliver its animated swipe; target="+hunter.getTarget()+" move="+hunter.attackMove()+" distance="+hunter.distanceTo(cow));
            var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HunterConsumeQA"));
            h.assertTrue(hunter.claim(p)&&hunter.isBeingConsumed()&&!hunter.committedAttack(),"consumption interrupts committed swipe");
            hunter.release();h.assertTrue(!hunter.isBeingConsumed()&&!hunter.isNoAi(),"release restores AI");hunter.discard();cow.discard();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=100)
    public static void hiveDropsEntranceSeed(GameTestHelper h){
        var hive=h.spawn(ModEntities.BLACKLIGHT_HIVE.get(),3,2,3);
        hive.hurt(h.getLevel().damageSources().genericKill(),10000);
        h.runAfterDelay(2,()->{
            int amount=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,hive.getBoundingBox().inflate(5)).stream()
                .filter(e->e.getItem().is(ModItems.LIVING_HIVE_TISSUE.get())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(amount==1,"hive must drop exactly one tissue, got "+amount);h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=160)
    public static void feedingRequiresAnAcceptedKillAndHonorsGriefing(GameTestHelper h){
        var level=h.getLevel();var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HydraFeedQA"));
        var origin=h.absolutePos(new BlockPos(4,2,4));p.moveTo(origin.getCenter().add(3,0,0));level.addNewPlayer(p);
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)h.setBlock(x,1,z,Blocks.COPPER_BLOCK);
        var e=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,2,4);e.plant(p.getUUID());
        var cow=h.spawn(EntityType.COW,5,2,4);cow.setNoAi(true);cow.setInvulnerable(true);
        boolean old=level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());
        h.runAfterDelay(55,()->{
            h.assertTrue(e.feeds()==0&&cow.isAlive(),"prevented death must not grant food");cow.setInvulnerable(false);
        });
        h.runAfterDelay(120,()->{
            boolean fed=e.feeds()==1&&!cow.isAlive();boolean intact=level.getBlockState(origin.below()).is(Blocks.COPPER_BLOCK);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(old,level.getServer());
            e.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            h.assertTrue(fed,"one killed creature must grant exactly one feed");h.assertTrue(intact,"mobGriefing=false preserves terrain");h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=280)
    public static void wildHydraHasTelegraphedDamageAndRecovers(GameTestHelper h){
        var level=h.getLevel();var hits=new java.util.concurrent.atomic.AtomicInteger();
        var p=new net.neoforged.neoforge.common.util.FakePlayer(level,new GameProfile(UUID.randomUUID(),"HydraHitQA")){
            @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){if(amount==14)hits.incrementAndGet();return true;}
        };
        for(int x=1;x<9;x++)for(int z=1;z<9;z++)h.setBlock(x,1,z,Blocks.STONE);
        p.moveTo(h.absolutePos(new BlockPos(5,2,4)).getCenter());level.addNewPlayer(p);
        var e=h.spawn(ModEntities.BLACKLIGHT_HYDRA.get(),4,2,4);
        h.runAfterDelay(90,()->h.assertTrue(hits.get()==0&&e.action()==3,"windup before damage"));
        h.runAfterDelay(235,()->{int action=e.action();e.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);h.assertTrue(hits.get()==1,"slam must dispatch one damage event");h.assertTrue(action==0,"slam must return to idle");h.succeed();});
    }
}
