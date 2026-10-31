package com.codex.blacklight.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Intent only. The actual passenger and world are taken from the sending connection. */
public record VehicleControlPayload(UUID vehicle, UUID lease, long sequence, double forward,
        double steering, double aimYaw, double aimPitch, int actions) implements CustomPacketPayload {
    public static final Type<VehicleControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("blacklight", "vehicle_control"));
    public static final StreamCodec<FriendlyByteBuf, VehicleControlPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.vehicle); buffer.writeUUID(value.lease); buffer.writeLong(value.sequence);
                buffer.writeDouble(value.forward); buffer.writeDouble(value.steering);
                buffer.writeDouble(value.aimYaw); buffer.writeDouble(value.aimPitch); buffer.writeVarInt(value.actions);
            }, buffer -> new VehicleControlPayload(buffer.readUUID(), buffer.readUUID(), buffer.readLong(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()));

    public boolean valid() {
        return vehicle!=null && lease!=null && sequence>=0 && (actions & ~31)==0
                && unit(forward) && unit(steering) && angle(aimYaw,180) && angle(aimPitch,90);
    }
    private static boolean unit(double value) { return Double.isFinite(value) && value>=-1 && value<=1; }
    private static boolean angle(double value,double maximum) { return Double.isFinite(value) && value>=-maximum && value<=maximum; }
    public boolean handle(ServerPlayer sender) {
        if (sender==null || !valid() || !sender.isAlive() || sender.isRemoved() || sender.isSpectator()) return false;
        if (!(sender.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle actual)
                || actual.isRemoved() || actual.level()!=sender.level() || !actual.getUUID().equals(vehicle)
                || !actual.getPassengers().contains(sender)) return false;
        return actual.acceptControl(sender,lease,sequence,forward,steering,aimYaw,aimPitch,actions);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
