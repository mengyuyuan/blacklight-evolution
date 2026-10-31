package com.codex.blacklight.firearms;


import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;


/** The persisted item clock drives staged Foley; no client can finish or refund a reload. */
@EventBusSubscriber(modid="blacklight")
public final class FirearmReloadSystem {
    public static final String START="BlacklightReloadStarted", CUES="BlacklightReloadCues";
    public static long started(ItemStack stack){var tag=stack.hasTag()?stack.getTag().copy():new net.minecraft.nbt.CompoundTag();return tag.contains(START)?tag.getLong(START):-1;}
    public static void begin(ItemStack stack,AmmoState state,long now){
        FirearmItem.store(stack,state);var tag=stack.hasTag()?stack.getTag().copy():new net.minecraft.nbt.CompoundTag();tag.putLong(START,now);tag.putInt(CUES,0);stack.setTag(tag);
    }
    @SubscribeEvent public static void tick(com.codex.blacklight.combat.ForgeEntityTickEvent.Post event){
        if(!(event.getEntity() instanceof LivingEntity actor)||actor.level().isClientSide||!actor.isAlive())return;
        for(var hand:net.minecraft.world.InteractionHand.values()){
            var stack=actor.getItemInHand(hand);if(!(stack.getItem() instanceof FirearmItem gun))continue;
            var tag=stack.hasTag()?stack.getTag().copy():new net.minecraft.nbt.CompoundTag();if(!tag.contains(START))continue;
            long start=tag.getLong(START),now=actor.level().getGameTime(),end=tag.getLong("WeaponReadyAt");
            if(now<start||now>=end||end-start!=gun.definition().reloadTicks())continue;
            double p=(double)(now-start)/(end-start);int mask=tag.getInt(CUES),next=mask;
            double[] cues={.16,.68,.84};
            for(int i=0;i<cues.length;i++)if(p>=cues[i]&&(next&(1<<i))==0){next|=1<<i;if(p-cues[i]<.12)GunplaySounds.reload(actor,i);}
            if(next!=mask){tag.putInt(CUES,next);stack.setTag(tag);}
        }
    }
    private FirearmReloadSystem(){}
}
