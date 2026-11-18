package com.codex.blacklight.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Air control carries intent and a revocable seat token; never coordinates, velocity, damage or ammo. */
public record HelicopterControlPayload(UUID aircraft,UUID lease,long sequence,double forward,double steering,double yaw,double pitch,int actions) implements CustomPacketPayload {
    public static final Type<HelicopterControlPayload> TYPE=new Type<>(new ResourceLocation("blacklight","helicopter_control"));
    public static final StreamCodec<FriendlyByteBuf,HelicopterControlPayload> STREAM_CODEC=StreamCodec.of(
            (b,v)->{b.writeUUID(v.aircraft);b.writeUUID(v.lease);b.writeLong(v.sequence);b.writeDouble(v.forward);b.writeDouble(v.steering);b.writeDouble(v.yaw);b.writeDouble(v.pitch);b.writeVarInt(v.actions);},
            b->new HelicopterControlPayload(b.readUUID(),b.readUUID(),b.readLong(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt()));
    public boolean valid(){return aircraft!=null&&lease!=null&&sequence>=0&&Double.isFinite(forward)&&Math.abs(forward)<=1&&Double.isFinite(steering)&&Math.abs(steering)<=1
            &&Double.isFinite(yaw)&&Math.abs(yaw)<=180&&Double.isFinite(pitch)&&Math.abs(pitch)<=90&&(actions&~119)==0;}
    public boolean handle(ServerPlayer player){
        if(player==null||!valid()||!player.isAlive()||player.isRemoved()||player.isSpectator()
                ||!(player.getVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft)||craft.isRemoved()||craft.level()!=player.level()||!craft.getUUID().equals(aircraft)||!craft.hasPassenger(player))return false;
        return craft.pilot().accept(player,lease,sequence,forward,steering,yaw,pitch,actions);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
