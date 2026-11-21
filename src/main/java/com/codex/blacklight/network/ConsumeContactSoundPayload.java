package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.phys.Vec3;

public record ConsumeContactSoundPayload(int victim,int owner,long started,int ordinal,String kind,Vec3 point) implements CustomPacketPayload {
    public static final Type<ConsumeContactSoundPayload> TYPE = new Type<>(
            new ResourceLocation(BlacklightNeoMod.MOD_ID, "consume_contact_sound"));
    public static final StreamCodec<FriendlyByteBuf, ConsumeContactSoundPayload> STREAM_CODEC =
            StreamCodec.of(ConsumeContactSoundPayload::encode, ConsumeContactSoundPayload::decode);


    private static void encode(FriendlyByteBuf b,ConsumeContactSoundPayload p){
        b.writeVarInt(p.victim());b.writeVarInt(p.owner());b.writeVarLong(p.started());b.writeVarInt(p.ordinal());
        b.writeUtf(p.kind(),16);b.writeDouble(p.point().x);b.writeDouble(p.point().y);b.writeDouble(p.point().z);
    }
    private static ConsumeContactSoundPayload decode(FriendlyByteBuf b){
        return new ConsumeContactSoundPayload(b.readVarInt(),b.readVarInt(),b.readVarLong(),b.readVarInt(),
                b.readUtf(16),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()));
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
