package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Small server-to-client impulse used for camera and FOV feedback. */
public record PhysicalFeedbackPayload(Effect effect, float intensity) implements CustomPacketPayload {
    public static final Type<PhysicalFeedbackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID, "physical_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicalFeedbackPayload> STREAM_CODEC =
            StreamCodec.of(PhysicalFeedbackPayload::encode, PhysicalFeedbackPayload::decode);

    public PhysicalFeedbackPayload {
        if (!Float.isFinite(intensity)) intensity = 0.0F;
        intensity = Math.max(0.0F, Math.min(1.5F, intensity));
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PhysicalFeedbackPayload payload) {
        buffer.writeVarInt(payload.effect.ordinal());
        buffer.writeFloat(payload.intensity);
    }

    private static PhysicalFeedbackPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PhysicalFeedbackPayload(Effect.byId(buffer.readVarInt()), buffer.readFloat());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Effect {
        JUMP_LAUNCH,
        AIR_DASH,
        LIGHT_LANDING,
        HEAVY_LANDING,
        DEVASTATING_LANDING,
        CLAW_HIT,
        CLAW_HEAVY,
        GROUND_SPIKE,
        SPRINT_COLLISION,
        SPRINT_START,
        WALL_RUN_START,
        WALL_MANTLE,
        BLADE_CUT,
        BLADE_RETURN,
        BLADE_HEAVY,
        BOSS_ARMOR, BOSS_HIT, BOSS_WEAK, BOSS_BREAK, BOSS_STRIKE;

        private static Effect byId(int id) {
            Effect[] values = values();
            return id >= 0 && id < values.length ? values[id] : LIGHT_LANDING;
        }
    }
}
