package com.codex.blacklight.mixin;

import com.codex.blacklight.city.CityTravel;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A completed unload callback can outlive the holder's reclamation by generation. */
@Mixin(ChunkMap.class)
public abstract class CityChunkUnloadMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Inject(method="scheduleUnload",at=@At("HEAD"),cancellable=true)
    private void blacklight$discardReclaimedUnload(long position,ChunkHolder holder,CallbackInfo ci){
        // Vanilla checks generation readiness before checking pendingUnloads.
        // A reclaimed holder can have a completed save future and a new live
        // generation reference, rescheduling the obsolete callback forever.
        // Only retire obsolete callbacks in our dimensions. A holder still in
        // pendingUnloads retains the complete vanilla generation/save path.
        if(CityTravel.isCity(level.dimension())&&pendingUnloads.get(position)!=holder)ci.cancel();
    }
}
