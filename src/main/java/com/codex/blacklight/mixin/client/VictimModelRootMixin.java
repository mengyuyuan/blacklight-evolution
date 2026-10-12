package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.animation.VictimModelRoot;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({QuadrupedModel.class, ChickenModel.class, HorseModel.class, WolfModel.class,
        OcelotModel.class, FoxModel.class, RabbitModel.class, LlamaModel.class,
        BeeModel.class, AxolotlModel.class, HoglinModel.class, ShulkerModel.class, TadpoleModel.class})
public abstract class VictimModelRootMixin implements VictimModelRoot {
    @Unique private ModelPart blacklight$receiverRoot;

    @Inject(method="<init>", at=@At("TAIL"))
    private void blacklight$captureRoot(CallbackInfo ci, @Local(argsOnly=true) ModelPart root) {
        blacklight$receiverRoot = root;
    }

    @Override public ModelPart blacklight$victimRoot() { return blacklight$receiverRoot; }
}
