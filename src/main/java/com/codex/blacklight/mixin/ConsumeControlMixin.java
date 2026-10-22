package com.codex.blacklight.mixin;

import com.codex.blacklight.combat.ConsumeControl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ConsumeControlMixin {
    @Inject(method="startRiding(Lnet/minecraft/world/entity/Entity;Z)Z",at=@At("HEAD"),cancellable=true)
    private void blacklight$preventForcedGrab(Entity vehicle,boolean force,CallbackInfoReturnable<Boolean> cir){
        if(ConsumeControl.protectedEntity((Entity)(Object)this))cir.setReturnValue(false);
    }
    @Inject(method="push(DDD)V",at=@At("HEAD"),cancellable=true)
    private void blacklight$preventImpulse(double x,double y,double z,CallbackInfo ci){
        if(ConsumeControl.protectedEntity((Entity)(Object)this))ci.cancel();
    }
    @Inject(method="setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true)
    private void blacklight$sceneMotion(Vec3 motion,CallbackInfo ci){
        Entity self=(Entity)(Object)this;
        if(motion.lengthSqr()>0 && !ConsumeControl.permitsMotion(self) && ConsumeControl.ownsMotion(self))ci.cancel();
    }
    @Inject(method="setDeltaMovement(DDD)V",at=@At("HEAD"),cancellable=true)
    private void blacklight$sceneMotionComponents(double x,double y,double z,CallbackInfo ci){
        Entity self=(Entity)(Object)this;
        if((x!=0||y!=0||z!=0) && !ConsumeControl.permitsMotion(self) && ConsumeControl.ownsMotion(self))ci.cancel();
    }
}
