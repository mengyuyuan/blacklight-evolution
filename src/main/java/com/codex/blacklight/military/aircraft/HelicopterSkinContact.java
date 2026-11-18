package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.combat.HydraContact;
import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.google.gson.*;
import com.codex.blacklight.military.vehicle.VehicleCollisionGeometry;
import com.codex.blacklight.military.vehicle.VehicleCollisionShapes;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** MC receiving adapter for the exact authored rigid airframe cuboids, using
 * HelicopterModel/Renderer's parent offset, door/gun/canopy pose, yaw and bank.
 * Locally phased rotor blades and expendable missiles are not structural hull.
 * This is not a reconstruction of the original game's melee damage tuning. */
public final class HelicopterSkinContact {
    private record Cube(String parent,Vec3 bone,Vec3 center,Vec3 half,Matrix4f local){}
    private static final List<Cube> CUBES=load();
    private record Pose(Vec3 position,float yaw,float bank,float gunYaw,float gunPitch,boolean doors,boolean hijack,int side,int age){}
    private record Cached(Pose pose,List<HydraContact.Box> boxes){}
    private static final Map<BlackwatchHelicopter,Cached> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static List<Cube> load(){
        try(var in=Objects.requireNonNull(HelicopterSkinContact.class.getResourceAsStream("/assets/blacklight/military/helicopter-model.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var data=JsonParser.parseReader(reader).getAsJsonObject();var bones=data.getAsJsonObject("bones");var cubes=new ArrayList<Cube>();
            for(var raw:data.getAsJsonArray("parts")){
                var part=raw.getAsJsonObject();String parent=part.get("parent").getAsString();
                if(parent.equals("rotor")||parent.equals("tail_rotor")||parent.startsWith("hellfire_"))continue;
                var b=part.getAsJsonArray("box");Vec3 size=new Vec3(b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble());
                Vec3 center=vector(b).add(size.scale(.5)).scale(1/16d);var local=new Matrix4f();
                if(part.has("rotation")){
                    var rotation=part.getAsJsonObject("rotation");var origin=rotation.has("origin")?vector(rotation.getAsJsonArray("origin")):Vec3.ZERO;
                    float angle=(float)Math.toRadians(rotation.get("degrees").getAsDouble());
                    local.translate((float)origin.x,(float)origin.y,(float)origin.z);
                    switch(rotation.get("axis").getAsString()){case "x"->local.rotateX(angle);case "y"->local.rotateY(angle);case "z"->local.rotateZ(angle);default->throw new IllegalStateException("Unknown helicopter part axis");}
                    local.translate((float)-origin.x,(float)-origin.y,(float)-origin.z);
                }
                cubes.add(new Cube(parent,vector(bones.getAsJsonArray(parent)),center,size.scale(1/32d),local));
            }return List.copyOf(cubes);
        }catch(Exception e){throw new IllegalStateException("Invalid helicopter receiving mesh",e);}
    }
    public static List<HydraContact.Box> boxes(BlackwatchHelicopter craft){
        var hijack=craft.pilot().hijack();
        var pose=new Pose(craft.position(),craft.getYRot(),craft.bank(),craft.gunYaw(),craft.gunPitch(),craft.doorsOpen(),hijack.active(),hijack.side(),hijack.ticks());
        var cached=CACHE.get(craft);if(cached!=null&&cached.pose.equals(pose))return cached.boxes;
        var base=new Matrix4f().rotateY((float)Math.toRadians(craft.getYRot())).rotateZ((float)Math.toRadians(craft.bank()));
        var result=new ArrayList<HydraContact.Box>();var parents=new HashMap<String,Matrix4f>();
        for(var cube:CUBES){
            var parent=parents.computeIfAbsent(cube.parent,name->{
                Vec3 at=cube.bone;if(craft.doorsOpen()&&(name.equals("door_left")||name.equals("door_right")))at=at.add(0,0,-31/16d);
                var m=new Matrix4f(base).translate((float)at.x,(float)at.y,(float)at.z);
                if(name.equals("gun"))m.rotateY((float)Math.toRadians(craft.gunYaw())).rotateX((float)Math.toRadians(craft.gunPitch()));
                if(hijack.active()&&name.equals(hijack.side()<0?"cockpit_left":"cockpit_right"))m.rotateY(hijack.side()*(float)Math.toRadians(75)*Math.min(1,hijack.ticks()/18F));
                return m;
            });
            var matrix=new Matrix4f(parent).mul(cube.local);var c=matrix.transformPosition(cube.center.toVector3f());
            var x=matrix.transformDirection(new Vector3f(1,0,0));var y=matrix.transformDirection(new Vector3f(0,1,0));var z=matrix.transformDirection(new Vector3f(0,0,1));
            result.add(HydraContact.affine(craft.position().add(c.x,c.y,c.z),cube.half,new Vec3(x.x,x.y,x.z),new Vec3(y.x,y.y,y.z),new Vec3(z.x,z.y,z.z)));
        }var boxes=List.copyOf(result);CACHE.put(craft,new Cached(pose,boxes));return boxes;
    }
    private record WalkHull(List<HydraContact.Box> pose, VehicleCollisionGeometry geometry) {}
    private static final Map<BlackwatchHelicopter, WalkHull> WALK_HULLS=Collections.synchronizedMap(new WeakHashMap<>());

    private static VehicleDefinition.Vec3 vector(Vec3 v){
        return new VehicleDefinition.Vec3(v.x,v.y,v.z);
    }

    public static VoxelShape walkingShape(BlackwatchHelicopter craft,AABB query){
        var skin=boxes(craft);
        var cached=WALK_HULLS.get(craft);
        if(cached==null||cached.pose()!=skin){
            var parts=new ArrayList<VehicleCollisionGeometry.Box>(skin.size());
            for(var box:skin){
                // Opposite face centres in the authored 3x3x3 sampling grid.
                var right=box.points.get(22).subtract(box.center);
                var up=box.points.get(16).subtract(box.center);
                var forward=box.points.get(14).subtract(box.center);
                parts.add(new VehicleCollisionGeometry.Box(vector(box.center.subtract(craft.position())),
                        new VehicleDefinition.Vec3(right.length(),up.length(),forward.length()),
                        vector(right.normalize()),vector(up.normalize()),vector(forward.normalize())));
            }
            cached=new WalkHull(skin,new VehicleCollisionGeometry(parts));
            WALK_HULLS.put(craft,cached);
        }
        var pos=craft.position();
        return VehicleCollisionShapes.shape(cached.geometry(),query.move(-pos.x,-pos.y,-pos.z).inflate(1e-7)).move(pos.x,pos.y,pos.z);
    }

    public static Optional<Vec3> raycast(BlackwatchHelicopter craft,Vec3 from,Vec3 to){
        Vec3 nearest=null;
        double distance=Double.POSITIVE_INFINITY;
        var line=new AABB(from,to).inflate(1e-6);
        for(var box:boxes(craft)){
            if(!line.intersects(box.bounds))continue;
            var hit=box.clip(from,to);
            if(hit.isPresent()&&from.distanceToSqr(hit.get())<distance){
                nearest=hit.get();
                distance=from.distanceToSqr(nearest);
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static Vec3 closestPoint(BlackwatchHelicopter craft,Vec3 from){return boxes(craft).stream().map(b->b.nearest(from)).min(Comparator.comparingDouble(from::distanceToSqr)).orElse(craft.position());}
    public static Vec3 visiblePoint(net.minecraft.world.entity.player.Player player,BlackwatchHelicopter craft,double range){
        var from=player.getEyePosition();Vec3 result=null;double best=range*range+1e-6;
        for(var box:boxes(craft)){
            var p=box.nearest(from);double d=from.distanceToSqr(p);
            if(d<=best&&com.codex.blacklight.combat.CombatTargeting.clearRay(player,from,p)){result=p;best=d;}
            else if(d<=best)for(var corner:box.points){double distance=from.distanceToSqr(corner);if(distance<=best&&com.codex.blacklight.combat.CombatTargeting.clearRay(player,from,corner)){result=corner;best=distance;}}
        }return result;
    }
    private HelicopterSkinContact(){}
}
