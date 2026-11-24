package com.codex.blacklight.gametest;

import com.codex.blacklight.infection.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Prepared mature lineage fixtures; these do not claim a full default-time ecology run. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class EvolvedVariantsGameTest {
    private static TissueCore core(EvolvedArchetype type){return switch(type){case BLADE->TissueCore.BLADE;case STRENGTH->TissueCore.MUSCLE;case ARMORED->TissueCore.CARAPACE;case PREDATOR->TissueCore.JAW;case TENDRIL->TissueCore.GRASP;case SKIRMISHER->TissueCore.TENDON;case SENSORY->TissueCore.CREST;case REGENERATOR->TissueCore.CROP;};}
    static BlacklightInfectedHost host(GameTestHelper h,EvolvedArchetype type){
        for(int x=-14;x<=24;x++)for(int z=-14;z<=24;z++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,29,z)),Blocks.STONE.defaultBlockState(),2);
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.ZOMBIE.create(h.getLevel()));var core=core(type);
        var genes=new TissueInheritance(Map.of(core,new TissueInheritance.Organ(900,0,0,0,type==EvolvedArchetype.BLADE?1:0,81)),6,2100,true,null,1,6);
        var tag=new CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",core.key());tag.putLong("BlacklightTissueAge",504000);tag.putInt("BlacklightTissueNutrition",100);tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(genes));host.load(tag);
        var spawn=h.absolutePos(new BlockPos(5,30,5));
        // Shared suites can leave a raised platform across this arena. Prepare the actual combat lane.
        for(var pos:BlockPos.betweenClosed(spawn.offset(-1,0,-1),spawn.offset(1,3,13)))
            h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        host.setNoAi(true);
        host.setPos(spawn.getBottomCenter());
        h.assertTrue(EvolvedForm.eligible(host.tissueInheritance(),host.tissueAge()),"loaded lineage qualifies for metamorphosis: "+type);
        h.assertTrue(h.getLevel().addFreshEntity(host),"mature host enters its test arena: "+type);
        h.assertTrue(host.beginEvolvedMetamorphosis(),"mature host can transform in the prepared arena: "+type);
        for(int i=0;i<EvolvedForm.DURATION;i++)host.tick();
        h.assertTrue(host.isEvolved()&&!host.isMetamorphosing()&&host.evolvedArchetype()==type&&!host.evolvedChoice().orElseThrow().fallback(),"actual chosen body matches its strongest inherited bias: "+host.evolvedChoice());
        host.setOnGround(true);host.setHealth(host.getMaxHealth());return host;
    }
    static Mob enemy(GameTestHelper h,BlacklightInfectedHost host,double distance){
        return enemy(h,host,distance,EntityType.HUSK);
    }
    private static Mob enemy(GameTestHelper h,BlacklightInfectedHost host,double distance,EntityType<? extends Mob> species){
        var e=species.create(h.getLevel());e.setPersistenceRequired();e.setNoAi(true);e.setPos(host.position().add(0,0,distance));e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);e.setHealth(100000);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);var chunk=new net.minecraft.world.level.ChunkPos(e.blockPosition());h.getLevel().setChunkForced(chunk.x,chunk.z,true);h.getLevel().getChunk(chunk.x,chunk.z);h.getLevel().addFreshEntity(e);return e;
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="variants_lineage_save")
    public static void allEightActualSelectedBodiesTransformAndSurviveSavingWithoutReroll(GameTestHelper h){
        for(var type:EvolvedArchetype.values()){
            var body=host(h,type);var choice=body.evolvedChoice().orElseThrow();var tag=new CompoundTag();body.setHealth(body.getMaxHealth()*.7F);body.saveWithoutId(tag);body.discard();
            var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());try{restored.load(tag);h.assertTrue(restored.isEvolved()&&restored.evolvedArchetype()==type&&restored.evolvedChoice().orElseThrow().equals(choice),"saved subtype and earned history persist: "+type);h.assertTrue(Math.abs(restored.getHealth()/restored.getMaxHealth()-.7)<.01,"reload never refills the specialized body");}finally{restored.discard();}
        }h.succeed();
    }
    private static void attack(GameTestHelper h,EvolvedArchetype type,double distance){
        var body=host(h,type);var target=enemy(h,body,distance);var seen=new java.util.concurrent.atomic.AtomicBoolean();var connected=new java.util.concurrent.atomic.AtomicBoolean();float[] health={target.getHealth()};body.setNoAi(false);body.setTarget(target);double initial=body.getZ();int budget=170;var first=new java.util.concurrent.atomic.AtomicInteger(-1);
        for(int i=1;i<budget;i++){final int observedTick=i;h.runAfterDelay(i,()->{if(body.evolvedAction().equals(EvolvedSpecialMove.of(type).key)){first.compareAndSet(-1,observedTick);seen.set(true);if(target.getHealth()<health[0])connected.set(true);}health[0]=target.getHealth();});}
        h.runAfterDelay(budget+5,()->{try{com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Evolved special fixture {}: firstSpecial={}, connected={}, targetHealth={}",type,first.get(),connected.get(),target.getHealth());h.assertTrue(seen.get(),"actual AI started its own specialized windup: "+type+" last="+body.evolvedAction());h.assertTrue(connected.get(),"damage was accepted during the actual special, not only by a later generic swing: "+type);if(type==EvolvedArchetype.PREDATOR||type==EvolvedArchetype.SKIRMISHER)h.assertTrue(body.getZ()>initial+.5,"collision-based advance, not stationary attacking: "+type);}finally{body.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=210,batch="variant_strength") public static void hammerAiUsesAnActualGroundSlam(GameTestHelper h){attack(h,EvolvedArchetype.STRENGTH,3);}
    @GameTest(template="wall_test",timeoutTicks=210,batch="variant_armor") public static void shieldAiUsesItsOwnBash(GameTestHelper h){attack(h,EvolvedArchetype.ARMORED,2.4);}
    @GameTest(template="wall_test",timeoutTicks=210,batch="variant_predator") public static void predatorAiActuallyPouncesIntoContact(GameTestHelper h){attack(h,EvolvedArchetype.PREDATOR,5);}
    @GameTest(template="wall_test",timeoutTicks=210,batch="variant_mobility") public static void mobileAiActuallyDashesIntoContact(GameTestHelper h){attack(h,EvolvedArchetype.SKIRMISHER,6);}
    @GameTest(template="wall_test",timeoutTicks=260,batch="variant_quill")
    public static void rangedAiFiresRealCollidingProjectiles(GameTestHelper h){
        verifyVolley(h,12,EntityType.HUSK);
    }
    @GameTest(template="wall_test",timeoutTicks=260,batch="variant_quill_range")
    public static void volleyAtFullRangeHitsItsAssignedTarget(GameTestHelper h){
        verifyVolley(h,22,EntityType.PIG);
    }
    private static void verifyVolley(GameTestHelper h,int range,EntityType<? extends Mob> species){
        var anchor=h.absolutePos(new BlockPos(5,0,5));
        var lane=new BlockPos((anchor.getX() & ~15)+8,h.getLevel().getMaxBuildHeight()-16,anchor.getZ());
        var ticket=UUID.randomUUID();
        var type=net.minecraft.server.level.TicketType.create("blacklight_volley_qa",UUID::compareTo);
        var owned=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(int z=lane.getZ()>>4;z<=(lane.getZ()+range)>>4;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(lane.getX()>>4,z);
            owned.add(chunk);
            h.getLevel().getChunkSource().addRegionTicket(type,chunk,3,ticket);
            h.getLevel().getChunk(chunk.x,chunk.z);
        }
        var observer=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"QuillObserver"));
        observer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        observer.setPos(net.minecraft.world.phys.Vec3.atCenterOf(lane.south(range/2)).add(3,3,0));
        h.getLevel().addNewPlayer(observer);
        Runnable cleanup=()->{
            h.getLevel().removePlayerImmediately(observer,Entity.RemovalReason.DISCARDED);
            for(var chunk:owned)h.getLevel().getChunkSource().removeRegionTicket(type,chunk,3,ticket);
        };
        h.testInfo.addListener(new GameTestListener(){
            public void testStructureLoaded(GameTestInfo info){}
            public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
            public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup.run();}
            public void testAddedForRerun(GameTestInfo original,GameTestInfo retry,GameTestRunner runner){}
        });
        h.startSequence().thenWaitUntil(()->{
            boolean ready=h.getLevel().isPositionEntityTicking(lane) && h.getLevel().isPositionEntityTicking(lane.south(range));
            if(!ready)java.util.concurrent.locks.LockSupport.parkNanos(50_000_000L);
            h.assertTrue(ready,"waiting for the firing lane to tick");
        }).thenExecute(()->fireAtTarget(h,lane,range,species));
    }
    private static void fireAtTarget(GameTestHelper h,BlockPos lane,int range,EntityType<? extends Mob> species){
        var body=host(h,EvolvedArchetype.SENSORY);
        for(var pos:BlockPos.betweenClosed(lane.offset(-3,-1,-3),lane.offset(3,-1,range+3)))
            h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),2);
        for(var pos:BlockPos.betweenClosed(lane.offset(-2,0,-2),lane.offset(2,4,range+2)))
            h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        body.moveTo(lane.getX()+.5,lane.getY(),lane.getZ()+.5,0,0);
        body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        body.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var target=enemy(h,body,range,species);
        // Exercise the attack AI against this target even when another suite has living mobs nearby.
        body.targetSelector.removeAllGoals(goal->true);
        body.setTarget(target);
        var volley=new java.util.concurrent.atomic.AtomicBoolean();
        var emitted=new java.util.concurrent.atomic.AtomicBoolean();
        var hit=new java.util.concurrent.atomic.AtomicBoolean();
        float[] previousHealth={target.getHealth()};
        h.onEachTick(()->{
            if(body.evolvedAction().equals(EvolvedSpecialMove.VOLLEY.key)&&body.evolvedSpecialTarget()==target.getId())
                volley.set(true);
            if(!emitted.get()&&!h.getLevel().getEntitiesOfClass(EvolvedQuill.class,
                    body.getBoundingBox().inflate(24),quill->quill.getOwner()==body).isEmpty())
                emitted.set(true);
            var source=target.getLastDamageSource();
            float health=target.getHealth();
            if(health<previousHealth[0]&&source!=null&&source.getDirectEntity() instanceof EvolvedQuill&&source.getEntity()==body)
                hit.set(true);
            previousHealth[0]=health;
        });
        body.setNoAi(false);
        h.startSequence().thenWaitUntil(()->h.assertTrue(hit.get(),"waiting for quill hit: volley="+volley+" emitted="+emitted+" caster="+body.position()+"/"+body.tickCount+"/"+body.getRemovalReason()+" target="+target.position()+"/"+target.tickCount+"/"+target.getRemovalReason()+" health="+target.getHealth()+" dmg="+target.getLastDamageSource())).thenExecute(()->{
            try{
                h.assertTrue(volley.get(),"the sensory AI committed a volley against the assigned target");
                h.assertTrue(emitted.get(),"the caster emitted a physical quill");
                h.assertTrue(hit.get(),"a quill fired by this caster damaged the assigned target; caster="+body.position()
                    +" target="+target.position()+" health="+target.getHealth()+" last damage="+target.getLastDamageSource());
            }finally{
                body.discard();
                target.discard();
            }
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="variant_tendril")
    public static void lashActuallyPullsAndALaterWallStopsContact(GameTestHelper h){
        var body=host(h,EvolvedArchetype.TENDRIL);var target=enemy(h,body,8);target.setNoAi(false);target.goalSelector.removeAllGoals(g->true);target.targetSelector.removeAllGoals(g->true);body.setNoAi(false);body.setTarget(target);double start=target.getZ();var actions=new java.util.ArrayList<String>();
        for(int i=1;i<70;i++)h.runAfterDelay(i,()->{String action=body.evolvedAction();if(actions.isEmpty()||!actions.getLast().equals(action))actions.add(action);});
        h.runAfterDelay(70,()->{try{h.assertTrue(target.getZ()<start-.4&&target.getHealth()<100000,"the physical tendon pulls and hurts its real target; start="+start+" now="+target.getZ()+" hp="+target.getHealth()+" moves="+actions);h.assertTrue(body.evolvedAction().isEmpty()||!body.evolvedAction().equals(EvolvedSpecialMove.LASH.key),"the first lash withdraws and releases, rather than permanently capturing");}finally{body.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=150,batch="variant_mend")
    public static void supportUsesRealNutritionAndHealsAnActualWoundedInfectedAlly(GameTestHelper h){
        var body=host(h,EvolvedArchetype.REGENERATOR);var target=enemy(h,body,5);var ally=ModEntities.INFECTED_HOST.get().create(h.getLevel());ally.inherit(EntityType.COW.create(h.getLevel()));ally.setNoAi(true);ally.setPos(body.position().add(2,0,0));h.getLevel().addFreshEntity(ally);ally.setHealth(ally.getMaxHealth()*.25F);float before=ally.getHealth();int food=body.tissueNutrition();body.setNoAi(false);body.setTarget(target);
        h.runAfterDelay(65,()->{try{h.assertTrue(ally.getHealth()>before,"support pulse heals actual ally health");h.assertTrue(body.tissueNutrition()<=food-6,"the cast consumes real host food instead of free regeneration");}finally{body.discard();target.discard();ally.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=180,batch="variant_live_tendon_wall")
    public static void aNewSolidWallInterruptsTheLiveTendonBeforeItCanMoveTheVictim(GameTestHelper h){
        var body=host(h,EvolvedArchetype.TENDRIL);var target=enemy(h,body,8);target.setNoAi(false);target.goalSelector.removeAllGoals(g->true);target.targetSelector.removeAllGoals(g->true);body.setNoAi(false);body.setTarget(target);double start=target.getZ();
        h.startSequence().thenWaitUntil(()->h.assertTrue(body.evolvedAction().equals(EvolvedSpecialMove.LASH.key)&&body.evolvedActionElapsed(0)>=10,"wait for the real extending tendon windup"))
          .thenExecute(()->{for(int x=-3;x<=3;x++)for(int y=0;y<=5;y++)h.getLevel().setBlock(body.blockPosition().offset(x,y,4),Blocks.STONE.defaultBlockState(),2);})
          .thenIdle(5).thenExecute(()->{try{h.assertTrue(!body.evolvedAction().equals(EvolvedSpecialMove.LASH.key),"new wall actually cancels the live action");h.assertTrue(Math.abs(target.getZ()-start)<.2&&target.getHealth()==100000,"no through-wall pull or damage");}finally{body.discard();target.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="variant_block")
    public static void armorBlocksFrontHitsButBreaksAndItsBrokenShieldPersists(GameTestHelper h){
        var body=host(h,EvolvedArchetype.ARMORED);var enemy=enemy(h,body,2);body.setYRot(0);body.yBodyRot=0;body.setEvolvedEngaged(true);
        try{
            for(int i=0;i<8;i++){body.invulnerableTime=0;body.hurt(body.damageSources().mobAttack(enemy),20);}
            var tag=new CompoundTag();body.saveWithoutId(tag);h.assertTrue(tag.getCompound("BlacklightEvolvedSpecials").getInt("Shield")==0,"accepted repeated front attacks break finite shield capacity");
            var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());try{restored.load(tag);var second=new CompoundTag();restored.saveWithoutId(second);h.assertTrue(second.getCompound("BlacklightEvolvedSpecials").getInt("Shield")==0,"reload does not restore shield strength");}finally{restored.discard();}
        }finally{body.discard();enemy.discard();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=120,batch="variant_wall")
    public static void physicalQuillCannotDamageATargetThroughAnActualWall(GameTestHelper h){
        var body=host(h,EvolvedArchetype.SENSORY);var target=enemy(h,body,8);
        for(int x=-3;x<=3;x++)for(int y=0;y<6;y++)h.getLevel().setBlock(body.blockPosition().offset(x,y,4),Blocks.STONE.defaultBlockState(),2);
        var dart=new EvolvedQuill(h.getLevel(),body);dart.damage(100);dart.setPos(body.getX(),body.getEyeY()-.4,body.getZ());var aim=target.getBoundingBox().getCenter().subtract(dart.position());dart.shoot(aim.x,aim.y,aim.z,1.3F,0);h.getLevel().addFreshEntity(dart);
        h.runAfterDelay(35,()->{try{h.assertTrue(target.getHealth()==100000&&!dart.isAlive(),"wall collision consumes the real projectile before target contact");}finally{body.discard();target.discard();dart.discard();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=80,batch="variant_shield_reentry")
    public static void aNestedExternalDamageCallbackCannotEraseTheOuterShieldWear(GameTestHelper h){
        var body=host(h,EvolvedArchetype.ARMORED);var enemy=enemy(h,body,2);body.setYRot(0);body.yBodyRot=0;body.setEvolvedEngaged(true);var nested=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre> callback=event->{if(event.getEntity()==body&&event.getSource().getEntity()==enemy&&nested.compareAndSet(false,true)){body.invulnerableTime=0;body.hurt(body.damageSources().generic(),1);}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(callback);
        try{body.invulnerableTime=0;body.hurt(body.damageSources().mobAttack(enemy),20);var tag=new CompoundTag();body.saveWithoutId(tag);h.assertTrue(nested.get()&&tag.getCompound("BlacklightEvolvedSpecials").getInt("Shield")<20,"outer accepted physical block still costs capacity after another mod reenters nonphysical damage");}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(callback);body.discard();enemy.discard();}h.succeed();
    }
}
