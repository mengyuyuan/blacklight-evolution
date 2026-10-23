package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueOrgans;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueOrganGameTest {
    @GameTest(template="wall_test",timeoutTicks=20,batch="tissue_organs")
    public static void calfKeepsGenericCoverageDimensions(GameTestHelper h) {
        var calf=EntityType.COW.create(h.getLevel());calf.setBaby(true);
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(calf);
        float width=host.getBbWidth(),height=host.getBbHeight();host.advanceTissue(12000);
        h.assertTrue(host.isBaby(),"fixture retains the original calf age");
        h.assertTrue(host.getBbWidth()==width&&host.getBbHeight()==height,"adult-only blueprint must not enlarge a calf's generic collider");
        calf.discard();host.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=20,batch="tissue_organs")
    public static void reconstructedCowKeepsStatsAndUpdatesPhysicalEnvelope(GameTestHelper h) {
        var host=ModEntities.INFECTED_HOST.get().create(h.getLevel());host.inherit(EntityType.COW.create(h.getLevel()));
        host.setNoAi(true);host.setPos(h.absolutePos(new BlockPos(3,30,3)).getCenter());h.getLevel().addFreshEntity(host);
        float width=host.getBbWidth(),height=host.getBbHeight(),health=host.getMaxHealth();double damage=host.getAttributeValue(Attributes.ATTACK_DAMAGE);
        host.advanceTissue(12000);
        h.assertTrue(host.getBbWidth()==width&&host.getBbHeight()==height,"procedural organs preserve the native collider instead of using cow-only dimensions");
        h.assertTrue(host.getMaxHealth()>health&&host.getAttributeValue(Attributes.ATTACK_DAMAGE)==damage,"mature defensive organs gain durability while attack keeps its existing budget");
        h.assertTrue(host.tissueAge()==12000,"mature state is still the saved tissue age");
        var saved=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(saved);
        var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(saved);
        h.assertTrue(restored.getBbWidth()==host.getBbWidth()&&restored.getBbHeight()==host.getBbHeight(),"authored physical envelope restores with the same tissue age");
        h.assertTrue(restored.getUUID().equals(host.getUUID()),"left/right growth seed survives reload");
        restored.discard();host.discard();h.succeed();
    }
    private record Pair(BlacklightInfectedHost host,Mob prey,java.util.List<net.minecraft.world.level.ChunkPos> forced) {
        void clear(){host.discard();prey.discard();for(var p:forced)((net.minecraft.server.level.ServerLevel)host.level()).setChunkForced(p.x,p.z,false);}
    }
    private static Pair prepare(GameTestHelper h,boolean mature) {
        var level=h.getLevel();level.getServer().setDifficulty(Difficulty.NORMAL,true);
        var pos=h.absolutePos(new BlockPos(5,30,5));
        for(int x=-5;x<=6;x++)for(int z=-5;z<=5;z++)level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        var host=ModEntities.INFECTED_HOST.get().create(level);host.inherit(EntityType.COW.create(level));
        long identity=pos.asLong();host.setUUID(new java.util.UUID(identity,identity^0x171));
        if(mature)host.advanceTissue(12000);
        host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        host.setPos(pos.getCenter().add(0,-.5,0));level.addFreshEntity(host);
        // Adjacent GameTest arenas share one level. Keep the explicit victim;
        // nearest-target goals could otherwise steal an escaping neighbour's
        // prey and make a range/hold test depend on the random layout.
        host.removeAllGoals(goal->true);
        var prey=EntityType.COW.create(level);prey.setNoAi(true);prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        float reach=TissueOrgans.reach(TissueOrgans.seed(host.getUUID()),12000,host.getBbWidth(),host.getBbHeight());
        prey.setHealth(100);prey.setPos(host.position().add((host.getBbWidth()+prey.getBbWidth())*.5+reach*.85,0,0));level.addFreshEntity(prey);host.setTarget(prey);
        h.assertTrue(!host.isWithinMeleeAttackRange(prey),"fixture must be outside ordinary melee reach");
        // Some random arena placements put the victim beyond the template's chunk.
        // Keep the complete isolated fixture ticking; production never adds tickets.
        var forced=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var chunk=new net.minecraft.world.level.ChunkPos(pos.offset(x*16,0,z*16));
            if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(chunk.x,chunk.z,true);forced.add(chunk);}
        }
        return new Pair(host,prey,forced);
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void grownTendrilHasWindupThenOneRealHit(GameTestHelper h) {
        var pair=prepare(h,true);float damage=(float)pair.host.getAttributeValue(Attributes.ATTACK_DAMAGE);
        h.runAfterDelay(7,()->h.assertTrue(pair.prey.getHealth()==100,"windup must not instantly hit"));
        // Contact infection is deliberately probabilistic in gameplay. Force
        // the post-hit allegiance change so retraction is tested on every run.
        h.runAfterDelay(14,()->com.codex.blacklight.infection.BlacklightInfectionSystem.infect(pair.prey,pair.host));
        h.runAfterDelay(18,()->{
            h.assertTrue(com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(pair.prey),"fixture changes allegiance after contact");
            h.assertTrue(pair.host.tissueStrikeElapsed(0)>TissueOrgans.HIT,"infecting the victim must not cancel retraction");
            h.assertTrue(pair.host.tissueStrikePoint().lengthSqr()>0,"the server retains a finite contact point after allegiance changes");
        });
        h.runAfterDelay(35,()->{
            // Infection changes displayed health units while preserving its
            // fraction. Count actual hits in the original units in both cases.
            float health=pair.prey.getHealth()/com.codex.blacklight.power.CombatScale.units(pair.prey);
            h.assertTrue(Math.abs(health-(100-damage))<.01F,"one server contact, not one hit per mesh/frame; health="+health+", damage="+damage+", reach="+pair.host.tissueOrganReach());
            h.assertTrue(pair.host.tissueStrikeElapsed(0)<0,"tendril retracts and clears transient target");
            pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void ancientTendrilReachesItsGrownDistanceAndRetracts(GameTestHelper h) {
        var pair=prepare(h,true);float mature=pair.host.tissueOrganReach();
        pair.host.advanceTissue(com.codex.blacklight.infection.TissueGrowth.ANCIENT_TICKS-12000);
        float reach=pair.host.tissueOrganReach();
        h.assertTrue(reach>mature*1.5F,"later visual length is also the server-authoritative organ reach");
        pair.prey.setPos(pair.host.position().add((pair.host.getBbWidth()+pair.prey.getBbWidth())*.5+reach*.85,0,0));
        var tag=new net.minecraft.nbt.CompoundTag();pair.host.saveWithoutId(tag);
        var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.tissueAge()==pair.host.tissueAge()&&Math.abs(restored.tissueOrganReach()-reach)<.001F,"saved age and seed restore the same late reach");
        h.runAfterDelay(7,()->h.assertTrue(pair.prey.getHealth()==100,"larger organs still require the normal windup"));
        h.runAfterDelay(35,()->{
            float health=pair.prey.getHealth()/com.codex.blacklight.power.CombatScale.units(pair.prey);
            float damage=(float)pair.host.getAttributeValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(Math.abs(health-(100-damage))<.01F,"growth does not multiply hits or attack damage");
            h.assertTrue(pair.host.tissueStrikeElapsed(0)<0,"ancient organs finish their natural retraction");
            restored.discard();pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void evolvedSkeletonAndTendrilHitOnceAtTheirActualGrownReach(GameTestHelper h) {
        var pair=prepare(h,true);
        var pos=pair.host.blockPosition();for(int x=-12;x<=12;x++)for(int z=-5;z<=5;z++)h.getLevel().setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
        float oldReach=pair.host.tissueOrganReach();pair.host.advanceTissue(48000+3*24000L-12000);
        h.assertTrue(pair.host.tissueBodyScale()>1.3F&&pair.host.tissueOrganReach()>oldReach*2,"later generations enlarge skeleton and supported reach together");
        pair.prey.setPos(pair.host.position().add((pair.host.getBbWidth()+pair.prey.getBbWidth())*.5+pair.host.tissueOrganReach()*.85,0,0));
        h.runAfterDelay(7,()->h.assertTrue(pair.prey.getHealth()==100,"new generation still has a real windup"));
        h.runAfterDelay(35,()->{
            float health=pair.prey.getHealth()/com.codex.blacklight.power.CombatScale.units(pair.prey);
            float damage=(float)pair.host.getAttributeValue(Attributes.ATTACK_DAMAGE);
            h.assertTrue(Math.abs(health-(100-damage))<.01F,"one evolved organ strike causes exactly one server hit");
            h.assertTrue(pair.host.tissueStrikeElapsed(0)<0,"evolved attack retracts and clears state");
            pair.clear();h.succeed();
        });
    }
    private static Pair tentacleHost(GameTestHelper h) {
        var pair=prepare(h,true);long seed=1;
        while(com.codex.blacklight.infection.TissueEvolution.tentacles(com.codex.blacklight.infection.TissueEvolution.sample(seed,120000))<.99F)seed++;
        pair.host.setUUID(new java.util.UUID(0,seed));pair.host.advanceTissue(108000);
        pair.host.removeAllGoals(goal->true);pair.host.setNoAi(false);pair.prey.removeAllGoals(goal->true);pair.prey.setNoAi(false);
        pair.prey.setPos(pair.host.position().add((pair.host.getBbWidth()+pair.prey.getBbWidth())*.5+pair.host.tissueOrganReach()*.8,0,0));
        // This fixture verifies the tether lifecycle; contact infection has a
        // separate allegiance-transition test above and must not reroll here.
        pair.prey.getPersistentData().putLong(com.codex.blacklight.infection.InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        pair.host.setTarget(pair.prey);return pair;
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void tentacleBranchReallyGrabsPullsAndReleasesWithoutChangingNoAi(GameTestHelper h) {
        var pair=tentacleHost(h);double before=pair.prey.getX();
        h.runAfterDelay(6,()->h.assertTrue(com.codex.blacklight.infection.TissueGrapples.owner(pair.prey)==null,"grasp has a windup before contact"));
        h.runAfterDelay(15,()->{
            h.assertTrue(com.codex.blacklight.infection.TissueGrapples.owner(pair.prey)==pair.host,"successful contact holds the actual victim");
            h.assertTrue(pair.host.tissueGripReleaseAt()>h.getLevel().getGameTime(),"client presentation has the real server hold/release clock");
            h.assertTrue(!pair.prey.isNoAi()&&!pair.prey.isPassenger(),"hold does not overwrite AI or use riding");
        });
        h.runAfterDelay(22,()->h.assertTrue(pair.prey.getX()<before-.1,"the tether physically pulls the prey toward the host"));
        h.runAfterDelay(31,()->{
            h.assertTrue(com.codex.blacklight.infection.TissueGrapples.owner(pair.prey)==null&&!pair.prey.isNoAi(),"retraction releases the victim and its normal AI");
            h.assertTrue(pair.host.tissueGripReleaseAt()<0,"the transient visual clock clears after retraction");
            pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void interruptingTheHostImmediatelyDropsItsTentacleClaim(GameTestHelper h) {
        var pair=tentacleHost(h);
        h.runAfterDelay(15,()->{
            h.assertTrue(com.codex.blacklight.infection.TissueGrapples.owner(pair.prey)==pair.host,"fixture establishes a real hold; ticks="+pair.host.tickCount+", entityTicking="+h.getLevel().isPositionEntityTicking(pair.host.blockPosition())+", target="+(pair.host.getTarget()==pair.prey)+", elapsed="+pair.host.tissueStrikeElapsed(0)+", alive="+pair.prey.isAlive()+", sight="+pair.host.hasLineOfSight(pair.prey)+", distance="+pair.host.distanceTo(pair.prey));
            pair.host.discard();
            h.assertTrue(com.codex.blacklight.infection.TissueGrapples.owner(pair.prey)==null&&!pair.prey.isNoAi(),"removing the host cannot leave a stuck victim");
            pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void youngTissueCannotHitAtMatureOrganDistance(GameTestHelper h) {
        var pair=prepare(h,false);
        h.runAfterDelay(35,()->{
            h.assertTrue(pair.prey.getHealth()==100,"a skin bud must not have adult tendril reach");
            pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void tendrilCannotHitThroughWall(GameTestHelper h) {
        var pair=prepare(h,true);var pos=pair.host.blockPosition().east();
        for(int z=-2;z<=2;z++)for(int y=0;y<4;y++)h.getLevel().setBlock(pos.offset(0,y,z),Blocks.STONE.defaultBlockState(),2);
        h.runAfterDelay(35,()->{
            h.assertTrue(pair.prey.getHealth()==100,"organ contact respects line of sight");
            pair.clear();h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="tissue_organs")
    public static void targetCanLeaveRangeDuringWindup(GameTestHelper h) {
        var pair=prepare(h,true);
        pair.prey.setNoGravity(true); // Leaving this small arena is not a fall-damage test.
        h.runAfterDelay(9,()->pair.prey.setPos(pair.prey.position().add(8,0,0)));
        h.runAfterDelay(35,()->{
            h.assertTrue(pair.prey.getHealth()==100,"an attack does not deal damage after the target escapes");
            pair.clear();h.succeed();
        });
    }
}
