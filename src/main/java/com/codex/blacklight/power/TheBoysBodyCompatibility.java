package com.codex.blacklight.power;

import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/** A biological Blacklight body owns its death transition; normal supes do not. */
public final class TheBoysBodyCompatibility {
    private TheBoysBodyCompatibility() { }
    public static boolean owns(LivingEntity entity) {
        if(!(entity instanceof ServerPlayer player)||!ModAttachments.available(player))return false;
        var state=player.getData(ModAttachments.PLAYER_STATE);
        return state.isInfected()&&state.isPrototypeMode();
    }
}
