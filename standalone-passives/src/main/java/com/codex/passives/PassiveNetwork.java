package com.codex.passives;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
public final class PassiveNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(PassivesMod.ID, "state"), () -> "1", "1"::equals, "1"::equals);
    public record Snapshot(CompoundTag data) {
        static Snapshot decode(FriendlyByteBuf b) { var n = b.readNbt(); return new Snapshot(n == null ? new CompoundTag() : n); }
        void encode(FriendlyByteBuf b) { b.writeNbt(data); }
    }
    public static void register() {
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::decode)
                .consumerMainThread((packet, context) -> {
                    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> PassiveClient.accept(packet.data())); context.get().setPacketHandled(true);
                }).add();
    }
    public static void send(ServerPlayer p, CompoundTag n) { CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Snapshot(n)); }
}
