package com.codex.blacklight.military;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** A bounded organization tree with atomic individual edits and no Minecraft references. */
public final class MilitaryOrganization {
    public static final int MAX_LEAF = 32, MAX_CHILDREN = 8, MAX_DEPTH = 4, MAX_TREE_MEMBERS = 256, MAX_UNITS = 2048;

    public record UnitView(UUID id, MilitaryFaction faction, MilitaryUnitType type, String name, int capacity,
                           MilitaryOrder.Point origin, UUID parent, List<UUID> children,
                           Map<UUID, MilitaryRank> members, UUID commander, MilitaryOrder order) {
        public UnitView { children = List.copyOf(children); members = Map.copyOf(members); }
    }

    private static final class Unit {
        final UUID id;
        final MilitaryFaction faction;
        final MilitaryUnitType type;
        final String name;
        final MilitaryOrder.Point origin;
        int capacity;
        UUID parent, commander;
        MilitaryOrder order;
        final TreeSet<UUID> children = new TreeSet<>();
        final TreeMap<UUID, MilitaryRank> members = new TreeMap<>();
        Unit(UUID id, MilitaryFaction faction, MilitaryUnitType type, String name, int capacity, MilitaryOrder.Point origin) {
            this.id = id; this.faction = faction; this.type = type; this.name = name; this.capacity = capacity; this.origin = origin;
        }
        UnitView view() { return new UnitView(id, faction, type, name, capacity, origin, parent, List.copyOf(children), members, commander, order); }
    }

    private final TreeMap<UUID, Unit> units = new TreeMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();
    private long revision;

    public long revision() { return revision; }
    public boolean create(UUID id, MilitaryFaction faction, MilitaryUnitType type, String name, int capacity, MilitaryOrder.Point origin) {
        if (id == null || faction == null || type == null || name == null || name.isBlank() || name.length() > 64
                || origin == null || !origin.finite() || units.containsKey(id) || units.size() >= MAX_UNITS
                || units.values().stream().anyMatch(u -> u.name.equalsIgnoreCase(name))) return false;
        if (!type.leaf() && capacity == 0) capacity = type.defaultSize(faction);
        if (capacity < 1 || capacity > (type.leaf() ? MAX_LEAF : MAX_TREE_MEMBERS)) return false;
        units.put(id, new Unit(id, faction, type, name, capacity, origin)); revision++;
        return true;
    }

    public Optional<UnitView> unit(UUID id) { return id == null ? Optional.empty() : Optional.ofNullable(units.get(id)).map(Unit::view); }
    public Optional<UnitView> named(String name) { return units.values().stream().filter(u -> u.name.equalsIgnoreCase(name)).findFirst().map(Unit::view); }
    public List<UnitView> units() { return units.values().stream().map(Unit::view).toList(); }
    public Optional<UUID> leafOf(UUID member) { return Optional.ofNullable(owners.get(member)); }

    public List<UUID> allMembers(UUID unit) {
        var result = new TreeSet<UUID>(); collect(unit, result); return List.copyOf(result);
    }
    private void collect(UUID id, Set<UUID> result) {
        var unit = units.get(id); if (unit == null) return;
        result.addAll(unit.members.keySet());
        for (var child : unit.children) collect(child, result);
    }
    public List<UUID> ancestors(UUID id) {
        var result = new ArrayList<UUID>();
        for (var unit = id == null ? null : units.get(id); unit != null;
             unit = unit.parent == null ? null : units.get(unit.parent)) result.add(unit.id);
        return List.copyOf(result);
    }
    public Optional<UUID> rootOf(UUID id) {
        var path = ancestors(id); return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
    }

    public boolean assign(UUID id, UUID member, MilitaryRank rank) {
        var unit = units.get(id);
        if (unit == null || !unit.type.leaf() || member == null || rank == null || owners.containsKey(member)
                || unit.members.size() >= unit.capacity || allMembers(rootOf(id).orElse(id)).size() >= MAX_TREE_MEMBERS) return false;
        unit.members.put(member, rank); owners.put(member, id); repairCommanders(id); revision++;
        return true;
    }
    public boolean resize(UUID id, int capacity) {
        var unit = units.get(id);
        if (unit == null || !unit.type.leaf() || capacity < unit.members.size() || capacity < 1 || capacity > MAX_LEAF) return false;
        if (unit.capacity != capacity) { unit.capacity = capacity; revision++; }
        return true;
    }
    public void removeMember(UUID member) {
        var id = owners.remove(member); if (id == null) return;
        units.get(id).members.remove(member); repairCommanders(id); revision++;
    }
    public void rank(UUID member, MilitaryRank rank) {
        var id = owners.get(member); if (id == null || rank == null) return;
        var unit = units.get(id);
        if (unit.members.put(member, rank) != rank) revision++;
    }

    public boolean attach(UUID parentId, UUID childId) {
        var parent = units.get(parentId); var child = units.get(childId);
        if (parent == null || child == null || parent == child || parent.type.level() <= child.type.level()
                || parent.faction != child.faction || child.parent != null || parent.children.size() >= MAX_CHILDREN
                || ancestors(parentId).size() + depth(childId) > MAX_DEPTH
                || allMembers(rootOf(parentId).orElse(parentId)).size() + allMembers(childId).size() > MAX_TREE_MEMBERS) return false;
        parent.children.add(childId); child.parent = parentId; repairCommanders(parentId); revision++;
        return true;
    }
    private int depth(UUID id) {
        var unit = units.get(id); if (unit == null) return 0;
        int depth = 0; for (var child : unit.children) depth = Math.max(depth, depth(child));
        return depth + 1;
    }
    public boolean detach(UUID childId) {
        var child = units.get(childId); if (child == null || child.parent == null) return false;
        var parent = child.parent; units.get(parent).children.remove(childId); child.parent = null;
        repairCommanders(parent); revision++; return true;
    }
    /** Remove only an empty node; members and child trees are never silently deleted. */
    public boolean removeUnit(UUID id) {
        var unit = units.get(id); if (unit == null || !unit.members.isEmpty() || !unit.children.isEmpty()) return false;
        detach(id); units.remove(id); revision++; return true;
    }

    public boolean appoint(UUID id, UUID member) {
        var unit = units.get(id); if (unit == null || member == null || !allMembers(id).contains(member)) return false;
        if (!member.equals(unit.commander)) { unit.commander = member; revision++; }
        return true;
    }
    public Optional<UUID> actingCommander(UUID id, Set<UUID> available) {
        var unit = units.get(id); if (unit == null || available == null) return Optional.empty();
        if (unit.commander != null && available.contains(unit.commander)) return Optional.of(unit.commander);
        return best(allMembers(id).stream().filter(available::contains).toList());
    }
    private Optional<UUID> best(List<UUID> members) {
        return members.stream().min(Comparator.<UUID>comparingInt(id -> rankOf(id).ordinal()).reversed().thenComparing(Comparator.naturalOrder()));
    }
    private MilitaryRank rankOf(UUID member) {
        var unit = units.get(owners.get(member)); return unit == null ? MilitaryRank.PRIVATE : unit.members.getOrDefault(member, MilitaryRank.PRIVATE);
    }
    private void repairCommanders(UUID start) {
        for (var id : ancestors(start)) {
            var unit = units.get(id); var roster = allMembers(id);
            if (unit.commander == null || !roster.contains(unit.commander)) unit.commander = best(roster).orElse(null);
        }
    }
    public boolean order(UUID id, MilitaryOrder order) {
        var unit = units.get(id); if (unit == null || order != null && !order.valid()) return false;
        if (!Objects.equals(unit.order, order)) { unit.order = order; revision++; }
        return true;
    }
    public Optional<MilitaryOrder> effectiveOrder(UUID id) {
        for (var ancestor : ancestors(id)) {
            var order = units.get(ancestor).order; if (order != null) return Optional.of(order);
        }
        return Optional.empty();
    }
    /** Private mutable nodes are copied; immutable UUIDs/enums/value records may be shared. */
    public MilitaryOrganization copy() {
        var copy = new MilitaryOrganization();
        for (var original : units.values()) {
            var unit = new Unit(original.id, original.faction, original.type, original.name, original.capacity, original.origin);
            unit.parent = original.parent; unit.commander = original.commander; unit.order = original.order;
            unit.members.putAll(original.members); unit.children.addAll(original.children); copy.units.put(unit.id, unit);
        }
        copy.owners.putAll(owners); copy.revision = revision; return copy;
    }
}
