package com.codex.blacklight.firearms;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GunplaySounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"blacklight");
    private static final Map<String,Supplier<SoundEvent>> EVENTS=new HashMap<>();
    static{for(String cue:List.of("rifle","heavy","launcher","cannon","reload_out","reload_insert","reload_bolt","impact")){
        String id="firearm."+cue;EVENTS.put(cue,SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("blacklight",id))));}}
    public static void register(IEventBus bus){SOUNDS.register(bus);}
    private static SoundSource source(LivingEntity actor){return actor instanceof Player?SoundSource.PLAYERS:SoundSource.HOSTILE;}
    public static void fire(ServerLevel level,LivingEntity actor,Vec3 at,int weapon){
        String cue=GunplayEffects.cannon(weapon)?"cannon":weapon==2||weapon==12?"heavy":GunplayEffects.bullet(weapon)?"rifle":"launcher";
        level.playSound(null,at.x,at.y,at.z,EVENTS.get(cue).get(),source(actor),cue.equals("cannon")?3.6F:1.5F,.96F+level.random.nextFloat()*.08F);
    }
    public static void reload(LivingEntity actor,int stage){if(actor.level() instanceof ServerLevel level){
        String cue=stage==0?"reload_out":stage==1?"reload_insert":"reload_bolt";
        level.playSound(null,actor.getX(),actor.getY()+1,actor.getZ(),EVENTS.get(cue).get(),source(actor),.65F,1F);}}
    public static void impact(ServerLevel level,Vec3 at,float radius){level.playSound(null,at.x,at.y,at.z,EVENTS.get("impact").get(),SoundSource.HOSTILE,Math.min(3,.9F+radius*.35F),.92F+level.random.nextFloat()*.12F);}
    private GunplaySounds(){}
}
