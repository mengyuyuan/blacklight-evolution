package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Written only: real receiver and persisted shared receipts, not a replay of
 * clock arithmetic. Keep this source set out of release builds. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneEmergenceGameTest {
    private static EncounterMob actor(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.setBlock(x,1,z,Blocks.STONE);
        var mob=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=mob.greeneBattle().save();standing.putBoolean("OpeningPending",false);mob.greeneBattle().load(standing);

        mob.setPos(h.absoluteVec(new Vec3(8,2,8)));mob.setNoAi(true);mob.setPersistenceRequired();h.getLevel().addFreshEntity(mob);return mob;
    }
    private static Pig target(GameTestHelper h,Vec3 at){
        var pig=h.spawn(EntityType.PIG,at);pig.setNoAi(true);pig.setNoGravity(true);pig.noPhysics=true;
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);pig.setHealth(300);
        pig.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return pig;
    }
    private static void begin(EncounterMob mob,double depth){
        double surface=mob.getY();mob.setPos(mob.getX(),surface-depth,mob.getZ());
        var tag=mob.greeneBattle().save();tag.putInt("Stage",GreeneBattleCycle.EMERGING);tag.putInt("Age",0);
        tag.putDouble("SurfaceY",surface);tag.putDouble("GoalY",surface);tag.putInt("EmergeElapsed",-1);
        var clock=new GreeneEmergenceClock();clock.reset();var hit=new CompoundTag();hit.put("Clock",clock.save());tag.put("EmergenceHit",hit);
        mob.greeneBattle().load(tag);
    }
    private static int receipts(EncounterMob mob){return mob.greeneBattle().save().getCompound("EmergenceHit").getList("Receipts",10).size();}
    private static void done(GameTestHelper h,EncounterMob mob,Pig pig){mob.discard();pig.discard();h.succeed();}

    @GameTest(template="blackwatch_arena",timeoutTicks=45)
    public static void earlierSmallHitConsumesLargeHitAndSurvivesSameObjectReload(GameTestHelper h){
        var mob=actor(h);var pig=target(h,new Vec3(9,2,8));float[] after={0};
        h.runAfterDelay(5,()->begin(mob,0));
        h.runAfterDelay(8,()->{
            h.assertTrue(pig.getHealth()<300&&pig.getHealth()>280&&receipts(mob)==1,"R3 contacts first and records one shared victim");
            after[0]=pig.getHealth();pig.invulnerableTime=0;
            var saved=new CompoundTag();mob.saveWithoutId(saved);mob.load(saved);
            h.assertTrue(receipts(mob)==1,"same-object reload preserves the accepted victim receipt");
        });
        h.runAfterDelay(35,()->{h.assertTrue(pig.getHealth()==after[0],"R20 cannot hit the prior R3 victim after invulnerability expires or reload");done(h,mob,pig);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=45)
    public static void rejectedReceiverStillConsumesBothTracks(GameTestHelper h){
        var mob=actor(h);var pig=target(h,new Vec3(9,2,8));pig.setInvulnerable(true);
        h.runAfterDelay(5,()->begin(mob,0));
        h.runAfterDelay(8,()->{
            h.assertTrue(pig.getHealth()==300&&receipts(mob)==1,"post-query invulnerability rejects damage after consuming its receipt");
            pig.setInvulnerable(false);pig.invulnerableTime=0;
        });
        h.runAfterDelay(35,()->{h.assertTrue(pig.getHealth()==300,"later eligibility cannot turn the same emergence into another hit");done(h,mob,pig);});
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=35)
    public static void undergroundRootCanHitTheSurfaceWithoutAnEyeRay(GameTestHelper h){
        var mob=actor(h);var pig=target(h,new Vec3(13,2,8));
        h.runAfterDelay(5,()->begin(mob,GreeneBattleCycle.SOURCE_DEPTH*GreeneMotion.table().sourceUnitBlocks()));
        h.runAfterDelay(18,()->{
            h.assertTrue(mob.getY()<h.absoluteVec(new Vec3(8,2,8)).y,"root is still below the stone ground during R20");
            h.assertTrue(pig.getHealth()<300&&receipts(mob)==1,"source overlap reaches surface receiver; stone does not become an invented eye-ray gate");
            done(h,mob,pig);
        });
    }
}
