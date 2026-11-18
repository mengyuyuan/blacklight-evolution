package com.codex.blacklight.network;

import net.minecraft.network.FriendlyByteBuf;
import com.codex.blacklight.forge.codec.StreamCodec;
import com.codex.blacklight.forge.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One confirmed contact and its cosmetic layers share the server's animation time. */
public record ContactEffectsPayload(long tick,long serial,int kind,CombatImpactPayload flash,
        InfectedImpactPayload tissue,boolean heavy,List<Surface> terrain,List<Sound> sounds) implements CustomPacketPayload {
    public static final int GROUND=0,CRUSH=1,TISSUE=2,GUARD=3,SOUND=4,OBSTACLE=5;
    public record Surface(double x,double y,double z,int state,int ring,int index,double angle) {}
    public record Sound(ResourceLocation id,float volume,float pitch) {}
    public static final Type<ContactEffectsPayload> TYPE=new Type<>(new ResourceLocation("blacklight","contact_effects"));
    public static final StreamCodec<FriendlyByteBuf,ContactEffectsPayload> STREAM_CODEC=StreamCodec.of(
        (b,p)->{
            b.writeLong(p.tick);b.writeLong(p.serial);b.writeVarInt(p.kind);CombatImpactPayload.STREAM_CODEC.encode(b,p.flash);
            b.writeBoolean(p.tissue!=null);if(p.tissue!=null)InfectedImpactPayload.STREAM_CODEC.encode(b,p.tissue);b.writeBoolean(p.heavy);
            b.writeVarInt(p.terrain.size());for(var s:p.terrain){b.writeDouble(s.x);b.writeDouble(s.y);b.writeDouble(s.z);b.writeVarInt(s.state);b.writeVarInt(s.ring);b.writeVarInt(s.index);b.writeDouble(s.angle);}
            b.writeVarInt(p.sounds.size());for(var s:p.sounds){b.writeResourceLocation(s.id);b.writeFloat(s.volume);b.writeFloat(s.pitch);}
        },b->{
            long tick=b.readLong(),serial=b.readLong();int kind=b.readVarInt();var flash=CombatImpactPayload.STREAM_CODEC.decode(b);
            var tissue=b.readBoolean()?InfectedImpactPayload.STREAM_CODEC.decode(b):null;boolean heavy=b.readBoolean();
            int count=bounded(b.readVarInt(),48);var terrain=new ArrayList<Surface>();
            for(int i=0;i<count;i++)terrain.add(new Surface(b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readDouble()));
            count=bounded(b.readVarInt(),3);var sounds=new ArrayList<Sound>();
            for(int i=0;i<count;i++)sounds.add(new Sound(b.readResourceLocation(),b.readFloat(),b.readFloat()));
            return new ContactEffectsPayload(tick,serial,kind,flash,tissue,heavy,terrain,sounds);
        });
    public ContactEffectsPayload {
        if(tick<0||kind<0||kind>OBSTACLE||flash==null||terrain.size()>48||sounds.size()>3)throw new IllegalArgumentException("Invalid contact receipt");
        if(!Double.isFinite(flash.x()+flash.y()+flash.z()))throw new IllegalArgumentException("Nonfinite contact");
        for(var s:terrain)if(!Double.isFinite(s.x+s.y+s.z+s.angle)||s.ring<0||s.ring>2||s.index<0||s.index>15||s.state<0
                ||Math.abs(s.x-flash.x())>13||Math.abs(s.z-flash.z())>13||Math.abs(s.y-flash.y())>2)throw new IllegalArgumentException("Invalid terrain sample");
        for(var s:sounds)if(!Float.isFinite(s.volume+s.pitch)||s.volume<0||s.volume>4||s.pitch<=0||s.pitch>2)throw new IllegalArgumentException("Invalid sound cue");
        if((kind==CRUSH||kind==TISSUE)&&tissue==null)throw new IllegalArgumentException("Missing hit receipt");
        terrain=List.copyOf(terrain);sounds=List.copyOf(sounds);
    }
    private static int bounded(int n,int max){if(n<0||n>max)throw new IllegalArgumentException("Contact receipt too large");return n;}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
