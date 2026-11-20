package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHive;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.entity.BlacklightSupremeHunter;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.network.PhysicalFeedbackPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.WeakHashMap;
import java.util.UUID;

/** Contact accents are emitted after damage acceptance. They never stop a boss's movement clock. */
public final class BossImpactFeedback {
    public enum Contact { ARMOR, FLESH, WEAK, BREAK }
    private record Last(UUID boss, long tick, Contact kind) {}
    private static final WeakHashMap<ServerPlayer, Last> recent = new WeakHashMap<>();

    private BossImpactFeedback() {}

    private static boolean boss(LivingEntity entity) {
        return ConsumeSequence.boss(entity) || entity instanceof BlacklightHive || entity instanceof BlacklightHydra;
    }

    public static void accepted(LivingEntity victim, DamageSource source, float damage) {
        if (!(damage > 0) || victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && boss(attacker)) {
            float strength = Math.min(1.2F, .4F + 2F * damage / Math.max(1F, player.getMaxHealth()));
            PacketDistributor.sendToPlayer(player, new PhysicalFeedbackPayload(PhysicalFeedbackPayload.Effect.BOSS_STRIKE, strength));
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer attacker) || !boss(victim)) return;
        // Goliath and Greene's mesh contacts are confirmed by their organ damage paths.
        if (victim instanceof EncounterMob mob && (mob.kind() == EncounterKind.GOLIATH
                || mob.kind() == EncounterKind.ELIZABETH_GREENE && mob.incomingContact() != null)) return;
        var skin = AnimatedHurtBody.of(victim);
        var hit = skin == null ? null : skin.incomingContact();
        var direct = source.getDirectEntity();
        var origin = direct == null || direct == attacker ? attacker.getEyePosition() : direct.position();
        var point = hit == null ? CombatTargeting.closestPoint(origin, victim) : hit.point();
        Contact kind = Contact.FLESH;
        if (victim instanceof EncounterMob mob && mob.kind() == EncounterKind.ELIZABETH_GREENE)
            kind = mob.exposed() ? Contact.WEAK : Contact.ARMOR;
        else if (victim instanceof BlacklightHive && !HiveDefense.fullDamage(source)
                || victim instanceof BlacklightSupremeHunter hunter && hunter.deflects(source))
            kind = Contact.ARMOR;
        contact(victim, source, point, kind, damage);
    }

    public static void contact(LivingEntity boss, DamageSource source, Vec3 point, Contact kind, float amount) {
        if (!(source.getEntity() instanceof ServerPlayer player) || boss.level().isClientSide || !(amount > 0)) return;
        long tick = boss.level().getGameTime();
        var last = recent.get(player);
        if (last != null && last.boss().equals(boss.getUUID()) && tick >= last.tick() && tick - last.tick() < 3
                && (last.kind() == kind || last.kind() == Contact.BREAK)) return;
        recent.put(player, new Last(boss.getUUID(), tick, kind));
        var effect = switch (kind) {
            case ARMOR -> PhysicalFeedbackPayload.Effect.BOSS_ARMOR;
            case FLESH -> PhysicalFeedbackPayload.Effect.BOSS_HIT;
            case WEAK -> PhysicalFeedbackPayload.Effect.BOSS_WEAK;
            case BREAK -> PhysicalFeedbackPayload.Effect.BOSS_BREAK;
        };
        double distance = player.getEyePosition().distanceTo(point);
        float force = kind == Contact.BREAK ? 1.25F : Math.min(1F, .55F + (float)Math.sqrt(amount) * .055F);
        float camera = (float)(force / (1 + Math.max(0, distance - 5) / 14));
        PacketDistributor.sendToPlayer(player, new PhysicalFeedbackPayload(effect, camera));
        ContactEffects.boss(boss, point, kind, force);
    }
}
