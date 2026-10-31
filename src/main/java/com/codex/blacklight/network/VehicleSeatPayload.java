package com.codex.blacklight.network;

import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Switching seats is available to real passengers, including seats without driving authority. */
public record VehicleSeatPayload(UUID vehicle) implements CustomPacketPayload {
    public static final Type<VehicleSeatPayload> TYPE=new Type<>(new ResourceLocation("blacklight","vehicle_seat"));
    public static final StreamCodec<FriendlyByteBuf,VehicleSeatPayload> STREAM_CODEC=StreamCodec.of(
            (buffer,value)->buffer.writeUUID(value.vehicle),buffer->new VehicleSeatPayload(buffer.readUUID()));
    private static final WeakHashMap<ServerPlayer,Long> LAST_SWITCH=new WeakHashMap<>();
    public boolean handle(ServerPlayer sender) {
        if (sender==null || vehicle==null || !sender.isAlive() || sender.isRemoved() || sender.isSpectator()) return false;
        if (!(sender.getVehicle() instanceof com.codex.blacklight.entity.MilitaryVehicle actual)
                || actual.isRemoved() || actual.level()!=sender.level() || !vehicle.equals(actual.getUUID())
                || !actual.getPassengers().contains(sender)) return false;
        long now=sender.level().getGameTime();
        Long previous=LAST_SWITCH.get(sender);
        if (previous!=null && now>=previous && now-previous<4) return false;
        LAST_SWITCH.put(sender,now);
        return actual.requestNextSeat(sender);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
