package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Confirmed server contact; clients simulate cosmetics, never damage or loot. */
public record RagdollImpactPayload(int entityId,int motion,int ticks,int freeze,boolean sharp,int severMask,
                                   double vx,double vy,double vz,List<CutImpulse> cuts,List<CutPlane> planes,List<Wound> wounds) implements CustomPacketPayload {
    /** Four independently traced blades per hand, including each short thumb blade. */
    public static final int MAX_CUT_PLANES=8;
    public record CutImpulse(int region,float x,float y,float z) {}
    public record CutPlane(Vec3 point,Vec3 normal) {}
    public record Wound(int region,Vec3 point,Vec3 direction,float strength) {}
    public RagdollImpactPayload {
        cuts=List.copyOf(cuts);planes=List.copyOf(planes);wounds=List.copyOf(wounds);
        if(cuts.size()>6||planes.size()>MAX_CUT_PLANES||wounds.size()>MAX_CUT_PLANES)throw new IllegalArgumentException("Too many cut regions/planes/wounds");
    }
    public static final Type<RagdollImpactPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","ragdoll_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RagdollImpactPayload> CODEC=StreamCodec.of(
            (b,p)->{
                b.writeVarInt(p.entityId);b.writeVarInt(p.motion);b.writeVarInt(p.ticks);b.writeVarInt(p.freeze);b.writeBoolean(p.sharp);b.writeVarInt(p.severMask);
                b.writeDouble(p.vx);b.writeDouble(p.vy);b.writeDouble(p.vz);b.writeVarInt(p.cuts.size());
                for(var cut:p.cuts){b.writeVarInt(cut.region);b.writeFloat(cut.x);b.writeFloat(cut.y);b.writeFloat(cut.z);}
                b.writeVarInt(p.planes.size());
                for(var plane:p.planes){writeVector(b,plane.point);writeVector(b,plane.normal);}
                b.writeVarInt(p.wounds.size());
                for(var wound:p.wounds){b.writeVarInt(wound.region);writeVector(b,wound.point);writeVector(b,wound.direction);b.writeFloat(wound.strength);}
            },RagdollImpactPayload::read);
    private static RagdollImpactPayload read(RegistryFriendlyByteBuf b){
        int entity=b.readVarInt(),motion=b.readVarInt(),ticks=b.readVarInt(),freeze=b.readVarInt();boolean sharp=b.readBoolean();int mask=b.readVarInt();
        double x=b.readDouble(),y=b.readDouble(),z=b.readDouble();int count=b.readVarInt();
        if(count<0||count>6)throw new IllegalArgumentException("Invalid cut region count");
        var cuts=new ArrayList<CutImpulse>();
        for(int i=0;i<count;i++){
            int region=b.readVarInt();float cx=b.readFloat(),cy=b.readFloat(),cz=b.readFloat();
            if(region<1||region>32||Integer.bitCount(region)!=1||!Float.isFinite(cx+cy+cz)||cx*cx+cy*cy+cz*cz>16)
                throw new IllegalArgumentException("Invalid cut impulse");
            cuts.add(new CutImpulse(region,cx,cy,cz));
        }
        int planeCount=b.readVarInt();if(planeCount<0||planeCount>MAX_CUT_PLANES)throw new IllegalArgumentException("Invalid cut plane count");
        var planes=new ArrayList<CutPlane>();
        for(int i=0;i<planeCount;i++){
            Vec3 point=readVector(b),normal=readVector(b);
            if(!Double.isFinite(point.lengthSqr()+normal.lengthSqr())||point.lengthSqr()>16||normal.lengthSqr()<.9||normal.lengthSqr()>1.1)
                throw new IllegalArgumentException("Invalid cut plane");
            planes.add(new CutPlane(point,normal.normalize()));
        }
        int woundCount=b.readVarInt();if(woundCount<0||woundCount>MAX_CUT_PLANES)throw new IllegalArgumentException("Invalid wound count");
        var wounds=new ArrayList<Wound>();
        for(int i=0;i<woundCount;i++){
            int region=b.readVarInt();Vec3 point=readVector(b),direction=readVector(b);float strength=b.readFloat();
            if(region<1||region>32||Integer.bitCount(region)!=1||!Double.isFinite(point.lengthSqr()+direction.lengthSqr())
                    ||point.lengthSqr()>16||direction.lengthSqr()<.9||direction.lengthSqr()>1.1||!Float.isFinite(strength)||strength<=0||strength>2)
                throw new IllegalArgumentException("Invalid wound");
            wounds.add(new Wound(region,point,direction.normalize(),strength));
        }
        return new RagdollImpactPayload(entity,motion,ticks,freeze,sharp,mask,x,y,z,cuts,planes,wounds);
    }
    private static void writeVector(RegistryFriendlyByteBuf b,Vec3 v){b.writeFloat((float)v.x);b.writeFloat((float)v.y);b.writeFloat((float)v.z);}
    private static Vec3 readVector(RegistryFriendlyByteBuf b){return new Vec3(b.readFloat(),b.readFloat(),b.readFloat());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
