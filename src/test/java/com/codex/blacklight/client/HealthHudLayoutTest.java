package com.codex.blacklight.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HealthHudLayoutTest {
    @Test void defaultKeepsOriginalPlacementAndResizedHudStaysInsideScreen(){
        assertEquals(new HealthHudLayout.Position(10,10),HealthHudLayout.position(960,540,360,0,0));
        for(int width:new int[]{320,640,960})for(int height:new int[]{180,360,540}){
            var edge=HealthHudLayout.position(width,height,Math.min(width-20,360),1,1);
            assertTrue(edge.x()+Math.min(width-20,360)<=width-10);
            assertTrue(edge.y()+40<=height-10);
        }
    }
    @Test void invalidPlacementCannotEscapeScreen(){
        assertEquals(new HealthHudLayout.Position(10,10),HealthHudLayout.position(640,360,360,Double.NaN,-1));
        assertEquals(HealthHudLayout.position(640,360,360,1,1),HealthHudLayout.position(640,360,360,100,100));
    }
}
