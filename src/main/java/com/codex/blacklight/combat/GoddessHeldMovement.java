package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/** The encounter moves the captured player's feet on both sides until release. */
public final class GoddessHeldMovement {
    private static final Map<ServerPlayer, WeakReference<EncounterMob>> owners = new WeakHashMap<>();

    static void commit(ServerPlayer player, EncounterMob boss) {
        var current = owners.get(player);
        if (current == null || current.get() != boss) owners.put(player, new WeakReference<>(boss));
        player.connection.resetPosition();
    }

    public static boolean controls(ServerPlayer player) {
        var reference = owners.get(player);
        if (reference == null) return false;
        var boss = reference.get();
        if (boss != null && boss.isAlive() && boss.level() == player.level() && player.isAlive() && !player.isSpectator()) {
            var encounter = boss.goddess;
            if (encounter.action() == GoddessEncounter.Action.EXTRACTING && encounter.isActor(player)) return true;
            if (encounter.attacks.holds(player)) return true;
        }
        owners.remove(player);
        return false;
    }

    static void release(ServerPlayer player, EncounterMob boss) {
        if (player == null) return;
        var current = owners.get(player);
        if (current == null || current.get() != boss) return;
        owners.remove(player);
        if (player.isAlive() && player.level() == boss.level()) {
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
    }
    private GoddessHeldMovement() {}
}
