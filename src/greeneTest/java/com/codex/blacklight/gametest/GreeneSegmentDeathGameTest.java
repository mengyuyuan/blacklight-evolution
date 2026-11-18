package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.EncounterEntities;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.*;

/** Pending entity-level checks. The corpse is spawned by the real damage
 * path; these fixtures never manufacture one in place of a missing spawn. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @Mod.EventBusSubscriber(modid="blacklight")
public final class GreeneSegmentDeathGameTest {
    private static final class ReloadFixture {
        final EncounterMob mother;final CompoundTag healthy;final Vec3 origin;
        boolean ran,brokenBeforeReload;
        ReloadFixture(EncounterMob mother){this.mother=mother;healthy=save(mother);origin=mother.position();}
    }
    private static ReloadFixture pendingReload;
    @SubscribeEvent public static void corpseSpawnReloadFixture(EntityJoinLevelEvent event){
        var fixture=pendingReload;
        if(fixture==null||!(event.getEntity() instanceof GreeneSegmentCorpse corpse)
                ||corpse.level()!=fixture.mother.level()||corpse.segment()!=0||corpse.position().distanceToSqr(fixture.origin)>1e-8)return;
        // Clear before re-entry. Only the synchronous spawn armed by this QA
        // fixture may replace its exact mother; ordinary game spawns do nothing.
        pendingReload=null;fixture.ran=true;fixture.brokenBeforeReload=fixture.mother.broken(0);
        fixture.mother.load(fixture.healthy.copy());
    }
    private static EncounterMob actor(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mother=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=mother.greeneBattle().save();standing.putBoolean("OpeningPending",false);mother.greeneBattle().load(standing);

        mother.setPos(h.absoluteVec(new Vec3(8,2,8)));mother.setNoAi(true);mother.setPersistenceRequired();
        mother.restrictTo(mother.blockPosition(),10);h.getLevel().addFreshEntity(mother);return mother;
    }
    private static CompoundTag save(Entity entity){var tag=new CompoundTag();entity.saveWithoutId(tag);return tag;}
    private static List<GreeneSegmentCorpse> corpses(GameTestHelper h,Vec3 root){
        return h.getLevel().getEntitiesOfClass(GreeneSegmentCorpse.class,new AABB(root,root).inflate(.25),c->c.position().distanceToSqr(root)<1e-8);
    }
    private static GreeneSegmentCorpse breakOne(GameTestHelper h,EncounterMob mother,int slot){
        Vec3 root=mother.position();var before=new HashSet<UUID>();for(var corpse:corpses(h,root))before.add(corpse.getUUID());
        var copied=GreeneSegmentDeath.savePose(GreeneMotion.pose(mother,0));
        float damage=mother.greeneSegmentMaximum()/CombatScale.units(mother)+1;
        h.assertTrue(mother.hurtPart(slot,mother.damageSources().generic(),damage),"real appendage damage is accepted");
        var fresh=corpses(h,root).stream().filter(c->!before.contains(c.getUUID())).toList();
        h.assertTrue(fresh.size()==1,"one real break creates exactly one independent corpse");var corpse=fresh.get(0);
        h.assertTrue(corpse.getType()==EncounterEntities.GREENE_SEGMENT_CORPSE.get()&&corpse.segment()==slot&&corpse.validPresentation(),"spawn uses the registered segment corpse with a complete copied pose");
        h.assertTrue(copied.equals(save(corpse).getCompound("CopiedPose"))&&corpse.deathAge(0)==0,"spawn copies the pre-collapse pose and starts its own death clock once");
        h.assertTrue(!corpse.isPickable()&&!corpse.isPushable(),"a corpse is neither a new health target nor a movable copy of the mother");
        return corpse;
    }
    private static void done(GameTestHelper h,Entity... entities){for(var entity:entities)entity.discard();h.succeed();}
    private static void samePose(GameTestHelper h,float[][] before,float[][] after){
        h.assertTrue(before.length==after.length,"pose bone count survives reload");
        for(int i=0;i<before.length;i++)h.assertTrue(Arrays.equals(before[i],after[i]),"current death pose does not restart on bone "+i);
    }
    private static Set<UUID> drops(GameTestHelper h,AABB area){
        var result=new HashSet<UUID>();
        for(var entity:h.getLevel().getEntities((Entity)null,area,e->e instanceof ItemEntity||e instanceof ExperienceOrb))result.add(entity.getUUID());
        return result;
    }
    private static void awaitAge(GameTestHelper h,GreeneSegmentCorpse corpse,int age,Runnable action){
        h.assertTrue(!corpse.isRemoved(),"corpse remains present before its authored final death tick");
        if(corpse.deathAge(0)>=age)action.run();else h.runAfterDelay(1,()->awaitAge(h,corpse,age,action));
    }

    @GameTest(template="blackwatch_arena",timeoutTicks=40)
    public static void realBreakCreatesOneCorpseAndBrokenReloadDoesNotDuplicateIt(GameTestHelper h){
        var mother=actor(h);
        h.runAfterDelay(5,()->{
            var corpse=breakOne(h,mother,0);Vec3 root=mother.position();var broken=save(mother);
            h.assertTrue(mother.broken(0)&&!mother.hurtPart(0,mother.damageSources().generic(),10000),"already broken limb rejects repeated damage");
            mother.load(broken.copy());
            h.assertTrue(mother.broken(0)&&!mother.hurtPart(0,mother.damageSources().generic(),10000),"broken ownership survives the mother's full same-object reload");
            h.runAfterDelay(12,()->{
                var found=corpses(h,root);
                h.assertTrue(found.size()==1&&found.get(0)==corpse,"load and expired hit cooldown cannot recreate the broken limb corpse");
                h.assertTrue(corpse.deathAge(0)>0,"the existing corpse continues its independent clock");done(h,mother,corpse);
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40)
    public static void motherMovementAndSubmergeCannotDragTheDetachedPresentation(GameTestHelper h){
        var mother=actor(h);
        h.runAfterDelay(5,()->{
            mother.setYRot(37);mother.yBodyRot=37;var corpse=breakOne(h,mother,1);
            Vec3 origin=corpse.position();float yaw=corpse.getYRot();var copied=save(corpse).getCompound("CopiedPose");
            mother.setPos(mother.position().add(3,0,0));mother.setYRot(127);mother.yBodyRot=127;
            // Natural segment death ends before the125tick collapse completes.
            // Load a separate active sink to isolate root ownership, rather
            // than claiming both presentations overlap in the normal timeline.
            double surface=mother.getY();var cycle=mother.greeneBattle().save();
            cycle.putInt("Stage",GreeneBattleCycle.SUBMERGING);cycle.putInt("Age",18);
            cycle.putDouble("SurfaceY",surface);cycle.putDouble("GoalY",surface-GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks());
            cycle.putInt("UndergroundRemaining",GreeneBattleCycle.UNDERGROUND_TICKS);cycle.putInt("EmergeElapsed",-1);
            cycle.putBoolean("RootControlled",false);cycle.putBoolean("StunPending",false);cycle.putBoolean("RecoveryPending",false);cycle.putBoolean("FloorPending",false);
            mother.greeneBattle().load(cycle);
            h.runAfterDelay(5,()->{
                h.assertTrue(mother.greeneBattle().stage()==GreeneBattleCycle.SUBMERGING&&mother.getY()<surface-.5,"fixture executes actual source sink movement after the loaded handoff");
                h.assertTrue(corpse.position().distanceToSqr(origin)<1e-12&&corpse.getYRot()==yaw,"corpse root and facing remain at the death location while mother moves and turns");
                h.assertTrue(copied.equals(save(corpse).getCompound("CopiedPose")),"mother pose changes do not overwrite the corpse's copied drawable");done(h,mother,corpse);
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=120)
    public static void corpseReloadPreservesAgeAndPoseThenExpiresWithoutLootOrExperience(GameTestHelper h){
        var mother=actor(h);var area=new AABB(mother.position(),mother.position()).inflate(7);var priorDrops=drops(h,area);
        h.runAfterDelay(5,()->{
            var original=breakOne(h,mother,2);
            awaitAge(h,original,11,()->{
                var snapshot=save(original);var pose=original.pose(0);int age=snapshot.getInt("DeathAge");Vec3 origin=original.position();
                original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
                h.runAfterDelay(8,()->{
                    var restored=EncounterEntities.GREENE_SEGMENT_CORPSE.get().create(h.getLevel());restored.load(snapshot.copy());
                    h.assertTrue(restored.validPresentation()&&restored.deathAge(0)==age,"offline time neither restarts nor advances the persisted death clock");
                    h.assertTrue(restored.position().distanceToSqr(origin)<1e-12&&snapshot.getCompound("CopiedPose").equals(save(restored).getCompound("CopiedPose")),"corpse save/load retains its independent root and named copied channels");
                    samePose(h,pose,restored.pose(0));
                    h.assertTrue(h.getLevel().addFreshEntity(restored),"unloaded corpse returns with its original UUID");
                    awaitAge(h,restored,GreeneSegmentDeath.ticks()-1,()->{
                        h.assertTrue(restored.deathAge(0)==GreeneSegmentDeath.ticks()-1,"the complete final presentation remains until its last observed tick");
                        h.runAfterDelay(2,()->{
                            h.assertTrue(restored.isRemoved()&&restored.deathAge(0)==GreeneSegmentDeath.ticks(),"only the original remaining lifetime is consumed after reload");
                            var created=drops(h,area);created.removeAll(priorDrops);
                            h.assertTrue(created.isEmpty(),"segment presentation expiry creates no item drops or experience orbs");done(h,mother,restored);
                        });
                    });
                });
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=40)
    public static void corpseSpawnCallbackReloadStopsOldCoreSharesBeforeFreshArmor(GameTestHelper h){
        var mother=actor(h);
        h.runAfterDelay(5,()->{
            var fixture=new ReloadFixture(mother);pendingReload=fixture;
            try{h.assertTrue(mother.hurt(mother.damageSources().generic(),10000),"old core hit reaches its first child and creates a corpse");}
            finally{pendingReload=null;}
            h.assertTrue(fixture.ran&&fixture.brokenBeforeReload,"real spawn callback observes committed broken ownership before restoring the same mother");
            var saved=save(mother);var spawned=corpses(h,fixture.origin);
            h.assertTrue(spawned.size()==1&&spawned.get(0).segment()==0,"the old redirect creates no further corpse after callback reload");
            h.assertTrue(mother.greeneBattle().stage()==GreeneBattleCycle.GUARDED&&mother.getHealth()==fixture.healthy.getFloat("Health"),"old transaction cannot expose or damage the newly loaded guarded core");
            for(int slot=0;slot<3;slot++)h.assertTrue(!mother.broken(slot)&&saved.getFloat("EncounterArmor"+slot)==fixture.healthy.getFloat("EncounterArmor"+slot),"old shared damage must not continue into restored armor slot "+slot);
            done(h,mother,spawned.get(0));
        });
    }
}
