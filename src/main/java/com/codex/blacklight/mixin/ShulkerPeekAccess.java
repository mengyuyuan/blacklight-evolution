package com.codex.blacklight.mixin;
import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Native lid interpolation remains responsible for the shell; no per-frame NBT reload. */
@Mixin(Shulker.class)
public interface ShulkerPeekAccess {
    @Invoker("setRawPeekAmount") void blacklight$peek(int amount);
}
