package com.codex.blacklight.infection;

/** Species abilities survive replacement; anatomy and acquired organs remain independent. */
public enum NativeInfectedProfile {
    MELEE(0,0,0,0), BOW(18,8,44,22), CROSSBOW(24,12,56,24),
    BLAZE(32,14,70,28), GHAST(32,20,80,48), GUARDIAN(64,48,86,26),
    FANGS(40,20,90,18), POTION(24,12,65,14), SHULKER(24,12,70,24),
    TRIDENT(24,12,58,22), SONIC(54,30,100,32), WITHER(38,18,65,48),
    DRAGON(48,24,95,56), WIND(30,16,66,24), CREEPER(34,28,110,4);

    public final int duration,contact,cooldown; public final double range;
    NativeInfectedProfile(int duration,int contact,int cooldown,double range){this.duration=duration;this.contact=contact;this.cooldown=cooldown;this.range=range;}
    public boolean ranged(){return this!=MELEE&&this!=CREEPER;}
    public static NativeInfectedProfile of(String species){return switch(species){
        case "minecraft:skeleton","minecraft:stray","minecraft:bogged","minecraft:illusioner"->BOW;
        case "minecraft:pillager"->CROSSBOW;
        case "minecraft:blaze"->BLAZE;case "minecraft:ghast"->GHAST;
        case "minecraft:guardian","minecraft:elder_guardian"->GUARDIAN;
        case "minecraft:evoker"->FANGS;case "minecraft:witch"->POTION;
        case "minecraft:shulker"->SHULKER;case "minecraft:drowned"->TRIDENT;
        case "minecraft:warden"->SONIC;case "minecraft:wither"->WITHER;
        case "minecraft:ender_dragon"->DRAGON;case "minecraft:breeze"->WIND;
        case "minecraft:creeper"->CREEPER;default->MELEE;
    };}
    public static NativeInfectedProfile saved(String species,String capability){
        var nativeProfile=of(species);if(nativeProfile!=MELEE)return nativeProfile;
        return "ranged".equals(capability)?BOW:MELEE;
    }
    public static boolean hops(String species){return species.equals("minecraft:slime")||species.equals("minecraft:magma_cube")||species.equals("minecraft:rabbit")||species.equals("minecraft:breeze");}
    public static boolean climbs(String species){return species.equals("minecraft:spider")||species.equals("minecraft:cave_spider");}
    public static boolean swoops(String species){return species.equals("minecraft:phantom")||species.equals("minecraft:bee")||species.equals("minecraft:vex");}
}
