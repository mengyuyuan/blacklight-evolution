package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HammerImpactGameTest {
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void groundImpactHitsBehindButNotThroughWall(GameTestHelper h){
        MovingCombatGameTest.supportFloor(h,24);
        var p=MovingCombatGameTest.player(h,PowerForm.HAMMERFISTS);
        var rear=h.spawn(EntityType.IRON_GOLEM,3,25,1);rear.setNoAi(true);
        var blocked=h.spawn(EntityType.IRON_GOLEM,3,25,6);blocked.setNoAi(true);
        for(int x=0;x<=7;x++)for(int y=24;y<=29;y++)h.setBlock(x,y,5,Blocks.STONE);
        float hp=rear.getHealth(),bhp=blocked.getHealth();
        WeaponCombatSystem.request(p,true);
        for(int t=0;t<WeaponMove.HAMMER_QUAKE.duration+2;t++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
        h.assertTrue(rear.getHealth()<hp,"quake must hit behind independently of fist collision");
        h.assertTrue(blocked.getHealth()==bhp,"quake cannot damage through solid walls");
        WeaponCombatSystem.cancel(p);rear.discard();blocked.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void basicHammerHasContactSplash(GameTestHelper h){
        var p=MovingCombatGameTest.player(h,PowerForm.HAMMERFISTS);
        var main=h.spawn(EntityType.IRON_GOLEM,3,25,5);main.setNoAi(true);
        var side=h.spawn(EntityType.IRON_GOLEM,5,25,5);side.setNoAi(true);
        side.setPos(main.position().add(1.8,0,0));
        float hp=main.getHealth(),sideHp=side.getHealth();WeaponCombatSystem.request(p,false);
        for(int t=0;t<WeaponMove.HAMMER_JAB.duration+2;t++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
        h.assertTrue(main.getHealth()<hp&&side.getHealth()<sideHp,"ordinary hammer hit must disperse adjacent crowd");
        h.assertTrue(hp-main.getHealth()<=WeaponMove.HAMMER_JAB.damage*5+.01,"splash must not double damage primary victim");
        WeaponCombatSystem.cancel(p);main.discard();side.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void massiveTargetTakesImpactWithoutBeingLaunched(GameTestHelper h){
        var p=MovingCombatGameTest.player(h,PowerForm.HAMMERFISTS);
        var light=h.spawn(EntityType.PIG,3,25,5);var heavy=h.spawn(EntityType.RAVAGER,6,25,5);
        WeaponImpact.apply(p,light,WeaponMove.HAMMER_QUAKE,new Vec3(0,0,1),false);
        WeaponImpact.apply(p,heavy,WeaponMove.HAMMER_QUAKE,new Vec3(0,0,1),false);
        h.assertTrue(light.getDeltaMovement().y>.3,"small victim lifts visibly");
        h.assertTrue(heavy.getDeltaMovement().horizontalDistance()<.08&&heavy.getDeltaMovement().y==0,"massive target stays grounded with small displacement");
        light.discard();heavy.discard();h.succeed();
    }
}
