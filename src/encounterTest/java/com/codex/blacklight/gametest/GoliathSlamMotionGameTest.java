package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathSlamMotionGameTest {
    private record Scene(EncounterMob actor,Pig target) {}
    private static final double START_Z=9;
    private static final double TARGET_DISTANCE=4*GoliathPresentation.SCALE;
    private static final Vec3 RETREAT=new Vec3(-.9578891847057928,0,-3.9467693161663506).scale(GoliathPresentation.SCALE);
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var actor=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        actor.setPos(h.absoluteVec(new Vec3(8,2,START_Z)));actor.setYRot(0);actor.yBodyRot=actor.yBodyRotO=0;
        actor.goalSelector.removeAllGoals(g->true);actor.targetSelector.removeAllGoals(g->true);
        actor.setNoGravity(true);actor.setPersistenceRequired();h.getLevel().addFreshEntity(actor);
        var target=h.spawn(EntityType.PIG,new Vec3(8,2,START_Z+TARGET_DISTANCE));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        // Keep the target directly ahead so the original AutoTarget turn gate
        // does not rotate the horizontal-root fixture during its release.
        h.onEachTick(()->{if(actor.isAlive()&&target.isAlive()){target.setPos(actor.position().add(0,0,TARGET_DISTANCE));target.setDeltaMovement(Vec3.ZERO);}});
        return new Scene(actor,target);
    }
    private static void wall(GameTestHelper h,boolean present){for(int x=3;x<=12;x++)for(int y=2;y<=13;y++)h.setBlock(x,y,4,present?Blocks.STONE:Blocks.AIR);}
    private static void finish(GameTestHelper h,Scene s){s.actor.discard();s.target.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="goliath_slam_root",timeoutTicks=125)
    public static void realFrontSlamRetreatsThenReturnsWithoutAdvancingTheDamageWindow(GameTestHelper h){
        var s=scene(h);Vec3[] start={Vec3.ZERO};
        h.runAfterDelay(25,()->{start[0]=s.actor.position();h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.target),"source front slam begins");});
        h.runAfterDelay(45,()->{
            h.assertTrue(s.actor.position().subtract(start[0]).distanceTo(RETREAT)<.12,"real entity follows the source retreat, not an in-place model");
            h.assertTrue(s.target.getHealth()==1000,"moving the actor must not make windup damaging");
        });
        h.runAfterDelay(62,()->h.assertTrue(s.target.getHealth()==1000,"source Hit is still tick38"));
        h.runAfterDelay(67,()->{
            h.assertTrue(s.actor.position().distanceTo(start[0])<.15,"source release returns on unobstructed ground");
            h.assertTrue(Math.abs(s.actor.getY()-start[0].y)<.001,"source rootY is not accumulated into physical height");
            h.assertTrue(s.target.getHealth()<1000&&s.target.getHealth()>960,"unchanged source sphere accepts one tuned contact");
        });
        h.runAfterDelay(110,()->{h.assertTrue(s.actor.move()==EncounterMove.NONE,"original recovery completes");finish(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_slam_root_wall",timeoutTicks=125)
    public static void clippedRetreatIsConsumedAndOpeningTheWallCannotRepayIt(GameTestHelper h){
        var s=scene(h);wall(h,true);Vec3[] clipped={Vec3.ZERO},start={s.actor.position()};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.target),"front slam begins beside rear wall"));
        h.runAfterDelay(45,()->{
            double stoppedZ=h.absoluteVec(new Vec3(0,0,5)).z+s.actor.getBbWidth()/2;
            h.assertTrue(s.actor.move()==EncounterMove.HAMMER&&!s.actor.exposed(),"clipped retreat does not become charging exposure");
            h.assertTrue(Math.abs(s.actor.getZ()-stoppedZ)<.1,"real movement respects the wall");
            clipped[0]=s.actor.position();wall(h,false);
        });
        h.runAfterDelay(50,()->h.assertTrue(s.actor.position().distanceTo(clipped[0])<.08,"no stored retreat is repaid while the source curve holds"));
        h.runAfterDelay(67,()->{
            h.assertTrue(s.actor.getZ()>start[0].z+2.8*GoliathPresentation.SCALE,"release uses only its next source differences, not absolute start-position chasing");
            h.assertTrue(!s.actor.exposed()&&s.actor.move()==EncounterMove.HAMMER,"wall clipping preserves recovery");
        });
        h.runAfterDelay(110,()->finish(h,s));
    }
    @GameTest(template="blackwatch_arena",batch="goliath_slam_root_load",timeoutTicks=195)
    public static void loadingDuringWindupCancelsOldTravelAndNextActionStartsAtFrameZero(GameTestHelper h){
        var s=scene(h);Vec3[] stopped={Vec3.ZERO},next={Vec3.ZERO};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.target),"first front slam begins"));
        h.runAfterDelay(40,()->{
            var tag=new CompoundTag();s.actor.saveWithoutId(tag);s.actor.load(tag);stopped[0]=s.actor.position();
            h.assertTrue(s.actor.move()==EncounterMove.NONE,"readAdditionalSaveData cancels an interrupted action");
        });
        h.runAfterDelay(105,()->{
            h.assertTrue(s.actor.position().distanceTo(stopped[0])<.05,"old release must not move a reloaded actor");
            h.assertTrue(s.target.getHealth()==1000,"no late pre-load Hit");
        });
        h.runAfterDelay(150,()->{
            // Reposition inside the cleared fixture: the cancelled retreat is
            // deliberately not restored by the production movement controller.
            s.actor.setPos(h.absoluteVec(new Vec3(8,2,START_Z)));s.actor.setDeltaMovement(Vec3.ZERO);
            s.actor.setYRot(0);s.actor.yBodyRot=s.actor.yBodyRotO=0;s.target.setPos(s.actor.position().add(0,0,TARGET_DISTANCE));
            next[0]=s.actor.position();h.assertTrue(s.actor.begin(EncounterMove.HAMMER,s.target),"after real saved cooldown, a fresh attack starts");
        });
        h.runAfterDelay(170,()->{
            h.assertTrue(s.actor.position().subtract(next[0]).distanceTo(RETREAT)<.12,"new action does not reuse old lastAge or accumulated root offset");
            s.actor.cancelAttack();finish(h,s);
        });
    }
}
