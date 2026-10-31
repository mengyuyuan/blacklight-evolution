package com.codex.blacklight.military.vehicle;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class VehicleInventory {
    public record Snapshot(int schemaVersion, String vehicleId, boolean initialLoadoutIssued,
            long fuelTicks, Map<String, Integer> ammunition, Map<String, Long> cooldownUntil) {
        public Snapshot { ammunition=Map.copyOf(ammunition); cooldownUntil=Map.copyOf(cooldownUntil); }
    }
    private final VehicleDefinition definition;
    private final Map<String,Integer> ammunition=new LinkedHashMap<>();
    private final Map<String,Long> cooldownUntil=new LinkedHashMap<>();
    private long fuelTicks;
    private boolean initialLoadoutIssued;
    public VehicleInventory(VehicleDefinition definition) {
        this.definition=Objects.requireNonNull(definition);
        definition.ammunitionCapacities().keySet().forEach(id -> ammunition.put(id,0));
        definition.weapons().keySet().forEach(id -> cooldownUntil.put(id,0L));
    }
    public static VehicleInventory restore(VehicleDefinition definition, Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.schemaVersion()!=1 || !definition.id().equals(snapshot.vehicleId()) || snapshot.fuelTicks()<0 || snapshot.fuelTicks()>definition.maxFuelTicks())
            throw new IllegalArgumentException("Invalid vehicle inventory snapshot");
        if (!snapshot.cooldownUntil().keySet().equals(definition.weapons().keySet()))
            throw new IllegalArgumentException("Schema 1 inventory requires every weapon cooldown");
        var result=new VehicleInventory(definition);
        for(var entry:snapshot.ammunition().entrySet()) {
            Integer cap=definition.ammunitionCapacities().get(entry.getKey());
            if (cap==null || entry.getValue()<0 || entry.getValue()>cap) throw new IllegalArgumentException("Invalid saved ammunition");
            result.ammunition.put(entry.getKey(),entry.getValue());
        }
        for(var entry:snapshot.cooldownUntil().entrySet()) {
            if (!definition.weapons().containsKey(entry.getKey()) || entry.getValue()<0) throw new IllegalArgumentException("Invalid saved cooldown");
            result.cooldownUntil.put(entry.getKey(),entry.getValue());
        }
        for (var a:definition.weapons().values()) for(var b:definition.weapons().values())
            if (a.cooldownGroup().equals(b.cooldownGroup()) && !result.cooldownUntil.get(a.id()).equals(result.cooldownUntil.get(b.id())))
                throw new IllegalArgumentException("Inconsistent shared breech cooldown");
        if(!snapshot.initialLoadoutIssued() && (snapshot.fuelTicks()>0 || result.ammunition.values().stream().anyMatch(n->n>0)
                || result.cooldownUntil.values().stream().anyMatch(n->n>0))) throw new IllegalArgumentException("Unclaimed inventory cannot contain issued resources");
        result.fuelTicks=snapshot.fuelTicks(); result.initialLoadoutIssued=snapshot.initialLoadoutIssued();
        return result;
    }
    /** Explicit natural-spawn allocation only; the caller must own the one-time dispatch ledger. */
    public boolean issueInitialLoadout() {
        if(initialLoadoutIssued) return false;
        initialLoadoutIssued=true; ammunition.putAll(definition.ammunitionCapacities()); return true;
    }
    public int addAmmunition(String ammunitionId, int offered) {
        if(offered<0) throw new IllegalArgumentException("Negative ammunition transfer");
        Integer cap=definition.ammunitionCapacities().get(ammunitionId); if(cap==null) return 0;
        int accepted=Math.min(offered,cap-ammunition.get(ammunitionId));
        if(accepted>0) { ammunition.put(ammunitionId,ammunition.get(ammunitionId)+accepted); initialLoadoutIssued=true; }
        return accepted;
    }
    public long addFuel(long offered) {
        if(offered<0) throw new IllegalArgumentException("Negative fuel transfer");
        long accepted=Math.min(offered,definition.maxFuelTicks()-fuelTicks);
        if(accepted>0) { fuelTicks+=accepted; initialLoadoutIssued=true; }
        return accepted;
    }
    public boolean consumeFuel(long ticks) {
        if(ticks<0) throw new IllegalArgumentException("Negative fuel consumption");
        if(ticks>fuelTicks) return false; fuelTicks-=ticks; return true;
    }
    public boolean tryFire(String weaponId, long tick) {
        if(tick<0) throw new IllegalArgumentException("Negative server tick");
        var weapon=definition.weapons().get(weaponId);
        if(weapon==null || tick>Long.MAX_VALUE-weapon.cooldownTicks() || tick<cooldownUntil.get(weaponId) || ammunition.get(weapon.ammunitionId())<1) return false;
        ammunition.put(weapon.ammunitionId(),ammunition.get(weapon.ammunitionId())-1);
        for(var other:definition.weapons().values()) if(other.cooldownGroup().equals(weapon.cooldownGroup())) cooldownUntil.put(other.id(),tick+weapon.cooldownTicks());
        return true;
    }
    public int ammunition(String ammunitionId) { return ammunition.getOrDefault(ammunitionId,0); }
    public long fuelTicks() { return fuelTicks; }
    public long cooldownUntil(String weaponId) { return cooldownUntil.getOrDefault(weaponId,0L); }
    public Snapshot snapshot() { return new Snapshot(1,definition.id(),initialLoadoutIssued,fuelTicks,ammunition,cooldownUntil); }
}
