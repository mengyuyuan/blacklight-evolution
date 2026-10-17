package com.codex.blacklight.item;

import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.core.Direction;

public final class HiveTissueItem extends Item {
    public HiveTissueItem(Properties p) { super(p); }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.Item.TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        for(int i=1;i<=3;i++)lines.add(net.minecraft.network.chat.Component.translatable("hydra.blacklight.tooltip."+i).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext c) {
        var level=c.getLevel();var player=c.getPlayer();
        if(level.dimension().equals(com.codex.blacklight.city.CityTravel.CITY)){
            if(player!=null&&!level.isClientSide)player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hydra.blacklight.outside"),true);
            return InteractionResult.FAIL;
        }
        if(player==null||!player.mayBuild()||c.getClickedFace()!=Direction.UP)return InteractionResult.FAIL;
        var pos=c.getClickedPos().above();
        if(!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).isAir()
            ||!level.getBlockState(pos.above()).isAir()
            ||!level.getBlockState(c.getClickedPos()).isFaceSturdy(level,c.getClickedPos(),Direction.UP)
            ||!player.mayUseItemAt(pos,Direction.UP,c.getItemInHand()))return InteractionResult.FAIL;
        if(!level.getEntitiesOfClass(BlacklightHydra.class,new net.minecraft.world.phys.AABB(pos).inflate(8)).isEmpty())return InteractionResult.FAIL;
        if(!level.isClientSide){
            var hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(level);
            if(hydra==null)return InteractionResult.FAIL;
            hydra.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,player.getYRot(),0);
            hydra.plant(player.getUUID());
            if(!level.addFreshEntity(hydra))return InteractionResult.FAIL;
            if(!player.getAbilities().instabuild)c.getItemInHand().shrink(1);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hydra.blacklight.planted"),false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
