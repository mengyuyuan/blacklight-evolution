package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.infection.CommonInfectedLeap;
import com.codex.blacklight.infection.InfectionRules;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class CommonInfectedLeapGameTest {
    private static final class DamageReload {
        final Fixture fixture;final net.minecraft.nbt.CompoundTag healthy;
        boolean ran;
        DamageReload(Fixture fixture){
            this.fixture=fixture;healthy=fixture.actor.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            healthy.putBoolean("OnGround",true);
            var velocity=new net.minecraft.nbt.ListTag();
            for(double value:new double[]{.12,0,.02})velocity.add(net.minecraft.nbt.DoubleTag.valueOf(value));
            healthy.put("Motion",velocity);
        }
    }
    private static DamageReload pendingDamageReload;
    @SubscribeEvent public static void reloadAttackerInsideActualWristDamage(LivingIncomingDamageEvent event){
        var pending=pendingDamageReload;
        if(pending==null||event.getEntity()!=pending.fixture.prey||event.getSource().getEntity()!=pending.fixture.actor
                ||pending.fixture.actor.attackHand()!=CommonInfectedLeap.HAND||pending.fixture.actor.attackStarted()<0)return;
        pendingDamageReload=null;pending.ran=true;
        pending.fixture.actor.load(pending.healthy.copy());
    }
    private record Fixture(BlacklightCommonInfected actor,Zombie prey) implements AutoCloseable {
        public void close(){
            if(pendingDamageReload!=null&&pendingDamageReload.fixture.actor==actor)pendingDamageReload=null;
            actor.discard();prey.discard();
        }
    }
    private static Fixture fixture(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<8;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var actor=ModEntities.COMMON_INFECTED.get().create(h.getLevel());
        var prey=EntityType.ZOMBIE.create(h.getLevel());
        actor.setPos(h.absoluteVec(new Vec3(3.5,2,3.5)));actor.setOnGround(true);actor.setPersistenceRequired();
        prey.setPos(h.absoluteVec(new Vec3(3.5,2,7.5)));prey.setNoAi(true);prey.setPersistenceRequired();
        // Keep daylight damage from being mistaken for a leap hit.
        prey.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        prey.getPersistentData().putLong(InfectionRules.NEXT_CONTACT_EXPOSURE,Long.MAX_VALUE);
        prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);
        prey.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.getLevel().addFreshEntity(prey);h.getLevel().addFreshEntity(actor);actor.setTarget(prey);
        return new Fixture(actor,prey);
    }
    @GameTest(template="wall_test",timeoutTicks=75,batch="common_leap_damage_reload")
    public static void actualWristDamageReloadCannotCommitTheOldLandingOrRootTail(GameTestHelper h){
        var f=fixture(h);var reload=new DamageReload(f);pendingDamageReload=reload;boolean[] done={false};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                h.assertTrue(h.getTick()<65,"real AI must reach its wrist hurt callback; a missed attack cannot pass this fixture");
                if(!reload.ran)return;
                h.assertTrue(f.actor.isAlive()&&!f.actor.isNoAi(),"callback loaded a healthy ordinary grounded actor, not a disabled actor that masks the stale commit");
                h.assertTrue(f.actor.attackStarted()<0&&f.actor.leapPunchAge(0)<0&&f.actor.leapLandAge(0)<0,
                        "old hurt stack cannot write landing/action clocks after same-object load");
                h.assertTrue(f.actor.leapExitWeight()==0,"ordinary horizontal Motion from the loaded snapshot is not stolen by the old root driver");
                done[0]=true;f.close();h.succeed();
            }catch(RuntimeException|AssertionError failure){done[0]=true;f.close();throw failure;}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=75,batch="common_leap_root_exit")
    public static void realLandingRetainsOnlyABoundedRootTailThenStopsWithoutMomentumCarry(GameTestHelper h){
        var f=fixture(h);boolean[] landed={false},moved={false},done={false};Vec3[] before={null},settled={null};
        long[] at={0};
        h.onEachTick(()->{
            if(done[0])return;
            try{
                if(!landed[0]){
                    if(f.actor.leapLandAge(0)<0)return;
                    landed[0]=true;at[0]=h.getLevel().getGameTime();before[0]=f.actor.position();
                    h.assertTrue(f.actor.leapExitWeight()>0,"actual ground contact releases the source root driver with positive fade weight");
                    // Remove ordinary walking acceleration and actor pushing while
                    // keeping the real committed landing state and floor collision.
                    f.actor.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
                    f.prey.setPos(f.prey.position().add(12,0,0));
                    return;
                }
                long elapsed=h.getLevel().getGameTime()-at[0];
                moved[0]|=f.actor.position().subtract(before[0]).horizontalDistance()>.005;
                h.assertTrue(f.actor.onGround(),"root fade cannot re-launch the grounded actor");
                if(elapsed==7){
                    h.assertTrue(f.actor.leapExitWeight()==0&&moved[0],"source duration ends after finite real horizontal movement");
                    settled[0]=f.actor.position();
                }
                if(elapsed==8){
                    h.assertTrue(f.actor.position().subtract(settled[0]).horizontalDistance()<.002,"expired driver does not leave its old velocity in vanilla travel");
                    done[0]=true;f.close();h.succeed();
                }
            }catch(RuntimeException|AssertionError failure){done[0]=true;f.close();throw failure;}
        });
    }
    @GameTest(template="wall_test",timeoutTicks=75,batch="common_leap_root_load")
    public static void sameObjectLoadDuringLandingClearsTransientRootDriverAndAttackPose(GameTestHelper h){
        var f=fixture(h);boolean[] done={false};
        h.onEachTick(()->{
            if(done[0]||f.actor.leapLandAge(0)<0)return;
            try(f){
                h.assertTrue(f.actor.leapExitWeight()>0,"fixture reaches a real active root release before load");
                var tag=f.actor.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                f.actor.readAdditionalSaveData(tag.copy());
                h.assertTrue(f.actor.leapExitWeight()==0&&f.actor.attackStarted()<0&&f.actor.leapPunchAge(0)<0&&f.actor.leapLandAge(0)<0,
                        "same-object load releases transient driver and synchronized action clocks instead of reusing a prior leap");
                h.assertTrue(!f.actor.isNoGravity(),"loading transient motion does not retain a gravity ownership flag");
            }finally{done[0]=true;}
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="common_leap_contact")
    public static void actualAiLaunchesThenOpensOneDelayedWristHit(GameTestHelper h){
        var f=fixture(h);boolean[] entered={false},air={false},punch={false},finished={false};int[] hits={0};float[] hp={f.prey.getHealth()};
        h.onEachTick(()->{
            f.prey.invulnerableTime=0;
            boolean active=f.actor.attackStarted()>=0&&f.actor.attackHand()==CommonInfectedLeap.HAND;
            if(active){entered[0]=true;air[0]|=!f.actor.onGround();punch[0]|=f.actor.leapPunchAge(0)>=0;
                if(f.prey.getHealth()<hp[0]){
                    h.assertTrue(CommonInfectedLeap.damageWindow(f.actor.leapPunchAge(0)),"root approach probe never applies damage");
                    hits[0]++;
                }
            }else if(entered[0]){finished[0]=true;f.actor.setNoAi(true);}
            hp[0]=f.prey.getHealth();
        });
        h.runAfterDelay(60,()->{try(f){
            h.assertTrue(entered[0]&&air[0]&&punch[0]&&finished[0],"real AI includes windup, flight, conditional punch and completed landing");
            h.assertTrue(hits[0]==1,"real wrist window deals one hit even when vanilla immunity is reset");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=55,batch="common_leap_ceiling")
    public static void lowCeilingRejectsLaunchWithoutRepeatedJumpAttempts(GameTestHelper h){
        var f=fixture(h);for(int x=1;x<=6;x++)for(int z=1;z<=9;z++)h.setBlock(x,4,z,Blocks.STONE);
        boolean[] leap={false},air={false};double floor=f.actor.getY();
        h.onEachTick(()->{leap[0]|=f.actor.attackStarted()>=0&&f.actor.attackHand()==CommonInfectedLeap.HAND;air[0]|=f.actor.getY()>floor+.1;});
        h.runAfterDelay(40,()->{try(f){h.assertTrue(!leap[0]&&!air[0],"ceiling swept by the real body prevents the launch; ordinary walking remains possible");}h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=55,batch="common_leap_wall")
    public static void newCoverDuringWindupAbortsBeforeLaunchAndDamage(GameTestHelper h){
        var f=fixture(h);boolean[] covered={false},air={false};float health=f.prey.getHealth();
        h.onEachTick(()->{
            if(!covered[0]&&f.actor.attackStarted()>=0&&f.actor.attackHand()==CommonInfectedLeap.HAND){
                covered[0]=true;for(int x=1;x<=6;x++)for(int y=2;y<7;y++)h.setBlock(x,y,5,Blocks.STONE);
            }
            air[0]|=f.actor.getY()>h.absoluteVec(new Vec3(0,2,0)).y+.1;
        });
        h.runAfterDelay(35,()->{try(f){
            h.assertTrue(covered[0]&&!air[0]&&f.actor.attackStarted()<0,"changed cover cancels the committed launch instead of endlessly jumping");
            h.assertTrue(f.prey.getHealth()==health,"cover prevents both probe and wrist damage");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=70,batch="common_leap_dodge")
    public static void sourceAirTrackingBendsTowardASidestepWithoutSnappingToIt(GameTestHelper h){
        var f=fixture(h);boolean[] moved={false},entered={false},turned={false},curved={false};float health=f.prey.getHealth();
        float[] yaw={0};double initialX=f.actor.getX();
        h.onEachTick(()->{
            if(f.actor.attackStarted()>=0&&f.actor.attackHand()==CommonInfectedLeap.HAND){
                entered[0]=true;long age=h.getLevel().getGameTime()-f.actor.attackStarted();
                if(!moved[0]&&age>CommonInfectedLeap.LAUNCH_TICK&&!f.actor.onGround()){
                    moved[0]=true;yaw[0]=f.actor.yBodyRot;f.prey.setPos(f.prey.position().add(3,0,0));
                }
                if(moved[0]&&f.actor.leapLandAge(0)<0){
                    float turn=net.minecraft.util.Mth.wrapDegrees(f.actor.yBodyRot-yaw[0]);
                    if(!turned[0]&&Math.abs(turn)>.1){
                        turned[0]=true;
                        h.assertTrue(turn<0&&Math.abs(turn)<10,"first flight correction is toward the sidestep, without an instant aim snap");
                    }
                    curved[0]|=f.actor.getX()>initialX+.02;
                }
            }else if(entered[0])f.actor.setNoAi(true);
        });
        h.runAfterDelay(60,()->{try(f){
            h.assertTrue(moved[0]&&turned[0]&&curved[0],"actual airborne AI changes both facing and trajectory");
            h.assertTrue(f.prey.getHealth()==health,"a large physical sidestep still escapes the rate-limited wrist attack");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=55,batch="common_leap_air_cover")
    public static void coverAddedInFlightCancelsTrackingAndDamage(GameTestHelper h){
        var f=fixture(h);boolean[] covered={false},finished={false};float health=f.prey.getHealth();
        h.onEachTick(()->{
            if(!covered[0]&&f.actor.attackStarted()>=0&&f.actor.attackHand()==CommonInfectedLeap.HAND&&!f.actor.onGround()){
                covered[0]=true;for(int x=1;x<=8;x++)for(int y=2;y<7;y++)h.setBlock(x,y,5,Blocks.STONE);
            }
            if(covered[0]&&f.actor.attackStarted()<0){finished[0]=true;f.actor.setNoAi(true);}
        });
        h.runAfterDelay(35,()->{try(f){
            h.assertTrue(covered[0]&&finished[0],"new cover stops the existing leap rather than starting another tracking loop");
            h.assertTrue(f.prey.getHealth()==health,"no wrist damage is delivered through in-flight cover");
            h.assertTrue(f.actor.getZ()<h.absoluteVec(new Vec3(0,0,5)).z,"ordinary collision never crosses the added wall");
        }h.succeed();});
    }
    @GameTest(template="wall_test",timeoutTicks=55,batch="common_leap_source_physics")
    public static void releasedFlightMovesOnceWithoutVanillaDragAndNoAiReleasesIt(GameTestHelper h){
        var f=fixture(h);int[] stage={0};Vec3[] before={null},center={null},velocity={null},controlBefore={null};
        BlacklightCommonInfected[] control={null};
        h.onEachTick(()->{
            if(stage[0]==0){
                if(f.actor.attackStarted()<0||f.actor.attackHand()!=CommonInfectedLeap.HAND||f.actor.onGround())return;
                before[0]=f.actor.position();center[0]=f.actor.getBoundingBox().getCenter();velocity[0]=f.actor.getDeltaMovement();stage[0]=1;
            }else if(stage[0]==1){
                h.assertTrue(!f.actor.onGround()&&f.actor.attackStarted()>=0,"the second released frame remains in actual flight");
                var expected=CommonInfectedLeap.advance(velocity[0],f.prey.getBoundingBox().getCenter().subtract(center[0]),Vec3.ZERO,true).velocity();
                h.assertTrue(f.actor.getDeltaMovement().distanceTo(expected)<1e-5,"source tracking then gravity is not followed by another vanilla gravity/drag step");
                h.assertTrue(f.actor.position().subtract(before[0]).distanceTo(expected)<1e-5,"one collision-free flight frame moves once using the resulting source velocity");
                control[0]=ModEntities.COMMON_INFECTED.get().create(h.getLevel());
                control[0].setPos(f.actor.position().add(6,0,0));control[0].setNoAi(true);control[0].setOnGround(false);
                control[0].setDeltaMovement(f.actor.getDeltaMovement());h.getLevel().addFreshEntity(control[0]);
                before[0]=f.actor.position();controlBefore[0]=control[0].position();f.actor.setNoAi(true);stage[0]=2;
            }else if(stage[0]==2){
                try(f){
                    h.assertTrue(f.actor.attackStarted()<0,"NoAI capture clears the released attack even when its goal no longer ticks");
                    h.assertTrue(f.actor.getDeltaMovement().distanceTo(control[0].getDeltaMovement())<1e-5,"interrupted flight uses the same normal travel as an unlaunched control");
                    var actual=f.actor.position().subtract(before[0]);var ordinary=control[0].position().subtract(controlBefore[0]);
                    h.assertTrue(actual.distanceTo(ordinary)<1e-5,"released native movement does not keep running under capture/NoAI");
                    h.assertTrue(!f.actor.isNoGravity(),"jump ownership never leaves a persistent NoGravity flag");
                }finally{control[0].discard();stage[0]=3;}
                h.succeed();
            }
        });
    }
}
