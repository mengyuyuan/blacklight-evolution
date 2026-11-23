package com.codex.blacklight.city;
import com.codex.blacklight.world.FieldNotes;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
public final class CityTransitItem extends Item {
    public CityTransitItem(Properties p){super(p);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> tooltip,TooltipFlag flag){
        tooltip.add(net.minecraft.network.chat.Component.translatable("city.blacklight.destinations_hint")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){
        if(!p.hasPermissions(2)){
            if(!l.isClientSide)FieldNotes.record(p, net.minecraft.network.chat.Component.translatable("city.blacklight.hydra_required"));
            return InteractionResultHolder.fail(p.getItemInHand(hand));
        }
        if(p instanceof ServerPlayer sp)CityTravel.travel(sp);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);
    }
}
