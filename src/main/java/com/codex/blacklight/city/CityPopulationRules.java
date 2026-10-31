package com.codex.blacklight.city;

/** Independent civilian/infected budgets with a combined safety ceiling. Existing bodies are never culled. */
public final class CityPopulationRules {
    private CityPopulationRules(){}
    public static int slots(int total,int infected,int civilianLimit,int infectedLimit,boolean infectedBirth){
        // Preserve the old zero-as-disabled city setting.
        if(civilianLimit<=0)return 0;
        total=Math.max(0,total);infected=com.codex.blacklight.forge.WingMath.clamp(infected,0,total);infectedLimit=Math.max(0,infectedLimit);
        long totalRoom=Math.max(0,(long)civilianLimit+infectedLimit-total);
        int poolRoom=Math.max(0,infectedBirth?infectedLimit-infected:civilianLimit-(total-infected));
        return (int)Math.min(totalRoom,poolRoom);
    }
}
