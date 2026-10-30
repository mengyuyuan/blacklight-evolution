package com.codex.blacklight.firearms;

import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

/** Vanilla use fires; the reload key requests a validated server operation. */
public final class FirearmItem extends Item {
    private final WeaponDefinition definition;
    public FirearmItem(Properties properties,WeaponDefinition definition){super(properties);this.definition=definition;}
    public WeaponDefinition definition(){return definition;}
    public static AmmoState state(ItemStack stack,int reserve){
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        int capacity=stack.getItem() instanceof FirearmItem gun?gun.definition.capacity():0;
        return new AmmoState(Math.min(capacity,Math.max(0,tag.getInt("Magazine"))),reserve,tag.getLong("WeaponReadyAt"));
    }
    public static void store(ItemStack stack,AmmoState state){
        CompoundTag tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        tag.putInt("Magazine",state.magazine());tag.putLong("WeaponReadyAt",state.readyAt());
        tag.remove(FirearmReloadSystem.START);tag.remove(FirearmReloadSystem.CUES);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!player.isAlive()||player.isSpectator()||com.codex.blacklight.combat.ConsumeControl.ownsMotion(player))return InteractionResultHolder.fail(stack);
        if(player.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(stack);
        long now=level.getGameTime();
        AmmoState before=state(stack,0);
        if(before.magazine()==0){emptyHint(player);return InteractionResultHolder.fail(stack);}
        if(!before.canFire(now)){player.displayClientMessage(Component.translatable("message.blacklight.firearm_loading",new ItemStack(FirearmsRegistry.ammo(definition.ammoId())).getHoverName()),true);return InteractionResultHolder.fail(stack);}
        store(stack,before.fire(definition,now));
        FirearmsCombat.fire(player,definition,player.getLookAngle(),1);
        player.getCooldowns().addCooldown(this,definition.cooldownTicks());
        AmmoState current=state(stack,0);
        player.displayClientMessage(Component.translatable("message.blacklight.firearm_ammo",current.magazine(),definition.capacity()),true);
        return InteractionResultHolder.success(stack);
    }
    public int reserveAmmo(Player player){
        Item ammo=FirearmsRegistry.ammo(definition.ammoId());long count=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++){var item=player.getInventory().getItem(i);if(item.is(ammo))count+=item.getCount();}
        return (int)Math.min(Integer.MAX_VALUE,count);
    }
    private void emptyHint(Player player){player.displayClientMessage(Component.translatable("message.blacklight.firearm_empty",
            new ItemStack(FirearmsRegistry.ammo(definition.ammoId())).getHoverName(),reserveAmmo(player),Component.keybind("key.blacklight.firearm_reload")),true);}
    /** Validated held-item operation. A reload request never supplies ammunition counts or deadlines. */
    public boolean reload(Player player,InteractionHand hand){
        if(player==null||player.level().isClientSide||!player.isAlive()||player.isRemoved()||player.isSpectator()
                ||player.getItemInHand(hand).getItem()!=this||com.codex.blacklight.combat.ConsumeControl.ownsMotion(player))return false;
        var stack=player.getItemInHand(hand);long now=player.level().getGameTime();
        var before=state(stack,reserveAmmo(player));
        if(player.getCooldowns().isOnCooldown(this)||now<before.readyAt())return false;
        if(before.magazine()>=definition.capacity()){player.displayClientMessage(Component.translatable("message.blacklight.firearm_full"),true);return false;}
        if(before.reserve()==0){emptyHint(player);return false;}
        var after=before.reload(definition,now);int consumed=before.reserve()-after.reserve();if(consumed<=0)return false;
        Item ammo=FirearmsRegistry.ammo(definition.ammoId());int remaining=consumed;
        for(int i=0;i<player.getInventory().getContainerSize()&&remaining>0;i++){var entry=player.getInventory().getItem(i);if(entry.is(ammo)){int take=Math.min(remaining,entry.getCount());entry.shrink(take);remaining-=take;}}
        FirearmReloadSystem.begin(stack,after,now);player.getCooldowns().addCooldown(this,definition.reloadTicks());
        player.displayClientMessage(Component.translatable("message.blacklight.firearm_loading",new ItemStack(ammo).getHoverName()),true);return true;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,java.util.List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.blacklight.firearm_controls",Component.keybind("key.blacklight.firearm_reload")));
        tooltip.add(Component.translatable("message.blacklight.firearm_ammo",state(stack,0).magazine(),definition.capacity()));
        tooltip.add(Component.translatable("tooltip.blacklight.firearm_ammo",Component.translatable("item.blacklight."+definition.ammoId()),definition.reloadTicks()/20F));
    }
}
