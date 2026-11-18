package com.codex.blacklight.combat;

/** Server ticks and animation phases share these contracts. Distances are MC adaptations. */
public enum CreatureAttack {
    NONE("idle",0,0,0),
    SWIPE("swipe",37,12,2.8,12),
    BARRAGE("combat_barrage",57,14,3.2,12,16,22,30,36),
    SLAM("combat_slam",43,18,3.8,12),
    LEAP("combat_leap",48,16,3.2,23),
    DODGE("dodge",24,12,0),
    // Append, preserving stored/networked ids of every existing action.
    COUNTER("combat_counter",32,14,3.2,22);

    public final String clip;
    public final int ticks,recovery;
    public final double reach;
    private final int[] hits;
    CreatureAttack(String clip,int ticks,int recovery,double reach,int... hits){this.clip=clip;this.ticks=ticks;this.recovery=recovery;this.reach=reach;this.hits=hits;}
    public boolean hitsAt(int tick){for(int hit:hits)if(hit==tick)return true;return false;}
    /** Source FIG local hit times translated onto the composed clip. Seconds, not frame indices.
     * Radius is supplied by the authored hand geometry in HunterContactPose. */
    public record Window(float from,float to,boolean left) {
        public boolean overlaps(float previousTick,float tick){return tick>=from*20&&previousTick<=to*20;}
    }
    private static final Window[] SWIPE_WINDOWS={new Window(.6F,.733333F,false)};
    private static final Window[] BARRAGE_WINDOWS={new Window(.566667F,.666667F,false),new Window(.8F,.966667F,false),
        new Window(1.1F,1.366667F,true),new Window(1.466667F,1.633333F,false),new Window(1.8F,2.2F,false)};
    private static final Window[] SLAM_WINDOWS={new Window(.566667F,.733333F,false)};
    private static final Window[] LEAP_WINDOWS={new Window(1.133333F,1.366667F,false)};
    private static final Window[] COUNTER_WINDOWS={new Window(1.066667F,1.166667F,true)};
    /** Source counter releases into barrage frame 3; skip the old .4 s MC anticipation. */
    public static final int COUNTER_BARRAGE_ENTRY_TICK=8;
    public static final float COUNTER_BARRAGE_BLEND_TICKS=10F/3;
    private static final Window[] NO_WINDOWS={};
    public Window[] windows(){return switch(this){case SWIPE->SWIPE_WINDOWS;case BARRAGE->BARRAGE_WINDOWS;case SLAM->SLAM_WINDOWS;case LEAP->LEAP_WINDOWS;case COUNTER->COUNTER_WINDOWS;default->NO_WINDOWS;};}
    public static CreatureAttack byId(int id){return id>=0&&id<values().length?values()[id]:NONE;}
    /** Stable, varied choices; random evasions are rate limited separately by the entity. */
    public static CreatureAttack choose(double distance,int sequence){
        if(distance>4&&distance<=10)return LEAP;
        return switch(Math.floorMod(sequence,3)){case 1->COUNTER;case 2->SLAM;default->SWIPE;};
    }
    public static boolean inArc(double dx,double dz,float yaw,double cosine){
        double length=Math.hypot(dx,dz);if(length<.01)return true;
        double angle=Math.toRadians(yaw);
        return (-Math.sin(angle)*dx+Math.cos(angle)*dz)/length>=cosine;
    }
}
