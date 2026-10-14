package com.codex.blacklight.item;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
public final class FieldScannerItem extends Item {
    public FieldScannerItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        if(!level.isClientSide){
            var nearest=level.getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(48),e->e!=player&&e.isAlive()&&player.distanceToSqr(e)<=48*48&&com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(e)).stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr));
            if(nearest.isEmpty())player.displayClientMessage(Component.translatable("message.blacklight.scan_clear"),true);
            else{var target=nearest.get();var delta=target.position().subtract(player.position());String direction=Math.abs(delta.x)>Math.abs(delta.z)?(delta.x>0?"east":"west"):(delta.z>0?"south":"north");player.displayClientMessage(Component.translatable("message.blacklight.scan_found",target.getDisplayName(),Math.round(player.distanceTo(target)),Component.translatable("direction.blacklight."+direction)),true);}
            level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,net.minecraft.sounds.SoundSource.PLAYERS,.6F,.6F);
            player.getCooldowns().addCooldown(this,100);
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}
