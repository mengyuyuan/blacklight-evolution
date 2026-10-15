package com.codex.blacklight.city;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CityGenerationPlanTest {
    @Test void cityIsCoveredExactlyOnceCentreOut(){
        var cells=CityGenerationPlan.cells(2);assertEquals(6930,cells.size());assertEquals(6930,new java.util.HashSet<>(cells).size());
        assertEquals(new CityGenerationPlan.Cell(41,51),cells.get(0));
        int previous=-1;
        for(var c:cells){assertTrue(c.x()>=0&&c.x()<90&&c.z()>=0&&c.z()<77);
            int distance=Math.max(Math.abs(c.x()-41),Math.abs(c.z()-51));assertTrue(distance>=previous);previous=distance;}
    }
    @Test void arrivalIsOnlySevenBySevenChunks(){
        var cells=CityGenerationPlan.cells(1);assertEquals(49,cells.size());
        assertTrue(cells.stream().allMatch(c->Math.abs(c.x()-41)<=3&&Math.abs(c.z()-51)<=3));
        assertEquals(cells,CityGenerationPlan.cells(2).subList(0,49));
    }
}
