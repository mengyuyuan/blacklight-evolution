package com.codex.blacklight.military.vehicle;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class VehicleSeats {
    private final Map<String, VehicleDefinition.Seat> definitions = new LinkedHashMap<>();
    private final Map<String, UUID> occupants = new LinkedHashMap<>();
    private long revision;
    public VehicleSeats(VehicleDefinition definition) { definition.seats().forEach(s -> definitions.put(s.id(),s)); }
    public boolean board(UUID occupant, String seatId, Set<UUID> actualPassengers) {
        if (occupant == null || !definitions.containsKey(seatId) || !actualPassengers.contains(occupant)
                || occupants.containsKey(seatId) || seatOf(occupant) != null) return false;
        occupants.put(seatId,occupant); revision++; return true;
    }
    public boolean switchSeat(UUID occupant, String seatId, Set<UUID> actualPassengers) {
        String prior=seatOf(occupant);
        if (prior == null || !definitions.containsKey(seatId) || !actualPassengers.contains(occupant) || occupants.containsKey(seatId)) return false;
        occupants.remove(prior); occupants.put(seatId,occupant); revision++; return true;
    }
    public boolean remove(UUID occupant) {
        String seat=seatOf(occupant); if (seat == null) return false;
        occupants.remove(seat); revision++; return true;
    }
    public void reconcile(Set<UUID> actualPassengers) {
        Objects.requireNonNull(actualPassengers);
        if (occupants.values().removeIf(id -> !actualPassengers.contains(id))) revision++;
    }
    public String seatOf(UUID occupant) {
        if (occupant == null) return null;
        return occupants.entrySet().stream().filter(e -> e.getValue().equals(occupant)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public UUID occupant(String seatId) { return occupants.get(seatId); }
    public Map<String, UUID> occupants() { return Map.copyOf(occupants); }
    public long revision() { return revision; }
    public VehicleDefinition.Job job(UUID occupant) {
        String seat=seatOf(occupant); return seat == null ? null : definitions.get(seat).job();
    }
    public boolean hasGunner() { return occupants.keySet().stream().anyMatch(s -> definitions.get(s).job() == VehicleDefinition.Job.GUNNER); }
}
