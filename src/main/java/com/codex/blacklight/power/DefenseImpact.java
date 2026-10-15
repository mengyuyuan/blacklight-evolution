package com.codex.blacklight.power;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** A single short additive response, independent of action ownership and locomotion. */
public record DefenseImpact(long at,float strength,float x,float y,float z,boolean shield,float hitX,float hitY,float hitZ,boolean braced) {
    public static final DefenseImpact EMPTY=new DefenseImpact(-1000,0,0,0,0,false,0,0,0,false);
    /** Successful deflection is a short material vibration; overload is a deliberate loaded guard. */
    public float spring(double now){
        double age=now-at;if(age<0)return 0;
        if(!braced){
            if(age>=6)return 0;
            double fade=1-age/6;
            return (float)(Math.sin(age*Math.PI)*fade*fade)*(.35F+.45F*Math.min(1.5F,strength));
        }
        if(age>=18)return 0;
        double envelope;
        if(age<1.5){double t=age/1.5;envelope=t*t*(3-2*t);}
        else if(age<4)envelope=1;
        else if(age<9){double t=(age-4)/5;envelope=1-1.12*t*t*(3-2*t);}
        else {double t=(age-9)/9;envelope=-.12*(1-t*t*(3-2*t));}
        return (float)envelope*Math.min(1.25F,Math.max(.65F,strength));
    }
    /** A separate return stroke gives the over-capacity guard a catch / push-away / settle sequence. */
    public float followThrough(double now){
        double age=now-at;if(!braced||age<4||age>=18)return 0;
        double t=age<9?(age-4)/5:(18-age)/9;
        return (float)(t*t*(3-2*t))*Math.min(1.25F,Math.max(.65F,strength));
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,DefenseImpact> CODEC=new StreamCodec<>() {
        public DefenseImpact decode(RegistryFriendlyByteBuf b){return new DefenseImpact(b.readLong(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean());}
        public void encode(RegistryFriendlyByteBuf b,DefenseImpact s){b.writeLong(s.at);b.writeFloat(s.strength);b.writeFloat(s.x);b.writeFloat(s.y);b.writeFloat(s.z);b.writeBoolean(s.shield);b.writeFloat(s.hitX);b.writeFloat(s.hitY);b.writeFloat(s.hitZ);b.writeBoolean(s.braced);}
    };
}
