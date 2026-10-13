package com.codex.blacklight.mixin;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(Level.class)
public abstract class BulletdiveBlockFeedbackMixin {
    @Redirect(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
    private void blacklight$budgetBreakFeedback(Level level,int event,BlockPos pos,int data) {
        if(com.codex.blacklight.combat.BulletdiveBreakEffects.allowFeedback(level,event))level.levelEvent(event,pos,data);
    }
}
