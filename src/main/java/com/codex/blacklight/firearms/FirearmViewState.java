package com.codex.blacklight.firearms;

/** Client-only presentation state with no Minecraft or input dependency. */
public final class FirearmViewState {
    public enum Action { NONE, SHOT, RELOAD }
    public record Frame(float recoil, float reload) { }
    public record HeldSnapshot(String weapon, int slot, int magazine, long readyAt,
                               int cooldownTicks, int reloadTicks) { }
    public record HandActions(Action main, Action off) { }
    private String weapon;
    private int slot;
    private int magazine;
    private long readyAt;
    private long observedAt;
    private double shotAt = Double.NEGATIVE_INFINITY;
    private long reloadEnd;
    private int reloadDuration;

    public static HandActions observeHands(FirearmViewState main, HeldSnapshot mainSnapshot,
                                          FirearmViewState off, HeldSnapshot offSnapshot,
                                          long now, boolean enabled) {
        // Compare both old hands before observing either new hand. One changed hand
        // matching the other is also a valid shot, so only a complete exchange resets.
        if (enabled && main.matchesHeld(offSnapshot) && off.matchesHeld(mainSnapshot)
                && (!main.matchesHeld(mainSnapshot) || !off.matchesHeld(offSnapshot))) {
            main.reset();
            off.reset();
        }
        return new HandActions(main.observe(mainSnapshot, now, enabled), off.observe(offSnapshot, now, enabled));
    }

    private boolean matchesHeld(HeldSnapshot snapshot) {
        // The physical hand slot deliberately differs when the two guns exchange.
        return snapshot != null && weapon != null && weapon.equals(snapshot.weapon())
                && magazine == snapshot.magazine() && readyAt == snapshot.readyAt();
    }

    private Action observe(HeldSnapshot snapshot, long now, boolean enabled) {
        if (!enabled || snapshot == null) {
            reset();
            return Action.NONE;
        }
        return observe(snapshot.weapon(), snapshot.slot(), snapshot.magazine(), snapshot.readyAt(),
                snapshot.cooldownTicks(), snapshot.reloadTicks(), now, true);
    }

    /** Observes server-synchronised item data, never raw use/attack input. */
    public Action observe(String weapon, int slot, int magazine, long readyAt,
                          int cooldownTicks, int reloadTicks, long now, boolean enabled) {
        if (!enabled || weapon == null || magazine < 0 || cooldownTicks < 1 || reloadTicks < 1) {
            reset();
            return Action.NONE;
        }
        boolean sameWeapon = weapon.equals(this.weapon) && slot == this.slot && now >= observedAt;
        Action action = Action.NONE;
        if (!sameWeapon) {
            reset();
        } else if (readyAt > this.readyAt) {
            long remaining = readyAt - now;
            // A short packet delay must not erase feedback for low-cooldown weapons.
            if (this.magazine - magazine == 1 && remaining >= -4 && remaining <= cooldownTicks + 2L) {
                shotAt = now;
                reloadDuration = 0;
                action = Action.SHOT;
            } else if (magazine > this.magazine && remaining > 0 && remaining <= reloadTicks + 2L) {
                shotAt = Double.NEGATIVE_INFINITY;
                reloadEnd = readyAt;
                reloadDuration = reloadTicks;
                action = Action.RELOAD;
            }
        }
        this.weapon = weapon;
        this.slot = slot;
        this.magazine = magazine;
        this.readyAt = readyAt;
        observedAt = now;
        return action;
    }

    public Frame frame(double now) {
        double age = now - shotAt;
        // The source has a short rise, one peak and a recovery over 0.5 seconds.
        // Sampling it at a shot's own clock prevents global-time oscillation or repeated shots.
        float recoil = age >= 0 && age <= 10 ? (float)(com.codex.blacklight.client.model.BlackwatchReferenceProfile.load()
                .sample("shoot_standing","Two Hand Carry","rotation",age/20)[0]/1.103638) : 0;
        recoil=Math.max(0,Math.min(1,recoil));
        double remaining = reloadEnd - now;
        float reload = reloadDuration > 0 && remaining > 0 && remaining <= reloadDuration
                ? (float) Math.sin(Math.PI * remaining / reloadDuration) : 0;
        return new Frame(recoil, reload);
    }

    public double shotAgeSeconds(double now) {
        double age=now-shotAt;return age>=0&&age<=10?age/20:Double.POSITIVE_INFINITY;
    }

    /** Server-owned reload duration; zero at rest, one when the observed reload has finished. */
    public double reloadProgress(double now) {
        return reloadDuration>0?Math.max(0,Math.min(1,1-(reloadEnd-now)/reloadDuration)):0;
    }

    public void reset() {
        weapon = null;
        shotAt = Double.NEGATIVE_INFINITY;
        reloadDuration = 0;
    }
}
