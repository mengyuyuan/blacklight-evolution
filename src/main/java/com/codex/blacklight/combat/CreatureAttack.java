package com.codex.blacklight.combat;

/** Server ticks and animation phases share these contracts. Distances are MC adaptations. */
public enum CreatureAttack {
    NONE("idle",0,0,0),
    SWIPE("swipe",32,12,2.8,12),
    BARRAGE("combat_barrage",52,14,3.2,12,16,22,29,36),
    SLAM("combat_slam",48,18,3.8,16),
    LEAP("combat_leap",48,16,3.2,22),
    DODGE("dodge",24,12,0);

    public final String clip;
    public final int ticks,recovery;
    public final double reach;
    private final int[] hits;
    CreatureAttack(String clip,int ticks,int recovery,double reach,int... hits){this.clip=clip;this.ticks=ticks;this.recovery=recovery;this.reach=reach;this.hits=hits;}
    public boolean hitsAt(int tick){for(int hit:hits)if(hit==tick)return true;return false;}
    public static CreatureAttack byId(int id){return id>=0&&id<values().length?values()[id]:NONE;}
    /** Stable, varied choices; random evasions are rate limited separately by the entity. */
    public static CreatureAttack choose(double distance,int sequence){
        if(distance>4&&distance<=10)return LEAP;
        return switch(Math.floorMod(sequence,3)){case 1->BARRAGE;case 2->SLAM;default->SWIPE;};
    }
    public static boolean inArc(double dx,double dz,float yaw,double cosine){
        double length=Math.hypot(dx,dz);if(length<.01)return true;
        double angle=Math.toRadians(yaw);
        return (-Math.sin(angle)*dx+Math.cos(angle)*dz)/length>=cosine;
    }
}
