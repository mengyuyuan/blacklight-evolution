package com.codex.blacklight.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Transient observer snapshot; the server owns release, landing and effect lifetime. */
public record DevastatorVisual(int kind,long session,long released,int phase,int targetId,
                               double x,double y,double z,float dx,float dy,float dz,
                               float radius,float effectStart,float effectEnd,double fallHeight,double downSpeed,
                               java.util.List<Contact> contacts,Vec3 diveAim) {
    public record Contact(int targetId,float at) { }
    public DevastatorVisual(int kind,long session,long released,int phase,int targetId,
                            double x,double y,double z,float dx,float dy,float dz,
                            float radius,float effectStart,float effectEnd,double fallHeight,double downSpeed,
                            java.util.List<Contact> contacts) {
        this(kind,session,released,phase,targetId,x,y,z,dx,dy,dz,radius,effectStart,effectEnd,fallHeight,downSpeed,contacts,null);
    }
    public DevastatorVisual { contacts=java.util.List.copyOf(contacts); if(contacts.size()>64)throw new IllegalArgumentException("Too many skill contacts"); }
    public DevastatorVisual(int kind,long session,long released,int phase,int targetId,
                            double x,double y,double z,float dx,float dy,float dz,
                            float radius,float effectStart,float effectEnd) {
        this(kind,session,released,phase,targetId,x,y,z,dx,dy,dz,radius,effectStart,effectEnd,0,0,java.util.List.of());
    }
    public static final DevastatorVisual EMPTY=new DevastatorVisual(-1,0,-1,0,-1,0,0,0,0,0,1,0,0,0);
    public Vec3 origin(){return new Vec3(x,y,z);}
    public Vec3 direction(){return new Vec3(dx,dy,dz);}
    public double age(Player owner,float partial){return Math.max(0,(owner.level().getGameTime()-(released>=0?released:session)+partial)/20D);}
    public static final StreamCodec<RegistryFriendlyByteBuf,DevastatorVisual> CODEC=new StreamCodec<>() {
        public DevastatorVisual decode(RegistryFriendlyByteBuf b){
            int kind=b.readVarInt();long session=b.readVarLong(),released=b.readVarLong();int phase=b.readVarInt(),target=b.readVarInt();
            double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();float dx=b.readFloat(),dy=b.readFloat(),dz=b.readFloat(),radius=b.readFloat(),start=b.readFloat(),end=b.readFloat();
            double height=b.readDouble(),speed=b.readDouble();int count=b.readVarInt();
            if(count<0||count>64)throw new IllegalArgumentException("Invalid skill contact count");
            var contacts=new java.util.ArrayList<Contact>(count);
            for(int i=0;i<count;i++)contacts.add(new Contact(b.readVarInt(),b.readFloat()));
            Vec3 aim=b.readBoolean()?new Vec3(b.readDouble(),b.readDouble(),b.readDouble()):null;
            return new DevastatorVisual(kind,session,released,phase,target,x,y,z,dx,dy,dz,radius,start,end,height,speed,contacts,aim);
        }
        public void encode(RegistryFriendlyByteBuf b,DevastatorVisual v){b.writeVarInt(v.kind);b.writeVarLong(v.session);b.writeVarLong(v.released);b.writeVarInt(v.phase);b.writeVarInt(v.targetId);b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);b.writeFloat(v.dx);b.writeFloat(v.dy);b.writeFloat(v.dz);b.writeFloat(v.radius);b.writeFloat(v.effectStart);b.writeFloat(v.effectEnd);b.writeDouble(v.fallHeight);b.writeDouble(v.downSpeed);b.writeVarInt(v.contacts.size());for(var contact:v.contacts){b.writeVarInt(contact.targetId());b.writeFloat(contact.at());}b.writeBoolean(v.diveAim!=null);if(v.diveAim!=null){b.writeDouble(v.diveAim.x);b.writeDouble(v.diveAim.y);b.writeDouble(v.diveAim.z);}}
    };
}
