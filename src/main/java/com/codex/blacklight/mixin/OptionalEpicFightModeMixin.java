package com.codex.blacklight.mixin;

import com.codex.blacklight.compat.EpicFightBlacklightBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Optional Epic Fight integration: preserve the normal Epic Fight event
 * dispatch, then prevent its player-mode event from overriding Blacklight's
 * active Prototype form. No Epic Fight API is linked at compile time.
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch", remap = false)
public abstract class OptionalEpicFightModeMixin {
    @Redirect(
            method = "preTick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lyesman/epicfight/api/event/CancelableEventHook;postWithListener(Lyesman/epicfight/api/event/Event;Lyesman/epicfight/api/event/EntityEventListener;)Lyesman/epicfight/api/event/Event;",
                    remap = false),
            require = 0,
            remap = false)
    @Coerce
    private Object blacklight$gateEpicFightModeEvent(
            @Coerce Object hook, @Coerce Object event, @Coerce Object listener) {
        return EpicFightBlacklightBridge.postEpicFightModeEvent(hook, event, listener);
    }
}
