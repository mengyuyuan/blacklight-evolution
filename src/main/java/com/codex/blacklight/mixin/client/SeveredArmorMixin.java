package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.renderer.DismemberVisuals;
import com.codex.blacklight.client.renderer.NativeRenderScope;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla armor visibility is reset per slot; apply the sever mask afterwards. */
@Mixin(HumanoidArmorLayer.class)
public abstract class SeveredArmorMixin {
    @Inject(method="setPartVisibility",at=@At("TAIL"))
    private void blacklight$severedArmor(HumanoidModel<?> model,EquipmentSlot slot,CallbackInfo ci){
        if(NativeRenderScope.current() instanceof LivingEntity e)DismemberVisuals.hideParts(model,e);
    }
}
