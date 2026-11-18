package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Native ordinary bullrush taunt contact windows; damage policy stays in the
 * actor. Prop-only Hit5000 is deliberately absent from living-target windows. */
public final class GoliathTaunt {
    public record Sweep(String joint,Vec3 from,Vec3 to,double radius) {}
    public record Window(int id,float sourceDamage,List<Sweep> sweeps) {}
    public record Sound(int phase,float at,String patch,String instance,float frequency,
                        String trigger,String triggerType,boolean stopOnExit,boolean autoRepeat) {}
    private record Hit(int id,int phase,float begin,float end,float sourceDamage,double radius,
                       boolean shapecast,String[] chain,float[] sourceOffset,long collideWith) {}
    private record Data(Hit[] hits,Sound[] sounds,float scale) {}
    private static final Data DATA=load();
    private GoliathTaunt(){}
    private static Data load(){
        try(var in=Objects.requireNonNull(GoliathTaunt.class.getResourceAsStream("/assets/blacklight/models/goliath_taunt.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            var hits=gson.fromJson(json.get("windows"),Hit[].class);var sounds=gson.fromJson(json.get("sounds"),Sound[].class);
            float scale=json.get("blocksPerSourceUnit").getAsFloat();var names=Arrays.asList(GoliathMotion.table().bones);
            if(hits.length!=11||sounds.length!=2||!Float.isFinite(scale)||scale<=0)throw new IllegalStateException("Taunt program changed");
            for(var hit:hits){
                if(hit.phase<7||hit.phase>12||hit.begin<0||hit.end<=hit.begin||hit.sourceDamage<=0||hit.sourceDamage>750
                    ||hit.radius<=0||hit.chain.length==0||hit.sourceOffset.length!=3||hit.collideWith!=0x1000000c0L)
                    throw new IllegalStateException("Invalid taunt window "+hit.id);
                for(String joint:hit.chain)if(!names.contains("source_"+joint))throw new IllegalStateException("Missing taunt joint "+joint);
            }
            // Inner/outer share an id: the stronger accepted hit wins, never a
            // silently additive750+400 blast. Other Execute instances differ.
            Arrays.sort(hits,Comparator.comparingInt(Hit::id).thenComparing(Comparator.comparingDouble(Hit::sourceDamage).reversed()));
            return new Data(hits,sounds,scale);
        }catch(Exception e){throw new IllegalStateException("Invalid source Goliath taunt",e);}
    }
    /** One observed20Hz interval only. The caller supplies the last valid
     * contact-sample actor transform, including intervening physical movement;
     * reset it on begin/cancel, skipped ticks or a displacement over8 blocks.
     * This does not sweep an old phase's joint pose into the current phase. */
    public static List<Window> windows(GoliathCharge.Snapshot snapshot,float age,Vec3 previousOrigin,float previousYaw,Vec3 origin,float yaw){
        return windows(snapshot,age,previousOrigin,previousYaw,origin,yaw,1F);
    }
    public static List<Window> windows(GoliathCharge.Snapshot snapshot,float age,Vec3 previousOrigin,float previousYaw,Vec3 origin,float yaw,float scale){
        if(snapshot==null||snapshot.phase()<7||snapshot.phase()>12)return List.of();
        float now=age-snapshot.phaseStartAge(),previous=Math.max(0,now-1);
        if(now<=0)return List.of();
        var result=new ArrayList<Window>();
        for(var hit:DATA.hits){
            if(hit.phase!=snapshot.phase()||now<hit.begin*20||previous>=hit.end*20)continue;
            float first=Math.max(previous,hit.begin*20),last=Math.min(now,hit.end*20);
            if(last<first)continue;
            float t0=Math.max(0,Math.min(1,first-(now-1))),t1=Math.max(0,Math.min(1,last-(now-1)));
            var p0=previousOrigin.lerp(origin,t0);var p1=previousOrigin.lerp(origin,t1);
            float a0=previousYaw+wrap(yaw-previousYaw)*t0,a1=previousYaw+wrap(yaw-previousYaw)*t1;
            var before=origins(hit,snapshot,snapshot.phaseStartAge()+first,p0,a0,scale);
            var after=origins(hit,snapshot,snapshot.phaseStartAge()+last,p1,a1,scale);
            double radius=hit.radius*scale;
            var sweeps=new ArrayList<Sweep>();
            for(int i=0;i<after.size();i++)sweeps.add(new Sweep(hit.chain[i],hit.shapecast?before.get(i):after.get(i),after.get(i),radius));
            for(int i=1;i<after.size();i++){
                var fromA=before.get(i-1);var fromB=before.get(i);var toA=after.get(i-1);var toB=after.get(i);
                int count=Math.max(inserts(fromA,fromB,radius),inserts(toA,toB,radius));
                // Fixed correspondence within this tick; extra samples on its
                // shorter endpoint remain covered by the endpoint spheres.
                for(int j=0;j<count;j++){
                    var to=between(toA,toB,radius,count,j);
                    var from=hit.shapecast?between(fromA,fromB,radius,count,j):to;
                    sweeps.add(new Sweep(hit.chain[i-1]+"->"+hit.chain[i]+":"+j,from,to,radius));
                }
            }
            result.add(new Window(hit.id,hit.sourceDamage,List.copyOf(sweeps)));
        }
        return List.copyOf(result);
    }
    private static float wrap(float degrees){float v=degrees%360;return v>180?v-360:v< -180?v+360:v;}
    /** Minecraft cover adapter: central chest, not the foot/root origin. The
     * source sphere has no such ray; this prevents a pre-windup hand already
     * inside/behind a wall from bypassing loaded-space and cover protection. */
    public static Vec3 bodyAnchor(GoliathCharge.Snapshot snapshot,float age,Vec3 origin,float yaw){
        return bodyAnchor(snapshot,age,origin,yaw,1F);
    }
    public static Vec3 bodyAnchor(GoliathCharge.Snapshot snapshot,float age,Vec3 origin,float yaw,float scale){
        var table=GoliathMotion.table();int index=Arrays.asList(table.bones).indexOf("source_Spine_3");
        if(index<0)throw new IllegalStateException("Missing source chest anchor");
        var point=new Matrix4f().scale(scale).rotateY((float)Math.toRadians(180-yaw))
            .mul(table.transforms(GoliathCharge.pose(snapshot,age))[index]).transformPosition(new Vector3f());
        return origin.add(point.x,point.y+.01,point.z);
    }
    private static int inserts(Vec3 a,Vec3 b,double radius){return Math.max(0,(int)Math.ceil((a.distanceTo(b)-2*radius)/(2*radius)));}
    private static Vec3 between(Vec3 a,Vec3 b,double radius,int count,int index){
        double distance=a.distanceTo(b);if(distance<1e-9)return a;
        double step=Math.max(0,distance-2*radius)/count;
        return a.lerp(b,Math.min(.5,radius/distance)+step*(index+.5)/distance);
    }
    private static List<Vec3> origins(Hit hit,GoliathCharge.Snapshot snapshot,float age,Vec3 origin,float yaw,float scale){
        var table=GoliathMotion.table();var matrices=table.transforms(GoliathCharge.pose(snapshot,age));
        var names=Arrays.asList(table.bones);var base=new Matrix4f().scale(scale).rotateY((float)Math.toRadians(180-yaw));
        var result=new ArrayList<Vec3>();
        for(String joint:hit.chain){
            // Native inclusive-index chain uses joint origins, even though its
            // record has offset[0,0,1]. Only single-joint geometry uses offset.
            var local=hit.chain.length>1?new Vector3f():new Vector3f(-hit.sourceOffset[0]*DATA.scale,hit.sourceOffset[1]*DATA.scale,hit.sourceOffset[2]*DATA.scale);
            var point=new Matrix4f(base).mul(matrices[names.indexOf("source_"+joint)]).transformPosition(local);
            result.add(origin.add(point.x,point.y+.01,point.z));
        }
        return result;
    }
    public static AABB bounds(Window window){
        AABB result=null;for(var sweep:window.sweeps){var box=new AABB(sweep.from,sweep.to).inflate(sweep.radius);result=result==null?box:result.minmax(box);}return GoliathAttackBounds.includingTouch(result);
    }
    public static ProjectileSphereContact.Hit contact(Sweep sweep,AABB box){return ProjectileSphereContact.sweep(sweep.from,sweep.to,sweep.radius,box);}
    public static ProjectileSphereContact.Hit contact(Sweep sweep,HydraContact.Box box){return ProjectileSphereContact.sweep(sweep.from,sweep.to,sweep.radius,box);}
    /** Source metadata for future source-bank routing, not a replacement voice.
     * Both sounds are Play/Retrigger, Frequency(probability)=1, no repeat and
     * StopOnExit=false: finishing the pose must not truncate their waveform. */
    public static List<Sound> sounds(){return List.copyOf(Arrays.asList(DATA.sounds));}
}
