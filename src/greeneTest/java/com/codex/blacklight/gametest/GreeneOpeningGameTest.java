package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Fresh-world entry checks, deliberately separate from existing-state combat
 * fixtures. No direct beginEmerge call or artificial phase load starts them. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneOpeningGameTest {
    private static void arena(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
    }
    private static EncounterMob actor(GameTestHelper h){
        var mob=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(8,2,8)));mob.setNoAi(true);mob.setPersistenceRequired();return mob;
    }
    private static CompoundTag save(EncounterMob mob){var tag=new CompoundTag();mob.saveWithoutId(tag);return tag;}
    private static void add(GameTestHelper h,EncounterMob mob){h.assertTrue(h.getLevel().addFreshEntity(mob),"actual entity joins the world");}
    private static void done(GameTestHelper h,EncounterMob... mobs){for(var mob:mobs)mob.discard();h.succeed();}
    private static void existing(EncounterMob mob){
        var state=mob.greeneBattle().save();state.putBoolean("OpeningPending",false);mob.greeneBattle().load(state);
    }

    @GameTest(template="blackwatch_arena",timeoutTicks=195)
    public static void freshSpawnRisesFromItsPlacedSurfaceWithoutRepeatWaveChildren(GameTestHelper h){
        arena(h);var mob=actor(h);Vec3 placed=mob.position();add(h,mob);
        h.assertTrue(mob.greeneBattle().openingPending()&&!mob.greeneBattle().targetable()&&!mob.greeneBattle().canAttack(),"newly constructed encounter cannot be struck or attack before entry");
        h.assertTrue(!mob.hurtPart(0,mob.damageSources().generic(),10000),"pre-entry damage cannot bypass the first rise");
        h.runAfterDelay(3,()->{
            var cycle=mob.greeneBattle();
            h.assertTrue(!cycle.openingPending()&&cycle.stage()==GreeneBattleCycle.EMERGE_WAIT,"first observed tick owns a single source spawn-prop wait");
            h.assertTrue(Math.abs(mob.getY()-(placed.y-GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks()))<.00001,"entry uses final placed surface, not constructor origin");
            h.assertTrue(mob.getX()==placed.x&&mob.getZ()==placed.z&&mob.noPhysics&&mob.isNoGravity(),"only vertical source movement temporarily owns root physics");
        });
        h.runAfterDelay(40,()->{
            var cycle=mob.greeneBattle();
            h.assertTrue(cycle.stage()==GreeneBattleCycle.EMERGING&&cycle.targetable()&&!cycle.coreDamageable(),"after source movement, parts become targetable while core stays guarded");
            h.assertTrue(Math.abs(mob.getY()-placed.y)<.00001,"actual root returns to placed ground");
            h.assertTrue(!cycle.canAttack(),"early emergence has not reached the source attack handoff");
        });
        h.runAfterDelay(180,()->{
            var cycle=mob.greeneBattle();
            h.assertTrue(cycle.stage()==GreeneBattleCycle.GUARDED&&cycle.canAttack()&&cycle.armourWave()==1&&cycle.waveCount()==1,"first rise completes without advancing exposure or armor waves");
            h.assertTrue(!mob.noPhysics&&!mob.isNoGravity(),"original gravity and collision flags return after emergence");
            h.assertTrue(h.getLevel().getEntities(mob,mob.getBoundingBox().inflate(50),e->e.getPersistentData().hasUUID("BlacklightGreeneOwner")&&e.getPersistentData().getUUID("BlacklightGreeneOwner").equals(mob.getUUID())).isEmpty(),"justCore opening creates neither repeat Hydras nor underground Hunters");
            done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=15)
    public static void minimalSummonNbtStillGetsFreshEntry(GameTestHelper h){
        arena(h);var tag=new CompoundTag();tag.putString("id","blacklight:elizabeth_greene");tag.putBoolean("NoAI",true);
        var entity=EntityType.loadEntityRecursive(tag,h.getLevel(),e->e);
        h.assertTrue(entity instanceof EncounterMob,"real summon deserializer creates Greene");var mob=(EncounterMob)entity;
        mob.setPos(h.absoluteVec(new Vec3(8,2,8)));double surface=mob.getY();
        h.assertTrue(mob.greeneBattle().openingPending(),"minimal spawn data is not mistaken for an old encounter save");add(h,mob);
        h.runAfterDelay(3,()->{
            h.assertTrue(mob.greeneBattle().stage()==GreeneBattleCycle.EMERGE_WAIT&&mob.getY()<surface-8,"summon starts the same first rise after placement");done(h,mob);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=20)
    public static void oldSavedEncountersDoNotReplayOpening(GameTestHelper h){
        arena(h);var source=actor(h);existing(source);var old=save(source);
        old.getCompound("GreeneBattle").putInt("Version",3);old.getCompound("GreeneBattle").remove("OpeningPending");
        var modern=actor(h);modern.load(old);add(h,modern);
        var legacy=actor(h);old.remove("GreeneBattle");old.remove("UUID");for(int slot=0;slot<3;slot++)old.putFloat("EncounterArmor"+slot,90);
        legacy.load(old);legacy.setPos(legacy.position().add(2,0,0));add(h,legacy);
        double y=source.getY();h.runAfterDelay(5,()->{
            for(var mob:new EncounterMob[]{modern,legacy})h.assertTrue(!mob.greeneBattle().openingPending()&&mob.greeneStage()==GreeneBattleCycle.GUARDED&&Math.abs(mob.getY()-y)<.01,"both previous-cycle and legacy saves stay in their existing guarded encounter");
            done(h,source,modern,legacy);
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=25)
    public static void pendingSnapshotDefersPlacementUntilItsFirstRealTick(GameTestHelper h){
        arena(h);var original=actor(h);var snapshot=save(original);var restored=actor(h);restored.load(snapshot);
        h.assertTrue(restored.greeneBattle().openingPending(),"a genuine unstarted entry survives save/load");
        restored.setPos(h.absoluteVec(new Vec3(10,2,9)));Vec3 finalPlacement=restored.position();
        h.runAfterDelay(8,()->{
            h.assertTrue(restored.position().equals(finalPlacement)&&restored.greeneBattle().openingPending(),"unadded actor has no wall-time entry catch-up");add(h,restored);
            h.runAfterDelay(3,()->{
                h.assertTrue(restored.getX()==finalPlacement.x&&restored.getZ()==finalPlacement.z&&restored.getY()<finalPlacement.y-8,"pending load uses its actual placement once");done(h,original,restored);
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=200)
    public static void activeOpeningReloadKeepsPoseDamageAndRemainingClock(GameTestHelper h){
        arena(h);var mob=actor(h);add(h,mob);
        h.runAfterDelay(42,()->{
            h.assertTrue(mob.hurtPart(0,mob.damageSources().generic(),10),"exposed entry appendage can take real damage");
            var snapshot=save(mob);var before=mob.greeneBattle().save();Vec3 position=mob.position();var pose=GreeneMotion.pose(mob,0);
            mob.load(snapshot.copy());
            h.assertTrue(!mob.greeneBattle().openingPending()&&mob.position().equals(position)&&mob.greeneBattle().save().getInt("Age")==before.getInt("Age"),"active reload does not re-enter underground or reset observed phase age");
            var after=GreeneMotion.pose(mob,0);for(int i=0;i<pose.length;i++)h.assertTrue(java.util.Arrays.equals(pose[i],after[i]),"source pose survives active reload on bone "+i);
            h.runAfterDelay(138,()->{
                h.assertTrue(mob.greeneStage()==GreeneBattleCycle.GUARDED&&save(mob).getFloat("EncounterArmor0")==snapshot.getFloat("EncounterArmor0"),"remaining opening completes without regenerating already damaged armor");
                h.assertTrue(!mob.noPhysics&&!mob.isNoGravity(),"reloaded entry releases its original gravity and collision lease");done(h,mob);
            });
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=50)
    public static void aLowCeilingCannotReplaceThePlacedFloor(GameTestHelper h){
        arena(h);h.setBlock(8,3,8,Blocks.STONE);var mob=actor(h);double floor=mob.getY();add(h,mob);
        h.runAfterDelay(3,()->{
            h.assertTrue(Math.abs(mob.greeneBattle().save().getDouble("GoalY")-floor)<.00001,"first entry selects floor below placed feet, not roof above them");
        });
        h.runAfterDelay(40,()->{
            h.assertTrue(Math.abs(mob.getY()-floor)<.00001,"source upward movement does not relocate the actor onto a low ceiling");done(h,mob);
        });
    }
}
