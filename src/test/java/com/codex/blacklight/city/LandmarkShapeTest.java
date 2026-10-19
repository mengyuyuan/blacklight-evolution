package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LandmarkShapeTest {
    @Test void crownsNarrowToAnApexRatherThanRepeatingTheFootprint(){
        for(int p:new int[]{1,2}){
            assertTrue(LandmarkShape.scale(p,.95)<LandmarkShape.scale(p,.7));
            assertTrue(LandmarkShape.scale(p,1)<.05);
            for(int i=0;i<=1000;i++)assertTrue(LandmarkShape.scale(p,i/1000.0)>0);
        }
    }
    @Test void bambooAndChamferedTowerHaveDistinctCrossSections(){
        assertEquals(1,LandmarkShape.radius(2,.6,.8),1e-9);
        assertTrue(LandmarkShape.radius(1,1,1)>1);
        assertEquals(1,LandmarkShape.radius(1,1,0),1e-9);
    }
}
