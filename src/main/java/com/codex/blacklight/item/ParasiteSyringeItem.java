package com.codex.blacklight.item;

import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.power.ParasiteSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import java.util.List;

/** Supreme Parasite Syringe (feedback #22). Right-click air: inject yourself. Right-click a creature: inject it.
 * Infected player -> abilities suppressed; Blacklight Hunter -> Infected Hunter; hive -> dies and releases the Supreme Hunter. */
public final class ParasiteSyringeItem extends Item {
    public ParasiteSyringeItem(Properties properties){super(properties);}
    public static boolean held(Player p){return p!=null&&(p.getMainHandItem().getItem() instanceof ParasiteSyringeItem||p.getOffhandItem().getItem() instanceof ParasiteSyringeItem);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(level.isClientSide)return InteractionResultHolder.success(stack);
        return ParasiteSystem.inject((ServerPlayer)player,player)?InteractionResultHolder.consume(spend(player,stack)):InteractionResultHolder.fail(stack);
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(player.level().isClientSide)return InteractionResult.SUCCESS;
        if(!(target instanceof Player||target instanceof BlacklightHunter||target instanceof BlacklightHive)){
            player.displayClientMessage(Component.translatable("message.blacklight.syringe_no_effect"),true);return InteractionResult.FAIL;
        }
        if(!ParasiteSystem.inject((ServerPlayer)player,target))return InteractionResult.FAIL;
        spend(player,player.getItemInHand(hand));return InteractionResult.CONSUME;
    }
    public static ItemStack spend(Player player,ItemStack stack){
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(stack.getItem()));
        if(!player.getAbilities().instabuild)stack.shrink(1);
        player.getCooldowns().addCooldown(stack.getItem(),20);
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack,Level context,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.blacklight.supreme_parasite_syringe.1"));
        tooltip.add(Component.translatable("tooltip.blacklight.supreme_parasite_syringe.2"));
        tooltip.add(Component.translatable("tooltip.blacklight.supreme_parasite_syringe.3"));
    }
}
