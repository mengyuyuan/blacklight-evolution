package com.codex.blacklight.infection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
@net.minecraftforge.eventbus.api.Cancelable
public final class NaturalInfectionEvent extends net.minecraftforge.event.entity.living.LivingEvent {
    private final Entity source;
    public NaturalInfectionEvent(LivingEntity target,Entity source){super(target);this.source=source;}
    public Entity source(){return source;}
}
