package com.codex.blacklight.network;

import com.codex.blacklight.military.vehicle.VehicleDestruction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-only cosmetic destruction receipt; no authority, inventory, damage or salvage is created by this packet. */
public record VehicleDestructionPayload(int actor,int chassis,boolean blackwatch,double x,double y,double z,float yaw,float turret,float pitch,boolean submerged,long tick,long seed) implements CustomPacketPayload {
    public static final Type<VehicleDestructionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","vehicle_destruction"));
    public static final StreamCodec<RegistryFriendlyByteBuf,VehicleDestructionPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.actor);b.writeByte(p.chassis);b.writeBoolean(p.blackwatch);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.yaw);b.writeFloat(p.turret);b.writeFloat(p.pitch);b.writeBoolean(p.submerged);b.writeLong(p.tick);b.writeLong(p.seed);},
        b->new VehicleDestructionPayload(b.readVarInt(),b.readUnsignedByte(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readLong(),b.readLong()));
    public boolean valid(){return actor>=0&&chassis>=0&&chassis<VehicleDestruction.Chassis.values().length&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<=30000100&&Math.abs(z)<=30000100&&Math.abs(y)<4096&&Float.isFinite(yaw)&&Float.isFinite(turret)&&Float.isFinite(pitch)&&Math.abs(yaw)<=360000&&Math.abs(turret)<=360000&&Math.abs(pitch)<=360000&&tick>=0;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
