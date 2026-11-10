package com.codex.blacklight.combat;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Local charge and body poses that cannot declare damage or recursively start another charge. */
public final class LocalVisualMotion {
    private static final Map<String, Optional<Definition>> LOADED = new HashMap<>();
    private static final Set<String> METADATA = Set.of("durationTicks", "transitionSeconds", "whipCurve");
    public record Definition(int durationTicks, float transitionSeconds, JsonObject animation,
                             Optional<WhipCurve> whipCurve) {}

    private LocalVisualMotion() {}

    public static Optional<Definition> find(String id) {
        if (!LocalMotionAnimation.validId(id)) throw new IllegalArgumentException("Invalid motion id: " + id);
        synchronized (LOADED) { var known = LOADED.get(id); if (known != null) return known; }
        // Parse outside the lock: a 100-400 KB clip took 100-200 ms and the render thread's
        // lookup of any other clip waited behind it. A rare duplicate parse keeps the first result.
        var parsed = read(id);
        synchronized (LOADED) { return LOADED.computeIfAbsent(id, ignored -> parsed); }
    }

    private static Optional<Definition> read(String id) {
        var authored=AuthoredMotion.find(id);
        if(authored.isPresent())return Optional.of(parse(authored.orElseThrow()));
        if (!LocalMotionAnimation.validId(id)) throw new IllegalArgumentException("Invalid motion id: " + id);
        return MotionResources.read(id + ".json", 8 * 1024 * 1024).map(LocalVisualMotion::parse);
    }

    static Definition parse(JsonObject json) {
        var meta = json.getAsJsonObject("blacklight");
        if (meta == null) throw new IllegalArgumentException("Missing visual motion metadata");
        for (String key : meta.keySet()) if (!METADATA.contains(key))
            throw new IllegalArgumentException("Visual-only motion cannot declare " + key);
        int duration = LocalMotionAnimation.integer(meta, "durationTicks");
        float transition = meta.get("transitionSeconds").getAsFloat();
        float clipEnd = LocalMotionAnimation.validate(json, duration, transition, true);
        Optional<WhipCurve> whip = meta.has("whipCurve")
                ? Optional.of(WhipCurve.parse(meta.getAsJsonObject("whipCurve"), clipEnd)) : Optional.empty();
        return new Definition(duration, transition, json.deepCopy(), whip);
    }
}
