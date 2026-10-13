package com.codex.blacklight.mixin.client;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

/** Dragon renders outside LivingEntityRenderer; apply reactions without altering its death model. */
@Mixin(EnderDragonRenderer.DragonModel.class)
public abstract class DragonConsumePoseMixin {
    @Shadow private EnderDragon entity;
    @Shadow private float a;
    @Shadow @Final private ModelPart head;
    @Shadow @Final private ModelPart rightWing;
    @WrapOperation(method={"renderToBuffer","renderSide"},at=@At(value="INVOKE",target="Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void blacklight$react(ModelPart part,PoseStack stack,VertexConsumer vertices,int light,int overlay,float red,float green,float blue,float alpha,Operation<Void> original) {
        var visual=entity==null?ConsumeVisual.EMPTY:ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL);
        if(!visual.active()){original.call(part,stack,vertices,light,overlay,red,green,blue,alpha);return;}
        var saved=part.storePose();float sx=part.xScale,sy=part.yScale,sz=part.zScale;
        try {
            float t=visual.motionProgress(entity.level().getGameTime(),a);
            var form=PowerForm.values()[net.minecraft.util.Mth.clamp(visual.form(),0,PowerForm.values().length-1)];
            float strike=visual.consuming()?(float)Math.exp(-Math.pow((t-com.codex.blacklight.combat.ConsumeSequence.contact(form,true))*18,2)):0;
            if(part==head){
                part.xRot+=strike*(form==PowerForm.HAMMERFISTS?.8F:form==PowerForm.MUSCLE_MASS?-.55F:.3F);
                part.zRot+=strike*(form==PowerForm.BLADE?.6F:form==PowerForm.CLAWS?-.45F:.12F);
                if(form==PowerForm.WHIPFIST)part.yRot-=strike*.7F;
            }
            if(part==rightWing && (visual.form()==PowerForm.BLADE.ordinal()||visual.form()==PowerForm.CLAWS.ordinal()) && t>.43F) {
                float detach=net.minecraft.util.Mth.clamp((t-.43F)/.18F,0,1),absorb=net.minecraft.util.Mth.clamp((t-.7F)/.28F,0,1);
                part.x+=detach*24*(1-absorb);part.y-=detach*10;
                part.xScale*=1-absorb*.98F;part.yScale*=1-absorb*.98F;part.zScale*=1-absorb*.98F;
            }
            original.call(part,stack,vertices,light,overlay,red,green,blue,alpha);
        } finally {part.loadPose(saved);part.xScale=sx;part.yScale=sy;part.zScale=sz;}
    }
}
