package com.codex.blacklight.registry;

import com.codex.blacklight.infection.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

/** Server-authoritative spatial sound; detached render proxies never emit this bank. */
public final class ModInfectedSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"blacklight");
    private static final Map<InfectedSoundCue,Supplier<SoundEvent>> EVENTS=new EnumMap<>(InfectedSoundCue.class);
    private static final Map<Entity,EnumMap<InfectedSoundCue.Group,Long>> ACTORS=new WeakHashMap<>();
    private static final Map<ServerLevel,Area> LEVELS=new WeakHashMap<>();
    private static final class Area {long cleanup;final Map<Long,InfectedSoundBudget> cells=new HashMap<>();}
    static {for(var cue:InfectedSoundCue.values()){String id="infection."+cue.id;EVENTS.put(cue,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight",id))));}}
    private static final java.util.List<Supplier<SoundEvent>> MOLT=new java.util.ArrayList<>();
    static {for(String suffix:java.util.List.of("summons","pierce","drain","weave","split","ascend")){
        String id="greene.molt."+suffix;MOLT.add(SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight",id))));
    }}
    public static SoundEvent molt(int cue){return MOLT.get(cue).get();}
    private ModInfectedSounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    /** Vanilla owns accepted hurt/death playback and its immunity/death deduplication. */
    public static SoundEvent event(InfectedSoundCue cue){return EVENTS.get(cue).get();}
    public static void play(Entity actor,InfectedSoundCue cue){play(actor,cue,actor.getBoundingBox().getCenter());}
    public static void play(Entity actor,InfectedSoundCue cue,Vec3 at){
        var sound=prepare(actor,cue,at);if(sound!=null)playPrepared(actor,sound,at);
    }
    public static void play(Entity actor,InfectedSoundCue cue,Vec3 at,float gain,float minPitch,float maxPitch){
        var sound=prepare(actor,cue,at,gain,minPitch,maxPitch);if(sound!=null)playPrepared(actor,sound,at);
    }
    public record Prepared(SoundEvent event,float volume,float pitch){}
    public static void playPrepared(Entity actor,Prepared sound,Vec3 at){
        if(actor.level() instanceof ServerLevel level)level.playSound(null,at.x,at.y,at.z,sound.event,SoundSource.HOSTILE,sound.volume,sound.pitch);
    }
    /** Reserve the original actor/spatial budget now; presentation may wait for its render frame. */
    public static Prepared prepare(Entity actor,InfectedSoundCue cue,Vec3 at){
        return prepare(actor,cue,at,1,.96F,1.04F);
    }
    private static Prepared prepare(Entity actor,InfectedSoundCue cue,Vec3 at,float gain,float minPitch,float maxPitch){
        if(!Float.isFinite(gain)||gain<=0||gain>3||!Float.isFinite(minPitch)||!Float.isFinite(maxPitch)
                ||minPitch<.5F||maxPitch>2||minPitch>maxPitch)return null;
        if(!(actor.level() instanceof ServerLevel level)||actor.isSilent()||!Double.isFinite(at.x+at.y+at.z))return null;
        long now=level.getGameTime();var clocks=ACTORS.computeIfAbsent(actor,k->new EnumMap<>(InfectedSoundCue.Group.class));
        Long last=clocks.get(cue.group);int gap=cue.group==InfectedSoundCue.Group.CONTACT?4:3;
        if(last!=null&&now>=last&&now-last<gap)return null;
        var area=LEVELS.computeIfAbsent(level,k->new Area());
        // Expired areas cannot accumulate forever as players explore cities.
        if(now<area.cleanup||now-area.cleanup>=40){area.cells.clear();area.cleanup=now;}
        int x=(int)Math.floor(at.x/32),z=(int)Math.floor(at.z/32);long key=((long)x<<32)^(z&0xffffffffL);
        if(!area.cells.computeIfAbsent(key,k->new InfectedSoundBudget()).accept(now))return null;
        clocks.put(cue.group,now);
        return new Prepared(EVENTS.get(cue).get(),cue.volume*gain,minPitch+level.random.nextFloat()*(maxPitch-minPitch));
    }
}
