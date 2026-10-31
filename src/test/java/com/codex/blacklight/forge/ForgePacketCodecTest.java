package com.codex.blacklight.forge;

import com.codex.blacklight.forge.codec.ByteBufCodecs;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.network.AttachmentSyncPayload;
import com.codex.blacklight.network.MovementInputPayload;
import com.codex.blacklight.network.UpgradePurchasePayload;
import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.state.BlacklightState;
import com.codex.blacklight.state.BlacklightStateStreamCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForgePacketCodecTest {
    @Test
    void playerStateWireRoundTripRetainsInfectionModeUpgradesAndDisguise() {
        BlacklightState state = new BlacklightState();
        state.infect(); state.addBiomass(100); state.addEvolutionPoints(400);
        assertTrue(state.buyClawUpgrade(ClawUpgrade.RANGE));
        assertTrue(state.setPrototypeMode(true));
        state.captureIdentity("minecraft:sheep");
        assertTrue(state.toggleDisguise());

        BlacklightState decoded = roundTrip(BlacklightStateStreamCodec.CODEC, state);
        assertNotSame(state, decoded);
        assertTrue(decoded.isInfected());
        assertTrue(decoded.isPrototypeMode());
        assertEquals(PowerForm.CLAWS, decoded.getActivePower());
        assertEquals(350, decoded.getBiomass());
        assertEquals(250, decoded.getEvolutionPoints());
        assertTrue(decoded.hasClawUpgrade(ClawUpgrade.RANGE));
        assertEquals("minecraft:sheep", decoded.getDisguiseIdentity());
        assertTrue(decoded.isDisguised());
    }

    @Test
    void attachmentPacketRoundTripRetainsEntityIdentityDimensionAndFieldBytes() {
        AttachmentSyncPayload packet = new AttachmentSyncPayload(42, UUID.randomUUID(),
                new ResourceLocation("minecraft", "overworld"), Map.of("target_lock_id", new byte[]{4, 3, 2}));
        AttachmentSyncPayload decoded = roundTrip(AttachmentSyncPayload.STREAM_CODEC, packet);
        assertEquals(packet.entityId(), decoded.entityId());
        assertEquals(packet.uuid(), decoded.uuid());
        assertEquals(packet.dimension(), decoded.dimension());
        assertArrayEquals(packet.fields().get("target_lock_id"), decoded.fields().get("target_lock_id"));
    }

    @Test
    void attachmentDecoderRejectsExcessiveCountsDuplicateKeysAndOversizedFieldLengths() {
        FriendlyByteBuf buffer = packetHeader(AttachmentSyncPayload.MAX_FIELDS+1);
        try {
            assertThrows(DecoderException.class, () -> AttachmentSyncPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }

        FriendlyByteBuf duplicate = packetHeader(2);
        try {
            duplicate.writeUtf("consuming").writeByteArray(new byte[]{1});
            duplicate.writeUtf("consuming").writeByteArray(new byte[]{0});
            assertThrows(DecoderException.class, () -> AttachmentSyncPayload.STREAM_CODEC.decode(duplicate));
        } finally { duplicate.release(); }

        FriendlyByteBuf oversized = packetHeader(1);
        try {
            oversized.writeUtf("disguise_identity").writeVarInt(ForgePayloadRegistrar.MAX_PACKET_BYTES + 1);
            assertThrows(DecoderException.class, () -> AttachmentSyncPayload.STREAM_CODEC.decode(oversized));
        } finally { oversized.release(); }
    }

    @Test
    void compoundTagDecoderEnforcesNbtAllocationBudget() {
        CompoundTag oversized = new CompoundTag();
        oversized.putByteArray("oversized", new byte[65_537]);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(oversized);
            assertThrows(RuntimeException.class, () -> ByteBufCodecs.COMPOUND_TAG.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void inputAndPurchaseRequestsRetainTheirOriginalNormalizationAndStringBoundary() {
        MovementInputPayload input = new MovementInputPayload(true, false, 70, -40, true, false, 37.5F);
        assertEquals(new MovementInputPayload(true, false, 1, -1, true, false, 37.5F), roundTrip(MovementInputPayload.STREAM_CODEC, input));
        UpgradePurchasePayload purchase = new UpgradePurchasePayload("a".repeat(64));
        assertEquals(purchase, roundTrip(UpgradePurchasePayload.STREAM_CODEC, purchase));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUtf("a".repeat(65));
            assertThrows(DecoderException.class, () -> UpgradePurchasePayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    private static FriendlyByteBuf packetHeader(int count) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(42); buffer.writeUUID(UUID.randomUUID());
        buffer.writeResourceLocation(new ResourceLocation("minecraft", "overworld")); buffer.writeVarInt(count);
        return buffer;
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T packet) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, packet);
            T decoded = codec.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally { buffer.release(); }
    }
}
