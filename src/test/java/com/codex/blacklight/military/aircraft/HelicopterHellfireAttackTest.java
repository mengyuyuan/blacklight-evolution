package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterHellfireAttackTest {
    private static HelicopterHellfireAttack ready(){var a=new HelicopterHellfireAttack();for(int i=0;i<300;i++)a.tick(true,true);return a;}
    @Test void initialAvoidThenSeparateWaitAimPulseAndRecovery(){
        var a=new HelicopterHellfireAttack();for(int i=0;i<300;i++){assertFalse(a.ready());a.tick(true,true);}assertTrue(a.begin(20,40));
        for(int i=0;i<20;i++){assertEquals(HelicopterHellfireAttack.Stage.WAIT,a.stage());assertFalse(a.wantsShot());a.tick(true,true);}
        for(int i=0;i<7;i++){assertEquals(HelicopterHellfireAttack.Stage.AIM,a.stage());a.tick(true,false);}assertFalse(a.wantsShot());a.tick(true,true);
        for(int i=0;i<4;i++){assertTrue(a.wantsShot());a.tick(true,true);}assertEquals(HelicopterHellfireAttack.Stage.RECOVER,a.stage());
        for(int i=0;i<40;i++){assertTrue(a.hovering());a.tick(true,true);}assertFalse(a.hovering());assertEquals(128,a.avoidTicks());
        for(int i=0;i<128;i++)a.tick(true,true);assertTrue(a.ready());
    }
    @Test void oneAcceptedShotIsLatchedAcrossNativePersistence(){
        var a=ready();a.begin(20,20);for(int i=0;i<21;i++)a.tick(true,true);assertTrue(a.wantsShot());a.shot();
        var b=new HelicopterHellfireAttack();b.load(a.save());assertEquals(a.stage(),b.stage());assertEquals(a.avoidTicks(),b.avoidTicks());assertFalse(b.wantsShot());
        for(int i=0;i<4;i++){assertFalse(b.wantsShot());b.tick(true,true);}assertEquals(HelicopterHellfireAttack.Stage.RECOVER,b.stage());
    }
    @Test void visibilityLossAndFiveSecondReevaluationCancelPendingAction(){
        var a=ready();a.begin(40,40);a.tick(false,true);assertFalse(a.hovering());assertFalse(a.wantsShot());assertEquals(199,a.avoidTicks());
        a=ready();a.begin(20,20);for(int i=0;i<99;i++)a.tick(true,false);assertEquals(HelicopterHellfireAttack.Stage.AIM,a.stage());a.tick(true,false);assertFalse(a.hovering());assertEquals(100,a.avoidTicks());
    }
    @Test void sourceHorizontalDotToleranceIsNotAnAngle(){
        assertTrue(HelicopterHellfireAttack.aimed(new Vec3(0,0,1),new Vec3(.4,100,1)));
        assertFalse(HelicopterHellfireAttack.aimed(new Vec3(0,0,1),new Vec3(.6,0,1)));
        assertFalse(HelicopterHellfireAttack.aimed(new Vec3(0,0,1),new Vec3(0,0,-1)));
        assertFalse(HelicopterHellfireAttack.aimed(Vec3.ZERO,new Vec3(0,0,1)));
        assertFalse(HelicopterHellfireAttack.aimed(new Vec3(Double.NaN,0,1),new Vec3(0,0,1)));
    }
    @Test void preparationWaitAndAvoidAreNotResetByReload(){
        var a=ready();a.begin(40,20);for(int i=0;i<13;i++)a.tick(true,true);var b=new HelicopterHellfireAttack();b.load(a.save());
        for(int i=0;i<27;i++){assertEquals(HelicopterHellfireAttack.Stage.WAIT,b.stage());b.tick(true,true);}assertEquals(HelicopterHellfireAttack.Stage.AIM,b.stage());assertEquals(160,b.avoidTicks());
        assertThrows(IllegalArgumentException.class,()->ready().begin(0,20));
    }
    @Test void malformedStageCannotRestoreAnImmediateFire(){
        var a=ready();a.begin(20,20);var tag=a.save();tag.putString("Stage","FIRE");tag.putInt("Remaining",40);
        var b=new HelicopterHellfireAttack();b.load(tag);assertFalse(b.hovering());assertEquals(300,b.avoidTicks());
        tag=a.save();tag.putInt("HoverAge",100);b.load(tag);assertFalse(b.hovering());assertEquals(300,b.avoidTicks());
    }
}
