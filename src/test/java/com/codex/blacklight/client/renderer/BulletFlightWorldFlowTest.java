package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.DevastatorEffectTimeline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletFlightWorldFlowTest {
    @Test void airRemainsInWorldAsActorAndCameraFallPastIt(){
        var optics=DevastatorEffectTimeline.flight(10,2,1);
        var first=new BulletFlightVisualRenderer.Frame(22,1,10,2,new Vec3(4,100,-5),new Vec3(0,1,0),optics);
        var later=new BulletFlightVisualRenderer.Frame(22,1.02,10.4,2,new Vec3(4,99.6,-5),new Vec3(0,1,0),optics);
        for(int branch:new int[]{0,1,3,8,12}){
            var a=BulletFlightVisualRenderer.airStart(first,branch);var b=BulletFlightVisualRenderer.airStart(later,branch);
            // Stay away from the wrapped/faded ends of the six-block shell.
            if(Math.abs(a.y-b.y)>1)continue;
            assertEquals(a.x,b.x,1e-9);assertEquals(a.y,b.y,1e-9);assertEquals(a.z,b.z,1e-9);
            assertEquals(.4,(b.y-later.origin().y)-(a.y-first.origin().y),1e-9);
            assertTrue(a.subtract(first.origin()).horizontalDistance()>=.85);
        }
    }
}
