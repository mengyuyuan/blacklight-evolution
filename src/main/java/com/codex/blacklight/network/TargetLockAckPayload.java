package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.ByteBufCodecs;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Every explicit lock request receives its own accepted parent ID, including rejection and clear. */
public record TargetLockAckPayload(int requestId,int acceptedEntityId,int acceptedRegion) implements CustomPacketPayload {
    public TargetLockAckPayload(int requestId,int acceptedEntityId){this(requestId,acceptedEntityId,-1);}
    public static final Type<TargetLockAckPayload> TYPE=new Type<>(new ResourceLocation("blacklight","target_lock_ack"));
    public static final StreamCodec<FriendlyByteBuf,TargetLockAckPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,TargetLockAckPayload::requestId,ByteBufCodecs.VAR_INT,TargetLockAckPayload::acceptedEntityId,ByteBufCodecs.VAR_INT,TargetLockAckPayload::acceptedRegion,TargetLockAckPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
