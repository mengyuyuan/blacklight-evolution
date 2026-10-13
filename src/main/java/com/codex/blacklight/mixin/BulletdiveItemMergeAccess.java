package com.codex.blacklight.mixin;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ItemEntity.class)
public interface BulletdiveItemMergeAccess {
    @Invoker("isMergable") boolean blacklight$mergeable();
    @Invoker("tryToMerge") void blacklight$merge(ItemEntity other);
}
