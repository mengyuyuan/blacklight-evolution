package com.codex.blacklight.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 1.20.1 returns the pending writer's mutable NBT to a load caller. A caller
 * upgrading that tree can race region serialization. Newer Minecraft copies it.
 * Detach only this borrowed read, on the original IO queue before completion. */
@Mixin(IOWorker.class)
public abstract class PendingChunkReadMixin {
    @Shadow @Final private Map<ChunkPos,?> pendingWrites;

    @WrapOperation(method="loadAsync",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/chunk/storage/IOWorker;submitTask(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Optional<CompoundTag>> blacklight$ownPendingRead(IOWorker worker,
            Supplier<Either<Optional<CompoundTag>,Exception>> task,
            Operation<CompletableFuture<Optional<CompoundTag>>> original,
            @Local(argsOnly=true) ChunkPos position){
        Supplier<Either<Optional<CompoundTag>,Exception>> snapshot=()->{
            boolean borrowed=pendingWrites.containsKey(position);
            var result=task.get();
            return borrowed?result.mapLeft(value->value.map(CompoundTag::copy)):result;
        };
        return original.call(worker,snapshot);
    }
}
