package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.world.FieldNotes;
import com.codex.blacklight.registry.ModEntities;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** MC logistics entry: a player-owned airframe, with its entire real landing envelope checked. */
public final class HelicopterChassisItem extends Item {
    public HelicopterChassisItem(Properties properties){super(properties);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        tooltip.add(Component.translatable("tooltip.blacklight.blackwatch_helicopter_chassis"));
    }
    @Override public InteractionResult useOn(UseOnContext context){
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()
                ||!player.mayBuild()||!context.getLevel().mayInteract(player,context.getClickedPos()))return InteractionResult.FAIL;
        var clicked=context.getClickedPos();boolean replaceable=context.getLevel().getBlockState(clicked).canBeReplaced();
        var cell=replaceable?clicked:clicked.relative(context.getClickedFace());
        double y=!replaceable&&context.getClickedFace()==net.minecraft.core.Direction.UP?context.getClickLocation().y:cell.getY();
        float yaw=net.minecraft.util.Mth.wrapDegrees(-player.getYRot());
        // The tail extends 6.4 blocks behind its origin. Using a clicked center
        // would overlap the player at ordinary survival reach and reject every
        // forward placement. The clicked cell is instead the rear pad anchor.
        Vec3 at=new Vec3(cell.getX()+.5,y,cell.getZ()+.5).add(HelicopterArmament.rotate(new Vec3(0,0,6.5),yaw,0));
        var craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(context.getLevel());if(craft==null)return InteractionResult.FAIL;
        craft.setYRot(yaw);craft.setPos(at);
        if(at.y<context.getLevel().getMinBuildHeight()||at.y+5.8>context.getLevel().getMaxBuildHeight()
                ||!craft.safeLanding(at)||context.getLevel().containsAnyLiquid(craft.getBoundingBox())){
            craft.discard();FieldNotes.record(player, Component.translatable("message.blacklight.helicopter_place_clear_pad"));return InteractionResult.FAIL;
        }
        craft.pilot().captured(player);craft.setFlightPhase(com.codex.blacklight.entity.BlackwatchHelicopter.Phase.IDLE);
        if(!context.getLevel().addFreshEntity(craft)){craft.discard();return InteractionResult.FAIL;}
        context.getItemInHand().shrink(1);return InteractionResult.CONSUME;
    }
}
