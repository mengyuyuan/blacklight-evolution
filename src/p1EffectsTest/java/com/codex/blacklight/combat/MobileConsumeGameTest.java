package com.codex.blacklight.combat;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MobileConsumeGameTest {
    private static ServerPlayer player(GameTestHelper h, PowerForm form) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"form-consume"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3))); p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(form);s.activate(form);
        p.setData(ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p) { p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p)); }

    private static void enter(GameTestHelper h,ServerPlayer p,Mob mob) {
        ConsumeSystem.request(p);
        h.assertTrue(mob.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CAPTURE,"capture was not accepted");
        // Confirmation during acquisition is buffered by the normal input path.
        ConsumeSystem.consume(p);
        for(int i=0;i<ConsumeVisual.captureDuration(PowerForm.NONE);i++)tick(p);
        h.assertTrue(mob.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CONSUME,"queued consume did not start");
    }

    @GameTest(template="wall_test",timeoutTicks=135,batch="mobile_consume")
    public static void walkingLinksTwoOrdinaryReceiversAndSettlesEachOnce(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var first=h.spawn(EntityType.COW,3,2,5);first.setNoAi(true);
        enter(h,p,first);
        var old=first.getData(ModAttachments.CONSUME_VISUAL);
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
                if(frame==release){
                    var current=first.getData(ModAttachments.CONSUME_VISUAL);
                    h.assertTrue(current.stage()==ConsumeVisual.MOBILE_CONSUME&&current.started()==old.started()&&current.variant()==old.variant(),"receiver clock or action changed");
                    h.assertTrue(p.getData(ModAttachments.MOBILE_CONSUME_START)==p.level().getGameTime()&&!ConsumeControl.ownsMotion(p)&&!ConsumeSystem.active(p),"player still locked to receiver");
                    h.assertTrue(!first.isRemoved()&&p.getHealth()==10,"premature removal or reward");
                    Vec3 before=p.position();
                    p.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(.40,0,0));
                    h.assertTrue(p.position().distanceToSqr(before)>.08,"player could not walk during active absorption");
                    p.setOnGround(true);
                    var second=EntityType.COW.create(h.getLevel());second.setNoAi(true);
                    second.setPos(p.position().add(0,0,1.65));h.getLevel().addFreshEntity(second);next[0]=second;
                    ConsumeSystem.request(p);ConsumeSystem.consume(p);ConsumeSystem.consume(p);
                    h.assertTrue(second.getData(ModAttachments.CONSUME_VISUAL).consumerId()==p.getId()
                            &&first.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.MOBILE_CONSUME,"new grab waits for previous receiver removal");
                }
                if(frame==finish){
                    h.assertTrue(first.isRemoved()&&p.getHealth()==12,"first return did not settle exactly once");
                    h.assertTrue(next[0].getData(ModAttachments.CONSUME_VISUAL).active()&&ConsumeSystem.active(p),"first completion unlocked the second scene");
                }
            });
        }
        h.runAfterDelay(finish+2,()->{
            for(int i=0;i<140;i++){tick(p);if(i==8)ConsumeSystem.consume(p);}
            h.assertTrue(next[0].isRemoved()&&p.getHealth()==14&&!ConsumeSystem.active(p),"second return failed or duplicate payout");
            h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="mobile_static")
    public static void stationaryConsumeRetainsPairedRecoveryAndNoMobileClock(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var target=h.spawn(EntityType.COW,3,2,5);target.setNoAi(true);enter(h,p,target);
        var visual=target.getData(ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        for(int i=1;i<finish;i++)tick(p);
        h.assertTrue(ConsumeSystem.committed(p)&&!target.isRemoved()&&p.getData(ModAttachments.MOBILE_CONSUME_START)<0,"stationary execution entered mobile branch");
        tick(p);
        h.assertTrue(target.isRemoved()&&p.getData(ModAttachments.CONSUME_RECOVERY).active()&&p.getHealth()==12,"stationary recovery or reward lost");
        h.succeed();
    }
}
