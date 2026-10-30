package com.codex.blacklight.military;

import com.codex.blacklight.firearms.*;
import java.util.*;

/** A one-time finite issue policy. Duty never changes rank, unit, faction or home. */
public record MilitaryLoadoutProfile(Duty duty,Gun primary,Gun secondary) {
    public MilitaryLoadoutProfile { Objects.requireNonNull(duty);Objects.requireNonNull(primary); }
    public enum Duty { RIFLE, CARBINE, MACHINE_GUN, GRENADIER, ANTI_ARMOR }
    public record Gun(String weaponId,int magazine,int reserve) {
        public Gun {
            var definition=WeaponCatalog.ALL.stream().filter(d->d.id().equals(weaponId)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown weapon"));
            if(magazine<0||magazine>definition.capacity()||reserve<0||reserve>4096)throw new IllegalArgumentException("Invalid finite gun issue");
        }
        public WeaponDefinition definition(){return WeaponCatalog.ALL.stream().filter(d->d.id().equals(weaponId)).findFirst().orElseThrow();}
    }
    public static MilitaryLoadoutProfile forDuty(Duty duty){
        return switch(Objects.requireNonNull(duty)) {
            case RIFLE->new MilitaryLoadoutProfile(duty,new Gun(WeaponCatalog.RIFLE.id(),30,90),null);
            case CARBINE->new MilitaryLoadoutProfile(duty,new Gun(WeaponCatalog.CARBINE.id(),24,72),null);
            case MACHINE_GUN->new MilitaryLoadoutProfile(duty,new Gun(WeaponCatalog.MACHINE_GUN.id(),60,180),null);
            case GRENADIER->new MilitaryLoadoutProfile(duty,new Gun(WeaponCatalog.GRENADE.id(),6,12),null);
            case ANTI_ARMOR->new MilitaryLoadoutProfile(duty,new Gun(WeaponCatalog.CARBINE.id(),24,48),new Gun(WeaponCatalog.ROCKET.id(),1,2));
        };
    }
    public static List<MilitaryLoadoutProfile> patrol(int size,boolean mechanicalThreat){
        if(size!=8&&size!=12)throw new IllegalArgumentException("A default patrol has eight or twelve people");
        var result=new ArrayList<MilitaryLoadoutProfile>();result.add(forDuty(Duty.CARBINE));
        for(int i=0;i<(size==8?5:8);i++)result.add(forDuty(Duty.RIFLE));
        result.add(forDuty(Duty.MACHINE_GUN));result.add(forDuty(size==8&&mechanicalThreat?Duty.ANTI_ARMOR:Duty.GRENADIER));
        if(size==12)result.add(forDuty(Duty.ANTI_ARMOR));return List.copyOf(result);
    }
    public static MilitaryLoadoutProfile weighted(UUID owner,String assignment,Map<Duty,Integer> weights){
        if(owner==null||assignment==null||assignment.isBlank()||assignment.length()>256||weights==null)throw new IllegalArgumentException("Invalid one-time assignment");
        long total=0;
        for(var entry:weights.entrySet()) {
            if(entry.getKey()==null||entry.getValue()==null||entry.getValue()<0||entry.getValue()>100_000)throw new IllegalArgumentException("Invalid ordinary role weight");
            total+=entry.getValue();
        }
        if(total<=0||total>1_000_000)throw new IllegalArgumentException("Ordinary role weights must have a positive bounded sum");
        long identity=UUID.nameUUIDFromBytes((owner+":"+assignment).getBytes(java.nio.charset.StandardCharsets.UTF_8)).getLeastSignificantBits();
        long cursor=Math.floorMod(identity,total);
        for(Duty duty:Duty.values()){int weight=weights.getOrDefault(duty,0);if(cursor<weight)return forDuty(duty);cursor-=weight;}
        throw new IllegalArgumentException("Invalid ordinary role weights");
    }
    public static MilitaryLoadoutProfile forNative(UUID owner,String assignment,MilitaryFaction faction,boolean heavy,MilitaryRole role){
        Objects.requireNonNull(faction);Objects.requireNonNull(role);
        if(role!=MilitaryRole.TROOPER)return forDuty(Duty.CARBINE);
        if(heavy)return forDuty(Duty.MACHINE_GUN);
        return weighted(owner,assignment,faction==MilitaryFaction.MILITARY
                ?Map.of(Duty.RIFLE,65,Duty.CARBINE,15,Duty.MACHINE_GUN,10,Duty.GRENADIER,6,Duty.ANTI_ARMOR,4)
                :Map.of(Duty.RIFLE,60,Duty.CARBINE,18,Duty.MACHINE_GUN,12,Duty.GRENADIER,6,Duty.ANTI_ARMOR,4));
    }
}
