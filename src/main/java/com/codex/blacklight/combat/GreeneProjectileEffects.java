package com.codex.blacklight.combat;

import com.codex.blacklight.entity.GreeneProjectile;
import com.codex.blacklight.registry.ModInfectedSounds;
import com.codex.blacklight.infection.InfectedSoundCue;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** MC particle adapter for the verified MotherProjectile/missileTrail/electric/
 * concrete effect families. Particle meshes, counts and sizes are authored,
 * not exported P1 emitters; these visual events never apply area damage. */
public final class GreeneProjectileEffects {
    private static final DustParticleOptions BIOMASS=new DustParticleOptions(new Vector3f(.08F,.8F,.12F),.7F);
    public static void tick(GreeneProjectile projectile){
        if(!(projectile.level() instanceof ServerLevel level)||projectile.finished())return;
        var profile=GreeneProjectileAppearance.of(projectile.ammo());var from=projectile.position();
        if(profile==GreeneProjectileAppearance.TORSO){
            level.sendParticles(BIOMASS,from.x,from.y,from.z,2,.07,.14,.07,0);
        }else{
            // Source missileTrail004 explicitly overrides emission per metre
            // to .6. Stochastic rounding avoids a low-speed every-tick floor.
            double emission=projectile.getDeltaMovement().length()*.6;
            int count=(int)Math.floor(emission);if(level.random.nextDouble()<emission-count)count++;
            count=Math.min(5,count);
            if(count>0)level.sendParticles(ParticleTypes.SMOKE,from.x,from.y,from.z,count,.08,.08,.08,.005);
        }
    }
    public static void impact(GreeneProjectile projectile,Vec3 point){
        if(!(projectile.level() instanceof ServerLevel level))return;
        var profile=GreeneProjectileAppearance.of(projectile.ammo());
        if(profile==GreeneProjectileAppearance.TORSO){
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,point.x,point.y,point.z,12,.18,.18,.18,.15);
            // Authored Foley role adapter for mother_projectile_hit/blood_gush;
            // source waves are private, and their two-layer mix is not claimed.
            ModInfectedSounds.play(projectile,InfectedSoundCue.BURST,point,1,1,1);
        }else{
            // Source rock_explode FIG pitch range, existing authored contact
            // bank and spatial sound budget; not the original sample.
            ModInfectedSounds.play(projectile,InfectedSoundCue.CRUSH,point,1,1.1F,1.9F);
            double spread=.15*4*profile.scale();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.GRAVEL.defaultBlockState()),point.x,point.y,point.z,20,spread,spread*.5,spread,.16);
        }
    }
    private GreeneProjectileEffects(){}
}
