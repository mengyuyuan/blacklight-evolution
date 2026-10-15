package com.codex.blacklight.power;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;

/** A single short additive response, independent of action ownership and locomotion. */
public record DefenseImpact(long at,float strength,float x,float y,float z,boolean shield,float hitX,float hitY,float hitZ) {
    public static final DefenseImpact EMPTY=new DefenseImpact(-1000,0,0,0,0,false,0,0,0);
    public float spring(double now){
        double age=now-at;if(age<0||age>14)return 0;
        // Fast compression, a brief loaded hold, then a small settling overshoot.
        // This holds only the additive recoil offset, never the player's action clock.
        double envelope;
        if(age<1)envelope=age;
        else if(age<2.5)envelope=1;
        else if(age<7){double t=(age-2.5)/4.5;envelope=1-1.16*t*t*(3-2*t);}
        else {double t=(age-7)/7;envelope=-.16*(1-t*t*(3-2*t));}
        return (float)envelope*Math.min(1.25F,strength);
    }
    public static final StreamCodec<FriendlyByteBuf,DefenseImpact> CODEC=new StreamCodec<>() {
        public DefenseImpact decode(FriendlyByteBuf b){return new DefenseImpact(b.readLong(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat());}
        public void encode(FriendlyByteBuf b,DefenseImpact s){b.writeLong(s.at);b.writeFloat(s.strength);b.writeFloat(s.x);b.writeFloat(s.y);b.writeFloat(s.z);b.writeBoolean(s.shield);b.writeFloat(s.hitX);b.writeFloat(s.hitY);b.writeFloat(s.hitZ);}
    };
}
