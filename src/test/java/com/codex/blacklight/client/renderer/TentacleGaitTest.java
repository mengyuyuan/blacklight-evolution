package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TentacleGaitTest {
    @Test void plantedFeetStayInWorldSpaceUntilTheirAlternatingGroupSteps() {
        for(int count:new int[]{4,6,8}) {
            var gait=new TentacleGait(count);gait.update(0,Vec3.ZERO,0,true,1,p->new Vec3(p.x,0,p.z));
            Vec3[] feet=new Vec3[count];for(int i=0;i<count;i++)feet[i]=gait.tip(i);
            gait.update(1,new Vec3(.1,0,0),0,true,1,p->new Vec3(p.x,0,p.z));
            for(int i=0;i<count;i++)assertEquals(feet[i],gait.tip(i),"feet must not slide with the torso");
            gait.update(2,new Vec3(.5,0,0),0,true,1,p->new Vec3(p.x,0,p.z));
            int support=0;for(int i=0;i<count;i++)if(!gait.swinging(i)){support++;assertEquals(feet[i],gait.tip(i));}
            assertEquals(count/2,support,"half the feet remain planted during a step");
            gait.update(4,new Vec3(.65,0,0),0,true,1,p->new Vec3(p.x,0,p.z));
            assertTrue(gait.tip(0).y>0,"swing tips lift above their ground contacts");
            gait.update(9,new Vec3(.65,0,0),0,true,1,p->new Vec3(p.x,0,p.z));
            assertEquals(0,gait.tip(0).y,.00001);
        }
    }
    @Test void teleportAirborneAndOldEightArmCallersResetWithoutStretching() {
        var gait=new TentacleGait();assertEquals(8,gait.count());
        gait.update(0,Vec3.ZERO,0,true,1,p->new Vec3(p.x,0,p.z));
        var destination=new Vec3(100,5,100);gait.update(1,destination,90,true,1,p->new Vec3(p.x,5,p.z));
        for(int i=0;i<8;i++)assertTrue(gait.tip(i).distanceTo(destination)<1.5);
        gait.update(2,destination,90,false,1,p->p);
        for(int i=0;i<8;i++)assertFalse(gait.swinging(i));
    }
}
