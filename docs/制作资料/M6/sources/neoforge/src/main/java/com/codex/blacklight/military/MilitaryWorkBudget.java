package com.codex.blacklight.military;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One per loaded level. FIFO work grants, coalescing repeated requests and enforcing tick budgets. */
public final class MilitaryWorkBudget {
    public enum Kind {
        NAVIGATION(8, 10), DISCOVERY(4, 20);
        final int limit, interval;
        Kind(int limit, int interval) { this.limit = limit; this.interval = interval; }
    }
    private static final class Queue {
        final LinkedHashMap<UUID, Long> pending = new LinkedHashMap<>();
        final Map<UUID, Long> granted = new HashMap<>();
        int used;
    }
    private final EnumMap<Kind, Queue> queues = new EnumMap<>(Kind.class);
    private long clock = Long.MIN_VALUE;
    public MilitaryWorkBudget() { for (var kind : Kind.values()) queues.put(kind, new Queue()); }

    private void advance(long now) {
        if (now == clock) return;
        for (var queue : queues.values()) {
            queue.used = 0;
            if (now < clock) { queue.granted.clear(); queue.pending.replaceAll((id, since) -> now); }
            else queue.granted.entrySet().removeIf(entry -> now - entry.getValue() > 200);
        }
        clock = now;
    }
    public void enqueue(Kind kind, UUID id, long now) {
        if (kind == null || id == null) return;
        advance(now); queues.get(kind).pending.put(id, now);
    }
    public List<UUID> drain(Kind kind, long now) {
        advance(now); var queue = queues.get(kind); var result = new ArrayList<UUID>();
        var iterator = queue.pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() > 100) { iterator.remove(); continue; }
            if (queue.used >= kind.limit) break;
            var previous = queue.granted.get(entry.getKey());
            if (previous != null && now - previous < kind.interval) continue;
            result.add(entry.getKey()); queue.granted.put(entry.getKey(), now); queue.used++; iterator.remove();
        }
        return List.copyOf(result);
    }
    public void remove(UUID id) { for (var queue : queues.values()) { queue.pending.remove(id); queue.granted.remove(id); } }
}
