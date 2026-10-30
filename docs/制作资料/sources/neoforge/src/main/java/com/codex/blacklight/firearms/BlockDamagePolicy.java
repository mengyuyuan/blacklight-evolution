package com.codex.blacklight.firearms;

/** Game balance in impact points, unrelated to real firearm penetration specifications. */
public final class BlockDamagePolicy {
    public enum Material { FRAGILE, WOOD, EARTH, MASONRY, METAL, PROTECTED }
    private BlockDamagePolicy(){}
    public static float durability(Material material,float hardness){
        if(material==null||material==Material.PROTECTED||!Float.isFinite(hardness)||hardness<0)return Float.POSITIVE_INFINITY;
        return switch(material){
            case FRAGILE -> 1;
            case WOOD -> Math.max(8,hardness*8);
            case EARTH -> Math.max(6,hardness*12);
            case MASONRY -> Math.max(30,hardness*24);
            case METAL -> Math.max(120,hardness*32);
            default -> Float.POSITIVE_INFINITY;
        };
    }
    public static float bulletImpact(Material material,float weaponDamage,boolean machineGun){
        if(material==null||!Float.isFinite(weaponDamage)||weaponDamage<=0)return 0;
        return switch(material){
            case PROTECTED,METAL -> 0;
            case MASONRY -> weaponDamage*(machineGun?.8F:.2F);
            default -> weaponDamage;
        };
    }
    public static float blastRadius(WeaponDefinition.Kind kind){
        if(kind==null)return 0;
        return switch(kind){
            case GRENADE -> 2.5F;
            case ROCKET -> 3;
            case MISSILE -> 3.5F;
            case THERMOBARIC -> 4;
            default -> 0;
        };
    }
    public static float blastImpact(Material material,WeaponDefinition.Kind kind,float weaponDamage,double distance,double radius){
        if(material==null||material==Material.PROTECTED||blastRadius(kind)==0||!Float.isFinite(weaponDamage)||weaponDamage<=0
                ||!Double.isFinite(distance)||!Double.isFinite(radius)||distance<0||radius<=0||distance>=radius)return 0;
        float type=kind==WeaponDefinition.Kind.GRENADE?.75F:kind==WeaponDefinition.Kind.THERMOBARIC?.65F:1;
        return weaponDamage*2.5F*type*(float)(1-distance/radius)*(material==Material.METAL?.6F:1);
    }
}
