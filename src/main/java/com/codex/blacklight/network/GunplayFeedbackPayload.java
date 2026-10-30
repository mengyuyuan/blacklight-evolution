package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cosmetic server receipt only. It carries no client hit, damage, inventory or firing request. */
public record GunplayFeedbackPayload(int actor,int weapon,int event,int impact,double x,double y,double z,
        double endX,double endY,double endZ,float nx,float ny,float nz,float size,long seed) implements CustomPacketPayload {
    public static final Type<GunplayFeedbackPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","gunplay_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf,GunplayFeedbackPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.actor);b.writeVarInt(p.weapon);b.writeByte(p.event);b.writeByte(p.impact);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeDouble(p.endX);b.writeDouble(p.endY);b.writeDouble(p.endZ);b.writeFloat(p.nx);b.writeFloat(p.ny);b.writeFloat(p.nz);b.writeFloat(p.size);b.writeLong(p.seed);},
        b->new GunplayFeedbackPayload(b.readVarInt(),b.readVarInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readLong()));
    public boolean valid(){return weapon>=0&&weapon<13&&event>=0&&event<=2&&impact>=0&&impact<=3
            &&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Double.isFinite(endX)&&Double.isFinite(endY)&&Double.isFinite(endZ)
            &&Math.abs(x)<=30000100&&Math.abs(z)<=30000100&&Math.abs(y)<4096&&Math.abs(endY)<4096
            &&Math.abs(endX-x)<=512&&Math.abs(endY-y)<=512&&Math.abs(endZ-z)<=512
            &&Float.isFinite(nx)&&Float.isFinite(ny)&&Float.isFinite(nz)&&Float.isFinite(size)&&size>=0&&size<=6;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
