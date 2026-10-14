package com.codex.blacklight.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Persist the player's form; transient victim IDs are cleared on login/dimension changes. */
public record BiomassState(int phase, long changedAt, boolean fatal, String shell, String previousPower, int victim) {
    public static final int HUMAN=0, CRAWLING=1, ABSORBING=2, REFORMING=3, READY=4;
    public static final int ABSORB_TICKS=40, REFORM_TICKS=24;
    public static final BiomassState EMPTY=new BiomassState(HUMAN,0,false,"","none",-1);
    public BiomassState {
        phase=Math.max(HUMAN,Math.min(READY,phase));
        shell=shell==null?"":shell;
        previousPower=previousPower==null?"none":previousPower;
    }
    public boolean recallable(){return !fatal&&!shell.isEmpty()&&(phase==CRAWLING||phase==ABSORBING);}
    public BiomassState afterRespawn(long now){
        if(!active())return EMPTY;
        return consumed()?reform(now):new BiomassState(CRAWLING,now,true,"",previousPower,-1);
    }
    public boolean active(){return phase!=HUMAN;}
    public boolean immobilized(){return phase==ABSORBING||phase==REFORMING;}
    public BiomassState crawl(long now){return new BiomassState(CRAWLING,now,fatal,shell,previousPower,-1);}
    public BiomassState absorb(int id,long now){return new BiomassState(ABSORBING,now,fatal,shell,previousPower,id);}
    public BiomassState reform(long now){return new BiomassState(REFORMING,now,fatal,shell,previousPower,-1);}
    public BiomassState ready(long now){return new BiomassState(READY,now,fatal,shell,previousPower,-1);}
    public boolean consumed(){return phase==REFORMING||phase==READY;}
    public float progress(double now,int ticks){return (float)Math.max(0,Math.min(1,(now-changedAt)/ticks));}
    public static final Codec<BiomassState> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.INT.optionalFieldOf("phase",HUMAN).forGetter(BiomassState::phase),
        Codec.LONG.optionalFieldOf("changed",0L).forGetter(BiomassState::changedAt),
        Codec.BOOL.optionalFieldOf("fatal",false).forGetter(BiomassState::fatal),
        Codec.STRING.optionalFieldOf("shell","").forGetter(BiomassState::shell),
        Codec.STRING.optionalFieldOf("power","none").forGetter(BiomassState::previousPower)
    ).apply(i,(phase,at,fatal,shell,power)->new BiomassState(phase,at,fatal,shell,power,-1)));
    public static final StreamCodec<FriendlyByteBuf,BiomassState> STREAM_CODEC=new StreamCodec<>() {
        public BiomassState decode(FriendlyByteBuf b){return new BiomassState(b.readVarInt(),b.readLong(),b.readBoolean(),b.readUtf(64),b.readUtf(64),b.readVarInt());}
        public void encode(FriendlyByteBuf b,BiomassState s){b.writeVarInt(s.phase);b.writeLong(s.changedAt);b.writeBoolean(s.fatal);b.writeUtf(s.shell,64);b.writeUtf(s.previousPower,64);b.writeVarInt(s.victim);}
    };
}
