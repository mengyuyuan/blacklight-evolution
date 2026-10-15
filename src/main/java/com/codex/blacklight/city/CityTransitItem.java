package com.codex.blacklight.city;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
public final class CityTransitItem extends Item {
    public CityTransitItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){
        if(p instanceof ServerPlayer sp)CityTravel.travel(sp);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);
    }
}
