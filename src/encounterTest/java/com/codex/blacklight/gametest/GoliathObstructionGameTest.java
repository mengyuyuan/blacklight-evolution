package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Actual entity movement, not manually setting the source phase. These cases
 * test the explicit Minecraft obstruction policy, not native P2 stun timing. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathObstructionGameTest {
    private record Scene(EncounterMob actor,Pig target) {}
    private static final double START_Z=9;
    private static final double TARGET_Z=START_Z+4*GoliathPresentation.SCALE;
    private static Scene scene(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<24;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(8,2,START_Z)));mob.setYRot(0);mob.yBodyRot=mob.yBodyRotO=0;
        mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);
        mob.setNoGravity(true);mob.setPersistenceRequired();h.getLevel().addFreshEntity(mob);
        var pig=h.spawn(EntityType.PIG,new Vec3(8,2,TARGET_Z));pig.setNoAi(true);pig.setNoGravity(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);
        pig.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        return new Scene(mob,pig);
    }
    private static void rearWall(GameTestHelper h){for(int x=4;x<=11;x++)for(int y=2;y<=13;y++)h.setBlock(x,y,4,Blocks.STONE);}
    private static void finish(GameTestHelper h,Scene s){s.actor.discard();s.target.discard();h.succeed();}
    @GameTest(template="blackwatch_arena",batch="goliath_windup_wall",timeoutTicks=100)
    public static void retreatClipsAgainstTheRearWallWithoutCancellingTheRealCharge(GameTestHelper h){
        var s=scene(h);rearWall(h);boolean[] checked={false};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.target),"real charge starts with room in front"));
        h.runAfterDelay(40,()->{
            double expected=h.absoluteVec(new Vec3(8,2,5)).z+s.actor.getBbWidth()/2;
            h.assertTrue(s.actor.goliathChargeSnapshot().phase()==0&&s.actor.move()==EncounterMove.CHARGE&&!s.actor.exposed(),"backward windup hitting a wall must not manufacture a charge impact");
            h.assertTrue(Math.abs(s.actor.getZ()-expected)<.08,"real collision clips source retreat at the rear wall");
            checked[0]=true;
        });
        h.onEachTick(()->{
            if(checked[0]&&s.actor.goliathChargeSnapshot().phase()==3){
                checked[0]=false;h.assertTrue(!s.actor.exposed(),"windup/release/enter survive the clipped retreat");
                s.actor.cancelAttack();finish(h,s);
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="goliath_active_wall",timeoutTicks=125)
    public static void actualForwardChargeCollisionStillExposesTheBoss(GameTestHelper h){
        var s=scene(h);boolean[] wall={false},done={false};int[] wallZ={0};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.target),"unobstructed charge starts"));
        h.onEachTick(()->{
            if(done[0])return;
            if(!wall[0]&&s.actor.goliathChargeSnapshot().phase()==3){
                var body=s.actor.getBoundingBox();int z=(int)Math.ceil(body.maxZ+.1);
                for(int x=(int)Math.floor(body.minX)-2;x<=(int)Math.ceil(body.maxX)+2;x++)
                    for(int y=(int)Math.floor(body.minY);y<=(int)Math.ceil(body.maxY)+2;y++)
                        h.getLevel().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
                wallZ[0]=z;wall[0]=true;
            }
            if(wall[0]&&s.actor.exposed()){
                done[0]=true;h.assertTrue(s.actor.move()==EncounterMove.NONE&&s.actor.goliathChargeSnapshot().phase()==-1,"physical impact during cycle clears the committed charge");
                var center=BlockPos.containing(s.actor.getX(),s.actor.getY()+1.6,wallZ[0]);
                h.assertTrue(h.getLevel().getBlockState(center).isAir(),"the collision opens the middle of the stone wall");
                h.assertTrue(h.getLevel().getBlockState(center.above(5)).is(Blocks.STONE),"untouched upper storey remains");
                finish(h,s);
            }
        });
    }
    @GameTest(template="blackwatch_arena",batch="goliath_clipped_cancel",timeoutTicks=105)
    public static void cancellingClippedWindupCannotResumeRootMotionOrLateHits(GameTestHelper h){
        var s=scene(h);rearWall(h);Vec3[] stopped={Vec3.ZERO};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.target),"charge starts"));
        h.runAfterDelay(40,()->{
            h.assertTrue(s.actor.move()==EncounterMove.CHARGE&&!s.actor.exposed(),"this cancellation must exercise clipped live windup");
            s.actor.cancelAttack();stopped[0]=s.actor.position();
        });
        h.runAfterDelay(95,()->{
            h.assertTrue(s.actor.move()==EncounterMove.NONE&&s.actor.goliathChargeSnapshot().phase()==-1,"cancelled source phases never resume");
            h.assertTrue(s.actor.position().distanceTo(stopped[0])<.05,"no queued retreat/release displacement after cancel");
            h.assertTrue(s.target.getHealth()==1000,"cancelled windup cannot later deliver the charge Hit");
            finish(h,s);
        });
    }
}
