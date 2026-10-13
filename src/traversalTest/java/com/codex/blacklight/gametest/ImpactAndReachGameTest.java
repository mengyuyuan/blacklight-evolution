package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.movement.PrototypeMovementSystem;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ImpactAndReachGameTest {
    private static ServerPlayer player(GameTestHelper h,PowerForm form){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"impact-range"));
        p.setPos(h.absoluteVec(new Vec3(3,2,1)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.unlock(form);s.activate(form);
        return p;
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void whipCapturesSmallAndLargeBodiesAtTwelveBlocks(GameTestHelper h){
        for(var type:new EntityType<?>[]{EntityType.PIG,EntityType.CHICKEN,EntityType.COW})for(double distance:new double[]{3,6,9,12}){
            var p=player(h,PowerForm.WHIPFIST);
            p.setPos(p.position().add(0,20,0));
            for(int x=-1;x<=1;x++)for(int y=0;y<=3;y++)for(int z=-1;z<=14;z++)
                h.getLevel().setBlockAndUpdate(p.blockPosition().offset(x,y,z),Blocks.AIR.defaultBlockState());
            var mob=(Mob)h.spawn(type,3,2,5);mob.setNoAi(true);mob.setPos(p.position().add(0,0,distance));
            var start=mob.position();ConsumeSystem.request(p);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.CAPTURE,"acquire "+type+" at "+distance);
            for(int i=0;i<36;i++){
                p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));
                if(i<13)h.assertTrue(mob.position().distanceToSqr(start)<1e-8,"cannot pull before whip reaches target");
            }
            h.assertTrue(ConsumeSystem.holding(p),"complete 12-block reel, not merely select: "+type+" / "+distance);
            p.tickCount++;ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            h.assertTrue(mob.position().distanceTo(ConsumePlacementTrack.carryAnchor(p.position(),p.getYRot(),mob,GrappleMotion.key(p,mob.getData(ModAttachments.CONSUME_VISUAL)),GrappleMotion.progress(p,mob.getData(ModAttachments.CONSUME_VISUAL),0)).orElseThrow())<.08,"arrive at animated hand");
            ConsumeSystem.cancel(p);mob.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void whipRejectsBeyondRangeAndSolidWall(GameTestHelper h){
        var p=player(h,PowerForm.WHIPFIST);var pig=h.spawn(EntityType.PIG,3,2,5);pig.setNoAi(true);
        pig.setPos(p.position().add(0,0,13));ConsumeSystem.request(p);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"13 blocks is outside grab reach");
        pig.setPos(p.position().add(0,0,10));
        for(int x=1;x<6;x++)for(int y=1;y<6;y++)h.setBlock(x,y,6,Blocks.STONE);
        ConsumeSystem.request(p);h.assertTrue(!p.getData(ModAttachments.CONSUMING),"cannot grab through solid wall");
        pig.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void runningJumpChargesWithoutBrakingAndReleasesOnce(GameTestHelper h){
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)h.setBlock(x,1,z,Blocks.STONE);
        var p=player(h,PowerForm.CLAWS);p.setDeltaMovement(0,0,.55);
        PrototypeMovementSystem.acceptInput(p,true,false,1,0,true,false);
        for(int i=0;i<12;i++){p.tickCount++;p.setOnGround(true);PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(p));}
        h.assertTrue(p.getData(ModAttachments.MOVEMENT_STATE).runningCharge(),"charge retains running mode");
        h.assertTrue(p.getDeltaMovement().horizontalDistance()>=.55,"running charge must not brake to a crouch");
        h.assertTrue(p.getData(ModAttachments.JUMP_CHARGE)>0,"leg charge visual is synchronized");
        PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);var launch=p.getDeltaMovement();
        h.assertTrue(!p.onGround()&&launch.y>.6&&launch.z>.55,"release launches up and retains running momentum");
        PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);
        h.assertTrue(p.getDeltaMovement().equals(launch)&&p.getData(ModAttachments.JUMP_CHARGE)==0,"release is consumed once and clears coil");
        p.getData(ModAttachments.MOVEMENT_STATE).reset();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void confirmedMeleeHitsStaggerAndOpenersKeepTargetClose(GameTestHelper h){
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS}){
            var p=player(h,form);var mob=h.spawn(EntityType.IRON_GOLEM,3,2,3);mob.setNoAi(true);mob.setPos(p.position().add(0,0,1.8));
            float before=mob.getHealth();WeaponCombatSystem.request(p,false);var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
            int hits=0;for(int t=1;t<=move.duration;t++){
                p.tickCount++;float old=mob.getHealth();WeaponCombatSystem.tick(new PlayerTickEvent.Post(p));
                if(mob.getHealth()<old){hits++;h.assertTrue(move.contactIndex(t)>=0,"damage only in contact window");}
            }
            h.assertTrue(hits==1&&mob.getHealth()<before,"one confirmed hit for "+form);
            h.assertTrue(mob.getData(ModAttachments.HIT_REACTION).duration()>0,"hit drives victim response: "+form);
            h.assertTrue(mob.getDeltaMovement().horizontalDistance()<=.23,"opener must not launch victim out of next attack");
            WeaponCombatSystem.cancel(p);mob.discard();
        }
        h.succeed();
    }
}
