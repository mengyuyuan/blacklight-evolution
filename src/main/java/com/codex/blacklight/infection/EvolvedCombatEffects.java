package com.codex.blacklight.infection;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModParticles;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/** Bounded server broadcasts: impacts only occur after accepted damage. */
final class EvolvedCombatEffects {
    private EvolvedCombatEffects(){}
    static void sweep(BlacklightInfectedHost host){
        host.level().playSound(null,host.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.75F,.65F);
    }
    static void impact(BlacklightInfectedHost host,LivingEntity target){
        if(!(host.level() instanceof ServerLevel level))return;
        var p=target.getBoundingBox().getCenter();
        level.sendParticles(new DustParticleOptions(new Vector3f(.38F,.025F,.04F),1.05F),p.x,p.y,p.z,9,.2,.22,.2,.08);
        level.sendParticles(ModParticles.BIOMASS_SLASH.get(),p.x,p.y,p.z,3,.12,.15,.12,.035);
        level.playSound(null,target.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.HOSTILE,.8F,.65F);
    }
    static void ground(BlacklightInfectedHost host,boolean landing){
        if(!(host.level() instanceof ServerLevel level))return;
        var ground=host.getOnPos();var block=level.getBlockState(ground);if(block.isAir())return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),host.getX(),host.getY()+.06,host.getZ(),landing?18:7,landing?.65:.3,.12,landing?.65:.3,.1);
        if(landing){
            level.sendParticles(ModParticles.IMPACT_SMOKE.get(),host.getX(),host.getY()+.12,host.getZ(),5,.45,.03,.45,.025);
            level.playSound(null,ground,SoundEvents.GENERIC_BIG_FALL,SoundSource.HOSTILE,.8F,.65F);
        }
    }
}
