package com.codex.blacklight.infection;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.infection.TissueMorphogenesis.*;

class TissueTongueShapeTest {
    private static float[] mesh(){
        var bone=new Bone("head",1,new Box(new V(-3,-4,-3),new V(3,4,3)),new V(0,-1,0),new V(0,0,-1),new V(1,0,0),new V(0,0,0));
        return TissueMorphogenesis.functional(TissueCore.TONGUE,bone,0,42).get(0).stages()[2].clone();
    }
    private static V centre(float[] points,int ring){V sum=new V(0,0,0);for(int i=ring*24;i<ring*24+24;i+=3)sum=sum.add(new V(points[i],points[i+1],points[i+2]));return sum.mul(.125F);}
    @Test void actualPreyEndpointCanBeAboveBehindOrBesideTheMouthWithoutMovingItsRoot(){
        for(var target:new V[]{new V(80,-12,-40),new V(0,-90,0),new V(0,3,80),new V(-50,7,0)}){
            float[] points=mesh(),root=Arrays.copyOf(points,24);TissueTongueShape.aim(points,target,1);
            assertArrayEquals(root,Arrays.copyOf(points,24),0,"the native bone/root is never displaced");
            var tip=centre(points,points.length/24-1);assertTrue(tip.sub(target).length()<.0001);
            for(float p:points)assertTrue(Float.isFinite(p));
        }
    }
    @Test void castAndRetractionRemainContinuousOnTheServerActionClock(){
        var c=TissueOrganCycle.cycle(TissueCore.TONGUE);
        assertEquals(0,TissueTongueShape.extension(-1));assertEquals(0,TissueTongueShape.extension(c.windup()));
        assertEquals(1,TissueTongueShape.extension(c.contact()));assertEquals(1,TissueTongueShape.extension(c.retract()));
        assertEquals(0,TissueTongueShape.extension(c.end()));assertEquals(0,TissueTongueShape.extension(Float.NaN));
        float previous=0;for(float tick=0;tick<=c.end();tick+=.1F){float e=TissueTongueShape.extension(tick);assertTrue(Math.abs(e-previous)<.025);previous=e;}
    }
    @Test void endpointDoesNotChangeWithRenderFrameHistoryAndKeepsItsOwnSectionWidth(){
        var target=new V(80,5,0);float[] one=mesh(),two=mesh();
        TissueTongueShape.aim(one,target,1);TissueTongueShape.aim(two,target,1);assertArrayEquals(one,two,0);
        float[] source=mesh();int last=one.length/24-1;float widthBefore=new V(source[last*24],source[last*24+1],source[last*24+2]).sub(centre(source,last)).length();
        float widthAfter=new V(one[last*24],one[last*24+1],one[last*24+2]).sub(centre(one,last)).length();assertEquals(widthBefore,widthAfter,.0001);
    }
    @Test void aCollapsedOrInvalidTransformCannotMakeAnInfiniteTongue(){
        float[] points=mesh(),old=points.clone();TissueTongueShape.aim(points,new V(Float.NaN,0,0),1);assertArrayEquals(old,points,0);
        TissueTongueShape.aim(points,new V(10,0,0),0);assertArrayEquals(old,points,0);
    }
}
