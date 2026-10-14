package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

/** Hunger is suspended only while an infected player uses Prototype mode. */
public final class PrototypeMetabolism {
    private PrototypeMetabolism() { }

    public static boolean active(Player player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !ModAttachments.available(player)) return false;
        var state = com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);
        return state.isInfected() && state.isPrototypeMode();
    }
}
