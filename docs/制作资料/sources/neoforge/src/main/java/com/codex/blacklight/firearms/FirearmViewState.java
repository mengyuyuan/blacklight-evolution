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
            if (magazine < this.magazine && remaining >= -4 && remaining <= cooldownTicks + 2L) {
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
        float recoil = age >= 0 && age < 8 ? (float) Math.pow(1 - age / 8, 2) : 0;
        double remaining = reloadEnd - now;
        float reload = reloadDuration > 0 && remaining > 0 && remaining <= reloadDuration
                ? (float) Math.sin(Math.PI * remaining / reloadDuration) : 0;
        return new Frame(recoil, reload);
    }

    public void reset() {
        weapon = null;
        shotAt = Double.NEGATIVE_INFINITY;
        reloadDuration = 0;
    }
}
