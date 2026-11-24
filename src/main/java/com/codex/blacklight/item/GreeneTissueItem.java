package com.codex.blacklight.item;

import com.codex.blacklight.world.FieldNotes;
import com.codex.blacklight.city.CityTravel;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.world.GreeneHiveCave;
import com.codex.blacklight.world.SurvivalGuide;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class GreeneTissueItem extends Item {
    public static final int LISTEN_TICKS = 32;

    public GreeneTissueItem(Properties properties) { super(properties); }

    public static boolean isTissue(ItemStack stack) { return stack.getItem() instanceof GreeneTissueItem; }
    public static boolean held(Player player) { return player != null && player.isHolding(GreeneTissueItem::isTissue); }

    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                                        int slot, boolean selected) {
        if (!(entity instanceof ServerPlayer player) || player.tickCount % 20 != 0 || player.isSpectator()
                || !(selected || player.getOffhandItem() == stack) || level.dimension() != CityTravel.WORLD
                || !SurvivalGuide.followingGreene(player)) return;
        // The city itself uses a custom flat-derived generator. Check the save's overworld preset.
        if (player.serverLevel().getServer().overworld().getChunkSource().getGenerator().getClass()
                == net.minecraft.world.level.levelgen.FlatLevelSource.class) return;
        var site = GreeneHiveCave.nearest(player.serverLevel().getSeed(), player.blockPosition());
        com.codex.blacklight.forge.PacketDistributor.sendToPlayer(player, new com.codex.blacklight.network.GreeneSignalPayload(site));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ModAttachments.getData(player, ModAttachments.CONSUMING)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack) { return LISTEN_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BLOCK; }

    // The valves spread before the first echo and fold back around the core after the second.
    public static int opening(int age) {
        if (age < 0 || age >= LISTEN_TICKS) return 0;
        if (age >= 12 && age < 26) return 2;
        return age >= 5 && age < 30 ? 1 : 0;
    }

    @Override public void onUseTick(Level level, LivingEntity holder, ItemStack stack, int remaining) {
        if (!(holder instanceof ServerPlayer player)) return;
        int age = LISTEN_TICKS - remaining;
        if (age != 12 && age != 21) return;
        var world = player.serverLevel();
        world.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, .6F, age == 12 ? 1.04F : .82F);
        if (world.dimension() != CityTravel.WORLD || !SurvivalGuide.followingGreene(player)) {
            if (age == 12) FieldNotes.record(player, Component.translatable(world.dimension() != CityTravel.WORLD
                    ? "item.blacklight.greene_tissue.distant" : "item.blacklight.greene_tissue.quiet"));
            return;
        }
        var den = GreeneHiveCave.nearest(world.getSeed(), player.blockPosition());
        boolean right = (player.getUsedItemHand() == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        var side = Vec3.directionFromRotation(0, player.getYRot() + (right ? 90 : -90));
        var palm = player.getEyePosition().add(player.getLookAngle().scale(.65)).add(side.scale(.25)).add(0, -.23, 0);
        var heading = GreeneHiveCave.guidance(den, palm);
        for (int i = 1; i <= 16; i++) {
            Vec3 point = palm.add(heading.scale(i * .35));
            world.sendParticles(player, ParticleTypes.CRIMSON_SPORE, true, point.x, point.y, point.z, 2, .035, .035, .035, 0);
        }

    }

    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity holder) {
        if (holder instanceof ServerPlayer player) SurvivalGuide.revisitGreene(player);
        if (holder instanceof Player player) player.getCooldowns().addCooldown(this, 12);
        return stack;
    }

    @Override public void appendHoverText(ItemStack stack, Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.blacklight.greene_tissue.hint", Component.keybind("key.use")).withStyle(ChatFormatting.GRAY));
    }
}
