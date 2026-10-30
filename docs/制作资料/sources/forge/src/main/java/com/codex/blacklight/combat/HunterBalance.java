package com.codex.blacklight.combat;

/** Fixed elite encounter values in pre-CombatScale units. Player upgrades retain their benefit. */
public final class HunterBalance {
    public static final double HEALTH = 96, DAMAGE = 14, SPEED = .32, KNOCKBACK_RESISTANCE = .65;
    public static final int LIGHT_STAGGER_TICKS = 8, LIGHT_STAGGER_INTERVAL = 32;
    // Fixed encounter modifiers: weapon upgrades still increase damage proportionally.
    public static final float PLAYER_ATTACK_FACTOR = .75F, ORDINARY_PLAYER_HURT_FACTOR = .6F;
    private HunterBalance() {}

    public static float damageMultiplier(CreatureAttack move) {
        return switch (move) {
            case BARRAGE -> .75F;
            case SLAM -> 1.6F;
            case LEAP -> 1.25F;
            default -> 1F;
        };
    }

    /** The generic infected-elite cap otherwise erases every heavy attack's extra damage. */
    public static float infectedDamage(float rawDamage, CreatureAttack move, float difficulty) {
        float budget = switch (move) {
            case BARRAGE -> 24F;
            case SLAM -> 48F;
            case LEAP -> 38F;
            default -> 30F;
        };
        return Math.min(rawDamage * .4F, budget * difficulty / 5F);
    }
}
