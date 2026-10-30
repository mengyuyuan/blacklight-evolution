package com.codex.blacklight.mixin;

import net.minecraft.world.entity.ItemBasedSteering;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** These vanilla saddles are synchronized flags, rather than inventory slots. */
@Mixin({Pig.class,Strider.class})
public interface SaddleSteeringAccess {
    @Accessor("steering") ItemBasedSteering blacklight$steering();
}
