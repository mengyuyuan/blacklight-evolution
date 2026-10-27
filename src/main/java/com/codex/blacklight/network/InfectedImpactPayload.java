package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client only. A cosmetic receipt of accepted damage, never a hit request. */
public record InfectedImpactPayload(int attacker,int victim,int style,double x,double y,double z,float dx,float dy,float dz,float size,long seed) implements CustomPacketPayload {
    public static final Type<InfectedImpactPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","infected_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InfectedImpactPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.attacker);b.writeVarInt(p.victim);b.writeVarInt(p.style);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.dx);b.writeFloat(p.dy);b.writeFloat(p.dz);b.writeFloat(p.size);b.writeLong(p.seed);},
        b->new InfectedImpactPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readLong()));
    public InfectedImpactPayload {
        style=Math.clamp(style,0,5);size=Float.isFinite(size)?Math.clamp(size,.18F,2.4F):.18F;
        if(!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(dz)){dx=0;dy=1;dz=0;}
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
