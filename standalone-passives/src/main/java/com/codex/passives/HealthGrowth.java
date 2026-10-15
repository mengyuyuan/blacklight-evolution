package com.codex.passives;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;

public final class HealthGrowth {
    private static final ResourceLocation ID = new ResourceLocation("adaptive_passives", "kill_health");
    private static final java.util.UUID UUID = java.util.UUID.nameUUIDFromBytes(ID.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private HealthGrowth() {}
    public static void apply(ServerPlayer p, double bonus) {
        var a = p.getAttribute(Attributes.MAX_HEALTH); if (a == null) return;
        double baseFactor = 1, totalFactor = 1;
        for (var modifier : a.getModifiers()) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) baseFactor += modifier.getAmount();
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) totalFactor *= 1 + modifier.getAmount();
        }
        // Flat health-point growth, not multiplied fivefold by Blacklight's unit modifier.
        double factor = baseFactor * totalFactor;
        double amount = factor > 0 && Double.isFinite(factor) ? bonus / factor : 0;
        var old = a.getModifier(UUID);
        if (old != null && Math.abs(old.getAmount() - amount) <= Math.max(1, Math.abs(amount)) * 1e-9) return;
        if (old != null) a.removeModifier(UUID);
        if (amount > 0 && Double.isFinite(amount)) a.addTransientModifier(new AttributeModifier(UUID, ID.toString(), amount, AttributeModifier.Operation.ADDITION));
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }
}
