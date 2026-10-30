package com.codex.blacklight.infection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildingTissuePatternTest {
    @Test void deterministicPatternHasBroadRootsAndGaps() {
        for(boolean wall:new boolean[]{true,false}) {
            int covered=0,empty=0,roots=0,transitions=0;
            for(int u=-24;u<=24;u++)for(int v=0;v<=40;v++) {
                var p=BuildingTissuePattern.at(77251,u,v,wall);
                assertEquals(p,BuildingTissuePattern.at(77251,u,v,wall));
                assertTrue(p.variant()>=0&&p.variant()<=2);
                if(p.covered())covered++;else empty++;
                if(p.covered()&&p.variant()==2)roots++;
                if(p.covered()!=BuildingTissuePattern.at(77251,u+1,v,wall).covered())transitions++;
            }
            assertTrue(covered>300&&empty>300,"preserve substantial original facade as well as flesh");
            assertTrue(roots>40,"coherent thick main roots must exist");
            assertTrue(transitions<650,"neighbouring cells form sheets, not checkerboard flecks");
        }
    }
    @Test void differentSitesHaveDifferentLayout() {
        int different=0;
        for(int x=-16;x<=16;x++)for(int y=0;y<32;y++)
            if(!BuildingTissuePattern.at(1,x,y,true).equals(BuildingTissuePattern.at(999,x,y,true)))different++;
        assertTrue(different>200);
    }
}
