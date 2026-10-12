package com.codex.blacklight.power;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** A transient last-chance lease; neither the timer nor its free release survives logout. */
public record AdrenalineState(long until, long undamageableUntil, boolean chargeAvailable) {
    public static final AdrenalineState EMPTY = new AdrenalineState(-1, -1, false);
    /** Release opportunity length is Minecraft adaptation, distinct from damage grace. */
    public static final int DURATION_TICKS = 100;
    /** PrototypeHealthBehaviour.adrenalineSurgeUndamageableTime = 1.8 seconds. */
    public static final int UNDAMAGEABLE_TICKS = 36;
    public AdrenalineState(long until, boolean chargeAvailable) { this(until, until, chargeAvailable); }
    public AdrenalineState {
        until = Math.max(-1, until);
        undamageableUntil = Math.clamp(undamageableUntil, -1, until);
        if (until < 0) chargeAvailable = false;
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, AdrenalineState> STREAM_CODEC = new StreamCodec<>() {
        @Override public AdrenalineState decode(RegistryFriendlyByteBuf b) {
            return new AdrenalineState(b.readVarLong(), b.readVarLong(), b.readBoolean());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, AdrenalineState s) {
            b.writeVarLong(s.until); b.writeVarLong(s.undamageableUntil); b.writeBoolean(s.chargeAvailable);
        }
    };
    public boolean active(long now) { return until > now; }
    public boolean undamageable(long now) { return active(now) && undamageableUntil > now; }
    public boolean ready(long now) { return active(now) && chargeAvailable; }
    public AdrenalineState spend() { return new AdrenalineState(until, undamageableUntil, false); }
}
