package com.codex.blacklight.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Confirmed server contact; clients simulate cosmetics, never damage or loot. */
public record RagdollImpactPayload(int entityId,int motion,int ticks,int freeze,boolean sharp,boolean refinement,int severMask,
                                   double vx,double vy,double vz,List<CutImpulse> cuts,List<CutPlane> planes,List<Wound> wounds,long at) implements CustomPacketPayload {
    public RagdollImpactPayload(int entityId,int motion,int ticks,int freeze,boolean sharp,boolean refinement,int severMask,
                                double vx,double vy,double vz,List<CutImpulse> cuts,List<CutPlane> planes,List<Wound> wounds){
        this(entityId,motion,ticks,freeze,sharp,refinement,severMask,vx,vy,vz,cuts,planes,wounds,-1);
    }
    /** Four independently traced blades per hand, including each short thumb blade. */
    public static final int MAX_CUT_PLANES=8;
    public static final int MAX_BLADE_SPANS=64;
    public static final int MAX_BLADE_POINTS=6;
    /** The receiver frame is retained so later corpse motion cannot move an old blade sample. */
    public record BladeSpan(List<Vec3> points,double time,Vec3 origin,float yaw) {
        public BladeSpan {
            points=List.copyOf(points);
            if(points.size()<2||points.size()>MAX_BLADE_POINTS||points.stream().anyMatch(p->!Double.isFinite(p.lengthSqr())))throw new IllegalArgumentException("Invalid blade profile");
            if(!Double.isFinite(time)||(time<0&&time!=-1)||!Double.isFinite(origin.lengthSqr())
                    ||Math.max(Math.abs(origin.x),Math.max(Math.abs(origin.y),Math.abs(origin.z)))>32_000_000||!Float.isFinite(yaw))throw new IllegalArgumentException("Invalid blade sample frame");
        }
        public BladeSpan(List<Vec3> points){this(points,-1,Vec3.ZERO,180);}
        public BladeSpan(Vec3 root,Vec3 tip){this(List.of(root,tip));}
        public boolean timed(){return time>=0;}
        public Vec3 worldPoint(Vec3 point){return point.yRot((float)Math.toRadians(180-yaw)).add(origin);}
        public Vec3 root(){return points.getFirst();}
        public Vec3 tip(){return points.getLast();}
        public boolean near(BladeSpan other){if(time!=other.time||origin.distanceToSqr(other.origin)>1e-10||yaw!=other.yaw||points.size()!=other.points.size())return false;for(int i=0;i<points.size();i++)if(points.get(i).distanceToSqr(other.points.get(i))>1e-10)return false;return true;}
    }
    /** Per-region contact impulse; a region need not be severed to receive it. Wire layout unchanged. */
    public record CutImpulse(int region,float x,float y,float z) {}
    /** Region 0 is a generic model contact; positive bits identify confirmed anatomy. */
    public record CutPlane(int region,Vec3 point,Vec3 normal,long stroke,String edge,List<BladeSpan> path) {
        public CutPlane {
            path=List.copyOf(path);if(path.size()>MAX_BLADE_SPANS||edge.length()>120)throw new IllegalArgumentException("Invalid blade path");
            double previous=-1;
            for(var sample:path){
                if(sample.points.size()!=path.getFirst().points.size()||sample.timed()!=path.getFirst().timed()||sample.time<previous)throw new IllegalArgumentException("Inconsistent blade profile/time");
                previous=sample.time;
            }
        }
        public CutPlane(int region,Vec3 point,Vec3 normal){this(region,point,normal,0,"",List.of());}
        public CutPlane(Vec3 point,Vec3 normal){this(0,point,normal);}
    }
    public record Wound(int region,Vec3 point,Vec3 direction,float strength) {}
    public RagdollImpactPayload {
        cuts=List.copyOf(cuts);planes=List.copyOf(planes);wounds=List.copyOf(wounds);
        if(cuts.size()>6||planes.size()>MAX_CUT_PLANES||wounds.size()>MAX_CUT_PLANES)throw new IllegalArgumentException("Too many cut regions/planes/wounds");
        if(at< -1)throw new IllegalArgumentException("Invalid contact clock");
    }
    public static final Type<RagdollImpactPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("blacklight","ragdoll_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RagdollImpactPayload> CODEC=StreamCodec.of(
            (b,p)->{
                b.writeVarInt(p.entityId);b.writeVarInt(p.motion);b.writeVarInt(p.ticks);b.writeVarInt(p.freeze);b.writeBoolean(p.sharp);b.writeBoolean(p.refinement);b.writeVarInt(p.severMask);
                b.writeDouble(p.vx);b.writeDouble(p.vy);b.writeDouble(p.vz);b.writeVarInt(p.cuts.size());
                for(var cut:p.cuts){b.writeVarInt(cut.region);b.writeFloat(cut.x);b.writeFloat(cut.y);b.writeFloat(cut.z);}
                b.writeVarInt(p.planes.size());
                for(var plane:p.planes){b.writeVarInt(plane.region);writeVector(b,plane.point);writeVector(b,plane.normal);
                    b.writeLong(plane.stroke);b.writeUtf(plane.edge,120);b.writeVarInt(plane.path.size());for(var span:plane.path){
                        b.writeDouble(span.time);b.writeDouble(span.origin.x);b.writeDouble(span.origin.y);b.writeDouble(span.origin.z);b.writeFloat(span.yaw);
                        b.writeVarInt(span.points.size());for(var point:span.points)writeVector(b,point);}}
                b.writeVarInt(p.wounds.size());
                for(var wound:p.wounds){b.writeVarInt(wound.region);writeVector(b,wound.point);writeVector(b,wound.direction);b.writeFloat(wound.strength);}
                b.writeLong(p.at);
            },RagdollImpactPayload::read);
    private static RagdollImpactPayload read(RegistryFriendlyByteBuf b){
        int entity=b.readVarInt(),motion=b.readVarInt(),ticks=b.readVarInt(),freeze=b.readVarInt();boolean sharp=b.readBoolean(),refinement=b.readBoolean();int mask=b.readVarInt();
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
            int region=b.readVarInt();Vec3 point=readVector(b),normal=readVector(b);
            if(region<0||region>32||(region!=0&&Integer.bitCount(region)!=1)||!Double.isFinite(point.lengthSqr()+normal.lengthSqr())||point.lengthSqr()>16384||normal.lengthSqr()<.9||normal.lengthSqr()>1.1)
                throw new IllegalArgumentException("Invalid cut plane");
            long stroke=b.readLong();String edge=b.readUtf(120);int spans=b.readVarInt();
            if(spans<0||spans>MAX_BLADE_SPANS)throw new IllegalArgumentException("Invalid blade sample count");
            var path=new ArrayList<BladeSpan>();for(int j=0;j<spans;j++){
                double time=b.readDouble();Vec3 origin=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();
                int points=b.readVarInt();if(points<2||points>MAX_BLADE_POINTS)throw new IllegalArgumentException("Invalid blade profile size");
                var profile=new ArrayList<Vec3>();for(int k=0;k<points;k++){var bladePoint=readVector(b);if(!Double.isFinite(bladePoint.lengthSqr())||bladePoint.lengthSqr()>16384)throw new IllegalArgumentException("Invalid blade span");profile.add(bladePoint);}
                path.add(new BladeSpan(profile,time,origin,yaw));
            }
            planes.add(new CutPlane(region,point,normal.normalize(),stroke,edge,path));
        }
        int woundCount=b.readVarInt();if(woundCount<0||woundCount>MAX_CUT_PLANES)throw new IllegalArgumentException("Invalid wound count");
        var wounds=new ArrayList<Wound>();
        for(int i=0;i<woundCount;i++){
            int region=b.readVarInt();Vec3 point=readVector(b),direction=readVector(b);float strength=b.readFloat();
            if(region<1||region>32||Integer.bitCount(region)!=1||!Double.isFinite(point.lengthSqr()+direction.lengthSqr())
                    ||point.lengthSqr()>16384||direction.lengthSqr()<.9||direction.lengthSqr()>1.1||!Float.isFinite(strength)||strength<=0||strength>2)
                throw new IllegalArgumentException("Invalid wound");
            wounds.add(new Wound(region,point,direction.normalize(),strength));
        }
        return new RagdollImpactPayload(entity,motion,ticks,freeze,sharp,refinement,mask,x,y,z,cuts,planes,wounds,b.readLong());
    }
    private static void writeVector(RegistryFriendlyByteBuf b,Vec3 v){b.writeFloat((float)v.x);b.writeFloat((float)v.y);b.writeFloat((float)v.z);}
    private static Vec3 readVector(RegistryFriendlyByteBuf b){return new Vec3(b.readFloat(),b.readFloat(),b.readFloat());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
