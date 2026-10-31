package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Transient phase snapshot; capture/holding never advance the dissolve clock. */
public record ConsumeVisual(int consumerId, long started, int duration, int form, int stage,float facing,net.minecraft.world.phys.Vec3 origin,int variant) {
    public ConsumeVisual {
        variant=ConsumeVariant.normalize(PowerForm.values()[Math.clamp(form,0,PowerForm.values().length-1)],variant);
    }
    public ConsumeVisual(int consumerId,long started,int duration,int form,int stage,float facing,net.minecraft.world.phys.Vec3 origin){this(consumerId,started,duration,form,stage,facing,origin,ConsumeVariant.DEFAULT);}
    public ConsumeVisual(int consumerId,long started,int duration,int form,int stage,float facing){this(consumerId,started,duration,form,stage,facing,net.minecraft.world.phys.Vec3.ZERO);}
    public ConsumeVisual(int consumerId,long started,int duration,int form,int stage){this(consumerId,started,duration,form,stage,0);}
    public static final int NONE=0, CAPTURE=1, HOLD=2, CONSUME=3, THROW=4, FLIGHT=5, WHIP_PULL=6,
            MOUNT=7,MOUNT_HOLD=8,MOUNT_CONSUME=9,HUNTER_CONSUME=10;
    public static boolean acquiring(int stage){return stage==CAPTURE||stage==WHIP_PULL||stage==MOUNT;}
    public static boolean holding(int stage){return stage==HOLD||stage==MOUNT_HOLD;}
    public static boolean blocksTravel(int stage){return stage==CAPTURE||stage==CONSUME||ConsumeSequence.mountedStage(stage);}
    public static boolean blocksInput(int stage){return blocksTravel(stage)||stage==WHIP_PULL;}
    public static final ConsumeVisual EMPTY = new ConsumeVisual(-1,0,46,0,NONE);
    public ConsumeVisual(int owner,long start,int duration,int form) { this(owner,start,duration,form,CONSUME); }
    public static final StreamCodec<RegistryFriendlyByteBuf, ConsumeVisual> CODEC = new StreamCodec<>() {
        public ConsumeVisual decode(RegistryFriendlyByteBuf b){return new ConsumeVisual(b.readVarInt(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readFloat(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt());}
        public void encode(RegistryFriendlyByteBuf b,ConsumeVisual v){b.writeVarInt(v.consumerId);b.writeVarLong(v.started);b.writeVarInt(v.duration);b.writeVarInt(v.form);b.writeVarInt(v.stage);b.writeFloat(v.facing);b.writeDouble(v.origin.x);b.writeDouble(v.origin.y);b.writeDouble(v.origin.z);b.writeVarInt(v.variant);}
    };
    public boolean active() { return consumerId >= 0; }
    public static boolean consumeStage(int stage){return stage==CONSUME||stage==MOUNT_CONSUME||stage==HUNTER_CONSUME;}
    public boolean consuming() { return active() && consumeStage(stage); }
    public float phaseProgress(long now,float partial) { return Math.clamp((now-started+partial)/Math.max(1,duration),0,1); }
    public float progress(long now,float partial) { return consuming()?phaseProgress(now,partial):0; }
    public float motionProgress(long now,float partial) {
        if(stage==HUNTER_CONSUME)return HunterConsume.actorProgress(HunterConsume.seconds(this,now,partial));
        return ConsumeSequence.motionProgress(PowerForm.values()[Math.clamp(form,0,PowerForm.values().length-1)],progress(now,partial),duration,stage==MOUNT_CONSUME,variant);
    }
    public static int captureDuration(PowerForm form) { return form==PowerForm.WHIPFIST?36:26; }
    /** Contact frames authored in tools/author_weight_r12.py, at 20 server ticks per second. */
    public static int impactTick(PowerForm form) {
        if(ConsumeSequence.paired(form,false))return Math.round(duration(form)*ConsumeSequence.strike(form));
        return switch(form) { case HAMMERFISTS->18; case BLADE->12; case WHIPFIST->9; case CLAWS->11; default->10; };
    }
    public static int duration(PowerForm form) {
        return ConsumeSequence.duration(form,false);
    }
    public static int legacyDuration(PowerForm form) {
        return switch(form) { case HAMMERFISTS->56; case BLADE->48; case WHIPFIST->38; default->46; };
    }
}
