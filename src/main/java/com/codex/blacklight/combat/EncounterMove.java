package com.codex.blacklight.combat;

/** Authored encounter timing, in server ticks. Damage never runs during anticipation or recovery. */
public enum EncounterMove {
    NONE(0,0,0,0,0), CLAW(28,12,18,3.2,100), DOUBLE(42,12,22,3.5,125),
    POUNCE(44,22,24,8,100), HAMMER(42,23,30,4.5,120), SLAM(54,30,38,7,360),
    CHARGE(50,17,32,12,90), HOWL(60,32,90,16,360), ORBS(58,22,38,24,180),
    VOLLEY(64,20,44,24,180), DIVE(54,25,32,16,110), BLADE(36,17,22,5,150),
    WHIP(46,24,28,10,45), ERUPTION(70,42,60,12,360), DODGE(24,0,24,8,0),
    BATON(30,14,18,2.8,90), GRENADE(52,26,50,18,180), GUARD(42,0,18,8,0);
    public final int duration,contact,recovery;public final double range,arc;
    EncounterMove(int duration,int contact,int recovery,double range,double arc){this.duration=duration;this.contact=contact;this.recovery=recovery;this.range=range;this.arc=arc;}
    public static EncounterMove byId(int id){return id>=0&&id<values().length?values()[id]:NONE;}
    public boolean impact(int age){return switch(this){case NONE,DODGE,GUARD->false;case DOUBLE->age==12||age==25;case VOLLEY->age==20||age==30||age==40;case CHARGE->age>=17&&age<=32;case SLAM,ERUPTION->age>=contact&&age<=contact+10;default->age==contact;};}
    public boolean projectile(){return this==ORBS||this==VOLLEY||this==GRENADE;}
    public boolean drives(int age){return switch(this){case POUNCE->age>=10&&age<=22;case CHARGE->age>=12&&age<=32;case DIVE->age>=12&&age<=25;case DODGE->age>=4&&age<=12;default->false;};}
    public float damageScale(){return switch(this){case SLAM,ERUPTION->1.5F;case HAMMER->1.3F;case DOUBLE->.7F;case POUNCE,DIVE->1.15F;case WHIP->.8F;default->1;};}
    /** Smooth pose weights shared by tests and the client, not an independent animation timer. */
    public float anticipation(float age){if(contact==0)return this==GUARD?1:0;return smooth(age/Math.max(1,contact-4F))*(1-smooth((age-contact+3)/4));}
    public float followThrough(float age){if(contact==0)return 0;return smooth((age-contact+3)/4)*(1-smooth((age-contact-6)/Math.max(1,duration-contact-6F)));}
    private static float smooth(float v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
}
