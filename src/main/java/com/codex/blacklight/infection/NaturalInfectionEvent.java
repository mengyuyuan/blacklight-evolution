package com.codex.blacklight.infection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

public final class NaturalInfectionEvent extends net.neoforged.neoforge.event.entity.living.LivingEvent implements net.neoforged.bus.api.ICancellableEvent {
    private final Entity source;
    public NaturalInfectionEvent(LivingEntity target,Entity source){super(target);this.source=source;}
    public Entity source(){return source;}
}
