package com.codex.blacklight.mixin.client;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ConsumeModelPartMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at=@At("HEAD"))
    private void blacklight$partStart(PoseStack stack,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.enterPart((ModelPart)(Object)this);
    }
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at=@At("RETURN"))
    private void blacklight$partEnd(PoseStack stack,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.leavePart();
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.clearPart();
    }
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void blacklight$anatomy(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.modelPart((ModelPart)(Object)this,pose);
        if(out instanceof ConsumeAnatomy.Consumer consumer)consumer.anatomy((ModelPart)(Object)this);
        if(com.codex.blacklight.client.renderer.InfectedMorphology.renderWounded((ModelPart)(Object)this,pose,out,light,overlay,color))ci.cancel();
    }
    @Inject(method="compile",at=@At("TAIL"))
    private void blacklight$infectedGrowth(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        com.codex.blacklight.client.renderer.InfectedMorphology.capture((ModelPart)(Object)this,pose,light,overlay,color);
        com.codex.blacklight.client.renderer.AdaptiveRagdoll.clearPart();
    }
}
