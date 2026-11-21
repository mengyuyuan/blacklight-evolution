package com.codex.blacklight.combat;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent.*;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MobileConsumeGameTest {
    private static ServerPlayer player(GameTestHelper h, PowerForm form) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"form-consume"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3))); p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(form);s.activate(form);
        com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p) { p.tickCount++;ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)); }

    private static void enter(GameTestHelper h,ServerPlayer p,Mob mob) {
        ConsumeSystem.request(p);
        h.assertTrue(ModAttachments.getData(mob,ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CAPTURE,"capture was not accepted");
        // Confirmation during acquisition is buffered by the normal input path.
        ConsumeSystem.consume(p);
        for(int i=0;i<ConsumeVisual.captureDuration(PowerForm.NONE);i++)tick(p);
        h.assertTrue(ModAttachments.getData(mob,ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CONSUME,"queued consume did not start");
    }


    @GameTest(template="wall_test",timeoutTicks=130,batch="mobile_carry_surface")
    public static void smallLiveReceiverFollowsWalkStopAndTurnOnTheShoulder(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var prey=h.spawn(EntityType.COW,3,2,5);prey.setNoAi(true);
        enter(h,p,prey);
        var initial=ModAttachments.getData(prey,ModAttachments.CONSUME_VISUAL);
        int handoff=(int)Math.ceil(ConsumeReceiverScene.absorptionSeconds(PowerForm.NONE,initial.variant())*20-.0001);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,initial.variant());
        Vec3[] previous={prey.position()};double[] path={0},maxError={0},maxStoppedStep={0},lastSide={0},maxRotation={0};boolean[] stationary={false};
        for(int i=1;i<=finish;i++){
            final int frame=i;
            h.runAfterDelay(frame,()->{
                if(frame<=handoff)ConsumeSystem.travelIntent(p,true);
                int mobileAge=frame-handoff;
                if(mobileAge>0 && mobileAge<6)p.move(MoverType.SELF,new Vec3(.13,mobileAge==3?.18:0,0));
                if(mobileAge>=6 && mobileAge<=9)p.yBodyRot=p.yBodyRotO=p.getYRot()+22.5F;
                if(mobileAge>=6 && mobileAge<=9)p.setYRot(p.yBodyRot);
                tick(p);
                var visual=ModAttachments.peekData(prey,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
                if(frame==handoff){
                    var carry=MobileConsumeCarry.state(prey,visual);
                    h.assertTrue(carry!=null&&carry.ownerId()==p.getId(),"carry never assigned to real receiver");
                    var want=carry.feet(p.position(),p.yBodyRot,p,prey,visual,p.level().getGameTime());
                    h.assertTrue(prey.position().distanceTo(want)<.001,"first mobile feet jump instead of inheriting final actual paired pose");
                    previous[0]=prey.position();
                }else if(frame>handoff && frame<finish){
                    var carry=MobileConsumeCarry.state(prey,visual);h.assertTrue(carry!=null,"carry lost");
                    Vec3 expected=carry.feet(p.position(),p.yBodyRot,p,prey,visual,p.level().getGameTime());
                    maxError[0]=Math.max(maxError[0],prey.position().distanceTo(expected));
                    h.assertTrue(prey.position().distanceTo(expected)<.025,"real world prey trails socket at "+frame+" distance "+prey.position().distanceTo(expected));
                    if(mobileAge>=6){
                        Vec3 local=prey.position().subtract(p.position()).yRot((float)Math.toRadians(p.yBodyRot));
                        lastSide[0]=local.x;maxRotation[0]=Math.max(maxRotation[0],Math.abs(p.yBodyRot));
                        h.assertTrue(local.x<-.33 && local.z>.1,"prey moved into torso or failed to rotate with player");
                    }
                    double step=prey.position().distanceTo(previous[0]);path[0]+=step;previous[0]=prey.position();
                    if(mobileAge>=12){maxStoppedStep[0]=Math.max(maxStoppedStep[0],step);h.assertTrue(step<.04,"stopped player left prey chasing an old world point");stationary[0]=true;}
                    h.assertTrue(prey.isAlive()&&p.getHealth()==10,"reward/damage changed before original due tick");
                }else if(frame==finish){
                    h.assertTrue(path[0]>.80&&stationary[0],"walk/turn/stop not exercised");
                    h.assertTrue(prey.isRemoved()&&p.getHealth()==12,"settlement did not pay once at original tick");
                    org.slf4j.LoggerFactory.getLogger(MobileConsumeGameTest.class).info("mobile_carry_sample handoff={} due={} path={} maxError={} maxStoppedStep={} side={} yaw={}",handoff,finish,path[0],maxError[0],maxStoppedStep[0],lastSide[0],maxRotation[0]);
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template="wall_test",timeoutTicks=135,batch="mobile_consume")
    public static void walkingLinksTwoOrdinaryReceiversAndSettlesEachOnce(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var first=h.spawn(EntityType.COW,3,2,5);first.setNoAi(true);
        enter(h,p,first);
        var old=ModAttachments.getData(first,ModAttachments.CONSUME_VISUAL);
        int release=(int)Math.ceil(ConsumeReceiverScene.absorptionSeconds(PowerForm.NONE,old.variant())*20-.0001);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,old.variant());
        h.assertTrue(release>1&&finish>release+2,"fixture must expose a real return-flow interval");
        final Mob[] next={null};
        for(int i=1;i<=finish+1;i++){
            final int frame=i;
            h.runAfterDelay(i,()->{
                if(frame<=release)com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,0,false,false);
                if(frame==release-1)h.assertTrue(ConsumeSystem.committed(p)&&ConsumeControl.ownsMotion(p),"strike was cut before its consume marker");
                tick(p);
                if(frame==release-1)h.assertTrue(!ModAttachments.getData(p,ModAttachments.CONSUME_BODY_VISUAL).active(),"giver shader started before its own cue");
                if(frame==release){
                    h.assertTrue(ModAttachments.getData(p,ModAttachments.CONSUME_BODY_VISUAL).equals(old),"walking restarted or lost the giver shader clock");
                    var current=ModAttachments.getData(first,ModAttachments.CONSUME_VISUAL);
                    h.assertTrue(current.stage()==ConsumeVisual.MOBILE_CONSUME&&current.started()==old.started()&&current.variant()==old.variant(),"receiver clock or action changed");
                    h.assertTrue(ModAttachments.getData(p,ModAttachments.MOBILE_CONSUME_START)==p.level().getGameTime()&&!ConsumeControl.ownsMotion(p)&&!ConsumeSystem.active(p),"player still locked to receiver");
                    h.assertTrue(!first.isRemoved()&&p.getHealth()==10,"premature removal or reward");
                    Vec3 before=p.position();
                    p.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(.40,0,0));
                    h.assertTrue(p.position().distanceToSqr(before)>.08,"player could not walk during active absorption");
                    p.setOnGround(true);
                    var second=EntityType.COW.create(h.getLevel());second.setNoAi(true);
                    second.setPos(p.position().add(0,0,1.65));h.getLevel().addFreshEntity(second);next[0]=second;
                    Vec3 oldFeet=first.position();
                    var oldCarry=MobileConsumeCarry.state(first,current);
                    ConsumeSystem.request(p);ConsumeSystem.consume(p);ConsumeSystem.consume(p);
                    h.assertTrue(oldCarry!=null&&oldCarry.equals(MobileConsumeCarry.state(first,current))&&first.position().distanceToSqr(oldFeet)<1e-8,
                            "second request displaced or re-keyed the first living receiver");

                    h.assertTrue(ModAttachments.getData(second,ModAttachments.CONSUME_VISUAL).consumerId()==p.getId()
                            &&ModAttachments.getData(first,ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.MOBILE_CONSUME,"new grab waits for previous receiver removal");
                }
                if(frame==finish){
                    h.assertTrue(first.isRemoved()&&p.getHealth()==12,"first return did not settle exactly once");
                    h.assertTrue(ModAttachments.getData(next[0],ModAttachments.CONSUME_VISUAL).active()&&ConsumeSystem.active(p),"first completion unlocked the second scene");
                }
            });
        }
        h.runAfterDelay(finish+2,()->{
            for(int i=0;i<140;i++){tick(p);if(i==8)ConsumeSystem.consume(p);}
            h.assertTrue(next[0].isRemoved()&&p.getHealth()==14&&!ConsumeSystem.active(p),"second return failed or duplicate payout");
            h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=120,batch="mobile_carry_two_owners")
    public static void crossingOwnersKeepTheirOwnLiveReceiver(GameTestHelper h){
        var first=player(h,PowerForm.NONE);var other=player(h,PowerForm.NONE);
        other.setPos(first.position().add(3,0,0));
        var a=h.spawn(EntityType.COW,3,2,5);a.setNoAi(true);
        var b=h.spawn(EntityType.COW,6,2,5);b.setNoAi(true);
        enter(h,first,a);enter(h,other,b);
        var va=ModAttachments.peekData(a,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        int handoff=(int)Math.ceil(ConsumeReceiverScene.absorptionSeconds(PowerForm.NONE,va.variant())*20-.0001);
        int end=ConsumeSequence.completionTick(PowerForm.NONE,false,va.variant());
        for(int i=1;i<=end;i++){
            final int frame=i;
            h.runAfterDelay(i,()->{
                if(frame<=handoff){ConsumeSystem.travelIntent(first,true);ConsumeSystem.travelIntent(other,true);}
                if(frame>handoff&&frame<=handoff+9){
                    first.move(MoverType.SELF,new Vec3(.19,0,0));
                    other.move(MoverType.SELF,new Vec3(-.19,0,0));
                    first.yBodyRot=0;other.yBodyRot=180;
                }
                tick(first);tick(other);
                if(frame>=handoff && frame<end){
                    var sa=ModAttachments.peekData(a,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
                    var sb=ModAttachments.peekData(b,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
                    var ca=MobileConsumeCarry.state(a,sa);var cb=MobileConsumeCarry.state(b,sb);
                    h.assertTrue(ca!=null&&cb!=null&&ca.ownerId()==first.getId()&&cb.ownerId()==other.getId(),"overlap transferred ownership");
                    h.assertTrue(MobileConsumeCarry.center(a,other,sa,1)==null&&MobileConsumeCarry.center(b,first,sb,1)==null,"other owner's anchor leaked");
                    h.assertTrue(a.position().distanceTo(ca.feet(first.position(),first.yBodyRot,first,a,sa,first.level().getGameTime()))<.025
                            && b.position().distanceTo(cb.feet(other.position(),other.yBodyRot,other,b,sb,other.level().getGameTime()))<.025,"live prey picked other actor");
                    if(frame==handoff+8)h.assertTrue(first.position().distanceTo(other.position())<1.2,"actors never crossed within one body width");
                }
                if(frame==end){h.assertTrue(a.isRemoved()&&b.isRemoved()&&first.getHealth()==12&&other.getHealth()==12,"overlapping owners duplicated or lost rewards");h.succeed();}
            });
        }
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="mobile_static")
    public static void weakenedReceiverOutsideDirectClassKeepsItsOwnExecution(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var mob=h.spawn(EntityType.COW,3,2,5);mob.setNoAi(true);
        h.assertTrue(ConsumeSystem.smallDirectReceiver(mob),"ordinary cow must be in the small carry class");
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(250);
        mob.setHealth(1);
        h.assertTrue(!ConsumeSystem.smallDirectReceiver(mob),"weakening a heavy target must not change its grab class");
        enter(h,p,mob);ConsumeSystem.travelIntent(p,true);
        var visual=ModAttachments.getData(mob,ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        for(int i=1;i<finish;i++)tick(p);
        h.assertTrue(ConsumeSystem.committed(p)&&ModAttachments.getData(p,ModAttachments.MOBILE_CONSUME_START)<0,"heavy target entered the mobile carry");
        h.assertTrue(!ModAttachments.getData(p,ModAttachments.CONSUME_BODY_VISUAL).active(),"heavy execution supplied the small-receiver coat");
        tick(p);
        h.assertTrue(mob.isRemoved()&&p.getHealth()>10,"ordinary weakened execution stopped settling");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="mobile_static")
    public static void stationaryConsumeRetainsPairedRecoveryAndNoMobileClock(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var target=h.spawn(EntityType.COW,3,2,5);target.setNoAi(true);enter(h,p,target);
        var visual=ModAttachments.getData(target,ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        for(int i=1;i<finish;i++)tick(p);
        h.assertTrue(ConsumeSystem.committed(p)&&!target.isRemoved()&&ModAttachments.getData(p,ModAttachments.MOBILE_CONSUME_START)<0,"stationary execution entered mobile branch");
        h.assertTrue(ModAttachments.getData(p,ModAttachments.CONSUME_BODY_VISUAL).equals(visual),"stationary consume never applied the giver material");
        tick(p);
        h.assertTrue(ModAttachments.getData(p,ModAttachments.CONSUME_BODY_VISUAL).equals(visual),"settlement discarded the residual coat");
        h.assertTrue(target.isRemoved()&&ModAttachments.getData(p,ModAttachments.CONSUME_RECOVERY).active()&&p.getHealth()==12,"stationary recovery or reward lost");
        h.succeed();
    }
}
