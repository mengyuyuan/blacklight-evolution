package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.ByteBufCodecs;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Persistent parent ID and logical region are validated together; no client-only limb ID.
 * Region -1 selects an ordinary actor or an exposed boss core. Entity -1 releases. */
public record TargetLockPayload(int entityId,int requestId,int region) implements CustomPacketPayload {
    public TargetLockPayload(int entityId,int requestId){this(entityId,requestId,-1);}
    public static final Type<TargetLockPayload> TYPE=new Type<>(new ResourceLocation("blacklight","target_lock"));
    public static final StreamCodec<FriendlyByteBuf,TargetLockPayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,TargetLockPayload::entityId,ByteBufCodecs.VAR_INT,TargetLockPayload::requestId,ByteBufCodecs.VAR_INT,TargetLockPayload::region,TargetLockPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
