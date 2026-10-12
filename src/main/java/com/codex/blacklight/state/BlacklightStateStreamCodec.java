package com.codex.blacklight.state;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Kept separate so plain JVM tests for progression state do not need Minecraft networking classes. */
public final class BlacklightStateStreamCodec {
    public static final StreamCodec<RegistryFriendlyByteBuf, BlacklightState> CODEC = new StreamCodec<>() {
        @Override
        public BlacklightState decode(RegistryFriendlyByteBuf buffer) {
            return new BlacklightState(
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readUtf(32),
                    buffer.readUtf(128),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readVarInt()
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BlacklightState state) {
            buffer.writeBoolean(state.isInfected());
            buffer.writeVarInt(state.getBiomass());
            buffer.writeVarInt(state.getEvolutionPoints());
            buffer.writeVarInt(state.getUnlockedMask());
            buffer.writeUtf(state.getActivePower().id(), 32);
            buffer.writeUtf(state.getDisguiseIdentity(), 128);
            buffer.writeBoolean(state.isDisguised());
            buffer.writeVarInt(state.getAlert());
            buffer.writeVarInt(state.getClawUpgradeMask());
            buffer.writeVarInt(state.getMovementUpgradeMask());
            buffer.writeBoolean(state.isPrototypeMode());
            buffer.writeVarInt(state.getCombatSkillMask());
        }
    };

    private BlacklightStateStreamCodec() {
    }
}
