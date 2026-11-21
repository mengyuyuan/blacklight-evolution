package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class WeaponFormTravelGameTest {
    private static ServerPlayer player(GameTestHelper h,PowerForm form,boolean ground){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"form-travel"));
        p.setPos(h.absoluteVec(new Vec3(4.5,75,2.5)));p.setOnGround(ground);p.setYRot(0);p.setXRot(0);p.tickCount=100;
        var state=p.getData(ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);if(form!=PowerForm.NONE)state.unlock(form);state.activate(form);
        p.setData(ModAttachments.PLAYER_STATE,state);
        h.assertTrue(state.getActivePower()==form,"fixture activated "+form);
        return p;
    }

    private static void floor(GameTestHelper h){
        for(int x=3;x<=5;x++)for(int z=1;z<=18;z++)h.setBlock(x,74,z,Blocks.STONE);
    }

    private static void tick(ServerPlayer p){p.tickCount++;WeaponCombatSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
    private static WeaponVisual visual(ServerPlayer p){return p.getData(ModAttachments.WEAPON_VISUAL);}
    private static void press(ServerPlayer p,boolean special,boolean held){WeaponCombatSystem.acceptInput(p,held&&!special,held&&special);}

    // Exercise the normal press/release path, including automatic release at full charge.
    private static double release(GameTestHelper h,ServerPlayer p,boolean special,WeaponMove expected,int strength){
        press(p,special,true);h.assertTrue(visual(p).spec()==expected && visual(p).charging(),"expected charge "+expected+" got "+visual(p).spec());
        var def=LocalWeaponMotion.find(expected.id).orElseThrow();var charge=def.charge().orElseThrow();
        int min=charge.minimumTicks()+(int)Math.ceil(def.transitionSeconds()*20-.00001);
        int held=strength==0?0:strength==1?min+charge.holdTicks()/2-1:min+charge.holdTicks();
        for(int i=0;i<held;i++){press(p,special,true);tick(p);}
        press(p,special,false);
        int wait=0;while(visual(p).charging() && wait++<12)tick(p);
        h.assertTrue(!visual(p).charging() && visual(p).spec()!=null,"release selected "+expected);
        double z=p.getZ();
        for(int i=0;i<12;i++)tick(p);
        double advance=p.getZ()-z;
        wait=0;while(WeaponCombatSystem.active(p)&&wait++<100)tick(p);
        h.assertTrue(!WeaponCombatSystem.active(p),"recovery terminates "+expected);
        return advance;
    }

    @GameTest(template="wall_test",timeoutTicks=90)
    public static void normalInputMeasuresEveryGroundChain(GameTestHelper h){
        floor(h);
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.MUSCLE_MASS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST}){
            WeaponMove first=WeaponMove.choose(form,WeaponMove.Input.ATTACK,false,false,false);
            for(int strength=0;strength<=2;strength++){
                var p=player(h,form,true);
                for(var move=first;move!=null;move=move.nextAttack()){
                    p.setPos(h.absoluteVec(new Vec3(4.5,75,2.5)));p.setOnGround(true);
                    double advance=release(h,p,false,move,strength);
                    System.out.printf(Locale.ROOT,"FORM_SAMPLE %s %s %d %.5f%n",form,move,strength,advance);
                    h.assertTrue(Double.isFinite(advance)&&Math.abs(advance)<16,"bounded ground step "+move);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=60)
    public static void sprintKickAndStandingSpecialUseCharge(GameTestHelper h){
        floor(h);
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.MUSCLE_MASS,PowerForm.BLADE,PowerForm.WHIPFIST}){
            for(int strength=0;strength<=2;strength++){
                var p=player(h,form,true);
                boolean sprint=form==PowerForm.NONE||form==PowerForm.MUSCLE_MASS;
                p.setSprinting(sprint);
                var move=WeaponMove.choose(form,!sprint,false,sprint,false);
                double advance=release(h,p,!sprint,move,strength);
                System.out.printf(Locale.ROOT,"FORM_SAMPLE %s %s %d %.5f%n",form,move,strength,advance);
                if(sprint && strength==2)h.assertTrue(advance>=9,"charged sprint kick must physically travel: "+advance);
            }
        }h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=60)
    public static void sprintKickCannotCrossWallOrClosedHeadroom(GameTestHelper h){
        floor(h);
        for(int x=3;x<=5;x++)for(int y=75;y<=79;y++)h.setBlock(x,y,7,Blocks.STONE);
        var p=player(h,PowerForm.NONE,true);p.setSprinting(true);
        var advance=release(h,p,false,WeaponMove.MUSCLE_RUSH,2);
        h.assertTrue(advance>3.8 && advance<=4.201,"body must stop at wall face "+advance);
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=90)
    public static void airborneSpecialRecoversAndEvades(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.NONE,PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST}){
            var p=player(h,form,false);var origin=p.position();
            WeaponCombatSystem.request(p,true);var move=visual(p).spec();
            h.assertTrue(move==(form==PowerForm.WHIPFIST?WeaponMove.WHIP_AIR:WeaponMove.AIR_KICK),"air special selection");
            int wait=0;
            while(WeaponCombatSystem.active(p)&&!WeaponCombatSystem.cancelableRecovery(p)&&wait++<100){
                PrototypeMovementSystem.acceptInput(p,false,true,0,1,false,false,true);
                h.assertTrue(WeaponCombatSystem.active(p),"protected charge, travel or contact "+move);
                p.move(MoverType.SELF,p.getDeltaMovement());tick(p);
            }
            double distance=p.position().subtract(origin).horizontalDistance();
            System.out.printf(Locale.ROOT,"FORM_AIR %s %s %.4f%n",form,move,distance);
            h.assertTrue(form==PowerForm.WHIPFIST?distance<.01:distance>5,"distinct flight "+form+" "+distance);
            h.assertTrue(WeaponCombatSystem.active(p)&&WeaponCombatSystem.cancelableRecovery(p),"has real recovery input window "+form);
            PrototypeMovementSystem.acceptInput(p,false,true,0,1,false,false,true);
            PrototypeMovementSystem.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            h.assertTrue(!WeaponCombatSystem.active(p)&&p.getDeltaMovement().x<-.8,"air dodge owns recovery "+form);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=350)
    public static void whipPullReachesHeavyBodyBeforeMount(GameTestHelper h){
        CombatMovementGameTest.loaded(h,()->{
            floor(h);var p=player(h,PowerForm.WHIPFIST,true);
            var mob=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,4,75,10);
            mob.setNoAi(true);mob.setHealth(1);var start=p.position();var victim=mob.position();
            CombatTargeting.requestLock(p,mob.getId());ConsumeSystem.request(p);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.WHIP_PULL,"heavy remote receiver enters whip pull");
            for(int i=0;i<13;i++){p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
            h.assertTrue(p.position().distanceTo(start)<.01 && p.getDeltaMovement().horizontalDistance()<.01,"extension precedes pulling");
            int ticks=0;
            while(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.WHIP_PULL && ticks++<115){
                p.move(MoverType.SELF,p.getDeltaMovement());p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            }
            double travel=p.position().subtract(start).horizontalDistance();
            h.assertTrue(travel>5&&travel<9 && victim.distanceTo(mob.position())<.02,"actor travels to anchored receiver "+travel);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.MOUNT,"pull hands control to mount only on physical approach");
            for(int i=0;i<60&&!ConsumeSystem.holding(p);i++){p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
            h.assertTrue(ConsumeSystem.holding(p)&&mob.isAlive(),"mount reaches holding stage without an automatic consume");
            System.out.printf(Locale.ROOT,"FORM_PULL heavy %.4f%n",travel);
            ConsumeSystem.releaseUncommitted(p,false);mob.discard();h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=350)
    public static void brokenWhipLineReleasesWithoutPassingWall(GameTestHelper h){
        CombatMovementGameTest.loaded(h,()->{
            floor(h);var p=player(h,PowerForm.WHIPFIST,true);
            var mob=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,4,75,10);
            mob.setNoAi(true);mob.setHealth(1);CombatTargeting.requestLock(p,mob.getId());ConsumeSystem.request(p);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.WHIP_PULL,"grab starts in the open lane");
            for(int x=3;x<=5;x++)for(int y=75;y<=79;y++)h.setBlock(x,y,6,Blocks.STONE);
            for(int i=0;i<14;i++){p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));}
            h.assertTrue(!ConsumeSystem.active(p)&&p.getZ()<h.absoluteVec(new Vec3(0,0,6)).z,"blocked tether releases the claim");
            mob.discard();h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=350)
    public static void sprintKickRespectsLockedBodyAtRange(GameTestHelper h){
        CombatMovementGameTest.loaded(h,()->{
            floor(h);var p=player(h,PowerForm.NONE,true);
            var mob=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,4,75,10);
            mob.setNoAi(true);var origin=p.position();CombatTargeting.requestLock(p,mob.getId());
            PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);p.setSprinting(true);
            press(p,false,true);
            h.assertTrue(visual(p).spec()==WeaponMove.MUSCLE_RUSH && visual(p).targetId()==mob.getId(),"lock selects body on the sprint path");
            int wait=0;while(visual(p).charging() && wait++<35){press(p,false,true);tick(p);}
            press(p,false,false);
            double closest=100;boolean overlap=false;
            for(int i=0;i<25;i++){tick(p);closest=Math.min(closest,mob.getZ()-p.getZ());overlap|=p.getBoundingBox().deflate(.01).intersects(mob.getBoundingBox());}
            h.assertTrue(mob.getHealth()<mob.getMaxHealth() && p.getZ()-origin.z>5 && closest>0 && !overlap,
                    "kick closes on distant body without overlap, gap="+closest+" travel="+(p.getZ()-origin.z)+" overlap="+overlap);
            mob.discard();h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=60)
    public static void forwardHeldPreservesSprintBladeBraking(GameTestHelper h){
        floor(h);
        for(int held=0;held<2;held++){
            var p=player(h,PowerForm.BLADE,true);
            PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);p.setSprinting(true);
            var origin=p.position();press(p,true,true);
            h.assertTrue(visual(p).spec()==WeaponMove.BLADE_RUSH,"sprint blade selected");
            if(held==0)press(p,true,false);
            int wait=0;while(visual(p).charging() && wait++<30)tick(p);
            press(p,true,false);for(int i=0;i<33;i++)tick(p);
            double advance=p.getZ()-origin.z;
            System.out.printf(Locale.ROOT,"FORM_BLADE sprint %d %.4f%n",held,advance);
            h.assertTrue(Math.abs(advance-(held==0?14.5:17.5)*.87)<.04,"forward held throughout sprint release and brake "+advance);
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=80)
    public static void unarmedPunchKickBranchesUseTheirOwnReleaseSteps(GameTestHelper h){
        floor(h);
        // Normal input strings cover both kicks off the jab and both forks off the knee.
        WeaponMove[][] routes={
                {WeaponMove.MUSCLE_JAB,WeaponMove.COMBO_KICK1,WeaponMove.COMBO_KICK2},
                {WeaponMove.COMBO_KNEE,WeaponMove.COMBO_SPIN,WeaponMove.COMBO_AXE,WeaponMove.COMBO_SPIN_LEFT},
                {WeaponMove.COMBO_KNEE,WeaponMove.COMBO_AXE_HANDLE,WeaponMove.COMBO_BACKHAND,WeaponMove.COMBO_MACH}
        };
        String[] inputs={"ASS","SSSS","SAAA"};
        for(int row=0;row<routes.length;row++){
            double[][] distances=new double[2][routes[row].length];
            for(int strength=0;strength<=1;strength++){
                var p=player(h,PowerForm.NONE,true);
                for(int i=0;i<routes[row].length;i++){
                    p.setPos(h.absoluteVec(new Vec3(4.5,75,2.5)));p.setOnGround(true);
                    distances[strength][i]=release(h,p,inputs[row].charAt(i)=='S',routes[row][i],strength*2);
                }
            }
            for(int i=0;i<routes[row].length;i++){
                var move=routes[row][i];double tap=distances[0][i],full=distances[1][i];
                System.out.printf(Locale.ROOT,"FORM_CHAIN %s %.5f %.5f%n",move,tap,full);
                h.assertTrue(full>tap+.35,"grounded contact advances with charge for "+move+" "+tap+" / "+full);
            }
        }h.succeed();
    }
}
