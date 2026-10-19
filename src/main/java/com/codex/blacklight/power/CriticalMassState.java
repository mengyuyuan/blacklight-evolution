package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Reserve is in base Devastator charges: one charge maps to a fraction of maximum HP. */
public record CriticalMassState(float reserve, boolean adrenalineArmed, long lastAdrenalineDevastatorAt) {
    public static final float CAPACITY_HEALTH_FRACTION = .25F;
    /** PrototypeHealthBehaviour.criticalMassHealthGainFactor, verified locally. */
    public static final float CONSUME_GAIN_FACTOR = .55F;
    public static final int MAX_CAPACITY = 4;
    /** minTimeBetweenAdrenalineDevastators = 22.5 seconds; does not gate reserve releases. */
    public static final int ADRENALINE_RELEASE_INTERVAL_TICKS = 450;
    public static final CriticalMassState EMPTY = new CriticalMassState(0, true, -1);
    public CriticalMassState(float reserve, boolean adrenalineArmed) { this(reserve, adrenalineArmed, -1); }
    public static final Codec<CriticalMassState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("reserve", 0F).forGetter(CriticalMassState::reserve),
            Codec.BOOL.optionalFieldOf("adrenaline_armed", true).forGetter(CriticalMassState::adrenalineArmed),
            Codec.LONG.optionalFieldOf("last_adrenaline_devastator_at", -1L).forGetter(CriticalMassState::lastAdrenalineDevastatorAt)
    ).apply(i, CriticalMassState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CriticalMassState> STREAM_CODEC = new StreamCodec<>() {
        @Override public CriticalMassState decode(RegistryFriendlyByteBuf b) {
            return new CriticalMassState(b.readFloat(), b.readBoolean(), b.readVarLong());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, CriticalMassState s) {
            b.writeFloat(s.reserve); b.writeBoolean(s.adrenalineArmed); b.writeVarLong(s.lastAdrenalineDevastatorAt);
        }
    };
    public CriticalMassState {
        reserve = Float.isFinite(reserve) ? Math.clamp(reserve, 0, MAX_CAPACITY) : 0;
        lastAdrenalineDevastatorAt = Math.max(-1, lastAdrenalineDevastatorAt);
    }
    /** Native availability checks health strictly above the ordinary maximum, not a complete cost. */
    public boolean ready() { return reserve > 0; }
    public CriticalMassState addOverheal(float overflow, float maximum) {
        return addOverheal(overflow, maximum, 1);
    }
    public CriticalMassState addOverheal(float overflow, float maximum, int capacity) {
        if (!Float.isFinite(overflow) || !Float.isFinite(maximum) || overflow <= 0 || maximum <= 0) return this;
        return new CriticalMassState(Math.min(Math.clamp(capacity, 1, MAX_CAPACITY),
                reserve + overflow * CONSUME_GAIN_FACTOR / (maximum * CAPACITY_HEALTH_FRACTION)), adrenalineArmed, lastAdrenalineDevastatorAt);
    }
    public CriticalMassState damaged(float damage, float maximum) {
        if (!Float.isFinite(damage) || !Float.isFinite(maximum) || damage <= 0 || maximum <= 0) return this;
        return new CriticalMassState(reserve - damage / (maximum * CAPACITY_HEALTH_FRACTION), adrenalineArmed, lastAdrenalineDevastatorAt);
    }
    public record DamageResult(CriticalMassState state, float remaining, float absorbed) { }
    /** Gain factor only scales new consume overflow; one stored charge absorbs its full health value. */
    public DamageResult absorb(float damage, float maximum) {
        if (!Float.isFinite(damage) || damage <= 0) return new DamageResult(this, 0, 0);
        float healthPerCharge=maximum*CAPACITY_HEALTH_FRACTION;
        if (!Float.isFinite(maximum) || healthPerCharge <= 0) return new DamageResult(this, damage, 0);
        float storedHealth=reserve*healthPerCharge;
        float absorbed=Math.min(damage, storedHealth);
        var remainingState=damage>=storedHealth ? new CriticalMassState(0,adrenalineArmed,lastAdrenalineDevastatorAt) : damaged(absorbed, maximum);
        return new DamageResult(remainingState, Math.max(0, damage-absorbed), absorbed);
    }
    public record SpendResult(CriticalMassState state, float healthLoss) { }
    /** Pay one complete release from reserve, using red health only for the missing portion. */
    public SpendResult spend(float maximum) {
        if (!ready() || !Float.isFinite(maximum) || maximum<=0) return new SpendResult(this,0);
        float healthLoss=Math.max(0,1-reserve)*maximum*CAPACITY_HEALTH_FRACTION;
        return new SpendResult(spend(),healthLoss);
    }
    /** Reserve-only projection; live releases must also apply spend(maximum).healthLoss(). */
    public CriticalMassState spend() { return new CriticalMassState(reserve - 1, adrenalineArmed, lastAdrenalineDevastatorAt); }
    public CriticalMassState armed(boolean armed) { return new CriticalMassState(reserve, armed, lastAdrenalineDevastatorAt); }
    public boolean adrenalineReleaseAllowed(long now) {
        return lastAdrenalineDevastatorAt < 0 || now >= lastAdrenalineDevastatorAt
                && now - lastAdrenalineDevastatorAt >= ADRENALINE_RELEASE_INTERVAL_TICKS;
    }
    public int adrenalineWaitTicks(long now) {
        if (adrenalineReleaseAllowed(now)) return 0;
        return now < lastAdrenalineDevastatorAt ? ADRENALINE_RELEASE_INTERVAL_TICKS
                : (int)(ADRENALINE_RELEASE_INTERVAL_TICKS - (now - lastAdrenalineDevastatorAt));
    }
    public CriticalMassState adrenalineSpent(long now) {
        return new CriticalMassState(reserve, adrenalineArmed, Math.max(0, now));
    }
}
