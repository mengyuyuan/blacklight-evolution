package com.codex.blacklight.military;

import com.codex.blacklight.firearms.WeaponDefinition;
import com.codex.blacklight.firearms.WeaponCatalog;
import java.util.*;

/** Typed reserves only. Actual gun ItemStacks own their magazines and cooldowns. */
public final class MilitaryLoadoutInventory {
    public record Snapshot(int schemaVersion,boolean initialized,boolean nativeIssued,UUID owner,String assignment,
            Map<String,Integer> reserves,Map<String,Integer> capacities) {
        public Snapshot { reserves=Map.copyOf(reserves);capacities=Map.copyOf(capacities); }
    }
    private boolean initialized,nativeIssued;private UUID owner;private String assignment="";
    private final Map<String,Integer> reserves=new LinkedHashMap<>(),capacities=new LinkedHashMap<>();
    private static boolean known(String ammo){return WeaponCatalog.ALL.stream().anyMatch(d->d.ammoId().equals(ammo));}
    public boolean claimNative(UUID owner,String assignment,MilitaryLoadoutProfile profile){
        if(initialized||owner==null||assignment==null||assignment.isBlank()||assignment.length()>256||profile==null)return false;
        var issued=new LinkedHashMap<String,Integer>();
        for(var gun:profile.secondary()==null?List.of(profile.primary()):List.of(profile.primary(),profile.secondary())) {
            String ammo=gun.definition().ammoId();int stock=Math.addExact(issued.getOrDefault(ammo,0),gun.reserve());
            if(stock>4096)throw new IllegalArgumentException("Combined typed stock exceeds its bound");
            issued.put(ammo,stock);
        }
        this.owner=owner;this.assignment=assignment;initialized=true;nativeIssued=true;
        reserves.putAll(issued);capacities.putAll(issued);return true;
    }
    public static MilitaryLoadoutInventory legacy(WeaponDefinition original,int reserve){
        if(reserve<0||reserve>4096)throw new IllegalArgumentException("Invalid legacy reserve");
        var result=new MilitaryLoadoutInventory();result.initialized=true;
        if(original!=null){
            // Old default troops only issued bullets. A manually changed launcher
            // cannot turn an old ninety-round integer into ninety rockets.
            int count=original.kind()==WeaponDefinition.Kind.BULLET?reserve:Math.min(reserve,limit(original));
            result.reserves.put(original.ammoId(),count);result.capacities.put(original.ammoId(),Math.max(count,limit(original)));
        }
        return result;
    }
    private static int limit(WeaponDefinition d){return switch(d.kind()){
        case BULLET->d.capacity()*3;case GRENADE->d.capacity()*2;case ROCKET->2;default->0;
    };}
    /** A found gun opens an empty matching supply pocket; this never grants rounds or raises an issued cap. */
    public void allow(WeaponDefinition definition){
        if(definition==null||!WeaponCatalog.ALL.contains(definition))return;
        initialized=true;String ammunition=definition.ammoId();
        if(!capacities.containsKey(ammunition)){capacities.put(ammunition,limit(definition));reserves.put(ammunition,0);}
    }
    public int reserve(String ammunition){return reserves.getOrDefault(ammunition,0);}
    public int deficit(String ammunition){return Math.max(0,capacities.getOrDefault(ammunition,0)-reserve(ammunition));}
    public int receive(String ammunition,int offered){
        if(offered<0)throw new IllegalArgumentException("Negative ammunition transfer");
        int accepted=Math.min(offered,deficit(ammunition));if(accepted>0)reserves.put(ammunition,reserve(ammunition)+accepted);return accepted;
    }
    public boolean consume(String ammunition,int amount){
        if(amount<0)throw new IllegalArgumentException("Negative ammunition debit");
        if(amount>reserve(ammunition))return false;
        if(amount>0)reserves.put(ammunition,reserve(ammunition)-amount);return true;
    }
    public Snapshot snapshot(){return new Snapshot(1,initialized,nativeIssued,owner,assignment,reserves,capacities);}
    public static MilitaryLoadoutInventory restore(Snapshot snapshot){
        if(snapshot==null||snapshot.schemaVersion()!=1||!snapshot.reserves().keySet().equals(snapshot.capacities().keySet())
                ||!snapshot.initialized()&&(!snapshot.reserves().isEmpty()||snapshot.nativeIssued())
                ||snapshot.nativeIssued()&&(snapshot.owner()==null||snapshot.assignment()==null||snapshot.assignment().isBlank()||snapshot.assignment().length()>256)
                ||!snapshot.nativeIssued()&&(snapshot.owner()!=null||!"".equals(snapshot.assignment())))throw new IllegalArgumentException("Invalid typed loadout snapshot");
        var result=new MilitaryLoadoutInventory();
        for(var entry:snapshot.capacities().entrySet()) {
            int cap=entry.getValue(),stock=snapshot.reserves().get(entry.getKey());
            if(!known(entry.getKey())||cap<0||cap>4096||stock<0||stock>cap)throw new IllegalArgumentException("Invalid typed ammunition quantity");
            result.capacities.put(entry.getKey(),cap);result.reserves.put(entry.getKey(),stock);
        }
        result.initialized=snapshot.initialized();result.nativeIssued=snapshot.nativeIssued();result.owner=snapshot.owner();result.assignment=snapshot.assignment();return result;
    }
}
