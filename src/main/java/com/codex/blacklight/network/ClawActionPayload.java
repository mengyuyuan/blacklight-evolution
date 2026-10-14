package com.codex.blacklight.network;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClawActionPayload(Action action) implements CustomPacketPayload {
    public static final Type<ClawActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BlacklightNeoMod.MOD_ID, "claw_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClawActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Action::byId, Action::id),
            ClawActionPayload::action,
            ClawActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        TOGGLE_CLAWS(0),
        ATTACK(1),
        GROUND_SPIKE(2),
        DASHING_SLICE(3),
        GROUND_SPIKE_CHARGE_START(4),
        GROUND_SPIKE_CHARGE_RELEASE(5),
        SPRINT_ATTACK(6),
        CONSUME(7),
        CYCLE_FORM(8),
        GRAB(9),
        THROW(10),
        WEAPON_SPECIAL(11),
        BLOCK_THROW_START(12),
        BLOCK_THROW_RELEASE(13),
        BLOCK_THROW_CANCEL(14),
        TERRAIN_GRAB_KEEPALIVE(15), TERRAIN_GRAB_RELEASE(16), TERRAIN_GRAB_CANCEL(17);

        private final int id;

        Action(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Action byId(int id) {
            return switch (id) {
                case 0 -> TOGGLE_CLAWS;
                case 1 -> ATTACK;
                case 2 -> GROUND_SPIKE;
                case 3 -> DASHING_SLICE;
                case 4 -> GROUND_SPIKE_CHARGE_START;
                case 5 -> GROUND_SPIKE_CHARGE_RELEASE;
                case 6 -> SPRINT_ATTACK;
                case 7 -> CONSUME;
                case 8 -> CYCLE_FORM;
                case 9 -> GRAB;
                case 10 -> THROW;
                case 11 -> WEAPON_SPECIAL;
                case 12 -> BLOCK_THROW_START;
                case 13 -> BLOCK_THROW_RELEASE;
                case 14 -> BLOCK_THROW_CANCEL;
                case 15 -> TERRAIN_GRAB_KEEPALIVE;
                case 16 -> TERRAIN_GRAB_RELEASE;
                case 17 -> TERRAIN_GRAB_CANCEL;
                default -> throw new IllegalArgumentException("Invalid claw action id: " + id);
            };
        }
    }
}
