package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.world.phys.Vec3;

public record ConsumeContactSoundPayload(int victim,int owner,long started,int ordinal,String kind,Vec3 point,float gain,float pitch) implements CustomPacketPayload {
    /** A negative ordinal denotes a lifecycle cue sent directly to the performer. */
    public static final int PHASE = -1;
    public static final int LANDING = -2;
    public ConsumeContactSoundPayload(int victim,int owner,long started,int ordinal,String kind,Vec3 point) {
        this(victim,owner,started,ordinal,kind,point,1F,1F);
    }
    public static final Type<ConsumeContactSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID, "consume_contact_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConsumeContactSoundPayload> STREAM_CODEC =
            StreamCodec.of(ConsumeContactSoundPayload::encode, ConsumeContactSoundPayload::decode);


    private static void encode(RegistryFriendlyByteBuf b,ConsumeContactSoundPayload p){
        b.writeVarInt(p.victim());b.writeVarInt(p.owner());b.writeVarLong(p.started());b.writeVarInt(p.ordinal());
        b.writeUtf(p.kind(),16);b.writeDouble(p.point().x);b.writeDouble(p.point().y);b.writeDouble(p.point().z);
        if(p.ordinal()==PHASE){b.writeFloat(p.gain());b.writeFloat(p.pitch());}
    }
    private static ConsumeContactSoundPayload decode(RegistryFriendlyByteBuf b){
        int victim=b.readVarInt(), owner=b.readVarInt();
        long started=b.readVarLong();
        int ordinal=b.readVarInt();
        String kind=b.readUtf(16);
        Vec3 point=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        return ordinal==PHASE ? new ConsumeContactSoundPayload(victim,owner,started,ordinal,kind,point,b.readFloat(),b.readFloat())
                : new ConsumeContactSoundPayload(victim,owner,started,ordinal,kind,point);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
