package com.codex.blacklight.animation;

import com.codex.blacklight.combat.WeaponMove;
import net.minecraft.server.level.ServerPlayer;

/** Server presentation boundary; damage, rewards and movement rules remain in gameplay systems. */
public interface PlayerActionBackend {
    void play(ServerPlayer player, PlayerAction action, float transitionSeconds);
    void playVisual(ServerPlayer player,String clipId,float transitionSeconds);
    void playWeapon(ServerPlayer player, WeaponMove move, boolean charging, float clipOffsetSeconds);
    void holdJumpCharge(ServerPlayer player, float progress, boolean fullyCharged);
    void resume(ServerPlayer player);
    void synchronizeMode(ServerPlayer player, boolean enabled);
}
