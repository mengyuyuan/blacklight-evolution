package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MovingCombatGameTest {
    static void supportFloor(GameTestHelper h,int y) {
        // Grounded fixtures need real support now that server action movement resolves collisions.
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)h.setBlock(x,y,z,Blocks.STONE);
    }
    static ServerPlayer player(GameTestHelper h,PowerForm form){
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"moving-qa"));
        p.setPos(h.absoluteVec(new Vec3(3,25,3)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
        return p;
    }
    static void moving(GameTestHelper h,boolean retreat,boolean air){
        for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST}){
            var p=player(h,form);p.setOnGround(!air);
            var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);
            mob.setPos(p.position().add(0,air?.25:0,form==PowerForm.WHIPFIST&&!air?4:2.15));
            float hp=mob.getHealth();var origin=p.position();
            WeaponCombatSystem.request(p,false);var move=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec();
            for(int t=1;t<=move.duration;t++){
                if(mob.getHealth()==hp)mob.setPos(mob.position().add(retreat?0:.105,0,retreat?.08:0));
                p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            }
            h.assertTrue(mob.getHealth()<hp,"must contact moving target "+form+" retreat="+retreat+" air="+air+" target="+mob.position().subtract(origin)+" player="+p.position().subtract(origin));
            WeaponCombatSystem.cancel(p);mob.discard();
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void strafingTargetsRemainInAttackPath(GameTestHelper h){moving(h,false,false);}
    @GameTest(template="wall_test",timeoutTicks=40) public static void retreatingTargetsReceiveBoundedPursuit(GameTestHelper h){moving(h,true,false);}
    @GameTest(template="wall_test",timeoutTicks=40) public static void airCombosFollowMovingVictims(GameTestHelper h){moving(h,false,true);}
    @GameTest(template="wall_test",timeoutTicks=40) public static void movingSmallBodiesAreNotIgnored(GameTestHelper h){
        for(var type:new EntityType<?>[]{EntityType.PIG,EntityType.CHICKEN})for(var form:new PowerForm[]{PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS}){
            var p=player(h,form);var mob=(Mob)h.spawn(type,3,25,5);mob.setNoAi(true);mob.setPos(p.position().add(0,0,1.7));float hp=mob.getHealth();
            WeaponCombatSystem.request(p,false);var move=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).spec();
            for(int t=1;t<=move.duration;t++){if(mob.getHealth()==hp)mob.setPos(mob.position().add(.055,0,0));p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
            h.assertTrue(mob.getHealth()<hp,"small moving body requires visible contact: "+type+" / "+form);WeaponCombatSystem.cancel(p);mob.discard();
        }h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=600,batch="ground_rear_escape")
    public static void pursuitCannotTurnBehindOrChaseEscapingTarget(GameTestHelper h){
        AerialSpecialGameTest.loadedFlight(h,()->checkRearEscape(h));
    }
    private static void checkRearEscape(GameTestHelper h){
        // Separate altitude from the flight fixtures, and remove the support
        // afterwards: GameTest only resets blocks inside the tiny template.
        for(int x=0;x<10;x++)for(int y=259;y<=265;y++)for(int z=-2;z<=12;z++)h.setBlock(x,y,z,Blocks.AIR);
        supportFloor(h,259);
        var p=player(h,PowerForm.BLADE);var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);mob.setPos(p.position().add(0,0,2.2));
        // Clear elevated lane, loaded before acquisition; a blocked rear ray must
        // not accidentally make this steering regression pass.
        p.setPos(h.absoluteVec(new Vec3(3,260,5)));mob.setPos(p.position().add(0,0,2.2));
        float hp=mob.getHealth();var origin=p.position();WeaponCombatSystem.request(p,false);
        h.assertTrue(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.WEAPON_VISUAL).targetId()==mob.getId(),"rear escape starts with the actual front target selected");
        mob.setPos(origin.add(0,0,-5));
        h.assertTrue(CombatTargeting.visible(p,mob),"rear escape must test steering, not occlusion");
        for(int t=0;t<WeaponMove.BLADE_CUT.duration+1;t++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
        var displacement=p.position().subtract(origin);
        var clip=com.codex.blacklight.animation.MotionLibrary.clip(WeaponMove.BLADE_CUT.id);
        double authoredPath=0;
        for(int frame=1;frame<=80;frame++)authoredPath+=com.codex.blacklight.animation.NativeRootMotion
                .clipStep(clip,clip.duration()*(frame-1)/80,clip.duration()*frame/80,0).length();
        h.assertTrue(mob.getHealth()==hp&&displacement.z>.25&&displacement.z<=authoredPath+.02
                &&Math.abs(displacement.x)<.05&&Math.abs(p.getYRot())<.1,
                "escaping behind cannot redirect the bounded authored forward lunge or guarantee damage: movement="+displacement+" authoredPath="+authoredPath);
        WeaponCombatSystem.cancel(p);mob.discard();
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)h.setBlock(x,259,z,Blocks.AIR);
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40) public static void pursuitCannotCrossSolidWall(GameTestHelper h){
        supportFloor(h,24);
        var p=player(h,PowerForm.CLAWS);var mob=h.spawn(EntityType.IRON_GOLEM,3,25,6);mob.setNoAi(true);mob.setPos(p.position().add(0,0,3));
        float hp=mob.getHealth();var origin=p.position();WeaponCombatSystem.request(p,false);
        for(int x=1;x<=5;x++)for(int y=24;y<=29;y++)h.setBlock(x,y,4,Blocks.STONE);
        for(int t=0;t<WeaponMove.CLAW_AUTO1.duration+1;t++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
        double wall=h.absoluteVec(new Vec3(0,0,4)).z;
        h.assertTrue(mob.getHealth()==hp&&p.getZ()>origin.z+.05&&p.getBoundingBox().maxZ<=wall+.00001,
                "authored lunge remains physical but cannot cross a solid wall or damage its obscured victim: movement="+p.position().subtract(origin));
        WeaponCombatSystem.cancel(p);mob.discard();h.succeed();
    }
}
