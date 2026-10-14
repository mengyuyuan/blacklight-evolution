package com.codex.blacklight.power;
public enum GrowthUpgrade {
    VITALITY("vitality",1,200),REGENERATION("regeneration",2,400),ASSIMILATION("assimilation",4,300),
    REACH_I("reach_1",8,300),REACH_II("reach_2",16,600),REACH_III("reach_3",32,900),
    VITALITY_II("vitality_2",64,400),VITALITY_III("vitality_3",128,650),VITALITY_IV("vitality_4",256,900),
    SLIME_SIZE_I("slime_size_1",512,300),SLIME_SIZE_II("slime_size_2",1024,600),SLIME_SIZE_III("slime_size_3",2048,900),
    SLIME_DECAY_I("slime_decay_1",4096,300),SLIME_DECAY_II("slime_decay_2",8192,600),SLIME_DECAY_III("slime_decay_3",16384,1200);
    public final String id;public final int bit,cost;
    GrowthUpgrade(String id,int bit,int cost){this.id=id;this.bit=bit;this.cost=cost;}
    public static GrowthUpgrade byId(String id){for(var g:values())if(g.id.equals(id))return g;return null;}
    public GrowthUpgrade requires(){return switch(this){case REACH_III->REACH_II;case REACH_II->REACH_I;
        case VITALITY_II->VITALITY;case VITALITY_III->VITALITY_II;case VITALITY_IV->VITALITY_III;case SLIME_SIZE_II->SLIME_SIZE_I;case SLIME_SIZE_III->SLIME_SIZE_II;case SLIME_DECAY_II->SLIME_DECAY_I;case SLIME_DECAY_III->SLIME_DECAY_II;default->null;};}
}
