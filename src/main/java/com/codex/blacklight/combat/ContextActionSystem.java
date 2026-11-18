package com.codex.blacklight.combat;

import net.minecraft.server.level.ServerPlayer;

/** E remains grab. C selects a held-body slam or a grounded stomp. */
public final class ContextActionSystem {
    private ContextActionSystem(){}
    public static void request(ServerPlayer player){
        // A rejected held action must never fall through into an unrelated stomp.
        if(ConsumeSystem.active(player)){ConsumeSystem.attackHeld(player);return;}
        var block=BlockThrowSystem.held(player);
        if(block!=null){block.slamFrom(player);return;}
        WeaponCombatSystem.requestStomp(player);
    }
}
