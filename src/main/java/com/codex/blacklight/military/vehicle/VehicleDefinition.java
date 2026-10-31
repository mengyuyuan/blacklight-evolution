package com.codex.blacklight.military.vehicle;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public record VehicleDefinition(String id, double width, double length, double height,
        double maxHealth, double maxSpeed, long maxFuelTicks, Armor armor,
        List<String> liveries, List<Seat> seats, Map<String, Anchor> anchors,
        Vec3 turretPivot, Vec3 gunPivot, double barrelRadius,
        Map<String, Integer> ammunitionCapacities, Map<String, Weapon> weapons) {
    public VehicleDefinition {
        identifier(id);
        bounded(width, .25, 32, "width"); bounded(length, .25, 32, "length");
        bounded(height, .25, 32, "height"); bounded(maxHealth, .01, 10000, "health");
        bounded(maxSpeed, .001, 1, "speed");
        if (maxFuelTicks < 1 || maxFuelTicks > 72000) throw new IllegalArgumentException("Invalid fuel capacity");
        Objects.requireNonNull(armor); Objects.requireNonNull(turretPivot); Objects.requireNonNull(gunPivot);
        bounded(barrelRadius, 0, 1, "barrel radius");
        liveries = List.copyOf(liveries); seats = List.copyOf(seats);
        anchors = Map.copyOf(anchors); ammunitionCapacities = Map.copyOf(ammunitionCapacities); weapons = Map.copyOf(weapons);
        if (liveries.size() != 2 || !Set.copyOf(liveries).equals(Set.of("military", "blackwatch")))
            throw new IllegalArgumentException("Both supported liveries are required");
        if (seats.isEmpty() || seats.size() > 16 || anchors.size() > 64 || weapons.size() > 8 || ammunitionCapacities.size() > 8)
            throw new IllegalArgumentException("Unbounded vehicle definition");
        var seatIds = new HashSet<String>(); var seatAnchors = new HashSet<String>();
        for (Seat seat : seats) {
            if (!seatIds.add(seat.id()) || !seatAnchors.add(seat.anchorId()) || !anchors.containsKey(seat.anchorId()))
                throw new IllegalArgumentException("Duplicate seat or missing mount");
            if (anchors.get(seat.anchorId()).frame() != Frame.BODY) throw new IllegalArgumentException("Seats must attach to body");
        }
        if (seats.stream().filter(s -> s.job() == Job.DRIVER).count() != 1 || seats.stream().filter(s -> s.job() == Job.GUNNER).count() > 1)
            throw new IllegalArgumentException("Ambiguous vehicle control seats");
        for (var entry : anchors.entrySet()) { identifier(entry.getKey()); local(entry.getValue().position()); }
        local(turretPivot); local(gunPivot);
        for (var entry : ammunitionCapacities.entrySet()) {
            identifier(entry.getKey());
            if (entry.getValue() < 1 || entry.getValue() > 100000) throw new IllegalArgumentException("Invalid ammunition capacity");
        }
        for (var entry : weapons.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id()) || !ammunitionCapacities.containsKey(entry.getValue().ammunitionId()))
                throw new IllegalArgumentException("Weapon has no matching ammunition storage");
        }
        for (var first : weapons.values()) for (var second : weapons.values())
            if (first.cooldownGroup().equals(second.cooldownGroup()) && first.cooldownTicks()!=second.cooldownTicks())
                throw new IllegalArgumentException("One physical breech requires one reload interval");
    }
    public enum Armor { LIGHT, ARMORED, HEAVY }
    public enum Job { DRIVER, GUNNER, OFFICER, COMMANDER, PASSENGER }
    public enum Frame { BODY, TURRET_YAW, GUN_PITCH }
    public record Vec3(double x, double y, double z) {
        public Vec3 { bounded(x,-60000000,60000000,"x"); bounded(y,-60000000,60000000,"y"); bounded(z,-60000000,60000000,"z"); }
    }
    public record Seat(String id, Job job, String anchorId) {
        public Seat { identifier(id); Objects.requireNonNull(job); identifier(anchorId); }
    }
    public record Anchor(Frame frame, Vec3 position) {
        public Anchor { Objects.requireNonNull(frame); Objects.requireNonNull(position); local(position); }
    }
    public record Weapon(String id, String ammunitionId, int cooldownTicks, String cooldownGroup) {
        public Weapon {
            identifier(id); identifier(ammunitionId); identifier(cooldownGroup);
            if (cooldownTicks < 1 || cooldownTicks > 1200) throw new IllegalArgumentException("Invalid weapon cooldown");
            String expectedGroup=switch(id) {
                case "tank_he", "tank_ap" -> "tank_main";
                case "autocannon", "vehicle_rocket", "coaxial_machine_gun" -> id;
                default -> null;
            };
            if (expectedGroup!=null && !expectedGroup.equals(cooldownGroup))
                throw new IllegalArgumentException("Weapon cooldown group must match its physical breech");
        }
    }
    public Vec3 mountWorld(String id, Vec3 origin, double bodyYaw, double turretYaw, double pitch) {
        var anchor = anchors.get(id);
        if (anchor == null) throw new IllegalArgumentException("Unknown mount: " + id);
        Objects.requireNonNull(origin); finiteAngle(bodyYaw); finiteAngle(turretYaw); finiteAngle(pitch);
        Vec3 position = anchor.position();
        if (anchor.frame() == Frame.GUN_PITCH) position = add(gunPivot, rotatePitch(subtract(position,gunPivot),pitch));
        if (anchor.frame() != Frame.BODY) position = add(turretPivot,rotateYaw(subtract(position,turretPivot),turretYaw));
        return add(origin,rotateYaw(position,bodyYaw));
    }
    static void identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid vehicle identifier");
    }
    static void bounded(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + name);
    }
    static void finiteAngle(double angle) { bounded(angle,-360000,360000,"angle"); }
    private static void local(Vec3 point) { bounded(point.x(),-32,32,"mount x"); bounded(point.y(),-32,32,"mount y"); bounded(point.z(),-32,32,"mount z"); }
    static Vec3 add(Vec3 a, Vec3 b) { return new Vec3(a.x()+b.x(),a.y()+b.y(),a.z()+b.z()); }
    static Vec3 subtract(Vec3 a, Vec3 b) { return new Vec3(a.x()-b.x(),a.y()-b.y(),a.z()-b.z()); }
    static Vec3 rotateYaw(Vec3 p, double yaw) {
        double a=Math.toRadians(yaw), c=Math.cos(a), s=Math.sin(a);
        return new Vec3(p.x()*c+p.z()*s,p.y(),p.z()*c-p.x()*s);
    }
    static Vec3 rotatePitch(Vec3 p, double pitch) {
        double a=Math.toRadians(pitch),c=Math.cos(a),s=Math.sin(a);
        return new Vec3(p.x(),p.y()*c-p.z()*s,p.y()*s+p.z()*c);
    }
}
