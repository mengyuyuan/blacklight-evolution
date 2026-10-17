package com.codex.blacklight.item;

import com.codex.blacklight.entity.BlacklightVirusVialEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BlacklightSampleItem extends Item {
    public BlacklightSampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.55F,
                0.82F + level.getRandom().nextFloat() * 0.12F);
        if (!level.isClientSide()) {
            BlacklightVirusVialEntity vial = new BlacklightVirusVialEntity(level, player);
            vial.setItem(stack);
            vial.shootFromRotation(player, player.getXRot(), player.getYRot(), -7.5F, 1.2F, 0.6F);
            if (!level.addFreshEntity(vial)) return InteractionResultHolder.fail(stack);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 12);
        if(!player.getAbilities().instabuild)stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        BlacklightVirusVialEntity vial = new BlacklightVirusVialEntity(
                level, position.x(), position.y(), position.z());
        vial.setItem(stack);
        return vial;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.blacklight.blacklight_sample.1"));
        tooltip.add(Component.translatable("tooltip.blacklight.blacklight_sample.2"));
    }
}
