package com.codex.blacklight.item;

import com.codex.blacklight.power.AdaptivePassiveSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A permanent drinkable passive, independent of infection and ordinary milk-removable effect duration. */
public final class PassiveSerumItem extends Item {
    private final int flag;
    public PassiveSerumItem(Properties properties, int flag) { super(properties); this.flag = flag; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        var stack = p.getItemInHand(hand);
        if (!com.codex.blacklight.power.IndependentPassives.installed()) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.blacklight.passive_moved"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (com.codex.blacklight.power.IndependentPassives.owns(p, flag)) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.blacklight.passive_already_owned"), true);
            return InteractionResultHolder.fail(stack);
        }
        p.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p && AdaptivePassiveSystem.grant(p, flag)) {
            p.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
            if (!p.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
                var bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!p.getInventory().add(bottle)) p.drop(bottle, false);
            }
        }
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack, Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blacklight.passive_serum"));
    }
}
