package com.codex.blacklight.power;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;

/** A single short additive response, independent of action ownership and locomotion. */
public record DefenseImpact(long at,float strength,float x,float y,float z,boolean shield,float hitX,float hitY,float hitZ) {
    public static final DefenseImpact EMPTY=new DefenseImpact(-1000,0,0,0,0,false,0,0,0);
    public float spring(double now){
        double intensity=Math.min(1,Math.max(0,strength));
        double duration=7+6*intensity,age=now-at;
        if(age<0||age>=duration)return 0;
        double hold=1+intensity,release=duration*.55,envelope;
        if(age<1)envelope=age*age*(3-2*age);
        else if(age<hold)envelope=1;
        else if(age<release){double t=(age-hold)/(release-hold);envelope=1-1.12*t*t*(3-2*t);}
        else {double t=(age-release)/(duration-release);envelope=-.12*(1-t*t*(3-2*t));}
        return (float)envelope*Math.min(1.25F,strength);
    }
    public static final StreamCodec<FriendlyByteBuf,DefenseImpact> CODEC=new StreamCodec<>() {
        public DefenseImpact decode(FriendlyByteBuf b){return new DefenseImpact(b.readLong(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat());}
        public void encode(FriendlyByteBuf b,DefenseImpact s){b.writeLong(s.at);b.writeFloat(s.strength);b.writeFloat(s.x);b.writeFloat(s.y);b.writeFloat(s.z);b.writeBoolean(s.shield);b.writeFloat(s.hitX);b.writeFloat(s.hitY);b.writeFloat(s.hitZ);}
    };
}
