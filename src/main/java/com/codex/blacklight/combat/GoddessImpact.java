package com.codex.blacklight.combat;

import com.codex.blacklight.power.CombatScale;
import net.minecraft.world.entity.player.Player;

/** Fractions of the struck player's maximum health, authored before shields, armor and other defenses. */
public enum GoddessImpact {
    CROWN_NEEDLE(.05F), FINGER_NEEDLE(.06F), SPINE_BOLT(.07F), BACKHAND(.08F),
    LOW_SURF(.10F), HIGH_SURF(.10F), FINGER_SHOT(.10F), TENDRIL_POD(.10F), GROUND_WRIST(.10F),
    WALL_SPEAR(.12F), HAND_SWEEP(.13F), THROWN_ROCK(.16F), PALM_CANNON(.20F),
    BODY_THROW(.23F), HAND_SLAM(.24F), VACUOLE(.30F),
    CROWN_ACID(.08F), TENDRIL_ERUPTION(.15F),
    SONIC_PING(.06F), SONIC_HOWL(.14F), SPIKE_FIELD(.10F), INFECTION_WAVE(.12F);

    private final float fraction;

    GoddessImpact(float fraction) { this.fraction = fraction; }

    public float incoming(Player player) {
        return incoming(player.getMaxHealth(), CombatScale.units(player));
    }

    float incoming(float maximum, float healthUnits) {
        return (maximum / healthUnits) * fraction;
    }
}
