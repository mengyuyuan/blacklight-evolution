package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The server resolves the currently selected species and cooldown. */
public record SpeciesAbilityPayload() implements CustomPacketPayload {
    public static final Type<SpeciesAbilityPayload> TYPE=new Type<>(new ResourceLocation("blacklight","species_ability"));
    public static final StreamCodec<FriendlyByteBuf,SpeciesAbilityPayload> STREAM_CODEC=StreamCodec.unit(new SpeciesAbilityPayload());
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
