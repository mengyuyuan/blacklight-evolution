package com.codex.blacklight.infection;

import java.util.*;

/** A persistent flood across all six actual cube faces; randomness chooses neighbours, never frames. */
public final class TissueSurfaceLayout {
    public record Cell(int face, int u, int v, int rank) {}
    private static final List<List<Cell>> PATTERNS = patterns();
    private TissueSurfaceLayout() {}

    public static List<Cell> cells(long seed) { return PATTERNS.get((int)(TissueGrowth.mix(seed) & 31)); }
    public static float reveal(float growth, int rank) {
        float t = Math.clamp((growth - rank / 24F * .72F) / .28F, 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static boolean adjacent(Cell a, Cell b) {
        float[] x = centre(a), y = centre(b);
        float distance = 0;
        for (int i=0;i<3;i++) distance += (x[i]-y[i])*(x[i]-y[i]);
        return distance > .01F && distance <= 1.01F;
    }
    private static float[] centre(Cell c) {
        int axis=c.face/2, a=(axis+1)%3, b=(axis+2)%3;
        float[] result=new float[3];
        result[axis]=c.face%2==0?-1:1;
        result[a]=c.u==0?-.5F:.5F;
        result[b]=c.v==0?-.5F:.5F;
        return result;
    }
    private static List<List<Cell>> patterns() {
        var result=new ArrayList<List<Cell>>();
        for(int pattern=0;pattern<32;pattern++) {
            var all=new ArrayList<Cell>();
            for(int face=0;face<6;face++)for(int u=0;u<2;u++)for(int v=0;v<2;v++)all.add(new Cell(face,u,v,0));
            var frontier=new ArrayList<Integer>();boolean[] visited=new boolean[24];
            var ordered=new ArrayList<Cell>();int step=0;
            frontier.add((int)(TissueGrowth.sample(pattern,0)*24));
            while(!frontier.isEmpty()) {
                int index=frontier.remove((int)(TissueGrowth.sample(pattern,++step)*frontier.size()));
                if(visited[index])continue;
                visited[index]=true;var cell=all.get(index);
                ordered.add(new Cell(cell.face,cell.u,cell.v,ordered.size()));
                for(int next=0;next<24;next++)
                    if(!visited[next]&&adjacent(cell,all.get(next))&&!frontier.contains(next))frontier.add(next);
            }
            if(ordered.size()!=24)throw new IllegalStateException("Disconnected tissue surface");
            result.add(List.copyOf(ordered));
        }
        return List.copyOf(result);
    }
}
