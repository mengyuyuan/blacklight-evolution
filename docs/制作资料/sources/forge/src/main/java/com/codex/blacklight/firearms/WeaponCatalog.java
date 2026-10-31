package com.codex.blacklight.firearms;
import java.util.List;
/** Original gameplay tuning, not a claim about real-world firearm specifications. */
public final class WeaponCatalog {
    public static final WeaponDefinition RIFLE=new WeaponDefinition("military_rifle","rifle_round",30,4,40,4,64,WeaponDefinition.Kind.BULLET,0);
    public static final WeaponDefinition CARBINE=new WeaponDefinition("military_carbine","rifle_round",24,3,32,3.5F,48,WeaponDefinition.Kind.BULLET,0);
    public static final WeaponDefinition MACHINE_GUN=new WeaponDefinition("military_machine_gun","machine_gun_round",60,3,65,5,72,WeaponDefinition.Kind.BULLET,0);
    public static final WeaponDefinition GRENADE=new WeaponDefinition("grenade_launcher","grenade_round",6,14,55,12,40,WeaponDefinition.Kind.GRENADE,3);
    public static final WeaponDefinition ROCKET=new WeaponDefinition("rocket_launcher","rocket_round",1,22,65,20,80,WeaponDefinition.Kind.ROCKET,4);
    public static final WeaponDefinition MISSILE=new WeaponDefinition("missile_launcher","missile_round",1,28,75,22,96,WeaponDefinition.Kind.MISSILE,4);
    public static final WeaponDefinition THERMOBARIC=new WeaponDefinition("thermobaric_launcher","thermobaric_round",1,25,70,24,48,WeaponDefinition.Kind.THERMOBARIC,5);
    public static final WeaponDefinition INFECTION=new WeaponDefinition("infection_launcher","infection_round",1,25,60,0,40,WeaponDefinition.Kind.INFECTION,3);
    public static final List<WeaponDefinition> ALL=List.of(RIFLE,CARBINE,MACHINE_GUN,GRENADE,ROCKET,MISSILE,THERMOBARIC,INFECTION);
    public static WeaponDefinition projectile(WeaponDefinition.Kind kind){return ALL.stream().filter(d->d.kind()==kind).findFirst().orElse(GRENADE);}
    private WeaponCatalog(){}
}
