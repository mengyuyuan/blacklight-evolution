package com.codex.blacklight.forge;

import net.minecraft.world.item.*;

/** 1.20.1 native food containers predate the consumable remainder component. */
public final class ForgeFoodRemainder {
    private ForgeFoodRemainder() {}
    public static ItemStack of(ItemStack stack) {
        var custom=stack.getCraftingRemainingItem();
        if(!custom.isEmpty())return custom;
        var item=stack.getItem();
        if(item instanceof BowlFoodItem||item instanceof SuspiciousStewItem)return new ItemStack(Items.BOWL);
        if(item instanceof HoneyBottleItem)return new ItemStack(Items.GLASS_BOTTLE);
        return ItemStack.EMPTY;
    }
}
