package com.codex.blacklight.mixin;
import net.minecraft.world.entity.ItemBasedSteering;
import net.minecraft.world.entity.animal.Pig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Pig.class)
public interface PigSteeringAccess extends com.codex.blacklight.infection.SaddleSteeringAccess {
    @Override @Accessor("steering") ItemBasedSteering blacklight$steering();
}
