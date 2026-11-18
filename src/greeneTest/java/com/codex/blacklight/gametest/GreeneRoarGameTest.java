package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Pending real selector/save-load regressions. No calls to begin(HOWL), no
 * synthetic matured timer, and no replacement of the actual FightGoal. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneRoarGameTest {
    private static EncounterMob actor(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<16;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mob=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // This is an already standing battle. Fresh entry has its own tests.
        var state=mob.greeneBattle().save();state.putBoolean("OpeningPending",false);mob.greeneBattle().load(state);
        mob.setPos(h.absoluteVec(new Vec3(8,2,4)));mob.setPersistenceRequired();mob.restrictTo(mob.blockPosition(),12);
        h.getLevel().addFreshEntity(mob);return mob;
    }
    private static LivingEntity target(GameTestHelper h){
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,12));target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);return target;
    }
    private static CompoundTag save(EncounterMob mob){var tag=new CompoundTag();mob.saveWithoutId(tag);return tag;}

    @GameTest(template="blackwatch_arena",timeoutTicks=380)
    public static void closeEnemyEarnsRoarWhileOrdinaryShootingContinues(GameTestHelper h){
        var mob=actor(h);var victim=target(h);boolean[] done={false},shot={false};
        h.onEachTick(()->{
            if(done[0])return;
            if(mob.greeneShots()>0)shot[0]=true;
            if(mob.tickCount<120)h.assertTrue(mob.move()!=EncounterMove.HOWL,"proximity alone cannot immediately select roar");
            if(mob.move()!=EncounterMove.HOWL)return;
            done[0]=true;
            h.assertTrue(mob.getTarget()==victim,"real acquisition supplies the on-foot target");
            h.assertTrue(shot[0],"the concurrent dwell does not freeze ordinary firing");
            var clock=save(mob).getCompound("GreeneRoar");
            h.assertTrue(clock.getInt("RechargeTicks")>150&&clock.getInt("RechargeTicks")<=300,"source recharge is already running during windup");
            mob.discard();victim.discard();h.succeed();
        });
    }
    @GameTest(template="blackwatch_arena",timeoutTicks=380)
    public static void unloadPreservesOnlyObservedDwellWithoutCatchUp(GameTestHelper h){
        var mob=actor(h);var victim=target(h);EncounterMob[] restored={null};int[] saved={0},first={0};boolean[] done={false};
        h.runAfterDelay(70,()->{
            var tag=save(mob);saved[0]=tag.getCompound("GreeneRoar").getInt("CloseTicks");
            h.assertTrue(saved[0]>0&&saved[0]<120,"save captures an actual partly accrued dwell");
            mob.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            restored[0]=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());restored[0].load(tag);
        });
        h.runAfterDelay(140,()->{
            var copy=restored[0];
            h.assertTrue(save(copy).getCompound("GreeneRoar").getInt("CloseTicks")==saved[0],"unadded restored object cannot accrue seventy unloaded ticks");
            copy.restrictTo(copy.blockPosition(),12);copy.setTarget(victim);
            h.assertTrue(h.getLevel().addFreshEntity(copy),"restored real actor joins the level");first[0]=copy.tickCount;
        });
        h.onEachTick(()->{
            var copy=restored[0];if(done[0]||copy==null||copy.move()!=EncounterMove.HOWL)return;
            done[0]=true;
            h.assertTrue(copy.tickCount-first[0]>=120-saved[0],"first post-load roar waits for the unobserved remainder");
            copy.discard();victim.discard();h.succeed();
        });
    }
}
