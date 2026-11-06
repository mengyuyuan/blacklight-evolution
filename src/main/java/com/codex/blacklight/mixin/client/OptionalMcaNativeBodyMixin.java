package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.NativePlayerRenderScope;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA's alternate body must not suppress the already-selected Blacklight body.
 * The UUID check preserves MCA's other players and nested render requests. */
@Pseudo
@Mixin(targets={"net.conczin.mca.MCAClient","net.mca.MCAClient","forge.net.conczin.mca.MCAClient"},remap=false)
public abstract class OptionalMcaNativeBodyMixin {
    @Inject(method={"useVillagerRenderer(Ljava/util/UUID;)Z","useGeneticsRenderer(Ljava/util/UUID;)Z"},
            at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void blacklight$ownNativeBody(UUID id,CallbackInfoReturnable<Boolean> callback) {
        Object owner=NativePlayerRenderScope.current();
        if(owner instanceof Player player&&player.getUUID().equals(id))callback.setReturnValue(false);
    }
}
