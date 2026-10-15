package com.codex.blacklight.power;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** A single short additive response, independent of action ownership and locomotion. */
public record DefenseImpact(long at,float strength,float x,float y,float z,boolean shield) {
    public static final DefenseImpact EMPTY=new DefenseImpact(-1000,0,0,0,0,false);
    public float spring(double now){double age=now-at;return age<0||age>12?0:(float)(Math.sin(age*.6)*Math.exp(-age*.29))*strength;}
    public static final StreamCodec<RegistryFriendlyByteBuf,DefenseImpact> CODEC=new StreamCodec<>() {
        public DefenseImpact decode(RegistryFriendlyByteBuf b){return new DefenseImpact(b.readLong(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,DefenseImpact s){b.writeLong(s.at);b.writeFloat(s.strength);b.writeFloat(s.x);b.writeFloat(s.y);b.writeFloat(s.z);b.writeBoolean(s.shield);}
    };
}
