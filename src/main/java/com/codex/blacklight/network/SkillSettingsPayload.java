package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Changes only the sending player's preferences, not their unlocks or power. */
public record SkillSettingsPayload(boolean bulletdiveBlockBreaking) implements CustomPacketPayload {
    public static final Type<SkillSettingsPayload> TYPE=new Type<>(
            ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID,"skill_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillSettingsPayload> STREAM_CODEC=
            StreamCodec.of((buffer,payload)->buffer.writeBoolean(payload.bulletdiveBlockBreaking),
                    buffer->new SkillSettingsPayload(buffer.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
