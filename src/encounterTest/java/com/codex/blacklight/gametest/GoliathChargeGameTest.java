package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathChargeGameTest {
    private static final class CancellingPig extends Pig {
        private final EncounterMob attacker;
        private final AtomicInteger accepted;
        CancellingPig(Level level,EncounterMob attacker,AtomicInteger accepted){super(EntityType.PIG,level);this.attacker=attacker;this.accepted=accepted;}
        @Override public boolean hurt(DamageSource source,float amount){
            boolean result=super.hurt(source,amount);
            if(result&&source.getEntity()==attacker){accepted.incrementAndGet();attacker.cancelAttack();}
            return result;
        }
    }
    private record Scene(EncounterMob actor,Pig victim,List<ChunkPos> forcedChunks) {}
    private static final double START_Z=9;
    private static final double TARGET_Z=START_Z+4*GoliathPresentation.SCALE;
    private static Scene scene(GameTestHelper h){
        // GameTest keeps the template ticking; the scaled charge carries the
        // entity beyond it. Keep the complete corridor simulation-loaded.
        var first=h.absolutePos(new BlockPos(-9,2,-8));
        var last=h.absolutePos(new BlockPos(25,2,50));
        var forced=new ArrayList<ChunkPos>();
        for(int x=first.getX()>>4;x<=last.getX()>>4;x++)for(int z=first.getZ()>>4;z<=last.getZ()>>4;z++){
            if(h.getLevel().setChunkForced(x,z,true))forced.add(new ChunkPos(x,z));
        }
        // The enlarged source charge can travel more than 27 blocks past its
        // starting point. Give the full sweep solid footing and clear air.
        for(int x=1;x<16;x++)for(int z=-2;z<46;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<25;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var mob=EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(h.getLevel());
        mob.setPos(h.absoluteVec(new Vec3(8,2,START_Z)));mob.setYRot(0);mob.yBodyRot=mob.yBodyRotO=0;
        mob.goalSelector.removeAllGoals(g->true);mob.targetSelector.removeAllGoals(g->true);mob.setPersistenceRequired();mob.setNoGravity(true);h.getLevel().addFreshEntity(mob);
        var pig=h.spawn(EntityType.PIG,new Vec3(8,2,TARGET_Z));pig.setNoAi(true);pig.setNoGravity(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);pig.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        return new Scene(mob,pig,List.copyOf(forced));
    }
    private static void end(GameTestHelper h,Scene scene){
        scene.actor.discard();
        scene.victim.discard();
        for(var chunk:scene.forcedChunks)h.getLevel().setChunkForced(chunk.x,chunk.z,false);
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="goliath_charge_clock",timeoutTicks=115)
    public static void windupUsesRootCurveWithoutBodyDamageAndCancelStopsFutureContact(GameTestHelper h){
        var s=scene(h);Vec3[] start={Vec3.ZERO},cancel={Vec3.ZERO};
        h.runAfterDelay(25,()->{start[0]=s.actor.position();h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"source phase bullrush starts");});
        h.runAfterDelay(35,()->{h.assertTrue(s.actor.move()==EncounterMove.CHARGE,"backward source windup remains active");h.assertTrue(s.actor.getZ()<start[0].z-2*GoliathPresentation.SCALE,"original early root curve retreats instead of fixed forward drive");});
        h.runAfterDelay(65,()->{h.assertTrue(s.victim.getHealth()==1000,"enter/release has no generic whole-body damage");s.actor.cancelAttack();cancel[0]=s.actor.position();});
        h.runAfterDelay(105,()->{h.assertTrue(s.actor.move()==EncounterMove.NONE,"cancelled move stays cancelled");h.assertTrue(s.actor.position().distanceTo(cancel[0])<.05,"cancelled root curve cannot keep driving");h.assertTrue(s.victim.getHealth()==1000,"cancel prevents the later source Hit window");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_charge_contact",timeoutTicks=110)
    public static void actualArticulatedSphereHitsOnceAndCancellationLeavesNoLateHit(GameTestHelper h){
        var s=scene(h);float[] health={1000};
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"source bullrush starts"));
        h.runAfterDelay(84,()->{
            h.assertTrue(s.actor.move()==EncounterMove.CHARGE,"source cycle is active");
            var spheres=GoliathCharge.spheres(s.actor.goliathChargeSnapshot(),s.actor.moveAge(0),s.actor.position(),s.actor.getYRot(),GoliathPresentation.SCALE);
            var wrist=spheres.stream().filter(p->p.joint().equals("Wrist_L")).findFirst().orElseThrow();
            s.victim.setPos(wrist.center().add(0,-.45,0));s.victim.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(90,()->{health[0]=s.victim.getHealth();h.assertTrue(health[0]<1000&&health[0]>900,"real server collision accepts one tuned joint-sphere hit");s.actor.cancelAttack();});
        h.runAfterDelay(100,()->{h.assertTrue(s.victim.getHealth()==health[0],"overlapping source spheres and cancel cannot deliver another blow");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_charge_callback",timeoutTicks=100)
    public static void acceptedHitCallbackCancellationStopsOtherVictimsInTheSameContactPass(GameTestHelper h){
        var s=scene(h);var accepted=new AtomicInteger();var targets=new CancellingPig[2];
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"source bullrush starts"));
        h.runAfterDelay(84,()->{
            var wrist=GoliathCharge.spheres(s.actor.goliathChargeSnapshot(),s.actor.moveAge(0),s.actor.position(),s.actor.getYRot(),GoliathPresentation.SCALE)
                .stream().filter(p->p.joint().equals("Wrist_L")).findFirst().orElseThrow();
            s.victim.setPos(s.actor.position().add(0,0,30));
            for(int i=0;i<targets.length;i++){
                var pig=new CancellingPig(h.getLevel(),s.actor,accepted);pig.setNoAi(true);pig.setNoGravity(true);
                pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);
                pig.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                pig.setPos(wrist.center().add(i*.05,-.45,0));targets[i]=pig;h.getLevel().addFreshEntity(pig);
            }
        });
        h.runAfterDelay(90,()->{
            h.assertTrue(accepted.get()==1,"the first hit cancels before stale spheres can contact a second target; count="+accepted.get()+", phase="+s.actor.goliathChargeSnapshot().phase());
            h.assertTrue(s.actor.move()==EncounterMove.NONE&&s.actor.goliathChargeSnapshot().phase()==-1,"callback clears action and synchronized source phase");
            for(var target:targets)if(target!=null)target.discard();end(h,s);
        });
    }
    @GameTest(template="blackwatch_arena",batch="goliath_charge_recharge",timeoutTicks=460)
    public static void npcChargeCompletesTheNormalSourceExitThenGetsItsFullRecharge(GameTestHelper h){
        var s=scene(h);boolean[] started={false},finished={false};long[] began={0};
        h.runAfterDelay(25,()->{h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"NPC-targeted charge starts");started[0]=true;began[0]=h.getLevel().getGameTime();});
        h.onEachTick(()->{
            if(!started[0]||finished[0]||s.actor.move()!=EncounterMove.NONE)return;finished[0]=true;
            long age=h.getLevel().getGameTime()-began[0];
            h.assertTrue(age>=312&&age<=313,"one cycle, return and taunt finish before recharge; age="+age+", exposed="+s.actor.exposed());
            h.assertTrue(s.actor.goliathChargeSnapshot().phase()==-1,"natural finish clears synchronized phase");
            var saved=new net.minecraft.nbt.CompoundTag();s.actor.saveWithoutId(saved);
            int remaining=saved.getCompound("EncounterRecharge").getInt("CHARGE");
            h.assertTrue(remaining>=84&&remaining<=85,"85-tick recharge starts at actual completion (allow one observation tick)");
            h.runAfterDelay(remaining-1,()->h.assertTrue(!s.actor.moveReady(EncounterMove.CHARGE),"cannot recharge before actual-finish deadline"));
            h.runAfterDelay(remaining,()->{h.assertTrue(s.actor.moveReady(EncounterMove.CHARGE),"recharge completes at actual-finish deadline");end(h,s);});
        });
    }
    @GameTest(template="blackwatch_arena",batch="goliath_charge_recovery",timeoutTicks=360)
    public static void targetRemovalAfterContactDoesNotCutOffSourceTauntOrInventANewTarget(GameTestHelper h){
        var s=scene(h);
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"source charge starts"));
        h.runAfterDelay(95,()->{h.assertTrue(s.actor.goliathChargeSnapshot().phase()==4,"exit follows the contact cycle; phase="+s.actor.goliathChargeSnapshot().phase()+", exposed="+s.actor.exposed());s.victim.discard();});
        h.runAfterDelay(115,()->h.assertTrue(s.actor.move()==EncounterMove.CHARGE&&s.actor.goliathChargeSnapshot().phase()>=7,"target removal preserves the committed source recovery"));
        h.runAfterDelay(340,()->{h.assertTrue(s.actor.move()==EncounterMove.NONE&&s.actor.goliathChargeSnapshot().phase()==-1,"finite taunt completes without a replacement target");end(h,s);});
    }
    @GameTest(template="blackwatch_arena",batch="goliath_taunt_cover",timeoutTicks=275)
    public static void handAlreadyAcrossWallBeforeActiveWindowCannotHurtThroughIt(GameTestHelper h){
        var s=scene(h);var replaced=new java.util.HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        h.runAfterDelay(25,()->h.assertTrue(s.actor.begin(EncounterMove.CHARGE,s.victim),"source charge starts"));
        h.runAfterDelay(246,()->{
            var snapshot=s.actor.goliathChargeSnapshot();float local=s.actor.moveAge(0)-snapshot.phaseStartAge();
            h.assertTrue(snapshot.phase()==11&&local>=16&&local<=18,"set wall during inactive recovery windup, before local tick18.6667");
            var origin=s.actor.position();float yaw=s.actor.getYRot(),sample=snapshot.phaseStartAge()+19;
            var wrist=GoliathTaunt.windows(snapshot,sample,origin,yaw,origin,yaw,GoliathPresentation.SCALE).stream().filter(w->w.id()==1100).findFirst().orElseThrow()
                .sweeps().stream().filter(p->p.joint().equals("Wrist_L")).findFirst().orElseThrow();
            var chest=GoliathTaunt.bodyAnchor(snapshot,sample,origin,yaw,GoliathPresentation.SCALE);var difference=wrist.to().subtract(chest);
            boolean xAxis=Math.abs(difference.x)>Math.abs(difference.z);double direction=xAxis?difference.x:difference.z;
            var body=s.actor.getBoundingBox();double low=xAxis?body.minX:body.minZ,high=xAxis?body.maxX:body.maxZ;
            // Use the nearest voxel column with at least half a block of body clearance.
            int plane=direction>0?(int)Math.ceil(high+.5):(int)Math.floor(low-.5)-1;double hand=xAxis?wrist.to().x:wrist.to().z;
            h.assertTrue(direction>0?hand-wrist.radius()>plane+1:hand+wrist.radius()<plane,"entire initial wrist sphere is beyond wall; this is not just sweep clipping");
            double chestPerp=xAxis?chest.z:chest.x,handPerp=xAxis?wrist.to().z:wrist.to().x;
            int minP=(int)Math.floor(Math.min(chestPerp,handPerp))-4,maxP=(int)Math.ceil(Math.max(chestPerp,handPerp))+4;
            int minY=(int)Math.floor(Math.min(chest.y,wrist.to().y))-4,maxY=(int)Math.ceil(Math.max(chest.y,wrist.to().y))+4;
            for(int p=minP;p<=maxP;p++)for(int y=minY;y<=maxY;y++){
                var at=new net.minecraft.core.BlockPos(xAxis?plane:p,y,xAxis?p:plane);
                h.assertTrue(!body.inflate(.5).intersects(new net.minecraft.world.phys.AABB(at)),"wall must not cancel the move by colliding with the actor body");
                replaced.put(at,h.getLevel().getBlockState(at));h.getLevel().setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
            }
            s.victim.setPos(wrist.to().add(0,-.45,0));s.victim.setHealth(1000);s.victim.invulnerableTime=0;s.victim.setDeltaMovement(Vec3.ZERO);
        });
        h.runAfterDelay(252,()->{
            h.assertTrue(s.actor.move()==EncounterMove.CHARGE&&s.actor.goliathChargeSnapshot().phase()==11,"cover result cannot be explained by cancelled or obstructed root movement");
            h.assertTrue(s.victim.getHealth()==1000,"chest-to-hand cover protects a target even when the active hand starts fully behind the wall");
            replaced.forEach((at,state)->h.getLevel().setBlockAndUpdate(at,state));s.actor.cancelAttack();end(h,s);
        });
    }
}
