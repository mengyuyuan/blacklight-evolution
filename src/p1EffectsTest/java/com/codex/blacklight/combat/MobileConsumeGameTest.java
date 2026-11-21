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


    @GameTest(template="wall_test",timeoutTicks=130,batch="mobile_carry_surface")
    public static void smallLiveReceiverFollowsWalkStopAndTurnOnTheShoulder(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var prey=h.spawn(EntityType.COW,3,2,5);prey.setNoAi(true);
        enter(h,p,prey);
        var initial=prey.getData(ModAttachments.CONSUME_VISUAL);
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
                if(frame==release-1)h.assertTrue(!p.getData(ModAttachments.CONSUME_BODY_VISUAL).active(),"giver shader started before its own cue");
                if(frame==release){
                    h.assertTrue(p.getData(ModAttachments.CONSUME_BODY_VISUAL).equals(old),"walking restarted or lost the giver shader clock");
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
                    Vec3 oldFeet=first.position();
                    var oldCarry=MobileConsumeCarry.state(first,current);
                    ConsumeSystem.request(p);ConsumeSystem.consume(p);ConsumeSystem.consume(p);
                    h.assertTrue(oldCarry!=null&&oldCarry.equals(MobileConsumeCarry.state(first,current))&&first.position().distanceToSqr(oldFeet)<1e-8,
                            "second request displaced or re-keyed the first living receiver");

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

    @GameTest(template="wall_test",timeoutTicks=60,batch="reinforced_infected_consume")
    public static void reinforcedCommonInfectedRetainsFullSceneWhenWeakened(GameTestHelper h){
        for(int x=0;x<=8;x++)for(int z=0;z<=8;z++)
            h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        for(int variant:new int[]{ConsumeVariant.DEFAULT,ConsumeVariant.HEAD_STOMP,ConsumeVariant.BEATDOWN,ConsumeVariant.KNEE_HEAD})
            checkReinforcedReceiver(h,variant);
        h.succeed();
    }
    private static void checkReinforcedReceiver(GameTestHelper h,int variant){
        var p=player(h,PowerForm.NONE);
        var mob=h.spawn(com.codex.blacklight.registry.ModEntities.COMMON_INFECTED.get(),3,2,5);
        mob.setNoAi(true);
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(250);
        mob.setHealth(mob.getMaxHealth());
        ConsumeSystem.request(p);
        h.assertTrue(!ConsumeSystem.active(p),"full-health reinforced infected was captured");
        mob.setHealth(1);
        h.assertTrue(ConsumeSystem.healthReady(p,mob)&&!ConsumeSystem.smallDirectReceiver(mob),
                "reinforced infected changed to the direct class at low health");
        int previous=ConsumeVariant.qaForced;
        try {
            ConsumeVariant.qaForced=variant;
            enter(h,p,mob);
        } finally {
            ConsumeVariant.qaForced=previous;
        }
        var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        ConsumeSystem.travelIntent(p,true);
        h.assertTrue(visual.variant()==variant,"wrong paired variant in test");
        int elapsed=0;
        while(!mob.isRemoved() && elapsed<finish+14){
            h.assertTrue(ConsumeSystem.committed(p)&&p.getData(ModAttachments.MOBILE_CONSUME_START)<0,
                    "paired execution cancelled or unlocked, variant="+variant+" tick="+elapsed);
            tick(p);
            elapsed++;
        }
        var recovery=p.getData(ModAttachments.CONSUME_RECOVERY);
        h.assertTrue(mob.isRemoved()&&recovery.active()&&elapsed==finish+Math.max(0,recovery.landingDelay()),
                "settlement did not follow the actual landing and recovery, variant="+variant+" tick="+elapsed);
    }

    @GameTest(template="wall_test",timeoutTicks=130,batch="late_actor_material")
    public static void clawsBodyEventStillStartsAfterReceiverSettlement(GameTestHelper h){
        var p=player(h,PowerForm.CLAWS);
        var mob=h.spawn(EntityType.VILLAGER,3,2,5);
        mob.setNoAi(true);
        int old=ConsumeVariant.qaForced;
        try{
            ConsumeVariant.qaForced=ConsumeVariant.CLASSIC;
            ConsumeSystem.request(p);
            ConsumeSystem.consume(p);
            for(int i=0;i<50&&mob.getData(ModAttachments.CONSUME_VISUAL).stage()!=ConsumeVisual.CONSUME;i++)tick(p);
        } finally {ConsumeVariant.qaForced=old;}
        var scene=mob.getData(ModAttachments.CONSUME_VISUAL);
        h.assertTrue(scene.stage()==ConsumeVisual.CONSUME&&scene.variant()==ConsumeVariant.CLASSIC,
                "did not enter the claw head-crush clip");
        int cue=ConsumeGiverEffect.tick(ConsumeGiverEffect.cue(scene));
        int finish=ConsumeSequence.completionTick(PowerForm.CLAWS,false,scene.variant());
        h.assertTrue(finish<cue,"fixture requires an independent actor event after receiver settlement");
        ConsumeSystem.travelIntent(p,true);
        for(int i=1;i<=cue;i++){
            int frame=i;
            h.runAfterDelay(frame,()->{
                tick(p);
                var body=p.getData(ModAttachments.CONSUME_BODY_VISUAL);
                if(frame<cue)h.assertTrue(!body.active(),"coat started early at "+frame);
                if(frame==finish)h.assertTrue(mob.isRemoved(),"receiver waited for the giver event");
                if(frame==cue){
                    h.assertTrue(body.active()&&body.started()==scene.started()&&body.variant()==scene.variant(),
                            "late source callback lost on the moving player");
                    h.succeed();
                }
            });
        }
    }

    @GameTest(template="wall_test",timeoutTicks=60,batch="hunter_consume_recovery")
    public static void allSixFormsRetainPairedHunterExecutionAndRecovery(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.CLAWS,PowerForm.HAMMERFISTS,
                PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST,PowerForm.BLADE}) {
            checkHunterRecovery(h,form);
        }
        h.succeed();
    }
    private static void checkHunterRecovery(GameTestHelper h,PowerForm form){
        var p=player(h,form);
        p.setPos(p.position().add(0,0,.55));
        var hunter=h.spawn(com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HUNTER.get(),3,2,5);
        hunter.setNoAi(true);
        hunter.setHealth(hunter.getMaxHealth());
        ConsumeSystem.request(p);
        h.assertTrue(!hunter.isBeingConsumed(),"healthy hunter accepted a finisher");
        hunter.setHealth(form == PowerForm.CLAWS ? hunter.getMaxHealth() * .05F : form == PowerForm.BLADE ? .2F : 1F);
        ConsumeSystem.request(p);
        h.assertTrue(hunter.isBeingConsumed(),"weakened hunter did not accept the mount");
        ConsumeSystem.consume(p);
        for(int i=0;i<60 && hunter.getData(ModAttachments.CONSUME_VISUAL).stage()!=ConsumeVisual.HUNTER_CONSUME;i++)tick(p);
        var paired=hunter.getData(ModAttachments.CONSUME_VISUAL);
        h.assertTrue(paired.stage()==ConsumeVisual.HUNTER_CONSUME&&paired.form()==PowerForm.NONE.ordinal(),
                form+" replaced the shared hunter choreography with a human execution");
        ConsumeSystem.travelIntent(p,true);
        float previousHealth=hunter.getHealth();
        int injuryCount=0;
        for(int i=1;i<HunterConsume.COMPLETE_TICKS;i++){
            tick(p);
            if(hunter.getHealth()<previousHealth){
                h.assertTrue(hunter.getHealth() >= previousHealth * .49F, "a single amplified contact emptied the execution health budget");
                injuryCount++;
            }
            previousHealth=hunter.getHealth();
            h.assertTrue(hunter.isAlive(),"hunter died before the finishing frame");
        }
        h.assertTrue(injuryCount==2,"expected two hunter contact wounds, observed "+injuryCount);
        h.assertTrue(ConsumeSystem.committed(p)&&!hunter.isConsumeRemains()&&p.getData(ModAttachments.MOBILE_CONSUME_START)<0,
                "moving interrupted the hunter sequence before the source detach event");
        tick(p);
        h.assertTrue(hunter.isConsumeRemains()&&p.getData(ModAttachments.CONSUME_RECOVERY).equals(paired)&&paired.duration()>HunterConsume.COMPLETE_TICKS,
                form+" lost the receiver remains or the actor's recovery at detach");
        hunter.discard();
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
        var visual=mob.getData(ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        for(int i=1;i<finish;i++)tick(p);
        h.assertTrue(ConsumeSystem.committed(p)&&p.getData(ModAttachments.MOBILE_CONSUME_START)<0,"heavy target entered the mobile carry");
        h.assertTrue(!p.getData(ModAttachments.CONSUME_BODY_VISUAL).active(),"heavy execution supplied the small-receiver coat");
        tick(p);
        h.assertTrue(mob.isRemoved()&&p.getHealth()>10,"ordinary weakened execution stopped settling");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="mobile_static")
    public static void stationaryConsumeRetainsPairedRecoveryAndNoMobileClock(GameTestHelper h){
        var p=player(h,PowerForm.NONE);
        var target=h.spawn(EntityType.COW,3,2,5);target.setNoAi(true);enter(h,p,target);
        var visual=target.getData(ModAttachments.CONSUME_VISUAL);
        int finish=ConsumeSequence.completionTick(PowerForm.NONE,false,visual.variant());
        float original=target.getHealth();
        target.hurt(target.damageSources().generic(),99);
        h.assertTrue(target.getHealth()==original,"external damage leaked into the execution");
        boolean injured=false;
        for(int i=1;i<finish;i++){
            tick(p);
            if(target.getHealth()<original)injured=true;
            h.assertTrue(target.isAlive(),"an intermediate contact executed the receiver");
        }
        h.assertTrue(injured,"the paired contact never dealt actual damage");
        h.assertTrue(ConsumeSystem.committed(p)&&!target.isRemoved()&&p.getData(ModAttachments.MOBILE_CONSUME_START)<0,"stationary execution entered mobile branch");
        h.assertTrue(p.getData(ModAttachments.CONSUME_BODY_VISUAL).equals(visual),"stationary consume never applied the giver material");
        tick(p);
        h.assertTrue(p.getData(ModAttachments.CONSUME_BODY_VISUAL).equals(visual),"settlement discarded the residual coat");
        h.assertTrue(target.isRemoved()&&p.getData(ModAttachments.CONSUME_RECOVERY).active()&&p.getHealth()==12,"stationary recovery or reward lost");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_contact_damage")
    public static void everyBeatdownContactDamagesOnceWithoutEarlyDeath(GameTestHelper h) {
        var p=player(h,PowerForm.NONE);
        var target=h.spawn(EntityType.COW,3,2,5);
        target.setNoAi(true); target.setHealth(1);
        var scene=ConsumeReceiverScene.profile(PowerForm.NONE,false,ConsumeVariant.BEATDOWN);
        var strikes=new ConsumeContactDamage();
        var visual=new ConsumeVisual(p.getId(),0,100,PowerForm.NONE.ordinal(),ConsumeVisual.CONSUME,0,p.position(),ConsumeVariant.BEATDOWN);
        float lastLoss=0;
        for (int index=0;index<scene.hitSeconds().size();index++) {
            int at=Math.round(scene.hitSeconds().get(index)*20);
            float previous=target.getHealth();
            h.assertTrue(strikes.strike(p,target,scene,0,at-1,visual)<0 && target.getHealth()==previous,"early damage");
            h.assertTrue(strikes.strike(p,target,scene,0,at,visual)==index,"wrong contact ordinal");
            float after=target.getHealth();
            float loss=previous-after;
            h.assertTrue(loss>lastLoss,"each successive beat should carry more damage: "+index);
            lastLoss=loss;
            h.assertTrue(after<previous && after>=.25F,"missing nonlethal contact "+index+" hp "+previous+" -> "+after);
            h.assertTrue(strikes.strike(p,target,scene,0,at,visual)<0 && target.getHealth()==after,"duplicate contact");
        }
        h.assertTrue(target.isAlive(),"receiver died before settlement");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="consume_contact_damage")
    public static void woundChangesHealthAtItsOwnContact(GameTestHelper h) {
        var owner=player(h,PowerForm.NONE);
        float previousLoss=0;
        int[] variants={ConsumeVariant.KNEE_HEAD,ConsumeVariant.DEFAULT,ConsumeVariant.HEAD_STOMP};
        for(int i=0;i<variants.length;i++){
            int variant=variants[i];
            var victim=h.spawn(EntityType.COW,3+i,2,5);
            victim.setNoAi(true);
            float start=victim.getHealth();
            var scene=ConsumeReceiverScene.profile(PowerForm.NONE,false,variant);
            var visual=new ConsumeVisual(owner.getId(),0,100,PowerForm.NONE.ordinal(),ConsumeVisual.CONSUME,0,owner.position(),variant);
            var strikes=new ConsumeContactDamage();
            int tick=Math.round(scene.hitSeconds().get(0)*20);
            h.assertTrue(strikes.strike(owner,victim,scene,0,tick-1,visual)<0 && victim.getHealth()==start,"damage before contact");
            h.assertTrue(strikes.strike(owner,victim,scene,0,tick,visual)==0,"contact missing");
            String cue=ConsumeImpactKind.forContact(visual,0);
            float loss=start-victim.getHealth();
            h.assertTrue(loss>previousLoss && victim.isAlive(),"wrong wound damage: "+cue+" "+loss);
            com.codex.blacklight.BlacklightNeoMod.LOGGER.info("Consume wound {} hp {} -> {}",cue,start,victim.getHealth());
            previousLoss=loss;
        }
        h.succeed();
    }

}
