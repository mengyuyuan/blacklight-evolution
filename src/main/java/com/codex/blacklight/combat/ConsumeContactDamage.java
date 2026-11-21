package com.codex.blacklight.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** A committed execution may injure its receiver at each contact. Only settlement delivers the final blow. */
public final class ConsumeContactDamage {
    public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("blacklight", "consume_contact"));
    private record Strike(Mob victim, DamageSource source, float minimumHealth) {}
    private static final ThreadLocal<Strike> CURRENT = new ThreadLocal<>();
    private int next;
    private float startingHealth;

    public static boolean owns(LivingEntity target, DamageSource source) {
        var current = CURRENT.get();
        return current != null && current.victim == target && current.source == source;
    }

    public static float healthFloor(LivingEntity target, float health) {
        var current = CURRENT.get();
        if (current == null || current.victim != target) return health;
        return Math.max(Math.min(current.minimumHealth, target.getHealth()), health);
    }

    /** Cap the actual event as well as the final health write when combat modifiers amplify a hit. */
    public static float limitDamage(LivingEntity target, DamageSource source, float damage) {
        var current = CURRENT.get();
        if (current == null || current.victim != target || current.source != source) return damage;
        return Math.min(damage, Math.max(0, target.getHealth() - current.minimumHealth));
    }

    int strike(ServerPlayer owner, Mob victim, ConsumeReceiverScene.Profile scene, int singleContact, int elapsed, ConsumeVisual visual) {
        boolean hunter = visual.stage() == ConsumeVisual.HUNTER_CONSUME;
        int count = hunter ? 2 : scene == null ? 1 : scene.hitSeconds().size();
        if (next >= count) return -1;
        int tick = scene == null ? singleContact : Math.round(scene.hitSeconds().get(next) * 20);
        if (hunter) tick = Math.round(20 * (next == 0 ? HunterConsume.EFFECT_BEGIN_SECONDS : HunterConsume.EFFECT_REVERSE_SECONDS));
        if (elapsed < tick) return -1;
        int ordinal = next++;
        float health = victim.getHealth();
        if (ordinal == 0) startingHealth = health;
        var wound = ConsumeWound.forCue(ConsumeImpactKind.forContact(visual, ordinal));
        float budget = wound.damage(startingHealth, health);
        apply(owner,victim,health,budget);
        return ordinal;
    }

    /** The embedded organ wounds a still-living, eligible host when it first opens for extraction. */
    public static void quillPuncture(ServerPlayer owner, Mob victim) {
        float health=victim.getHealth();
        apply(owner,victim,health,ConsumeWound.QUILL_PUNCTURE.damage(health,health));
    }

    private static void apply(ServerPlayer owner,Mob victim,float health,float budget) {
        float amount = budget / com.codex.blacklight.power.CombatScale.units(victim);
        if (amount <= 0 || !victim.isAlive()) return;
        var type = owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE);
        var source = new DamageSource(type, owner);
        var previous = CURRENT.get();
        CURRENT.set(new Strike(victim, source, health - budget));
        try {
            if (victim instanceof EnderDragon dragon) dragon.hurt(dragon.head, source, amount);
            else victim.hurt(source, amount);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
