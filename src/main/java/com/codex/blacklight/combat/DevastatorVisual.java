package com.codex.blacklight.combat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Transient observer snapshot; the server owns release, landing and effect lifetime. */
public record DevastatorVisual(int kind,long session,long released,int phase,int targetId,
                               double x,double y,double z,float dx,float dy,float dz,
                               float radius,float effectStart,float effectEnd) {
    public static final DevastatorVisual EMPTY=new DevastatorVisual(-1,0,-1,0,-1,0,0,0,0,0,1,0,0,0);
    public Vec3 origin(){return new Vec3(x,y,z);}
    public Vec3 direction(){return new Vec3(dx,dy,dz);}
    public double age(Player owner,float partial){return Math.max(0,(owner.level().getGameTime()-(released>=0?released:session)+partial)/20D);}
    public static final StreamCodec<RegistryFriendlyByteBuf,DevastatorVisual> CODEC=new StreamCodec<>() {
        public DevastatorVisual decode(RegistryFriendlyByteBuf b){return new DevastatorVisual(b.readVarInt(),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readDouble(),b.readDouble(),b.readDouble(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat());}
        public void encode(RegistryFriendlyByteBuf b,DevastatorVisual v){b.writeVarInt(v.kind);b.writeVarLong(v.session);b.writeVarLong(v.released);b.writeVarInt(v.phase);b.writeVarInt(v.targetId);b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);b.writeFloat(v.dx);b.writeFloat(v.dy);b.writeFloat(v.dz);b.writeFloat(v.radius);b.writeFloat(v.effectStart);b.writeFloat(v.effectEnd);}
    };
}
