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
    @Test void compactBossFightSeparatesStackedBarsAndShield() {
        var boss=new HealthHudLayout.Bounds(97,0,403,38);
        var defense=new HealthHudLayout.Bounds(97,40,403,50);
        var shield=new HealthHudLayout.Bounds(10,40,110,55);
        var panels=java.util.List.of(boss,defense,shield);
        var anchor=new HealthHudLayout.Position(10,10);
        var result=HealthHudLayout.avoid(anchor,160,24,293,panels);
        assertEquals(new HealthHudLayout.Position(10,58),result);
        assertEquals(anchor,HealthHudLayout.avoid(anchor,160,24,293,java.util.List.of()));
        assertEquals(anchor,HealthHudLayout.avoid(anchor,160,24,540,
                java.util.List.of(new HealthHudLayout.Bounds(326,0,634,38))));
    }
    @Test void customMeterAndReserveClearQteChargeLockPanelAndHotbar() {
        for(int width:new int[]{320,500,854,1280})for(int height:new int[]{240,320,480}){
            int x=width/2,y=height/2,meter=HealthHudLayout.meterWidth(width);
            var panels=java.util.List.of(new HealthHudLayout.Bounds(x-70,y+15,x+70,y+34),
                    new HealthHudLayout.Bounds(x-80,y+42,x+80,y+72),
                    new HealthHudLayout.Bounds(4,height-112,194,height-96),
                    new HealthHudLayout.Bounds(x-98,height-46,x+98,height));
            var anchor=new HealthHudLayout.Position(10,y+18);
            var result=HealthHudLayout.avoid(anchor,meter*2,34,height,panels);
            for(var panel:panels)assertFalse(panel.overlaps(result.x(),result.y(),meter*2,34));
            assertTrue(result.y()>=4 && result.y()+34<=height-4);
        }
    }
}
