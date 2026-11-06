package com.codex.blacklight.combat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Prototype's slowMotion/small after a full-charge hit. Minecraft 1.20.1 has no server tick-rate
 * manager (added in 1.20.3), so the Forge build keeps only the contact freeze and camera hit.
 */
public final class HeavySlowMotion {
    private HeavySlowMotion(){}
    public static void trigger(ServerPlayer p,int afterTicks){}
}
