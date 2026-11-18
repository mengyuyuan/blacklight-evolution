package com.codex.blacklight.combat;

/** Authored creature clips. Melee windows permit physical contact checks, never guaranteed damage. */
public enum MutantMove {
    NONE("", "idle",0,0,0,0,0,0,0,0),
    STALKER_CLAW("STALKER","claw",24,12,2.2,.2,1,.25,0,0),
    STALKER_POUNCE("STALKER","pounce",36,22,2.6,.1,1.3,.55,9,19),
    STALKER_DOUBLE("STALKER","double_claw",38,20,2.4,.05,.85,.2,0,0),
    SPITTER_SHOT("SPITTER","spit",30,24,16,.25,1,0,0,0,14),
    SPITTER_VOLLEY("SPITTER","volley",46,38,15,.2,1,0,0,0,14,24,34),
    SPITTER_RETREAT("SPITTER","retreat",26,16,0,0,0,0,5,14),
    BRUTE_HAMMER("BRUTE","hammer",34,18,2.8,.2,1,.55,0,0),
    BRUTE_SLAM("BRUTE","ground_slam",46,28,4.2,-1,1.25,.85,0,0),
    BRUTE_CHARGE("BRUTE","charge",46,26,2.4,.25,1.15,.8,12,26);

    public final String species,clip;
    public final int ticks,recovery,driveStart,driveEnd;
    public final double reach,arc,damage,push;
    private final int[] contacts;
    MutantMove(String species,String clip,int ticks,int recovery,double reach,double arc,double damage,double push,int driveStart,int driveEnd,int... contacts){
        this.species=species;this.clip=clip;this.ticks=ticks;this.recovery=recovery;this.reach=reach;this.arc=arc;
        this.damage=damage;this.push=push;this.driveStart=driveStart;this.driveEnd=driveEnd;this.contacts=contacts;
    }
    public boolean contact(int age){if(species.equals("BRUTE"))return BruteContact.active(this,age);if(species.equals("STALKER")){for(var w:StalkerContact.windows(this))if(age>=w.start()&&age<=w.end())return true;return false;}for(int tick:contacts)if(tick==age)return true;return false;}
    public boolean driving(int age){return driveEnd>0&&age>=driveStart&&age<=driveEnd;}
    public boolean projectile(){return this==SPITTER_SHOT||this==SPITTER_VOLLEY;}
    public static MutantMove byId(int id){return id>=0&&id<values().length?values()[id]:NONE;}
    public static MutantMove choose(String species,double distance,int sequence){
        return switch(species){
            case "STALKER" -> distance>3?STALKER_POUNCE:Math.floorMod(sequence,3)==2?STALKER_DOUBLE:STALKER_CLAW;
            case "SPITTER" -> distance<4?SPITTER_RETREAT:Math.floorMod(sequence,3)==2?SPITTER_VOLLEY:SPITTER_SHOT;
            case "BRUTE" -> distance>4?BRUTE_CHARGE:Math.floorMod(sequence,3)==2?BRUTE_SLAM:BRUTE_HAMMER;
            default -> NONE;
        };
    }
    public double initiationRange(){return switch(this){case STALKER_POUNCE->7;case BRUTE_CHARGE->9;case SPITTER_RETREAT->4;default->reach;};}
}
