package com.codex.passives;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
public final class PassiveNetwork {
    public record Snapshot(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(PassivesMod.ID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            public Snapshot decode(RegistryFriendlyByteBuf b) { var n = b.readNbt(); return new Snapshot(n == null ? new CompoundTag() : n); }
            public void encode(RegistryFriendlyByteBuf b, Snapshot s) { b.writeNbt(s.data()); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(Snapshot.TYPE, Snapshot.CODEC,
                (packet, context) -> context.enqueueWork(() -> PassiveData.decode(packet.data()).save(context.player())));
    }
    public static void send(ServerPlayer p, CompoundTag n) { PacketDistributor.sendToPlayer(p, new Snapshot(n)); }
}
