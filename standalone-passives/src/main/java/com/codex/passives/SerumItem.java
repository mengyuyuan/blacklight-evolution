package com.codex.passives;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class SerumItem extends Item {
    private final int flag, tier;
    public SerumItem(int flag, int tier) { super(new Properties().stacksTo(1).rarity(tier == 2 ? Rarity.EPIC : Rarity.RARE)); this.flag = flag; this.tier = tier; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        var d = PassiveData.read(p); var stack = p.getItemInHand(hand);
        boolean owned = flag == 1 ? d.adaptationTier >= tier : flag == 2 ? d.regenerationTier >= tier : d.vitality;
        if (owned) {
            if (!level.isClientSide) p.displayClientMessage(Component.translatable("message.adaptive_passives.owned"), true);
            return InteractionResultHolder.fail(stack);
        }
        p.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack) { return 32; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer p && PassiveApi.grant(p, flag, tier)) {
            p.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
            if (!p.getAbilities().instabuild) {
                stack.shrink(1); if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
                var bottle = new ItemStack(Items.GLASS_BOTTLE); if (!p.getInventory().add(bottle)) p.drop(bottle, false);
            }
        }
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag tooltip) {
        text.add(Component.translatable("tooltip.adaptive_passives.permanent"));
        text.add(Component.translatable("tooltip.adaptive_passives." + (flag == 1 ? "adaptation_" + tier : flag == 2 ? "regeneration_" + tier : "vitality")));
    }
}
