package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.network.VehicleDestructionPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/** One bounded receipt at the accepted destruction event; repeated damage cannot replay it. */
public final class VehicleDestructionEffects {
    public static void emit(Entity actor,VehicleDestruction.Chassis chassis,boolean blackwatch,float turret,float pitch,boolean nativeExplosion){
        if(!(actor.level() instanceof ServerLevel level))return;
        var at=actor.position();boolean submerged=!level.getFluidState(actor.blockPosition().above()).isEmpty();
        var packet=new VehicleDestructionPayload(actor.getId(),chassis.ordinal(),blackwatch,at.x,at.y,at.z,actor.getYRot(),turret,pitch,submerged,level.getGameTime(),level.random.nextLong());
        if(!packet.valid())return;PacketDistributor.sendToPlayersNear(level,null,at.x,at.y,at.z,128,packet);
        if(!nativeExplosion)com.codex.blacklight.firearms.GunplaySounds.impact(level,at.add(0,chassis.height*.5,0),chassis.radius);
        level.playSound(null,at.x,at.y+1,at.z,net.minecraft.sounds.SoundEvents.ANVIL_BREAK,net.minecraft.sounds.SoundSource.HOSTILE,1.2F,chassis==VehicleDestruction.Chassis.HELICOPTER?.65F:.85F);
    }
    private VehicleDestructionEffects(){}
}
