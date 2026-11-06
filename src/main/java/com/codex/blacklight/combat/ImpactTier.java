package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/**
 * Hit weight shared by hitstop, knockback, camera, sound and contact effects.
 * Combos escalate: the opener is LIGHT, later links MEDIUM, launchers and
 * knockdowns HEAVY, and combo enders / charged / quake moves FINISHER.
 */
public enum ImpactTier {
    LIGHT(1, 1.00, .36F, 0, .85F),
    MEDIUM(2, 1.18, .62F, 0, 1.0F),
    HEAVY(4, 1.38, .98F, .06, 1.22F),
    FINISHER(6, 1.62, 1.40F, .14, 1.5F);

    /** Server ticks both actor and victim are held at the contact frame. */
    public final int hitstop;
    public final double force;
    public final float feedback;
    public final double lift;
    public final float effectScale;

    ImpactTier(int hitstop, double force, float feedback, double lift, float effectScale) {
        this.hitstop = hitstop; this.force = force; this.feedback = feedback; this.lift = lift; this.effectScale = effectScale;
    }

    public boolean atLeast(ImpactTier other) { return ordinal() >= other.ordinal(); }

    public static ImpactTier of(WeaponMove move) {
        if (move.id.startsWith("dev_") || move == WeaponMove.BULLETDIVE_DROP) return FINISHER;
        if (WeaponImpact.finisher(move)) return FINISHER;
        var m = move.uncharged();
        if (m.airCombo() || m == WeaponMove.AIR_LAUNCH_FOLLOW) return MEDIUM;
        var profile = ImpactProfile.of(move);
        if (profile.knockdown() || profile.kind() == HitReaction.SLAM || profile.kind() == HitReaction.LAUNCH
                || move.rush() || move.landingAttack()) return HEAVY;
        if (m.firstCombo() || m == WeaponMove.CLAW_AUTO1 || m == WeaponMove.COMBO_KICK1
                || m == WeaponMove.AIR_AUTO1) return m.form == PowerForm.HAMMERFISTS ? MEDIUM : LIGHT;
        return MEDIUM;
    }
}
