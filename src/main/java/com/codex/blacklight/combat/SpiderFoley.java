package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.registry.ModInfectedSounds;
import net.minecraft.core.particles.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Transients originate at the joint, prey or surface that actually made contact. */
public final class SpiderFoley {
    public enum Cue { PLANT, LOAD, STRIKE, SCRAPE, CATCH, INHALE, STRAIN, GULP, TEAR, FRACTURE, NEEDLE, RUPTURE, BRACE, SHED }
    private SpiderFoley() {}

    public static void play(Entity actor, Vec3 at, Cue cue, float volume) {
        if (!(actor.level() instanceof ServerLevel level) || actor.isSilent()) return;
        float pitch=.93F+level.random.nextFloat()*.12F;
        level.playSound(null,at.x,at.y,at.z,ModInfectedSounds.spider(cue),SoundSource.HOSTILE,volume,pitch);
    }

    public static void step(EncounterMob boss,Vec3 at) { ground(boss,at,.65F,Cue.PLANT); }

    public static void ground(EncounterMob boss,Vec3 at,float force,Cue cue) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        var hit=level.clip(new ClipContext(at.add(0,.7,0),at.add(0,-1.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if (hit.getType()!=HitResult.Type.BLOCK) return;
        Vec3 p=hit.getLocation();
        var state=level.getBlockState(hit.getBlockPos());
        var surface=state.getSoundType(level,hit.getBlockPos(),boss);
        play(boss,p,cue,force);
        if (!boss.isSilent()) level.playSound(null,p.x,p.y,p.z,surface.getStepSound(),SoundSource.HOSTILE,force*.76F,surface.getPitch()*.73F);
        var fragments=new BlockParticleOption(ParticleTypes.BLOCK,state);
        level.sendParticles(fragments,p.x,p.y+.07,p.z,Math.round(force*7),force*.28,.09,force*.28,.07*force);
        if(force>=1.6F)TerrainImpactEffects.emit(boss,hit,Math.min(4,force*1.25F),false);
    }

    public static void strands(Entity boss,Vec3 at,Vec3 pull,int count) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        Vec3 axis=pull.normalize();
        for(int i=0;i<count;i++){
            double speed=.045+.14*level.random.nextDouble();
            level.sendParticles(ParticleTypes.CRIMSON_SPORE,at.x+(level.random.nextDouble()-.5)*.38,
                    at.y+(level.random.nextDouble()-.5)*.3,at.z+(level.random.nextDouble()-.5)*.38,
                    0,axis.x*speed,axis.y*speed+.035,axis.z*speed,1);
        }
    }
}
