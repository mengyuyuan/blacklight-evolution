package com.codex.blacklight.combat;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Animation imports from the bundled profile or an explicit developer directory. */
public final class LocalWeaponMotion {
    public static final String DIRECTORY_PROPERTY = "blacklight.localWeaponMotions";
    private static final Map<String, Optional<Definition>> LOADED = new HashMap<>();

    public record ContactWindow(int startTick, int endTick) {
        public boolean contains(double tick) { return tick >= startTick && tick < endTick; }
        public double progress(double tick) { return com.codex.blacklight.util.MathCompat.clamp((tick-startTick)/Math.max(1,endTick-startTick-1),0,1); }
    }
    /** Swept blade bounds in actor-local right/up/forward axes, relative to the current feet. */
    public record ContactBounds(double minRight, double minUp, double minForward,
                                double maxRight, double maxUp, double maxForward) {
        public boolean intersects(double right, double up, double forward,
                                  double radiusRight, double radiusUp, double radiusForward) {
            return right+radiusRight>=minRight && right-radiusRight<=maxRight
                    && up+radiusUp>=minUp && up-radiusUp<=maxUp
                    && forward+radiusForward>=minForward && forward-radiusForward<=maxForward;
        }
    }
    public record Definition(int durationTicks, int contactStartTick, int contactEndTick,
                             float transitionSeconds, JsonObject animation, List<ContactWindow> contactWindows,
                             Map<Integer,ContactBounds> contactBounds, Optional<Charge> charge,
                             Optional<WhipCurve> whipCurve, Optional<Landing> landing) {}

    public record Landing(float holdTime, float impactTime, boolean takeoff) {}

    public record Charge(int windupTicks, int holdTicks, float releaseTime, String animation,
                         Optional<String> chargedMove,int minimumTicks) {}

    private LocalWeaponMotion() {}

    /** Read once: the server's timing and the client's pose use the same immutable startup snapshot. */
    public static synchronized Optional<Definition> find(String id) {
        return LOADED.computeIfAbsent(id, LocalWeaponMotion::read);
    }

    private static Optional<Definition> read(String id) {
        var authored=AuthoredMotion.find(id);
        if(authored.isPresent())return Optional.of(parse(authored.orElseThrow()));
        if (!LocalMotionAnimation.validId(id)) throw new IllegalArgumentException("Invalid motion id: " + id);
        return MotionResources.read(id + ".json", 8 * 1024 * 1024).map(LocalWeaponMotion::parse);
    }

    static Definition parse(JsonObject json) {
        JsonObject meta = json.getAsJsonObject("blacklight");
        int duration = LocalMotionAnimation.integer(meta, "durationTicks");
        int start = LocalMotionAnimation.integer(meta, "contactStartTick");
        int end = LocalMotionAnimation.integer(meta, "contactEndTick");
        float transition = meta.get("transitionSeconds").getAsFloat();
        if (duration < 4 || duration > 200 || start < 2 || start >= end || end >= duration
                || !Float.isFinite(transition) || transition < 0 || transition > .15F)
            throw new IllegalArgumentException("Invalid local motion timing");
        var windows = new java.util.ArrayList<ContactWindow>();
        if (meta.has("contactWindows")) {
            for (var item : meta.getAsJsonArray("contactWindows")) {
                var window = item.getAsJsonObject();
                int from = LocalMotionAnimation.integer(window, "startTick"), to = LocalMotionAnimation.integer(window, "endTick");
                if (from < start || to > end || from >= to
                        || (!windows.isEmpty() && from < windows.get(windows.size()-1).endTick()))
                    throw new IllegalArgumentException("Invalid or overlapping local contact windows");
                windows.add(new ContactWindow(from, to));
            }
            if (windows.isEmpty() || windows.get(0).startTick() != start || windows.get(windows.size()-1).endTick() != end)
                throw new IllegalArgumentException("Contact windows must match the outer contact bounds");
        }
        var bounds = new HashMap<Integer,ContactBounds>();
        if (meta.has("contactBounds")) {
            for (var item : meta.getAsJsonArray("contactBounds")) {
                var value = item.getAsJsonObject();
                int tick = LocalMotionAnimation.integer(value, "tick");
                var min = value.getAsJsonArray("min"); var max = value.getAsJsonArray("max");
                if (tick < start || tick >= end || min.size()!=3 || max.size()!=3
                        || (!windows.isEmpty() && windows.stream().noneMatch(window->window.contains(tick))))
                    throw new IllegalArgumentException("Blade bounds must belong to an active contact tick");
                double[] low = new double[3], high = new double[3];
                for (int axis=0;axis<3;axis++) {
                    low[axis]=min.get(axis).getAsDouble(); high[axis]=max.get(axis).getAsDouble();
                    if (!Double.isFinite(low[axis]) || !Double.isFinite(high[axis])
                            || low[axis] < -16 || high[axis] > 16 || low[axis] > high[axis])
                        throw new IllegalArgumentException("Invalid actor-local blade bounds");
                }
                if (bounds.put(tick,new ContactBounds(low[0],low[1],low[2],high[0],high[1],high[2]))!=null)
                    throw new IllegalArgumentException("Duplicate blade bounds tick");
            }
            for(int tick=start;tick<end;tick++) {
                final int current=tick;
                if ((windows.isEmpty() || windows.stream().anyMatch(window->window.contains(current))) && !bounds.containsKey(tick))
                    throw new IllegalArgumentException("Missing blade bounds for active tick " + tick);
            }
        }
        float clipEnd = LocalMotionAnimation.validate(json, duration, transition, false);
        Optional<Charge> charge = Optional.empty();
        if (meta.has("charge")) {
            var value = meta.getAsJsonObject("charge");
            int windup = LocalMotionAnimation.integer(value, "windupTicks"), hold = LocalMotionAnimation.integer(value, "holdTicks");
            float release = value.get("releaseTime").getAsFloat();
            String animation = LocalMotionAnimation.id(value, "animation");
            Optional<String> charged = value.has("chargedMove") ? Optional.of(LocalMotionAnimation.id(value, "chargedMove")) : Optional.empty();
            int minimum=value.has("minimumTicks")?LocalMotionAnimation.integer(value,"minimumTicks"):windup;
            if (windup < 1 || windup > 40 || hold < 1 || hold > 100 || windup + hold > 160
                    || !Float.isFinite(release) || release <= 0 || release >= clipEnd
                    || Math.abs(windup - (int)Math.ceil(release * 20 - .0001)) > 0
                    || Math.ceil((release + transition) * 20 - .0001) - windup > Math.ceil(transition * 20) + 1
                    || start / 20F + .001F < release + transition || minimum<1 || minimum>windup)
                throw new IllegalArgumentException("Invalid charge phase timing");
            charge = Optional.of(new Charge(windup, hold, release, animation, charged,minimum));
        }
        Optional<WhipCurve> whip = meta.has("whipCurve")
                ? Optional.of(WhipCurve.parse(meta.getAsJsonObject("whipCurve"), clipEnd)) : Optional.empty();
        Optional<Landing> landing=Optional.empty();
        if(meta.has("landing")){
            var value=meta.getAsJsonObject("landing");
            float hold=value.get("holdTime").getAsFloat(),impact=value.get("impactTime").getAsFloat();
            if(!Float.isFinite(hold)||!Float.isFinite(impact)||hold<0||impact<=hold||impact>=clipEnd
                    ||start<Math.ceil((impact+transition)*20-.001))
                throw new IllegalArgumentException("Invalid landing phase timing");
            landing=Optional.of(new Landing(hold,impact,value.has("takeoff")&&value.get("takeoff").getAsBoolean()));
        }
        return new Definition(duration, start, end, transition, json.deepCopy(), List.copyOf(windows), Map.copyOf(bounds), charge, whip, landing);
    }
}
