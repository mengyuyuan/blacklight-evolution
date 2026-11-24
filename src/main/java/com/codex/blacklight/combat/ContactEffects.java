package com.codex.blacklight.combat;

import com.codex.blacklight.network.*;
import com.codex.blacklight.registry.ModInfectedSounds;
import com.codex.blacklight.infection.InfectedSoundCue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Packages feedback after a server-approved contact. No queued callback can damage anything. */
public final class ContactEffects {
    private static long serial;
    private ContactEffects(){}
    public static void ground(Entity actor,BlockHitResult contact,float radius,boolean heavy,List<TerrainImpactEffects.Surface> samples){
        var at=contact.getLocation();var level=(ServerLevel)actor.level();var state=level.getBlockState(contact.getBlockPos());
        var flash = new CombatImpactPayload(at.x, at.y, at.z, radius, true);
        var surfaces = new ArrayList<ContactEffectsPayload.Surface>();
        for (var sample : samples) {
            var hit = sample.hit();
            var point = hit.getLocation();
            var material = level.getBlockState(hit.getBlockPos());
            var surface = new ContactEffectsPayload.Surface(point.x, point.y, point.z,
                    Block.getId(material), sample.ring(), sample.index(), sample.angle());
            if (surface.fits(flash)) {
                surfaces.add(surface);
            } else {
                // A wide slam can hit another elevation. Emit its debris at the real hit.
                TerrainImpactEffects.emitSurface(level, sample, material, heavy);
            }
        }
        var sounds=new ArrayList<ContactEffectsPayload.Sound>();
        if(!actor.isSilent())sounds.add(new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(state.getSoundType().getBreakSound()),heavy?1.2F:.7F,heavy?.65F:.75F));
        var cue=ModInfectedSounds.prepare(actor,InfectedSoundCue.GROUND,at);if(cue!=null)sounds.add(sound(cue));
        send(actor,new ContactEffectsPayload(level.getGameTime(),++serial,ContactEffectsPayload.GROUND,flash,null,heavy,surfaces,sounds));
    }
    public static void obstacle(Entity actor,Vec3 point,AABB shape){
        var level=(ServerLevel)actor.level();var state=level.getBlockState(net.minecraft.core.BlockPos.containing(shape.getCenter()));
        var outward=actor.position().subtract(point).multiply(1,0,1).normalize();var at=point.add(outward.scale(.035));
        var sounds=new ArrayList<ContactEffectsPayload.Sound>();
        if(!actor.isSilent())sounds.add(new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(state.getSoundType().getBreakSound()),1.1F,.7F));
        var cue=ModInfectedSounds.prepare(actor,InfectedSoundCue.CRUSH,at);if(cue!=null)sounds.add(sound(cue));
        var surface=new ContactEffectsPayload.Surface(at.x,at.y,at.z,Block.getId(state),0,0,Math.atan2(outward.z,outward.x));
        send(actor,new ContactEffectsPayload(level.getGameTime(),++serial,ContactEffectsPayload.OBSTACLE,new CombatImpactPayload(at.x,at.y,at.z,.8F,false),null,false,List.of(surface),sounds));
    }
    public static void metal(Entity actor,InfectedImpactPayload hit,ModInfectedSounds.Prepared cue){
        var flash=new CombatImpactPayload(hit.x(),hit.y(),hit.z(),hit.size(),false,false,hit.dx(),hit.dy(),hit.dz());
        send(actor,new ContactEffectsPayload(actor.level().getGameTime(),++serial,ContactEffectsPayload.GUARD,flash,null,false,List.of(),cue==null?List.of():List.of(sound(cue))));
    }
    public static void body(Entity actor,InfectedImpactPayload tissue,boolean crush,ModInfectedSounds.Prepared cue){
        if(actor.level().getEntity(tissue.victim()) instanceof net.minecraft.world.entity.animal.IronGolem){metal(actor,tissue,cue);return;}
        var flash=new CombatImpactPayload(tissue.x(),tissue.y(),tissue.z(),tissue.size()*.85F,false);
        send(actor,new ContactEffectsPayload(actor.level().getGameTime(),++serial,crush?ContactEffectsPayload.CRUSH:ContactEffectsPayload.TISSUE,flash,tissue,false,List.of(),cue==null?List.of():List.of(sound(cue))));
    }
    public static void boss(Entity actor, Vec3 at, BossImpactFeedback.Contact kind, float force) {
        int material = switch (kind) {
            case ARMOR -> ContactEffectsPayload.BOSS_ARMOR;
            case FLESH -> ContactEffectsPayload.BOSS_FLESH;
            case WEAK -> ContactEffectsPayload.BOSS_WEAK;
            case BREAK -> ContactEffectsPayload.BOSS_BREAK;
        };
        var sound = switch (kind) {
            case ARMOR -> SoundEvents.DEEPSLATE_HIT;
            case FLESH -> SoundEvents.MUD_HIT;
            case WEAK -> SoundEvents.ROOTED_DIRT_BREAK;
            case BREAK -> SoundEvents.BONE_BLOCK_BREAK;
        };
        var sounds = new ArrayList<ContactEffectsPayload.Sound>();
        if (!actor.isSilent()) {
            sounds.add(new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(sound), .85F * force, kind == BossImpactFeedback.Contact.ARMOR ? 1.1F : .72F));
            if (kind == BossImpactFeedback.Contact.BREAK)
                sounds.add(new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.RAVAGER_HURT), 1.1F, .75F));
        }
        send(actor, new ContactEffectsPayload(actor.level().getGameTime(), ++serial, material,
                new CombatImpactPayload(at.x, at.y, at.z, force, false), null,
                kind == BossImpactFeedback.Contact.BREAK, List.of(), sounds));
    }

    public static void guard(Entity actor,Vec3 at){
        var sounds=actor.isSilent()?List.<ContactEffectsPayload.Sound>of():List.of(new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.SHIELD_BLOCK),.9F,.7F));
        send(actor,new ContactEffectsPayload(actor.level().getGameTime(),++serial,ContactEffectsPayload.GUARD,new CombatImpactPayload(at.x,at.y,at.z,.48F,false),null,false,List.of(),sounds));
    }
    public static void audio(Entity actor,Vec3 at,ModInfectedSounds.Prepared cue){
        if(cue!=null)send(actor,new ContactEffectsPayload(actor.level().getGameTime(),++serial,ContactEffectsPayload.SOUND,new CombatImpactPayload(at.x,at.y,at.z,.1F,false),null,false,List.of(),List.of(sound(cue))));
    }
    private static ContactEffectsPayload.Sound sound(ModInfectedSounds.Prepared p){return new ContactEffectsPayload.Sound(BuiltInRegistries.SOUND_EVENT.getKey(p.event()),p.volume(),p.pitch());}
    private static void send(Entity actor,ContactEffectsPayload p){
        if(actor.level() instanceof ServerLevel level)PacketDistributor.sendToPlayersNear(level,null,p.flash().x(),p.flash().y(),p.flash().z(),80,p);
    }
}
