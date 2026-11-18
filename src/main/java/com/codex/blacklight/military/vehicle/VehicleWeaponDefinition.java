package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.firearms.WeaponDefinition;
import java.util.List;

/** Minecraft gameplay balance; identities remain explicit even when projectile kinds are shared. */
public record VehicleWeaponDefinition(WeaponDefinition weapon,DamageClass damageClass,String breech,String muzzle,double speed) {
    public enum DamageClass { BULLET,AUTOCANNON,EXPLOSIVE,AP }
    public VehicleWeaponDefinition {
        if(weapon==null||damageClass==null||breech==null||muzzle==null||!breech.matches("breech_[a-z_]+")||!muzzle.matches("muzzle_[a-z_]+")
                ||!Double.isFinite(speed)||speed<0||speed>8||!Float.isFinite(weapon.damage())||weapon.damage()>500
                ||!Double.isFinite(weapon.range())||weapon.range()>256||!Float.isFinite(weapon.radius())||weapon.radius()>4)
            throw new IllegalArgumentException("Invalid vehicle weapon");
    }
    public static final List<VehicleWeaponDefinition> ALL=List.of(
            create("autocannon","autocannon_round",6,8,96,WeaponDefinition.Kind.GRENADE,1.2F,DamageClass.AUTOCANNON,3,false),
            create("vehicle_rocket","rocket_round",30,20,96,WeaponDefinition.Kind.ROCKET,4,DamageClass.EXPLOSIVE,2.2,true),
            create("tank_he","tank_he_shell",80,24,96,WeaponDefinition.Kind.ROCKET,4,DamageClass.EXPLOSIVE,2.8,false),
            create("tank_ap","tank_ap_shell",80,36,96,WeaponDefinition.Kind.ROCKET,1,DamageClass.AP,3.2,false),
            create("coaxial_machine_gun","machine_gun_round",4,4,80,WeaponDefinition.Kind.BULLET,0,DamageClass.BULLET,0,true),
            create("helicopter_hellfire","rocket_round",60,20,175,WeaponDefinition.Kind.MISSILE,4,DamageClass.EXPLOSIVE,3,true),
            create("helicopter_70mm","rocket_round",5,2.4F,175,WeaponDefinition.Kind.ROCKET,4,DamageClass.EXPLOSIVE,7.5,true),
            // Source heli_gunship_30mm_ammo: 5Hz, damage20, distanceToLive175.
            // Base20 is the MC balance adapter; CombatScale still converts living HP.
            // Current chin gun remains hitscan, not a claim to native speed200 flight.
            create("helicopter_30mm","helicopter_cannon_round",4,20,175,WeaponDefinition.Kind.BULLET,0,DamageClass.AUTOCANNON,0,false));
    private static VehicleWeaponDefinition create(String id,String ammo,int cooldown,float damage,double range,WeaponDefinition.Kind kind,float radius,DamageClass impact,double speed,boolean secondary){
        return new VehicleWeaponDefinition(new WeaponDefinition(id,ammo,1,cooldown,cooldown,damage,range,kind,radius),impact,
                secondary?"breech_secondary":"breech_primary",secondary?"muzzle_secondary":"muzzle_primary",speed);
    }
    public static VehicleWeaponDefinition require(String id){return ALL.stream().filter(value->value.weapon.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown vehicle weapon: "+id));}
    public float mechanicalDamage(float amount,VehicleDefinition.Armor armor){
        if(!Float.isFinite(amount)||amount<0||armor==null)throw new IllegalArgumentException("Invalid mechanical impact");
        float coefficient=switch(damageClass){
            case BULLET->armor==VehicleDefinition.Armor.LIGHT?1:armor==VehicleDefinition.Armor.ARMORED?.25F:.10F;
            case AUTOCANNON->armor==VehicleDefinition.Armor.LIGHT?1:armor==VehicleDefinition.Armor.ARMORED?.70F:.30F;
            case EXPLOSIVE->armor==VehicleDefinition.Armor.LIGHT?1:armor==VehicleDefinition.Armor.ARMORED?.75F:.50F;
            case AP->1;
        };
        return amount*coefficient;
    }
}
