package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;

/** Immutable, server-owned, saved and copied through death. Potion grants are separate from infection. */
public record AdaptivePassiveState(int permanentMask, int adaptation, int regeneration, long nextHealAt) {
    public static final int ADAPTATION = 1, REGENERATION = 2;
    public static final AdaptivePassiveState EMPTY = new AdaptivePassiveState(0, 0, 0, 0);
    public static final Codec<AdaptivePassiveState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("permanent", 0).forGetter(AdaptivePassiveState::permanentMask),
            Codec.INT.optionalFieldOf("adaptation", 0).forGetter(AdaptivePassiveState::adaptation),
            Codec.INT.optionalFieldOf("regeneration", 0).forGetter(AdaptivePassiveState::regeneration),
            Codec.LONG.optionalFieldOf("next_heal", 0L).forGetter(AdaptivePassiveState::nextHealAt)
    ).apply(i, AdaptivePassiveState::new));
    public static final StreamCodec<FriendlyByteBuf, AdaptivePassiveState> STREAM_CODEC = new StreamCodec<>() {
        public AdaptivePassiveState decode(FriendlyByteBuf b) {
            return new AdaptivePassiveState(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong());
        }
        public void encode(FriendlyByteBuf b, AdaptivePassiveState s) {
            b.writeVarInt(s.permanentMask); b.writeVarInt(s.adaptation); b.writeVarInt(s.regeneration); b.writeVarLong(s.nextHealAt);
        }
    };
    public AdaptivePassiveState {
        permanentMask &= ADAPTATION | REGENERATION;
        adaptation = Math.max(0, adaptation); regeneration = Math.max(0, regeneration); nextHealAt = Math.max(0, nextHealAt);
    }
    public boolean permanent(int flag) { return (permanentMask & flag) != 0; }
    public AdaptivePassiveState grant(int flag) {
        return new AdaptivePassiveState(permanentMask | flag, adaptation, regeneration, nextHealAt);
    }
}
