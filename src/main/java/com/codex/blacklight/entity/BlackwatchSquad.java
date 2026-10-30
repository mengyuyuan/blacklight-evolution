package com.codex.blacklight.entity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/** Server-thread squad decisions, independent of Minecraft and loader APIs. */
public final class BlackwatchSquad {
    public static final int MAX_MEMBERS = 4;
    public static final int CONTACT_TICKS = 100;
    public static final int BURST_TICKS = 29;
    public static final int FORM_RADIUS = 16;
    public static final int PATROL_RADIUS = 12;
    public static final int SHARE_RADIUS = 32;
    public static final int PURSUIT_RADIUS = 48;

    public record Position(double x, double y, double z) {}
    public record Contact(UUID observer, UUID target, Position position,
                          Position observerPosition, long observedAt, boolean retaliation) {}

    private final TreeSet<UUID> members = new TreeSet<>();
    private final LinkedHashSet<UUID> waiting = new LinkedHashSet<>();
    private record FireLease(long since, int ticks) {}
    private final LinkedHashMap<UUID, FireLease> firing = new LinkedHashMap<>();
    private UUID leader;
    private Contact contact;

    public boolean add(UUID member) {
        if (member == null || members.size() >= MAX_MEMBERS || !members.add(member)) return false;
        if (leader == null) leader = member;
        return true;
    }

    public void remove(UUID member) {
        members.remove(member);
        releaseFire(member);
        if (member != null && member.equals(leader)) leader = members.isEmpty() ? null : members.first();
        while (firing.size() > fireLimit()) {
            UUID last = null;
            for (var id : firing.keySet()) last = id;
            firing.remove(last);
        }
        if (members.isEmpty()) contact = null;
    }

    public List<UUID> members() { return List.copyOf(members); }
    public UUID leader() { return leader; }

    public void observe(Contact observation) {
        if (observation == null || !members.contains(observation.observer())
                || observation.target() == null || members.contains(observation.target())
                || observation.observedAt() < 0 || !finite(observation.position())
                || !finite(observation.observerPosition())) return;
        if (contact == null || observation.observedAt() >= contact.observedAt()) contact = observation;
    }

    public Optional<Contact> contact(long now) {
        return contact != null && fresh(now, contact.observedAt(), CONTACT_TICKS)
                ? Optional.of(contact) : Optional.empty();
    }

    public void forget(UUID target) {
        if (contact != null && contact.target().equals(target)) contact = null;
    }

    public boolean requestFire(UUID member, long now) {
        return requestFire(member, now, BURST_TICKS);
    }

    public boolean requestFire(UUID member, long now, int duration) {
        if (duration < 1 || duration > CONTACT_TICKS) return false;
        expire(now);
        if (!members.contains(member)) return false;
        if (firing.containsKey(member)) return true;
        waiting.add(member);
        if (firing.size() >= fireLimit() || !waiting.iterator().next().equals(member)) return false;
        waiting.remove(member);
        firing.put(member, new FireLease(now, duration));
        return true;
    }

    public boolean hasFirePermit(UUID member, long now) {
        expire(now);
        return members.contains(member) && firing.containsKey(member);
    }

    public void releaseFire(UUID member) {
        firing.remove(member);
        waiting.remove(member);
    }

    private int fireLimit() { return (members.size() + 1) / 2; }

    private void expire(long now) {
        firing.entrySet().removeIf(entry -> !fresh(now, entry.getValue().since(), entry.getValue().ticks()));
    }

    private static boolean fresh(long now, long observed, int lifetime) {
        return now >= observed && now - observed < lifetime;
    }

    private static boolean finite(Position position) {
        return position != null && Double.isFinite(position.x())
                && Double.isFinite(position.y()) && Double.isFinite(position.z());
    }
}
