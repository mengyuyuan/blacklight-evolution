package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Prepared species fixtures exercise real server ticks, collision and accepted damage. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class NativeInfectedCombatGameTest {
    private static final List<Entity> cleanup=new ArrayList<>();
    private static final Set<net.minecraft.world.level.ChunkPos> tickets=new HashSet<>();
    private static final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> originalBlocks=new HashMap<>();
    private static net.minecraft.server.level.ServerLevel testLevel;
    private static int nextArea;
    private static void loaded(GameTestHelper h,BlockPos at){testLevel=h.getLevel();var chunk=new net.minecraft.world.level.ChunkPos(at);if(!testLevel.getForcedChunks().contains(chunk.toLong())){testLevel.setChunkForced(chunk.x,chunk.z,true);tickets.add(chunk);}testLevel.getChunk(chunk.x,chunk.z);}
    private static void fixture(GameTestHelper h,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){testLevel=h.getLevel();var old=testLevel.getBlockState(pos);if(old==state)return;originalBlocks.putIfAbsent(pos.immutable(),old);testLevel.setBlock(pos,state,2);}
    private static BlacklightInfectedHost body(GameTestHelper h,EntityType<? extends Mob> source){
        // Vertical arenas stay in the GameTest runner's entity-ticking columns.
        // Forcing distant new columns is asynchronous while GameTest time runs without a 50-ms sleep.
        int offset=(++nextArea%17)*14;
        var origin=h.absolutePos(BlockPos.ZERO);var at=new BlockPos((origin.getX()&~15)+8,origin.getY()+30+offset,(origin.getZ()&~15)+2);
        // Keep both ends of the 12-block projectile lane inside the runner's already
        // entity-ticking origin column. A loaded neighbour may not tick entities yet.
        for(int x=-8;x<=13;x++)for(int z=-6;z<=19;z++)for(int y=0;y<=11;y++)fixture(h,at.offset(x,y-1,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState());
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());var original=source.create(h.getLevel());host.inherit(original);host.setPos(at.getBottomCenter());loaded(h,host.blockPosition());host.setPersistenceRequired();h.getLevel().addFreshEntity(host);cleanup.add(host);return host;
    }
    // Deliberate combat dummies must survive distant test players and vanilla despawn checks.
    private static Mob enemy(GameTestHelper h,BlacklightInfectedHost body,Vec3 offset){
        var target=EntityType.HUSK.create(h.getLevel());target.setPersistenceRequired();target.setNoAi(true);target.setPos(body.position().add(offset));loaded(h,target.blockPosition());target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);target.setHealth(100000);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);h.getLevel().addFreshEntity(target);cleanup.add(target);return target;
    }
    private static void end(){cleanup.forEach(Entity::discard);cleanup.clear();originalBlocks.forEach((p,s)->testLevel.setBlock(p,s,2));originalBlocks.clear();for(var p:tickets)testLevel.setChunkForced(p.x,p.z,false);tickets.clear();}
    private static void ranged(GameTestHelper h,EntityType<? extends Mob> source,Class<? extends Entity> projectile,boolean mustHit){
        var body=body(h,source);var target=enemy(h,body,new Vec3(0,0,10));body.setTarget(target);var telegraph=new java.util.concurrent.atomic.AtomicBoolean();var fired=new java.util.concurrent.atomic.AtomicBoolean();var nativeWither=new java.util.concurrent.atomic.AtomicBoolean();
        for(int tick=1;tick<=120;tick++)h.runAfterDelay(tick,()->{
            if(!body.nativeAction().isEmpty())telegraph.set(true);
            if(source==EntityType.WITHER)for(var e:h.getLevel().getAllEntities())if(e instanceof WitherSkull skull&&skull.getOwner()==body){nativeWither.set(true);if(!cleanup.contains(e))cleanup.add(e);}
            if(projectile!=null)for(var e:h.getLevel().getAllEntities())if(projectile.isInstance(e)&&(e instanceof Projectile shot&&shot.getOwner()==body||e instanceof EvokerFangs fangs&&fangs.getOwner()==body)){fired.set(true);if(!cleanup.contains(e))cleanup.add(e);}
        });
        h.runAfterDelay(125,()->{try{h.assertTrue(telegraph.get(),source+" has a real synchronized telegraph");if(projectile!=null)h.assertTrue(fired.get(),source+" creates an owned physical "+projectile.getSimpleName());if(source==EntityType.WITHER)h.assertTrue(nativeWither.get(),"infection adds flesh without deleting native skulls");if(mustHit)h.assertTrue(target.getHealth()<100000,source+" deals accepted damage beyond melee range; pos="+body.position()+" target="+target.position());}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_bow") public static void infectedSkeletonShootsAndActuallyHits(GameTestHelper h){ranged(h,EntityType.SKELETON,Arrow.class,true);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_crossbow") public static void infectedPillagerRetainsRangedWeapon(GameTestHelper h){ranged(h,EntityType.PILLAGER,Arrow.class,true);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_blaze") public static void infectedBlazeKeepsFireAndAddsFlesh(GameTestHelper h){ranged(h,EntityType.BLAZE,SmallFireball.class,false);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_ghast") public static void infectedGhastRetainsPhysicalFireball(GameTestHelper h){ranged(h,EntityType.GHAST,LargeFireball.class,false);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_wither") public static void infectedWitherHasBothSkullsAndTumors(GameTestHelper h){ranged(h,EntityType.WITHER,InfectedTumor.class,true);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_shulker") public static void infectedShulkerKeepsOwnedHomingBullet(GameTestHelper h){ranged(h,EntityType.SHULKER,ShulkerBullet.class,false);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_trident") public static void infectedDrownedThrowsARealTrident(GameTestHelper h){ranged(h,EntityType.DROWNED,ThrownTrident.class,true);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_witch") public static void infectedWitchThrowsItsPotion(GameTestHelper h){ranged(h,EntityType.WITCH,ThrownPotion.class,false);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_fangs") public static void infectedEvokerKeepsFangs(GameTestHelper h){ranged(h,EntityType.EVOKER,EvokerFangs.class,false);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_guardian") public static void infectedGuardianActuallyUsesItsBeam(GameTestHelper h){ranged(h,EntityType.GUARDIAN,null,true);}
    @GameTest(template="wall_test",timeoutTicks=150,batch="native_sonic")
    public static void infectedWardenSonicReachesThroughWallLikeNative(GameTestHelper h){
        var body=body(h,EntityType.WARDEN);var target=enemy(h,body,new Vec3(0,0,12));body.setTarget(target);var throughWall=new java.util.concurrent.atomic.AtomicBoolean();float[] previous={target.getHealth()};
        for(int x=-2;x<=2;x++)for(int y=0;y<7;y++)fixture(h,body.blockPosition().offset(x,y,5),Blocks.STONE.defaultBlockState());
        for(int i=1;i<94;i++)h.runAfterDelay(i,()->{if(target.getHealth()<previous[0]&&!body.hasLineOfSight(target))throughWall.set(true);previous[0]=target.getHealth();});
        h.runAfterDelay(95,()->{try{h.assertTrue(throughWall.get(),"accepted sonic damage occurred while a solid wall actually occluded the victim");h.assertTrue(body.getAttributeValue(Attributes.ATTACK_DAMAGE)>=30,"infection does not flatten native 30 damage to generic 18");}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="native_tumor")
    public static void tumorBurstsPiercesActualLaneAndRetracts(GameTestHelper h){
        var body=body(h,EntityType.WITHER);body.setNoAi(true);var target=enemy(h,body,new Vec3(0,0,8));
        var tumor=new InfectedTumor(h.getLevel(),body);tumor.configure(100,6);tumor.setPos(target.getBoundingBox().getCenter().add(3.5,0,0));h.getLevel().addFreshEntity(tumor);cleanup.add(tumor);tumor.burst();
        h.assertTrue(target.getHealth()==100000,"outside initial burst: no immediate invisible radius hit");h.assertTrue(tumor.tendrils().stream().anyMatch(v->v.length()>3),"real arm endpoint reaches the distant target");
        h.runAfterDelay(16,()->{com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Tumor lane: age={}, removed={}, owner={}, tumor={}, target={}, arms={}, hp={}",tumor.burstAge(0),tumor.isRemoved(),tumor.getOwner(),tumor.position(),target.position(),tumor.tendrils(),target.getHealth());h.assertTrue(target.getHealth()<100000,"delayed extension makes an accepted physical lane contact");});
        h.runAfterDelay(42,()->{try{h.assertTrue(tumor.isRemoved(),"all arms and actor retract; no permanent active hazard");}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=90,batch="native_tumor_wall")
    public static void tumorCannotPierceThroughSolidWall(GameTestHelper h){
        var body=body(h,EntityType.WITHER);body.setNoAi(true);var target=enemy(h,body,new Vec3(0,0,10));
        for(int x=-3;x<=3;x++)for(int y=0;y<7;y++)fixture(h,body.blockPosition().offset(x,y,7),Blocks.STONE.defaultBlockState());
        var tumor=new InfectedTumor(h.getLevel(),body);tumor.configure(100,6);tumor.setPos(body.position().add(0,1,5));h.getLevel().addFreshEntity(tumor);cleanup.add(tumor);tumor.burst();
        h.runAfterDelay(30,()->{try{h.assertTrue(target.getHealth()==100000,"wall blocks both splash and actual tendril contact");}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=30,batch="native_tumor_defuse")
    public static void tumorCanBeShotDownBeforeBurst(GameTestHelper h){
        var body=body(h,EntityType.WITHER);body.setNoAi(true);var target=enemy(h,body,new Vec3(0,0,6));var tumor=new InfectedTumor(h.getLevel(),body);tumor.setPos(target.position().add(0,1,1));h.getLevel().addFreshEntity(tumor);cleanup.add(tumor);
        try{h.assertTrue(tumor.hurt(body.damageSources().mobAttack(target),3)&&tumor.isRemoved(),"enemy can defuse incoming flesh projectile");h.assertTrue(target.getHealth()==100000,"shooting it down does not detonate a free hit");}finally{end();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="native_reload")
    public static void oldSpeciesSaveRecoversAbilityWithoutRefillAndMidcastCannotReplay(GameTestHelper h){
        var body=body(h,EntityType.WARDEN);body.setNoAi(true);body.setHealth(body.getMaxHealth()*.4F);var tag=new CompoundTag();body.saveWithoutId(tag);tag.remove("BlacklightNativeCombat");
        var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());try{restored.load(tag);h.assertTrue(restored.nativeCombat().profile()==NativeInfectedProfile.SONIC,"old saved species gains its native ability without manual respawn");h.assertTrue(Math.abs(restored.getHealth()/restored.getMaxHealth()-.4F)<.01,"no health refill");restored.setNativeAction("SONIC",body);restored.saveWithoutId(tag);restored.load(tag);h.assertTrue(restored.nativeAction().isEmpty()&&tag.getCompound("BlacklightNativeCombat").getInt("Cooldown")>=100,"pending cast does not duplicate on chunk reload");}finally{restored.discard();end();}h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="native_movement")
    public static void infectedSlimeActuallyJumpsAndBatActuallyFlies(GameTestHelper h){
        var slime=body(h,EntityType.SLIME);var target=enemy(h,slime,new Vec3(0,0,10));slime.setTarget(target);var bat=body(h,EntityType.BAT);bat.setPos(bat.position().add(4,0,0));var seenJump=new java.util.concurrent.atomic.AtomicBoolean();var seenFlight=new java.util.concurrent.atomic.AtomicBoolean();double initial=bat.getY();
        for(int i=1;i<50;i++)h.runAfterDelay(i,()->{if(slime.getY()>target.getY()+.3)seenJump.set(true);if(bat.getY()>initial+.3&&bat.isNoGravity())seenFlight.set(true);});
        // Roaming may descend again after taking off; test the actual flight path,
        // rather than requiring a random bat to finish above its initial altitude.
        h.runAfterDelay(55,()->{try{h.assertTrue(seenJump.get(),"slime uses real hopping locomotion instead of walking");h.assertTrue(seenFlight.get(),"bat lifts itself into actual free flight");}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=110,batch="native_water")
    public static void infectedSquidSwimsInsteadOfSinkingLikeALandMob(GameTestHelper h){
        var squid=body(h,EntityType.SQUID);var base=squid.blockPosition();
        for(int x=-5;x<=5;x++)for(int z=-5;z<=6;z++)for(int y=0;y<6;y++)fixture(h,base.offset(x,y,z),Blocks.WATER.defaultBlockState());
        squid.setPos(squid.position().add(0,2,0));var start=squid.position();var moved=new java.util.concurrent.atomic.AtomicBoolean();
        for(int i=5;i<85;i++)h.runAfterDelay(i,()->{if(squid.position().distanceToSqr(start)>.4)moved.set(true);});
        h.runAfterDelay(90,()->{try{h.assertTrue(moved.get()&&squid.isInWater(),"actual aquatic roaming moves through water");h.assertTrue(squid.getY()>base.getY()+.4,"water locomotion does not apply land gravity to pin it to the bottom");}finally{end();}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=190,batch="native_organ_scheduler")
    public static void aRestoredRangedHostCanStillUseAnAcquiredCloseCombatOrgan(GameTestHelper h){
        var b=body(h,EntityType.SKELETON);var tag=new CompoundTag();b.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",TissueCore.HYDROSTAT.key());tag.putLong("BlacklightTissueAge",72000);tag.putInt("BlacklightTissueNutrition",100);tag.put("BlacklightTissueInheritance",TissueInheritanceCodec.write(new TissueInheritance(Map.of(TissueCore.HYDROSTAT,new TissueInheritance.Organ(900,0,0,0,0,81)),0,900,false,null,1,0)));b.load(tag);
        var target=enemy(h,b,new Vec3(0,0,2.6));b.setTarget(target);var organ=new java.util.concurrent.atomic.AtomicBoolean();var bow=new java.util.concurrent.atomic.AtomicBoolean();
        for(int i=1;i<165;i++)h.runAfterDelay(i,()->{if(b.organActionCore()==TissueCore.HYDROSTAT)organ.set(true);if(b.nativeAction().equals("BOW"))bow.set(true);});
        h.runAfterDelay(170,()->{try{h.assertTrue(organ.get(),"native ranged AI does not starve mature inherited organs");h.assertTrue(bow.get(),"the original ranged weapon still resumes after organ recovery");}finally{end();}h.succeed();});
    }
}
