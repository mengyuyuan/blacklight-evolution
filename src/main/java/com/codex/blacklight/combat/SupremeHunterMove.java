package com.codex.blacklight.combat;

/** Supreme Hunter actions. Clip lengths and hit ticks come from the decoded original
 * supreme_hunter clips (30 fps source frames converted to 20 Hz server ticks). Reach,
 * radii, damage budgets and AI selection are Minecraft adaptations. */
public enum SupremeHunterMove {
    NONE("idle",0,0,0,1,0,0),
    ROAR("roar",125,0,8,-1,0,0,40),
    RAGE("rage",42,0,7,-1,0,0,20),
    CLAW_SLASH("claw_slash",57,4.4,0,.15,80,40,10,15,30),
    BACKHAND("backhand",49,4.8,0,-.25,80,40,12),
    OVERHAND("overhand",43,4.6,0,.3,80,40,21),
    STOMP("stomp",40,0,5.5,-1,60,40,12),
    GROUNDSPIKE("groundspike",137,0,2.8,-1,100,100,17,61),
    DEVASTATOR("devastator",104,0,12,-1,200,100,44,67,87),
    PICKUP_THROW("pickup_throw",67,0,2.2,-1,40,40,17,37),
    SHOULDER_CHARGE("shoulder_charge",24,2.2,0,.2,60,40,3),
    LEAP("leap",55,0,7,-1,120,60,14,34),
    GRAB_THROW("pickup_large_throw",55,3.8,0,.35,25,40,13,27),
    BLOCK("block",21,0,0,1,0,0),
    HIT_FRONT("hit_front",24,0,0,1,0,0),
    HIT_BACK("hit_back",24,0,0,1,0,0),
    HIT_LEFT("hit_left",24,0,0,1,0,0),
    HIT_RIGHT("hit_right",24,0,0,1,0,0),
    LEG_COLLAPSE("leg_collapse",77,0,0,1,0,0),
    DEATH("death",69,0,0,1,0,0);

    public final String clip;
    public final int ticks;
    /** Melee reach beyond the target's half width, or an area radius. */
    public final double reach,radius,arcCosine;
    /** Damage to an infected protagonist in converted HP (feedback #27: ULT 200, spike 100,
     * basic two-hit 80 then 60, grab landing 25). */
    public final float budget;
    /** Player stagger ticks after an accepted hit (ULT and spike 5 s, basic and debris 2 s). */
    public final int stagger;
    private final int[] events;
    SupremeHunterMove(String clip,int ticks,double reach,double radius,double arcCosine,float budget,int stagger,int... events){
        this.clip=clip;this.ticks=ticks;this.reach=reach;this.radius=radius;this.arcCosine=arcCosine;this.budget=budget;this.stagger=stagger;this.events=events;
    }
    public boolean eventAt(int tick){for(int e:events)if(e==tick)return true;return false;}
    public int event(int index){return index<events.length?events[index]:-1;}
    public int eventCount(){return events.length;}
    public static SupremeHunterMove byId(int id){return id>=0&&id<values().length?values()[id]:NONE;}
    /** Hyper armor: player hits still deal damage, but do not flinch the boss. */
    public boolean armored(int elapsed){
        return switch(this){
            case ROAR,RAGE,GROUNDSPIKE,DEVASTATOR,LEAP,SHOULDER_CHARGE,LEG_COLLAPSE,DEATH,GRAB_THROW->true;
            case CLAW_SLASH,BACKHAND,OVERHAND,STOMP,PICKUP_THROW->elapsed>=event(0)-2;
            default->false;
        };
    }
    /** The basic slash is a two-stage combo: the second stage hits for 60. */
    public float budget(int hitIndex){return this==CLAW_SLASH&&hitIndex>0?60:budget;}
    public boolean reaction(){return this==HIT_FRONT||this==HIT_BACK||this==HIT_LEFT||this==HIT_RIGHT||this==BLOCK;}
}
