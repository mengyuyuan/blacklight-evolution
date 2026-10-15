package com.codex.blacklight.city;

import java.util.*;

/** Deterministic centre-out order; the cursor can be saved without a giant coordinate list. */
final class CityGenerationPlan {
    record Cell(int x,int z){}
    private static final List<Cell> FULL=build(false),ARRIVAL=build(true);
    static List<Cell> cells(int mode){return mode==2?FULL:ARRIVAL;}
    private static List<Cell> build(boolean arrival){
        var cells=new ArrayList<Cell>();
        for(int z=0;z<FutianMap.DEPTH/16;z++)for(int x=0;x<FutianMap.WIDTH/16;x++)
            if(!arrival||Math.max(Math.abs(x-41),Math.abs(z-51))<=3)cells.add(new Cell(x,z));
        cells.sort(Comparator.<Cell>comparingInt(c->Math.max(Math.abs(c.x-41),Math.abs(c.z-51)))
            .thenComparingInt(c->Math.abs(c.x-41)+Math.abs(c.z-51)).thenComparingInt(Cell::z).thenComparingInt(Cell::x));
        return List.copyOf(cells);
    }
    private CityGenerationPlan(){}
}
