package com.codex.blacklight.infection;

/** One inherited primary organ per host. Generations remodel it without rerolling its identity. */
public enum TissueCore {
    MUSCLE("organ_muscle"), CARAPACE("organ_carapace"), JAW("organ_jaw"), GRASP("organ_grasp"), CREST("organ_crest");
    public final String id;
    TissueCore(String id){this.id=id;}
    public static TissueCore forSeed(long seed){return values()[(int)(TissueGrowth.sample(TissueGrowth.mix(seed),846)*values().length)];}
    private static final int[][] VISUAL_SEEDS=visualSeeds();
    /** Four shapes per primary organ: a finite cache that cannot silently
     * give a jaw host the armour/crest kit of an unrelated truncated seed. */
    public static int visualSeed(long seed){return VISUAL_SEEDS[forSeed(seed).ordinal()][(int)(TissueGrowth.mix(seed)&3)];}
    private static int[][] visualSeeds(){
        int[][] seeds=new int[values().length][4];int[] count=new int[values().length];int remaining=values().length*4;
        for(int seed=0;remaining>0;seed++){
            int core=forSeed(seed).ordinal();if(count[core]<4){seeds[core][count[core]++]=seed;remaining--;}
        }
        return seeds;
    }
    public static TissueCore byId(String id){for(var core:values())if(core.id.equals(id))return core;return null;}
    public static int quality(long age){return age>=816000?3:age>=240000?2:age>=72000?1:0;}
    /** Resource yield grows slowly, independently of arbitrary health edits or body size. */
    public static float yieldMultiplier(long age){return (float)(1+Math.min(2,.35*Math.log1p(Math.max(0,age)/12000D)));}
    public static int cost(int nextRank){return switch(nextRank){case 1->200;case 2->400;case 3->700;default->0;};}
}
