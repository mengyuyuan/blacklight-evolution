package com.codex.blacklight.mixin.client;

import com.codex.blacklight.client.vehicle.VehicleSeatPose;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapply only to mounted Players after the chosen Humanoid skin renderer's animation. */
@Mixin(LivingEntityRenderer.class)
public abstract class VehiclePlayerPoseMixin {
    @Shadow protected EntityModel<?> model;
    @WrapMethod(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void blacklight$restoreSeat(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,Operation<Void> original) {
        if(entity instanceof Player&&entity.getVehicle() instanceof MilitaryVehicle&&model instanceof HumanoidModel<?> humanoid) {
            try(var saved=VehicleSeatPose.save(humanoid)){original.call(entity,yaw,partial,stack,buffers,light);}
        }else original.call(entity,yaw,partial,stack,buffers,light);
    }
    @Inject(method="render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V",shift=At.Shift.AFTER))
    private void blacklight$seat(LivingEntity entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light,CallbackInfo ci) {
        if(!(entity instanceof Player)||!(entity.getVehicle() instanceof MilitaryVehicle vehicle)||!(model instanceof HumanoidModel<?> humanoid))return;
        String seat=vehicle.seatOf(entity.getUUID());
        boolean driver=vehicle.definition().seats().stream().anyMatch(s->s.id().equals(seat)&&s.job()==VehicleDefinition.Job.DRIVER);
        VehicleSeatPose.apply(humanoid,driver,net.minecraft.util.Mth.wrapDegrees(entity.getViewYRot(partial)+vehicle.getYRot()),entity.getViewXRot(partial),VehicleSeatPose.drop(vehicle.definition()));
        VehicleSeatPose.capture((Player)entity,humanoid);
    }
}
