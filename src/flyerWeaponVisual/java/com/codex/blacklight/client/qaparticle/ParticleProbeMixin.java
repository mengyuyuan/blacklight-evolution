package com.codex.blacklight.client.qaparticle;
import com.codex.blacklight.client.ParticleProbe;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(SingleQuadParticle.class)
public abstract class ParticleProbeMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void audit(VertexConsumer out,Camera camera,float partial,CallbackInfo ci){ParticleProbe.draw((Particle)(Object)this,camera);}
}