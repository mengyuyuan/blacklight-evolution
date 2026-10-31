package com.codex.blacklight.firearms;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;



/** All input is vanilla use; damage, inventory and cooldown are authoritative on the server. */
public final class FirearmItem extends Item {
    private final WeaponDefinition definition;
    public FirearmItem(Properties properties,WeaponDefinition definition){super(properties);this.definition=definition;}
    public WeaponDefinition definition(){return definition;}
    public static AmmoState state(ItemStack stack,int reserve){
        CompoundTag tag=stack.getOrCreateTag().copy();
        int capacity=stack.getItem() instanceof FirearmItem gun?gun.definition.capacity():0;
        return new AmmoState(Math.min(capacity,Math.max(0,tag.getInt("Magazine"))),reserve,tag.getLong("WeaponReadyAt"));
    }
    public static void store(ItemStack stack,AmmoState state){
        CompoundTag tag=stack.getOrCreateTag().copy();
        tag.putInt("Magazine",state.magazine());tag.putLong("WeaponReadyAt",state.readyAt());
        stack.setTag(tag);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!player.isAlive()||player.isSpectator()||com.codex.blacklight.combat.ConsumeControl.ownsMotion(player))return InteractionResultHolder.fail(stack);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        long now=level.getGameTime();
        if(player.isShiftKeyDown()){
            Item ammo=FirearmsRegistry.ammo(definition.ammoId());
            int count=0;
            for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack entry=player.getInventory().getItem(i);if(entry.is(ammo))count+=entry.getCount();}
            AmmoState before=state(stack,count),after=before.reload(definition,now);
            int consumed=before.reserve()-after.reserve();
            if(consumed>0){
                int remaining=consumed;
                for(int i=0;i<player.getInventory().getContainerSize()&&remaining>0;i++){ItemStack entry=player.getInventory().getItem(i);if(entry.is(ammo)){int take=Math.min(remaining,entry.getCount());entry.shrink(take);remaining-=take;}}
                store(stack,after);player.getCooldowns().addCooldown(this,definition.reloadTicks());
                level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,net.minecraft.sounds.SoundSource.PLAYERS,.5F,1.5F);
            }
        }else{
            AmmoState before=state(stack,0);
            if(!before.canFire(now)){player.displayClientMessage(Component.translatable("message.blacklight.firearm_empty"),true);return InteractionResultHolder.fail(stack);}
            store(stack,before.fire(definition,now));
            FirearmsCombat.fire(player,definition,player.getLookAngle(),1);
            player.getCooldowns().addCooldown(this,definition.cooldownTicks());
        }
        AmmoState current=state(stack,0);
        player.displayClientMessage(Component.translatable("message.blacklight.firearm_ammo",current.magazine(),definition.capacity()),true);
        return InteractionResultHolder.success(stack);
    }
    @Override public void appendHoverText(ItemStack stack,Level context,java.util.List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.blacklight.firearm_controls"));
        tooltip.add(Component.translatable("message.blacklight.firearm_ammo",state(stack,0).magazine(),definition.capacity()));
        tooltip.add(Component.translatable("tooltip.blacklight.firearm_ammo",Component.translatable("item.blacklight."+definition.ammoId()),definition.reloadTicks()/20F));
    }
}
