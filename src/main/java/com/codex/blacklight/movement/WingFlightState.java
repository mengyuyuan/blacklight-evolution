package com.codex.blacklight.movement;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Transient flight state, synchronized to the owner and late tracking observers. */
public record WingFlightState(int phase, long started, int foldVariant, long transformedAt, boolean deployed) {
    public WingFlightState(int phase,long started){this(phase,started,0);}
    public WingFlightState(int phase,long started,int foldVariant){this(phase,started,foldVariant,0,false);}
    public WingFlightState { foldVariant &= 1; }
    public static final int GROUNDED=0, TAKEOFF=1, HOVER=2, CRUISE=3, BOOST=4, LAND=5;
    public static final int WINDUP_TICKS=8, TAKEOFF_TICKS=18, LAND_TICKS=16;
    public static final WingFlightState EMPTY=new WingFlightState(GROUNDED,0);
    public boolean flying(){return phase>=TAKEOFF&&phase<=BOOST;}
    public static final StreamCodec<RegistryFriendlyByteBuf,WingFlightState> CODEC=new StreamCodec<>() {
        public WingFlightState decode(RegistryFriendlyByteBuf b){return new WingFlightState(b.readVarInt(),b.readVarLong(),b.readUnsignedByte(),b.readVarLong(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,WingFlightState s){b.writeVarInt(s.phase);b.writeVarLong(s.started);b.writeByte(s.foldVariant);b.writeVarLong(s.transformedAt);b.writeBoolean(s.deployed);}
    };
}
