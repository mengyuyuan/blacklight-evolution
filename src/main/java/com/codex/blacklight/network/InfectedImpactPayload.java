package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.codex.blacklight.combat.FleshStrike;
import com.codex.blacklight.infection.InfectedAttackStyle;

/** Server-to-client only. A cosmetic receipt of accepted damage, never a hit request. */
public record InfectedImpactPayload(int attacker,int victim,int appearance,double x,double y,double z,float dx,float dy,float dz,float size,long seed) implements CustomPacketPayload {
    public static final Type<InfectedImpactPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","infected_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,InfectedImpactPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{b.writeVarInt(p.attacker);b.writeVarInt(p.victim);b.writeVarInt(p.appearance);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeFloat(p.dx);b.writeFloat(p.dy);b.writeFloat(p.dz);b.writeFloat(p.size);b.writeLong(p.seed);},
        b->new InfectedImpactPayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readLong()));
    public InfectedImpactPayload {
        appearance=normalizeAppearance(appearance);size=Float.isFinite(size)?Math.clamp(size,.18F,2.4F):.18F;
        if(!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(dz)){dx=0;dy=1;dz=0;}
    }
    // The original six style values remain valid. The existing VarInt also carries
    // the player strike in bits 3..5 and confirmed full charge in bit 6.
    public static int appearance(InfectedAttackStyle style, FleshStrike strike, boolean charged) {
        return style.ordinal() | (strike.id << 3) | (charged ? 64 : 0);
    }
    private static int normalizeAppearance(int value) {
        if (value < 0) return 0;
        int style = Math.min(value & 7, 5);
        if ((value & ~127) != 0 || FleshStrike.byId((value >> 3) & 7) == FleshStrike.GENERIC) return style;
        return (value & ~7) | style;
    }
    public int style() { return appearance & 7; }
    public FleshStrike strike() { return FleshStrike.byId((appearance >> 3) & 7); }
    public boolean charged() { return (appearance & 64) != 0; }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
