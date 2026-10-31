package com.codex.blacklight.combat;

import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Temporary control ownership, never damage immunity or a saved invulnerability flag. */
public final class ConsumeControl {
    public static final TagKey<MobEffect> CONTROL_EFFECTS = TagKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("blacklight", "consume_control"));
    private static final ThreadLocal<Set<Entity>> MOTION = new ThreadLocal<>();
    private static boolean biomassPrey(Entity entity) {
        return entity instanceof net.minecraft.world.entity.Mob mob
                && mob.hasEffect(com.codex.blacklight.registry.ModEffects.BIOMASS_CONTACT)
                && BiomassSystem.claimant(entity)!=null;
    }

    public static boolean protectedEntity(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) return false;
        var visual = ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        return ConsumeSystem.grappleProtected(living) || visual.active() && visual.stage()!=ConsumeVisual.FLIGHT
                || BiomassSystem.state(entity).immobilized() || biomassPrey(entity);
    }

    /** Holding remains walkable; confirmed scenes own their own root motion. */
    public static boolean ownsMotion(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) return false;
        var visual = ModAttachments.peekData(entity, ModAttachments.CONSUME_VISUAL, ConsumeVisual.EMPTY);
        return com.codex.blacklight.infection.TissueCompetition.controls(entity)||ConsumeSystem.committed(living) || visual.active() && visual.stage()!=ConsumeVisual.FLIGHT
                || BiomassSystem.state(entity).immobilized() || biomassPrey(entity);
    }

    public static boolean permitsMotion(Entity entity) {
        var allowed=MOTION.get();return allowed!=null && allowed.contains(entity);
    }

    public static Scope motion(Entity... entities) {
        var previous=MOTION.get();
        Set<Entity> allowed=Collections.newSetFromMap(new IdentityHashMap<>());
        if(previous!=null)allowed.addAll(previous);
        for(var entity:entities)if(entity!=null)allowed.add(entity);
        MOTION.set(allowed);
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Set<Entity> previous;
        private Scope(Set<Entity> previous){this.previous=previous;}
        @Override public void close(){if(previous==null)MOTION.remove();else MOTION.set(previous);}
    }

    public static boolean controlEffect(MobEffectInstance effect){return effect.getEffect().is(CONTROL_EFFECTS);}
    public static void releaseControls(LivingEntity entity) {
        for(var effect:java.util.List.copyOf(entity.getActiveEffects()))
            if(controlEffect(effect))entity.removeEffect(effect.getEffect());
        if (entity instanceof com.codex.blacklight.entity.MilitarySoldier soldier && !soldier.tacticsAvailable())
            soldier.suspendTactics();
    }
    private ConsumeControl(){}
}
