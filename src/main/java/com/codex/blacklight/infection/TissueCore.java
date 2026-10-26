package com.codex.blacklight.infection;

/** Stable native bias. Additional earned organs coexist in TissueInheritance. */
public enum TissueCore {
    MUSCLE("organ_muscle"), CARAPACE("organ_carapace"), JAW("organ_jaw"), GRASP("organ_grasp"), CREST("organ_crest"),
    TENDON("elastic_tendon"), HYDRAULIC("hydraulic_extensor"), HYDROSTAT("muscular_hydrostat"),
    BLADE("cutting_blade"), CHELA("chela"), HORN("ramming_horn"), PHARYNX("pharyngeal_jaw"),
    SADDLE("saddle_spring"), CLUB("dactyl_club"), TONGUE("ballistic_tongue"), CNIDOCYTE("cnidocyte_battery"),
    GIZZARD("gizzard"), FERMENT("fermentation_chamber"), ABSORPTIVE("absorptive_gut"), PANCREAS("digestive_pancreas"), CROP("crop"), FAT("fat_body"),
    EXTRAORAL("extraoral_gland"), GILL_RAKER("gill_raker"),
    GILL_LAMELLA("gill_lamella"), LUNG("bronchial_lung"), AIRFLOW("avian_airflow"), BOOK_LUNG("book_lung"), TRACHEA("tracheal_network"),
    HEART("main_heart"), BRANCHIAL_HEART("branchial_heart"), SPLEEN("splenic_reservoir");
    public final String id;
    TissueCore(String id){this.id=id;}
    /** Frozen aggregate.2 mapping. Never use the expanded enum length here. */
    public static TissueCore forSeed(long seed){return values()[(int)(TissueGrowth.sample(TissueGrowth.mix(seed),846)*5)];}
    public boolean legacy(){return ordinal()<5;}
    public boolean digestive(){return switch(this){case GIZZARD,FERMENT,ABSORPTIVE,PANCREAS,CROP,FAT,GILL_RAKER->true;default->false;};}
    public boolean vital(){return switch(this){case GILL_LAMELLA,LUNG,AIRFLOW,BOOK_LUNG,TRACHEA,HEART,BRANCHIAL_HEART,SPLEEN->true;default->false;};}
    public String key(){return "blacklight:"+id;}
    public TissueCore structuralBias(){return switch(this){
        case TENDON,HYDRAULIC->MUSCLE;case HYDROSTAT,CHELA,BLADE->GRASP;
        case HORN->CARAPACE;case PHARYNX,TONGUE,CROP,EXTRAORAL,GILL_RAKER,GILL_LAMELLA->JAW;
        case SADDLE,CLUB,GIZZARD,FERMENT,ABSORPTIVE,PANCREAS,FAT,LUNG,AIRFLOW,BOOK_LUNG,TRACHEA,HEART,BRANCHIAL_HEART,SPLEEN->MUSCLE;case CNIDOCYTE->GRASP;default->this;
    };}
    private static final int[][] VISUAL_SEEDS=visualSeeds();
    /** Four shapes per primary organ: a finite cache that cannot silently
     * give a jaw host the armour/crest kit of an unrelated truncated seed. */
    public static int visualSeed(long seed){return VISUAL_SEEDS[forSeed(seed).ordinal()][(int)(TissueGrowth.mix(seed)&3)];}
    public static int visualSeed(TissueCore core,long donorVariant){return VISUAL_SEEDS[core.structuralBias().ordinal()][(int)(TissueGrowth.mix(donorVariant)&3)];}
    private static int[][] visualSeeds(){
        int[][] seeds=new int[5][4];int[] count=new int[5];int remaining=20;
        for(int seed=0;remaining>0;seed++){
            int core=forSeed(seed).ordinal();if(count[core]<4){seeds[core][count[core]++]=seed;remaining--;}
        }
        return seeds;
    }
    public static TissueCore byId(String id){for(var core:values())if(core.id.equals(id)||core.key().equals(id))return core;return null;}
    public static TissueCore forNewHost(long seed,int plan,boolean water){
        TissueCore[] choices=water?new TissueCore[]{MUSCLE,CARAPACE,JAW,GRASP,CREST,HYDROSTAT,PHARYNX,TONGUE,CNIDOCYTE,GIZZARD,ABSORPTIVE,PANCREAS,CROP,FAT,EXTRAORAL,GILL_RAKER,GILL_LAMELLA,LUNG,HEART,BRANCHIAL_HEART,SPLEEN}
                :plan==2?new TissueCore[]{MUSCLE,CARAPACE,JAW,GRASP,CREST,HYDRAULIC,CHELA,BLADE,SADDLE,CNIDOCYTE,GIZZARD,ABSORPTIVE,PANCREAS,CROP,FAT,EXTRAORAL,BOOK_LUNG,TRACHEA,HEART,SPLEEN}
                :new TissueCore[]{MUSCLE,CARAPACE,JAW,GRASP,CREST,TENDON,HYDROSTAT,BLADE,CHELA,HORN,PHARYNX,SADDLE,CLUB,TONGUE,CNIDOCYTE,GIZZARD,FERMENT,ABSORPTIVE,PANCREAS,CROP,FAT,EXTRAORAL,GILL_LAMELLA,LUNG,AIRFLOW,BOOK_LUNG,TRACHEA,HEART,SPLEEN};
        return choices[(int)(TissueGrowth.sample(TissueGrowth.mix(seed),1846)*choices.length)];
    }
    public static int quality(long age){return age>=816000?3:age>=240000?2:age>=72000?1:0;}
    /** Resource yield grows slowly, independently of arbitrary health edits or body size. */
    public static float yieldMultiplier(long age){return (float)(1+Math.min(2,.35*Math.log1p(Math.max(0,age)/12000D)));}
    public static int cost(int nextRank){return switch(nextRank){case 1->200;case 2->400;case 3->700;default->0;};}
}
