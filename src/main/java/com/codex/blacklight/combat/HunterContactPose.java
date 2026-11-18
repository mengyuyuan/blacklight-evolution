package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The existing Hunter rig drives both the visible hands and authoritative swept contact.
 * FIG selects the active wrist; the capsule fits the authored palm and claws, not a range cone. */
public final class HunterContactPose {
    private record Bone(String name,int parent,int channel,Vector3f pivot) {}
    private static final Bone[] RIG=load();
    private HunterContactPose(){}
    private static Bone[] load(){
        try(var in=Objects.requireNonNull(HunterContactPose.class.getResourceAsStream("/assets/blacklight/geo/blacklight_hunter_mc.geo.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var raw=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            var result=new ArrayList<Bone>();var indices=new HashMap<String,Integer>();
            var channels=Arrays.asList(CreaturePoseTable.HUNTER.bones);
            for(var value:raw){var b=value.getAsJsonObject();var p=b.getAsJsonArray("pivot");String name=b.get("name").getAsString();
                int parent=b.has("parent")?Objects.requireNonNull(indices.get(b.get("parent").getAsString())):-1;
                result.add(new Bone(name,parent,channels.indexOf(name),new Vector3f(-p.get(0).getAsFloat()/16,p.get(1).getAsFloat()/16,p.get(2).getAsFloat()/16)));
                indices.put(name,result.size()-1);
            }return result.toArray(Bone[]::new);
        }catch(Exception ex){throw new IllegalStateException("Invalid Hunter contact rig",ex);}
    }
    public static float[][] pose(CreatureAttack move,float age){
        var table=CreaturePoseTable.HUNTER;var pose=table.sample(move.clip,age);var idle=table.sample("idle",0);
        float weight=Math.max(0,Math.min(1,move==CreatureAttack.COUNTER?age/3:Math.min(age/3,(move.ticks-age)/5)));
        for(int i=0;i<pose.length;i++)pose[i]=CreaturePoseTable.blend(idle[i],pose[i],weight);
        return pose;
    }
    /** Source release blend ends before its first active wrist window. The counter does not
     * fall back to idle between the two stages, and ordinary standalone barrage is unchanged. */
    public static float[][] pose(CreatureAttack move,float age,boolean counterRelease){
        var pose=pose(move,age);
        if(!counterRelease||move!=CreatureAttack.BARRAGE)return pose;
        float weight=Math.max(0,Math.min(1,(age-CreatureAttack.COUNTER_BARRAGE_ENTRY_TICK)/CreatureAttack.COUNTER_BARRAGE_BLEND_TICKS));
        if(weight>=1)return pose;
        var opening=pose(CreatureAttack.COUNTER,CreatureAttack.COUNTER.ticks);
        for(int i=0;i<pose.length;i++)pose[i]=CreaturePoseTable.blend(opening[i],pose[i],weight);
        return pose;
    }
    public record Hand(Vec3 palm,Vec3 tip,double radius) {
        public Vec3 center(){return palm.add(tip).scale(.5);}
        public boolean touches(AABB box){return capsuleTouches(box,palm,tip,radius);}
    }
    private static final class ReachSamples {
        static final Map<CreatureAttack,List<Hand>> VALUES=build();
        private static Map<CreatureAttack,List<Hand>> build(){
            var map=new EnumMap<CreatureAttack,List<Hand>>(CreatureAttack.class);
            for(var move:CreatureAttack.values()){
                var samples=new ArrayList<Hand>();
                for(var window:move.windows())for(float t=window.from()*20;t<=window.to()*20;t+=.5F)
                    samples.add(hand(move,t,window.left(),HunterCounterMotion.offset(move,false,t),0));
                map.put(move,List.copyOf(samples));
            }return map;
        }
    }
    /** AI approaches until its selected clip can reach. Runtime still tests the moving
     * target on every active tick; this preview cannot grant or guarantee damage. */
    public static boolean canReach(CreatureAttack move,Vec3 origin,float yaw,AABB box){
        double radians=Math.toRadians(yaw),c=Math.cos(radians),s=Math.sin(radians);
        for(var hand:ReachSamples.VALUES.get(move)){
            var a=hand.palm;var b=hand.tip;
            if(capsuleTouches(box,origin.add(a.x*c-a.z*s,a.y,a.x*s+a.z*c),origin.add(b.x*c-b.z*s,b.y,b.x*s+b.z*c),hand.radius))return true;
        }return false;
    }
    /** The counter release starts at its own root origin. Preview only the
     * remaining source travel; world collision still owns actual movement. */
    public static boolean canReach(CreatureAttack move,Vec3 origin,float yaw,AABB box,boolean counterRelease){
        if(!counterRelease||move!=CreatureAttack.BARRAGE)return canReach(move,origin,yaw,box);
        for(var window:move.windows())for(float age=window.from()*20;age<=window.to()*20;age+=.5F){
            var offset=HunterCounterMotion.offset(move,true,age).yRot((float)Math.toRadians(-yaw));
            if(hand(move,age,window.left(),origin.add(offset),yaw).touches(box))return true;
        }
        return false;
    }
    public static Hand hand(CreatureAttack move,float age,boolean left,Vec3 origin,float yaw){
        var pose=pose(move,age);var transforms=new Matrix4f[RIG.length];
        String name=left?"left_forearm":"right_forearm";
        for(int i=0;i<RIG.length;i++){
            var b=RIG[i];var p=b.pivot;var m=b.parent<0?new Matrix4f():new Matrix4f(transforms[b.parent]);
            float[] v=b.channel<0?new float[6]:pose[b.channel];
            // Gecko bakes X mirrored and applies (-posX,posY,posZ), then pivot, Rz Ry Rx.
            m.translate(p.x-v[3]/16,p.y+v[4]/16,p.z+v[5]/16).rotateZYX(v[2],v[1],v[0]).translate(-p.x,-p.y,-p.z);
            transforms[i]=m;
            if(b.name.equals(name)){
                float side=left?-1:1;
                return new Hand(world(m,new Vector3f(side*12.5F/16,2F/16,-15.5F/16),origin,yaw),
                    world(m,new Vector3f(side*12.5F/16,1.1F/16,-24F/16),origin,yaw),.32);
            }
        }throw new IllegalStateException(name);
    }
    private static Vec3 world(Matrix4f m,Vector3f point,Vec3 origin,float yaw){
        m.transformPosition(point);point.rotateY((float)Math.toRadians(180-yaw));
        return origin.add(point.x,point.y+.01,point.z); // GeoEntityRenderer's fixed floor offset.
    }
    /** Four substeps follow the curved wrist motion instead of skipping fast slashes. */
    public static Vec3 contact(CreatureAttack move,CreatureAttack.Window window,float previous,float age,Vec3 oldOrigin,Vec3 origin,float yaw,AABB box){
        if(!window.overlaps(previous,age))return null;
        float from=Math.max(previous,window.from()*20),to=Math.min(age,window.to()*20);
        Hand last=null;
        for(int i=0;i<=4;i++){
            float t=from+(to-from)*i/4;double fraction=age==previous?1:(t-previous)/(age-previous);
            Hand current=hand(move,t,window.left(),oldOrigin.lerp(origin,fraction),yaw);
            if(current.touches(box))return nearest(box,current.center());
            if(last!=null&&(capsuleTouches(box,last.palm,current.palm,current.radius)||capsuleTouches(box,last.tip,current.tip,current.radius)))return nearest(box,current.center());
            last=current;
        }return null;
    }
    public static Vec3 nearest(AABB b,Vec3 p){return new Vec3(Math.max(b.minX,Math.min(b.maxX,p.x)),Math.max(b.minY,Math.min(b.maxY,p.y)),Math.max(b.minZ,Math.min(b.maxZ,p.z)));}
    public static boolean capsuleTouches(AABB box,Vec3 a,Vec3 b,double radius){
        if(!box.inflate(radius).contains(a)&&box.inflate(radius).clip(a,b).isEmpty())return false;
        // Distance to a convex box along a segment is convex. The final Euclidean check
        // rejects false corner hits from an inflated AABB, including stationary hands.
        double lo=0,hi=1;
        for(int i=0;i<24;i++){double u=(lo*2+hi)/3,v=(lo+hi*2)/3;Vec3 pu=a.lerp(b,u),pv=a.lerp(b,v);
            if(pu.distanceToSqr(nearest(box,pu))<=pv.distanceToSqr(nearest(box,pv)))hi=v;else lo=u;
        }
        Vec3 p=a.lerp(b,(lo+hi)*.5);return p.distanceToSqr(nearest(box,p))<=radius*radius+1e-7;
    }
}
