package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueSurfaceLayoutTest {
    @Test void growthTraversesEveryFaceWithoutDetachedIslands() {
        for(long seed=0;seed<100;seed++) {
            var cells=TissueSurfaceLayout.cells(seed);
            assertEquals(24,cells.size());
            var unique=new java.util.HashSet<String>();
            for(int i=0;i<cells.size();i++) {
                var current=cells.get(i);
                assertEquals(i,current.rank());
                assertTrue(unique.add(current.face()+":"+current.u()+":"+current.v()));
                if(i>0)assertTrue(cells.subList(0,i).stream().anyMatch(other->TissueSurfaceLayout.adjacent(current,other)));
                assertEquals(0,TissueSurfaceLayout.reveal(0,i));
                assertEquals(1,TissueSurfaceLayout.reveal(1,i));
            }
        }
    }
    @Test void coverageNeverShrinksOrRerollsAndDifferentHostsVary() {
        assertEquals(TissueSurfaceLayout.cells(7242),TissueSurfaceLayout.cells(7242));
        assertTrue(java.util.stream.LongStream.range(1,100).anyMatch(seed->!TissueSurfaceLayout.cells(0).equals(TissueSurfaceLayout.cells(seed))));
        for(int rank=0;rank<24;rank++) {
            float previous=0;
            for(int step=0;step<=100;step++) {
                float value=TissueSurfaceLayout.reveal(step/100F,rank);
                assertTrue(value>=previous&&value<=1);previous=value;
            }
        }
    }
}
