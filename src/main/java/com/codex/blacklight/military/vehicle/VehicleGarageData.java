package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.military.MilitaryFaction;
import java.util.*;

/** Identity and one-time issuance only. Actual resources live on vehicles and in containers. */
public final class VehicleGarageData {
    public static final int SCHEMA_VERSION = 1;
    public static final List<String> CHASSIS = List.of("truck", "apc", "tank");
    public enum State { UNISSUED, RESERVED, ISSUED, LOST }
    public record Site(int x, int floor, int z, int rotation) {
        public Site {
            if (Math.abs((long)x)>29_999_900 || Math.abs((long)z)>29_999_900 || Math.abs((long)floor)>4096 || rotation<0 || rotation>3)
                throw new IllegalArgumentException("Invalid garage site");
        }
        public int width() { return (rotation&1)==0?31:27; }
        public int depth() { return (rotation&1)==0?27:31; }
        /** Block coordinates, not continuous boundary coordinates. */
        public int[] world(int localX, int localZ) {
            return switch(rotation) {
                case 1 -> new int[]{x+26-localZ,z+localX};
                case 2 -> new int[]{x+30-localX,z+26-localZ};
                case 3 -> new int[]{x+localZ,z+30-localX};
                default -> new int[]{x+localX,z+localZ};
            };
        }
    }
    public record Slot(String chassis, UUID vehicle, State state) { }
    public record Garage(String id, String baseId, String dimension, MilitaryFaction faction, Site site, Map<String, Slot> slots) {
        public Garage { slots = Map.copyOf(slots); }
    }
    public record Snapshot(int schemaVersion, List<Garage> garages) {
        public Snapshot { garages = List.copyOf(garages); }
    }
    private final Map<String, Garage> garages = new LinkedHashMap<>();
    public boolean register(String id, String baseId, String dimension, MilitaryFaction faction, Site site) {
        if (!reference(id) || !reference(baseId) || !dimension(dimension) || faction==null || site==null || garages.size()>=2048 || garages.containsKey(id)) return false;
        if (garages.values().stream().anyMatch(g->g.dimension().equals(dimension)&&g.site().equals(site))) return false;
        var slots=new LinkedHashMap<String,Slot>();
        for(String chassis:CHASSIS) slots.put(chassis,new Slot(chassis,null,State.UNISSUED));
        garages.put(id,new Garage(id,baseId,dimension,faction,site,slots));return true;
    }
    public boolean reserve(String garage, String chassis, UUID vehicle) {
        var found=garages.get(garage);
        if(found==null || vehicle==null || !CHASSIS.contains(chassis))return false;
        if(found.slots().get(chassis).state()!=State.UNISSUED || garages.values().stream().flatMap(g->g.slots().values().stream()).anyMatch(s->vehicle.equals(s.vehicle())))return false;
        update(found,new Slot(chassis,vehicle,State.RESERVED));return true;
    }
    public boolean issued(String garage, String chassis, UUID vehicle) {
        var found=garages.get(garage);if(found==null || vehicle==null)return false;
        var slot=found.slots().get(chassis);
        if(slot==null || slot.state()!=State.RESERVED || !vehicle.equals(slot.vehicle()))return false;
        update(found,new Slot(chassis,vehicle,State.ISSUED));return true;
    }
    /** Only explicit destruction is loss evidence. Missing loaded entities never call this. */
    public boolean lost(UUID vehicle) {
        if(vehicle==null)return false;
        for(var garage:garages.values())for(var slot:garage.slots().values())
            if(vehicle.equals(slot.vehicle()) && slot.state()!=State.LOST) {
                update(garage,new Slot(slot.chassis(),vehicle,State.LOST));return true;
            }
        return false;
    }
    private void update(Garage garage,Slot slot) {
        var next=new LinkedHashMap<>(garage.slots());next.put(slot.chassis(),slot);
        garages.put(garage.id(),new Garage(garage.id(),garage.baseId(),garage.dimension(),garage.faction(),garage.site(),next));
    }
    public Optional<Garage> garage(String id) { return Optional.ofNullable(garages.get(id)); }
    public List<Garage> garages() { return List.copyOf(garages.values()); }
    public Snapshot snapshot() { return new Snapshot(SCHEMA_VERSION, garages()); }
    public static VehicleGarageData restore(Snapshot snapshot) {
        if(snapshot==null || snapshot.schemaVersion()!=SCHEMA_VERSION || snapshot.garages().size()>2048)throw new IllegalArgumentException("Invalid garage ledger schema");
        var data=new VehicleGarageData();var identities=new HashSet<UUID>();var sites=new HashSet<String>();
        for(var garage:snapshot.garages()) {
            if(garage==null || !reference(garage.id()) || !reference(garage.baseId()) || !dimension(garage.dimension()) || garage.faction()==null || garage.site()==null
                    || data.garages.containsKey(garage.id()) || !sites.add(garage.dimension()+":"+garage.site()) || !garage.slots().keySet().equals(Set.copyOf(CHASSIS)))
                throw new IllegalArgumentException("Invalid or duplicate garage registration");
            for(String chassis:CHASSIS) {
                var slot=garage.slots().get(chassis);
                if(slot==null || !chassis.equals(slot.chassis()) || slot.state()==null || (slot.state()==State.UNISSUED)!=(slot.vehicle()==null)
                        || slot.vehicle()!=null && !identities.add(slot.vehicle()))throw new IllegalArgumentException("Invalid or duplicate garage slot");
            }
            data.garages.put(garage.id(),garage);
        }
        return data;
    }
    private static boolean reference(String value) { return value!=null && !value.isBlank() && value.length()<=256; }
    private static boolean dimension(String value) { return reference(value) && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"); }
}
