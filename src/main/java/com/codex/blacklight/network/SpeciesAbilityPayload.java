package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The server resolves the currently selected species and cooldown. */
public record SpeciesAbilityPayload() implements CustomPacketPayload {
    public static final Type<SpeciesAbilityPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","species_ability"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SpeciesAbilityPayload> STREAM_CODEC=StreamCodec.unit(new SpeciesAbilityPayload());
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
