package com.codex.blacklight.client.qagrip;

import com.codex.blacklight.client.NativeGripProbe;
import com.codex.blacklight.client.renderer.layer.BlacklightClawLayer;
import com.codex.blacklight.client.model.ClawArmModel;
import com.codex.blacklight.client.animation.BiomassSurface;
import com.codex.blacklight.combat.WeaponAttackScales;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BlacklightClawLayer.class,remap=false)
public abstract class NativeGripProbeMixin {
    @Inject(method="renderSkeleton",at=@At("HEAD"))
    private void begin(AbstractClientPlayer p,PoseStack stack,MultiBufferSource buffer,int light,
                       Matrix4f[] poses,float bob,float yaw,float pitch,float partial,CallbackInfo ci){NativeGripProbe.begin(p,partial,stack,poses);}
    @Inject(method="renderSkeleton",at=@At("RETURN"))
    private void end(AbstractClientPlayer p,PoseStack stack,MultiBufferSource buffer,int light,
                     Matrix4f[] poses,float bob,float yaw,float pitch,float partial,CallbackInfo ci){NativeGripProbe.end();}
    @Inject(method="renderMesh",at=@At("HEAD"))
    private static void draw(ClawArmModel model,String joint,PoseStack stack,MultiBufferSource buffer,int light,
                             float progress,float length,Matrix4f wrist,float limit,WeaponAttackScales.Shape hammer,
                             BiomassSurface.Frame surface,float time,CallbackInfo ci){NativeGripProbe.draw(model,joint,stack,progress,wrist,surface);}
}
