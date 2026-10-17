package com.codex.blacklight.mixin;
import com.codex.blacklight.city.HydraTransport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Player.class,priority=1100)
public abstract class HydraTransportMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void blacklight$pairedHydraMotion(Vec3 input,CallbackInfo ci){
        var p=(Player)(Object)this;
        if(HydraTransport.active(p)){HydraTransport.place(p);ci.cancel();}
    }
}
