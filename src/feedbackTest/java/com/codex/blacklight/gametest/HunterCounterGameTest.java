package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.CreatureAttack;
import com.codex.blacklight.combat.HunterCounterMotion;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HunterCounterGameTest {
    private record Scene(BlacklightHunter hunter,Zombie prey) implements AutoCloseable {
        public void close(){hunter.discard();prey.discard();}
    }
    private static Scene scene(GameTestHelper h,boolean autonomous){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<9;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var hunter=ModEntities.BLACKLIGHT_HUNTER.get().create(h.getLevel());
        hunter.setPos(h.absoluteVec(new Vec3(5.5,2,5.5)));hunter.setOnGround(true);hunter.setPersistenceRequired();
        // The stationary zombie is assigned below. Other residents must not replace it while the
        // normal swipe goal chooses and commits the counter, then evaluates its barrage release.
        hunter.targetSelector.removeAllGoals(g->true);
        if(!autonomous)hunter.goalSelector.removeAllGoals(g->true);
        // Native forward travel now reaches this standing body before release.
        var prey=EntityType.ZOMBIE.create(h.getLevel());prey.setPos(hunter.position().add(0,0,2.1));prey.setNoAi(true);prey.setPersistenceRequired();
        prey.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);prey.setHealth(10000);
        prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        prey.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        h.getLevel().addFreshEntity(prey);h.getLevel().addFreshEntity(hunter);
        if(autonomous)hunter.setTarget(prey);
        return new Scene(hunter,prey);
    }
    @GameTest(template="wall_test",timeoutTicks=110,batch="hunter_counter_chain")
    public static void sourceLeftOpeningContinuesOnceWithoutRepeatingTheBarrageWindup(GameTestHelper h){
        var s=scene(h,false);h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"source counter starts");
        int[] openingHits={0},releaseHits={0};boolean[] chained={false};float[] health={s.prey.getHealth()};
        var previous=new CreatureAttack[]{CreatureAttack.COUNTER};
        h.onEachTick(()->{
            s.prey.invulnerableTime=0;var move=s.hunter.attackMove();
            if(move==CreatureAttack.BARRAGE&&!chained[0]){
                chained[0]=true;h.assertTrue(previous[0]==CreatureAttack.COUNTER&&s.hunter.counterBarrage(),"same committed chain");
                h.assertTrue(s.hunter.attackAge(0)==CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK,"release begins at source barrage frame 3");
                h.assertTrue(s.prey.getHealth()==health[0],"transition tick does not run a second strike");
            }
            if(s.prey.getHealth()<health[0]){
                h.assertTrue(move==CreatureAttack.COUNTER||move==CreatureAttack.BARRAGE,"damage stays inside its source action");
                float age=s.hunter.attackAge(0);
                h.assertTrue(java.util.Arrays.stream(move.windows()).anyMatch(w->w.overlaps(age-1,age)),"damage follows the active wrist window");
                if(move==CreatureAttack.COUNTER)openingHits[0]++;else releaseHits[0]++;
            }
            previous[0]=move;health[0]=s.prey.getHealth();
        });
        h.runAfterDelay(92,()->{try(s){
            h.assertTrue(openingHits[0]==1&&chained[0]&&releaseHits[0]>0,"one left opening and actual subsequent claw contacts");
            h.assertTrue(!s.hunter.committedAttack()&&!s.hunter.counterBarrage(),"whole chain finishes");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=65,batch="hunter_counter_dodge")
    public static void movingBehindTheCommittedHunterPreventsRetargetedContinuation(GameTestHelper h){
        var s=scene(h,false);h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"counter starts");
        float yaw=s.hunter.getYRot();float[] health={s.prey.getHealth()};
        h.runAfterDelay(25,()->{health[0]=s.prey.getHealth();s.prey.setPos(s.hunter.position().add(0,0,-1.5));});
        h.runAfterDelay(45,()->{try(s){
            h.assertTrue(!s.hunter.committedAttack()&&!s.hunter.counterBarrage(),"unreachable committed victim cancels release");
            h.assertTrue(s.prey.getHealth()==health[0],"no attack through its back");
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(s.hunter.getYRot()-yaw))<.01,"no snap turn at the stage boundary");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=105,batch="hunter_counter_cancel")
    public static void noAiInterruptionCannotLeaveADelayedBarrage(GameTestHelper h){
        var s=scene(h,false);float health=s.prey.getHealth();h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"counter starts");
        h.runAfterDelay(10,()->s.hunter.setNoAi(true));
        h.runAfterDelay(20,()->s.hunter.setNoAi(false));
        h.runAfterDelay(90,()->{try(s){
            h.assertTrue(!s.hunter.committedAttack()&&!s.hunter.counterBarrage()&&s.prey.getHealth()==health,"cancel clears every pending phase and hit");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=105,batch="hunter_counter_claim")
    public static void actualConsumeClaimAndReleaseCannotResumeTheCounterChain(GameTestHelper h){
        var s=scene(h,false);float health=s.prey.getHealth();
        var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CounterClaim"));
        h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"counter starts");
        h.runAfterDelay(10,()->h.assertTrue(s.hunter.claim(owner),"existing consume claim succeeds"));
        h.runAfterDelay(20,s.hunter::release);
        h.runAfterDelay(90,()->{try(s){
            h.assertTrue(!s.hunter.isBeingConsumed()&&!s.hunter.isNoAi(),"claim restores prior control state");
            h.assertTrue(!s.hunter.committedAttack()&&!s.hunter.counterBarrage()&&s.prey.getHealth()==health,"release cannot restart an old counter");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=185,batch="hunter_counter_ai")
    public static void existingAutonomousChoiceNowEntersTheSourceCounterOpening(GameTestHelper h){
        var s=scene(h,true);boolean[] opening={false},release={false};
        h.onEachTick(()->{opening[0]|=s.hunter.attackMove()==CreatureAttack.COUNTER;release[0]|=s.hunter.counterBarrage();});
        h.runAfterDelay(165,()->{try(s){
            h.assertTrue(opening[0]&&release[0],"ordinary grounded Hunter AI reaches both source phases without scripted beginAttack; opening="
                    +opening[0]+" release="+release[0]+" assignedTarget="+(s.hunter.getTarget()==s.prey)
                    +" distance="+s.hunter.distanceTo(s.prey)+" move="+s.hunter.attackMove());
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=105,batch="hunter_counter_root")
    public static void nativeRootAdvancesEntityAndHasNoJumpOrDuplicateTickAtRelease(GameTestHelper h){
        var s=scene(h,false);Vec3 origin=s.hunter.position();boolean[] release={false},duplicate={false},done={false};
        h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"source counter starts");
        h.onEachTick(()->{
            if(done[0])return;
            var move=s.hunter.attackMove();float age=s.hunter.attackAge(0);
            if(move==CreatureAttack.COUNTER){
                var expected=origin.add(HunterCounterMotion.offset(move,false,age));
                h.assertTrue(s.hunter.position().subtract(expected).horizontalDistance()<.015,"native root moves the actual actor, not only its visible bones");
                if(age>=10&&!duplicate[0]){duplicate[0]=true;Vec3 before=s.hunter.position();s.hunter.tick();h.assertTrue(s.hunter.position().subtract(before).horizontalDistance()<.00001,"a duplicate tick cannot consume the same root delta twice");}
            }else if(s.hunter.counterBarrage()){
                release[0]=true;
                var expected=origin.add(HunterCounterMotion.offset(CreatureAttack.COUNTER,false,32)).add(HunterCounterMotion.offset(CreatureAttack.BARRAGE,true,age));
                h.assertTrue(s.hunter.position().subtract(expected).horizontalDistance()<.015,"release rebases at the accepted counter endpoint and follows lateral source travel");
            }
        });
        h.runAfterDelay(92,()->{done[0]=true;try(s){
            h.assertTrue(duplicate[0]&&release[0]&&!s.hunter.committedAttack(),"both observed source stages complete");
            var expected=origin.add(HunterCounterMotion.offset(CreatureAttack.COUNTER,false,32)).add(HunterCounterMotion.offset(CreatureAttack.BARRAGE,true,57));
            h.assertTrue(s.hunter.position().subtract(expected).horizontalDistance()<.015,"forward and lateral accepted travel remain after recovery");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=60,batch="hunter_counter_wall")
    public static void fullHeightWallBlocksRootAndRemovingItDoesNotRepayLostTravel(GameTestHelper h){
        var s=scene(h,false);s.hunter.setPos(h.absoluteVec(new Vec3(8.5,2,4.25)));s.prey.setPos(s.hunter.position().add(0,0,2.1));
        Vec3 origin=s.hunter.position();float health=s.prey.getHealth();Vec3[] cleared={null};float[] clearAge={0};
        h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"counter starts before cover appears");
        for(int x=0;x<16;x++)for(int y=2;y<9;y++)h.setBlock(x,y,5,Blocks.STONE);
        h.runAfterDelay(25,()->{
            cleared[0]=s.hunter.position();clearAge[0]=s.hunter.attackAge(0);
            h.assertTrue(s.hunter.getBoundingBox().maxZ<=h.absolutePos(new net.minecraft.core.BlockPos(0,2,5)).getZ()+.001,"full body remains on the near side of the collision shape");
            h.assertTrue(s.prey.getHealth()==health,"opening cannot damage a victim behind cover");
            h.assertTrue(s.hunter.position().z-origin.z+.1<HunterCounterMotion.offset(CreatureAttack.COUNTER,false,clearAge[0]).z,"fixture actually rejected some source travel");
            for(int x=0;x<16;x++)for(int y=2;y<9;y++)h.setBlock(x,y,5,Blocks.AIR);
        });
        h.runAfterDelay(34,()->{try(s){
            double remaining=HunterCounterMotion.offset(CreatureAttack.COUNTER,false,32).z-HunterCounterMotion.offset(CreatureAttack.COUNTER,false,clearAge[0]).z;
            h.assertTrue(s.hunter.position().z-cleared[0].z<=remaining+.015,"removed wall allows only future frame deltas, never a catch-up teleport");
            h.assertTrue(s.hunter.position().z-origin.z<1,"blocked path does not regain the full1.0545block advance");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=105,batch="hunter_counter_load")
    public static void sameObjectLoadCancelsRootAndPendingCounterRelease(GameTestHelper h){
        var s=scene(h,false);float health=s.prey.getHealth();Vec3[] loaded={null};
        h.assertTrue(s.hunter.beginAttack(CreatureAttack.COUNTER,s.prey),"counter starts");
        h.runAfterDelay(10,()->{
            var tag=new net.minecraft.nbt.CompoundTag();s.hunter.saveWithoutId(tag);s.hunter.load(tag);loaded[0]=s.hunter.position();
            h.assertTrue(!s.hunter.committedAttack()&&!s.hunter.counterBarrage(),"transient attack state clears even when load reuses the same Java object");
        });
        h.runAfterDelay(90,()->{try(s){
            h.assertTrue(s.hunter.position().subtract(loaded[0]).horizontalDistance()<.0001&&!s.hunter.counterBarrage(),"old root samples cannot resume after load");
            h.assertTrue(s.prey.getHealth()==health,"no delayed opening/release damage survives load");
        }h.succeed();});
    }
}
