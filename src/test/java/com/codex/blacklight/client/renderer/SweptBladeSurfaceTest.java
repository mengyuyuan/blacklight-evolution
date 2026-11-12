package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SweptBladeSurfaceTest {
    @Test void finiteBladeCannotSeverAlongAnInfiniteExtension(){
        var cut=new SweptBladeSurface(List.of(new BladeSpan(new Vec3(-1,0,-1),new Vec3(1,0,-1)),new BladeSpan(new Vec3(-1,0,1),new Vec3(1,0,1))));
        assertTrue(cut.covers(Vec3.ZERO));assertFalse(cut.covers(new Vec3(3,0,0)));
        assertTrue(cut.distance(new Vec3(0,.3,0))*cut.distance(new Vec3(0,-.3,0))<0);
        assertFalse(cut.covers(new Vec3(0,0,0),new Vec3(3,0,0)));
    }
    @Test void curvedStrokeFollowsItsRibbonAndRejectsAChordOutsideIt(){
        var path=new ArrayList<BladeSpan>();for(int i=0;i<=16;i++){
            double angle=Math.PI*.5*i/16;path.add(new BladeSpan(new Vec3(Math.cos(angle),Math.sin(angle),-1),new Vec3(Math.cos(angle),Math.sin(angle),1)));
        }
        var cut=new SweptBladeSurface(path);double d=Math.sqrt(.5);
        assertTrue(cut.covers(new Vec3(d,d,0)));
        assertTrue(cut.distance(new Vec3(d*.8,d*.8,0))*cut.distance(new Vec3(d*1.2,d*1.2,0))<0);
        assertFalse(cut.covers(new Vec3(1,0,0),new Vec3(0,1,0)));
        assertTrue(Math.abs(cut.normal(new Vec3(d,d,0)).dot(new Vec3(d,d,0)))>.99);
    }
    @Test void stationaryAndDegenerateSamplesCannotInventACut(){
        var still=new BladeSpan(Vec3.ZERO,new Vec3(0,1,0));
        var cut=new SweptBladeSurface(List.of(still,still));assertTrue(cut.empty());assertFalse(cut.covers(Vec3.ZERO));
    }
    @Test void curvedBladeSweepsItsProfileInsteadOfAChordThroughTheHilt(){
        var a=new BladeSpan(List.of(new Vec3(-1,.5,-1),new Vec3(0,0,-1),new Vec3(1,.5,-1)));
        var b=new BladeSpan(List.of(new Vec3(-1,.5,1),new Vec3(0,0,1),new Vec3(1,.5,1)));
        var cut=new SweptBladeSurface(List.of(a,b));assertTrue(cut.covers(Vec3.ZERO));assertTrue(cut.covers(new Vec3(.5,.25,0)));
        assertFalse(cut.covers(new Vec3(0,.5,0)));assertTrue(cut.distance(new Vec3(0,.1,0))*cut.distance(new Vec3(0,-.1,0))<0);
    }
}
