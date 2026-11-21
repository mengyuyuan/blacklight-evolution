package com.codex.blacklight.combat;

import com.codex.blacklight.network.ConsumeContactSoundPayload;
import com.codex.blacklight.registry.ModWeaponSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Observers hear the event at the receiver. The performer's cinematic camera has its own perspective. */
public final class ConsumeSounds {
    private ConsumeSounds() {}

    public static void phase(ServerPlayer performer,String cue,Vec3 point,float gain,float pitch) {
        var event=ModWeaponSounds.event("consume."+cue);
        if(event==null)return;
        var phase=new ConsumeContactSoundPayload(-1,performer.getId(),performer.level().getGameTime(),
                ConsumeContactSoundPayload.PHASE,cue,point,gain,pitch);
        double range=event.getRange(gain);
        for(var listener:performer.serverLevel().players()){
            if(listener.connection!=null && (listener==performer || listener.distanceToSqr(point)<range*range))
                PacketDistributor.sendToPlayer(listener,phase);
        }
    }
}
