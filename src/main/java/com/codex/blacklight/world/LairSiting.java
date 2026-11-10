package com.codex.blacklight.world;

/** Pure Lair siting rules (no world access) so they can be unit tested. */
public final class LairSiting {
    private LairSiting(){}
    /** Hall interior height plus the rock cover kept over its ceiling. */
    public static final int HALL=20,COVER=8,DROP=HALL+COVER;
    /** Longest drop shaft accepted when the footprint spans a valley. */
    public static final int MAX_SHAFT=64;
    /** Pustule ground may vary this much around the entrance. */
    public static final int MOUND_STEP=3;
    /** Candidate offsets around a rolled chunk, spaced so the mound footprints never overlap. */
    public static final int STEP=12,REACH=36;
    /** The hall floor follows the lowest covering ground in the footprint, not the entrance height.
     * Returns Integer.MIN_VALUE when the hall cannot fit. */
    public static int floor(int surface,int lowestCover,int minBuild){
        int floor=Math.min(surface,lowestCover)-DROP;
        if(floor-4<=minBuild+6)return Integer.MIN_VALUE;
        if(surface-floor>MAX_SHAFT)return Integer.MIN_VALUE;
        return floor;
    }
    public static double chance(double days,double perDay,double max){
        if(!(days>=0))days=0;
        return Math.max(0,Math.min(max,perDay*(1+days)));
    }
    /** Offsets ordered nearest first; the first entry is the rolled point itself. */
    public static java.util.List<int[]> offsets(){
        var list=new java.util.ArrayList<int[]>();
        for(int x=-REACH;x<=REACH;x+=STEP)for(int z=-REACH;z<=REACH;z+=STEP)list.add(new int[]{x,z});
        list.sort(java.util.Comparator.comparingInt(o->o[0]*o[0]+o[1]*o[1]));
        return list;
    }
}
