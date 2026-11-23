package com.codex.blacklight.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.Attribute;

/** A small wearable in a vanilla armor slot. The player layer supplies its living markings. */
public final class SymbioteCharm extends Item implements Equipable {
    private final EquipmentSlot slot;
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public SymbioteCharm(EquipmentSlot slot) {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
        this.slot = slot;
        var id = UUID.nameUUIDFromBytes(("blacklight:symbiote_" + slot.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        modifiers = ImmutableMultimap.of(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(id, "Blacklight symbiote", 25, AttributeModifier.Operation.ADDITION));
    }

    @Override public EquipmentSlot getEquipmentSlot() { return slot; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot equipped) {
        return equipped == slot ? modifiers : super.getDefaultAttributeModifiers(equipped);
    }

    @Override public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this);
        lines.add(net.minecraft.network.chat.Component.translatable("symbiote.blacklight." + id.getPath()));
        lines.add(net.minecraft.network.chat.Component.translatable("symbiote.blacklight.link").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    private static final int DAMAGE_PER_PIECE = 25;

    /** Authored attacks use ordinary health units. The stat page uses the fivefold Blacklight scale. */
    public static float attackBase(Player player, float rawDamage) {
        int pieces = 0;
        for (ItemStack item : player.getArmorSlots()) {
            if (item.getItem() instanceof SymbioteCharm charm && player.getItemBySlot(charm.slot) == item) pieces++;
        }
        return rawDamage + pieces * DAMAGE_PER_PIECE / com.codex.blacklight.power.CombatScale.UNITS;
    }

    public static boolean wearing(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).getItem() instanceof SymbioteCharm charm && charm.slot == slot;
    }
}
