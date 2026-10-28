package com.codex.blacklight.registry;

import com.codex.blacklight.infection.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

/** Server-authoritative spatial sound; detached render proxies never emit this bank. */
public final class ModInfectedSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"blacklight");
    private static final Map<InfectedSoundCue,Supplier<SoundEvent>> EVENTS=new EnumMap<>(InfectedSoundCue.class);
    private static final Map<Entity,EnumMap<InfectedSoundCue.Group,Long>> ACTORS=new WeakHashMap<>();
    private static final Map<ServerLevel,Area> LEVELS=new WeakHashMap<>();
    private static final class Area {long cleanup;final Map<Long,InfectedSoundBudget> cells=new HashMap<>();}
    static {for(var cue:InfectedSoundCue.values()){String id="infection."+cue.id;EVENTS.put(cue,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("blacklight",id))));}}
    private ModInfectedSounds(){}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    public static void play(Entity actor,InfectedSoundCue cue){play(actor,cue,actor.getBoundingBox().getCenter());}
    public static void play(Entity actor,InfectedSoundCue cue,Vec3 at){
        if(!(actor.level() instanceof ServerLevel level)||actor.isSilent()||!Double.isFinite(at.x+at.y+at.z))return;
        long now=level.getGameTime();var clocks=ACTORS.computeIfAbsent(actor,k->new EnumMap<>(InfectedSoundCue.Group.class));
        Long last=clocks.get(cue.group);int gap=cue.group==InfectedSoundCue.Group.CONTACT?4:3;
        if(last!=null&&now>=last&&now-last<gap)return;
        var area=LEVELS.computeIfAbsent(level,k->new Area());
        // Expired areas cannot accumulate forever as players explore cities.
        if(now<area.cleanup||now-area.cleanup>=40){area.cells.clear();area.cleanup=now;}
        int x=(int)Math.floor(at.x/32),z=(int)Math.floor(at.z/32);long key=((long)x<<32)^(z&0xffffffffL);
        if(!area.cells.computeIfAbsent(key,k->new InfectedSoundBudget()).accept(now))return;
        clocks.put(cue.group,now);
        level.playSound(null,at.x,at.y,at.z,EVENTS.get(cue).get(),SoundSource.HOSTILE,cue.volume,.96F+level.random.nextFloat()*.08F);
    }
}
