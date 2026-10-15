package com.codex.passives;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;

public final class HealthGrowth {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("adaptive_passives", "kill_health");
    private HealthGrowth() {}
    public static void apply(ServerPlayer p, double bonus) {
        var a = p.getAttribute(Attributes.MAX_HEALTH); if (a == null) return;
        double baseFactor = 1, totalFactor = 1;
        for (var modifier : a.getModifiers()) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) baseFactor += modifier.amount();
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) totalFactor *= 1 + modifier.amount();
        }
        // Flat health-point growth, not multiplied fivefold by Blacklight's unit modifier.
        double factor = baseFactor * totalFactor;
        double amount = factor > 0 && Double.isFinite(factor) ? bonus / factor : 0;
        var old = a.getModifier(ID);
        if (old != null && Math.abs(old.amount() - amount) <= Math.max(1, Math.abs(amount)) * 1e-9) return;
        if (old != null) a.removeModifier(ID);
        if (amount > 0 && Double.isFinite(amount)) a.addTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_VALUE));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }
}
