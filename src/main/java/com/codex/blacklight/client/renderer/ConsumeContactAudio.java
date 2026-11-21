package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.network.ConsumeContactSoundPayload;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Use the rendered contact position when available; remote and off-camera impacts use the server position. */
public final class ConsumeContactAudio {
    private record Cue(String kind,Vec3 point){}
    private static final ConsumeContactMix<Cue> mix=new ConsumeContactMix<>();
    private static ClientLevel world;
    private ConsumeContactAudio(){}

    private static ClientLevel level(){
        ClientLevel next=Minecraft.getInstance().level;
        if(next!=world){world=next;mix.clear();}
        return next;
    }
    public static void confirmed(LivingEntity victim,ConsumeVisual scene,String kind,int ordinal,Vec3 point){
        var current=level();
        if(current==null||victim.level()!=current||Minecraft.getInstance().isPaused()||!scene.consuming()||ordinal<0)return;
        var key=new ConsumeContactMix.Strike(victim.getId(),scene.consumerId(),scene.started(),ordinal);
        if(ModWeaponSounds.event("consume."+kind)!=null)
            mix.local(key,new Cue(kind,point),current.getGameTime(),cue->play(current,cue));
    }
    public static void receive(ConsumeContactSoundPayload data){
        var current=level();if(current==null)return;
        var key=new ConsumeContactMix.Strike(data.victim(),data.owner(),data.started(),data.ordinal());
        mix.server(key,new Cue(data.kind(),data.point()),current.getGameTime());
    }
    private static void play(ClientLevel level,Cue cue){
        var sound=ModWeaponSounds.event("consume."+cue.kind());
        if(sound==null)return;
        var point=cue.point();
        level.playLocalSound(point.x,point.y,point.z,sound,SoundSource.PLAYERS,.99F,1F,false);
    }
    public static void tick(){
        var current=level();if(current==null||Minecraft.getInstance().isPaused())return;
        mix.tick(current.getGameTime(),cue->play(current,cue));
    }
}
