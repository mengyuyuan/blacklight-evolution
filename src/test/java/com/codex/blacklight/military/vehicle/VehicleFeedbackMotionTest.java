package com.codex.blacklight.military.vehicle;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleFeedbackMotionTest {
    @Test void roofHitUsesContactLeverAndCompressesSuspension(){
        var down=new Vec3(0,-1,0);
        var front=VehicleFeedbackMotion.sample("",-1,-1,0,1,1,down,new Vec3(0,0,1));
        var rear=VehicleFeedbackMotion.sample("",-1,-1,0,1,1,down,new Vec3(0,0,-1));
        var right=VehicleFeedbackMotion.sample("",-1,-1,0,1,1,down,new Vec3(1,0,0));
        assertTrue(front.pitch()>1&&rear.pitch()<-1);assertEquals(-front.pitch(),rear.pitch(),1e-9);
        assertTrue(right.roll()<-1&&front.heave()<-.08);assertEquals(0,right.pitch(),1e-9);
        var safe=VehicleFeedbackMotion.sample("",-1,-1,0,1,1000,down,new Vec3(1e9,0,1e9));
        assertTrue(Math.abs(safe.roll())<=4.5&&Math.abs(safe.pitch())<=4.5&&safe.heave()>=-.16);
    }
    @Test void noPreFireKickAndHydraulicsReturnToRest(){
        assertEquals(0,VehicleFeedbackMotion.recoil(-1));assertEquals(0,VehicleFeedbackMotion.recoil(0));
        assertEquals(1,VehicleFeedbackMotion.recoil(.65),1e-9);
        assertTrue(VehicleFeedbackMotion.recoil(3)>VehicleFeedbackMotion.recoil(8));
        assertEquals(0,VehicleFeedbackMotion.recoil(12));assertEquals(0,VehicleFeedbackMotion.recoil(Double.NaN));
    }
    @Test void bulletCannotRockLikeHeavyHitAndImpulseIsBounded(){
        var bullet=VehicleFeedbackMotion.strength(10,1000,VehicleFeedbackMotion.Impact.BULLET);
        var heavy=VehicleFeedbackMotion.strength(10,1000,VehicleFeedbackMotion.Impact.HEAVY);
        assertTrue(heavy>bullet*3);assertEquals(0,VehicleFeedbackMotion.strength(Float.NaN,1000,VehicleFeedbackMotion.Impact.HEAVY));
        for(double age=0;age<30;age+=.1){
            var p=VehicleFeedbackMotion.sample("tank_he",age,age,90,age,1000,new Vec3(1,0,1));
            assertTrue(Math.abs(p.pitch())<=4.5&&Math.abs(p.roll())<=4.5);
            if(age>=18){assertEquals(0,p.pitch(),1e-12);assertEquals(0,p.roll(),1e-12);assertEquals(0,p.heave(),1e-12);}
        }
    }
    @Test void hitDirectionAndTurretBearingChooseTheCorrectSpringAxis(){
        var left=VehicleFeedbackMotion.sample("",-1,-1,0,1,1,new Vec3(1,0,0));
        var right=VehicleFeedbackMotion.sample("",-1,-1,0,1,1,new Vec3(-1,0,0));
        assertEquals(-left.roll(),right.roll(),1e-9);assertEquals(0,left.pitch());
        var fire=VehicleFeedbackMotion.sample("tank_he",1,-1,90,-1,0,Vec3.ZERO);
        assertEquals(0,fire.pitch(),1e-9);assertTrue(Math.abs(fire.roll())>.1);
        var coax=VehicleFeedbackMotion.sample("",-1,1,0,-1,0,Vec3.ZERO);
        assertEquals(0,coax.primaryRecoil());assertTrue(coax.secondaryRecoil()>0);assertEquals(0,coax.pitch());
    }
}
