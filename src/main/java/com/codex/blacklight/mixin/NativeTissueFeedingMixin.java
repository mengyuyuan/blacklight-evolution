package com.codex.blacklight.mixin;

import com.codex.blacklight.infection.NativeTissueFeeding;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original method, item use and loader cancellation run unchanged exactly once.
 * @Share keeps the receipt local to the invocation, including nested interactions. */
@Mixin(Player.class)
public abstract class NativeTissueFeedingMixin {
    @WrapOperation(method="interactOn",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult blacklight$observeNativeInteraction(Entity target,Player player,InteractionHand hand,Operation<InteractionResult> original,
            @Share("nativeMeal") LocalRef<NativeTissueFeeding.Receipt> receipt){
        var value=NativeTissueFeeding.begin(player,target,hand);receipt.set(value);
        var result=original.call(target,player,hand);if(value!=null)value.source(result);return result;
    }
    @WrapOperation(method="interactOn",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;interactLivingEntity(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult blacklight$observeNativeItem(ItemStack stack,Player player,LivingEntity target,InteractionHand hand,Operation<InteractionResult> original,
            @Share("nativeMeal") LocalRef<NativeTissueFeeding.Receipt> receipt){
        var result=original.call(stack,player,target,hand);if(receipt.get()!=null)receipt.get().item(result);return result;
    }
    @Inject(method="interactOn",at=@At("RETURN"),cancellable=true)
    private void blacklight$settleNativeFood(Entity target,InteractionHand hand,CallbackInfoReturnable<InteractionResult> cir,
            @Share("nativeMeal") LocalRef<NativeTissueFeeding.Receipt> receipt){
        var before=cir.getReturnValue();var after=NativeTissueFeeding.finish(receipt.get(),(Player)(Object)this,target,hand,before);
        if(after!=before)cir.setReturnValue(after);
    }
}
