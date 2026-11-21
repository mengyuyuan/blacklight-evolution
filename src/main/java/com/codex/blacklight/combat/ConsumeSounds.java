package com.codex.blacklight.combat;

import com.codex.blacklight.network.ConsumeContactSoundPayload;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Observers hear the event at the receiver. The performer's cinematic camera has its own perspective. */
public final class ConsumeSounds {
    private ConsumeSounds() {}

    public static void phase(ServerPlayer performer,String cue,Vec3 point,float gain,float pitch) {
        var event=ModWeaponSounds.event("consume."+cue);
        if(event==null)return;
        performer.level().playSound(performer,point.x,point.y,point.z,event,SoundSource.PLAYERS,gain,pitch);
        if(performer.connection!=null)
            PacketDistributor.sendToPlayer(performer,new ConsumeContactSoundPayload(-1,performer.getId(),
                    performer.level().getGameTime(),ConsumeContactSoundPayload.PHASE,cue,point,gain,pitch));
    }
}
