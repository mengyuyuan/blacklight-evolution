package com.codex.blacklight.mixin.client;

import com.codex.blacklight.animation.SceneClock;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A server time sync never rewinds the local player's running consume scene; see {@link SceneClock}. */
@Mixin(ClientLevel.class)
public abstract class SceneClockMixin {
    @Unique private final SceneClock blacklight$sceneClock = new SceneClock();

    @ModifyVariable(method = "setGameTime", at = @At("HEAD"), argsOnly = true)
    private long blacklight$holdScene(long requested) {
        var self = (ClientLevel)(Object)this;
        var player = Minecraft.getInstance().player;
        boolean scene = player != null && player.level() == self
                && ModAttachments.getData(player,ModAttachments.CONSUMING)
                && ModAttachments.getData(player,ModAttachments.GRAB_STAGE) != ConsumeVisual.NONE;
        return blacklight$sceneClock.resolve(self.getGameTime(), requested, scene);
    }
}
