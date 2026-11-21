package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumeWhipGrabPathTest {
    static final Vec3 WRIST=new Vec3(.32,1.14,.17), RIGHT=new Vec3(1,0,0),UP=new Vec3(0,1,0),FORWARD=new Vec3(0,0,1);
    static WhipGrabPath path(Vec3 center,int stage,float age,float opening,float returning){
        return new WhipGrabPath(WRIST,RIGHT,FORWARD,center,UP,FORWARD,.6,1.95,WhipGrabPath.deployment(stage,age,opening,returning));
    }
    @Test void reachesBeforeTowingAndKeepsTheSameGripInHold(){
        for(double distance:new double[]{.75,7,12}){
            var center=new Vec3(0,1.2,distance);
            var caught=path(center,ConsumeVisual.CAPTURE,36,0,0);
            var held=path(center,ConsumeVisual.HOLD,0,0,0);
            var heavy=path(center,ConsumeVisual.WHIP_PULL,36,0,0);
            assertEquals(WRIST,caught.frame(0).center());
            for(int i=0;i<=WhipGrabPath.BANDS;i++){
                assertEquals(caught.frame(i),held.frame(i));assertEquals(caught.frame(i),heavy.frame(i));
                var f=held.frame(i);assertEquals(0,f.right().dot(f.tangent()),1e-6);
                assertEquals(1,f.right().cross(f.tangent()).dot(f.back()),1e-6);
                if(i>0)assertTrue(f.center().distanceTo(held.frame(i-1).center())<(distance+2)/WhipGrabPath.BANDS);
            }
            var hook=held.frame(WhipGrabPath.BANDS).center();
            assertTrue(hook.y>center.y+.2);assertTrue(hook.distanceTo(center)<.43);
            assertEquals(1,WhipGrabPath.deployment(ConsumeVisual.CAPTURE,14,0,0).reach());
        }
    }
    @Test void returnsToTheContactAndStopsWhenTheHandIsFree(){
        var center=new Vec3(.24,.99,1.77);
        var open=path(center,ConsumeVisual.CONSUME,0,1,0);
        assertEquals(0,open.frame(WhipGrabPath.BANDS).center().distanceTo(center),1e-9);
        for(int stage:new int[]{ConsumeVisual.MOBILE_CONSUME,ConsumeVisual.FLIGHT,ConsumeVisual.NONE})
            assertEquals(0,WhipGrabPath.deployment(stage,0,0,0).reach());
        assertEquals(0,WhipGrabPath.deployment(ConsumeVisual.THROW,6,0,0).reach());
        assertEquals(0,WhipGrabPath.deployment(ConsumeVisual.CONSUME,40,1,.35F).reach());
    }
}
