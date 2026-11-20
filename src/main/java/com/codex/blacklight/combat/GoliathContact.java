package com.codex.blacklight.combat;

import com.google.gson.*;
import com.codex.blacklight.entity.EncounterMob;
import net.minecraft.world.entity.player.Player;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Authored skin cuboids use exactly the same rebound source pose as rendering. */
public final class GoliathContact {
    private record Cube(String bone,int index,Vec3 center,Vec3 half,Matrix4f local) {}
    private static final List<Cube> CUBES=load();
    private GoliathContact() {}
    private static Vec3 vector(JsonObject o,String key){var a=o.getAsJsonArray(key);return a==null?Vec3.ZERO:new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static List<Cube> load(){
        try(var in=Objects.requireNonNull(GoliathContact.class.getResourceAsStream("/assets/blacklight/geo/goliath_mc.geo.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var result=new ArrayList<Cube>();var names=Arrays.asList(GoliathMotion.table().bones);var bones=new LinkedHashMap<String,JsonObject>();
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var bone=value.getAsJsonObject();bones.put(bone.get("name").getAsString(),bone);
            }
            for(var entry:bones.entrySet()){
                String owner=entry.getKey(),mapped=owner;var bone=entry.getValue();if(!bone.has("cubes"))continue;
                while(!names.contains(mapped)){
                    var ancestor=bones.get(mapped);if(ancestor==null||!ancestor.has("parent"))throw new IllegalStateException("Unmapped Goliath skin "+owner);
                    // These authored fingers/plates have no independent transform.
                    // A pivot with zero rotation/translation cancels exactly. Do not
                    // silently drop a future non-identity bind under its ancestor.
                    if(vector(ancestor,"rotation").lengthSqr()>0||vector(ancestor,"position").lengthSqr()>0||ancestor.has("scale"))throw new IllegalStateException("Unmapped Goliath bind transform "+mapped);
                    mapped=ancestor.get("parent").getAsString();
                }
                int index=names.indexOf(mapped);
                for(var item:bone.getAsJsonArray("cubes")){
                    var cube=item.getAsJsonObject();var o=vector(cube,"origin");var size=vector(cube,"size");var p=vector(cube,"pivot").multiply(-1,1,1).scale(1/16d);var r=vector(cube,"rotation");
                    double inflate=cube.has("inflate")?cube.get("inflate").getAsDouble()/16:0;
                    var local=new Matrix4f().translate((float)p.x,(float)p.y,(float)p.z).rotateZYX((float)Math.toRadians(r.z),(float)Math.toRadians(-r.y),(float)Math.toRadians(-r.x)).translate((float)-p.x,(float)-p.y,(float)-p.z);
                    result.add(new Cube(owner,index,o.add(size.scale(.5)).multiply(-1,1,1).scale(1/16d),size.scale(1/32d).add(inflate,inflate,inflate),local));
                }
            }
            return List.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Invalid rebound Goliath skin",e);}
    }
    private static HydraContact.Box worldBox(Cube cube, Matrix4f[] transforms, Matrix4f base, Vec3 origin) {
        var matrix = new Matrix4f(base).mul(transforms[cube.index]).mul(cube.local);
        var center = matrix.transformPosition(cube.center.toVector3f());
        var axes = new Vec3[3];
        var lengths = new double[3];
        for (int i = 0; i < 3; i++) {
            var axis = matrix.transformDirection(new Vector3f(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
            lengths[i] = axis.length();
            axis.normalize();
            axes[i] = new Vec3(axis.x, axis.y, axis.z);
        }
        return HydraContact.affine(origin.add(center.x, center.y + .01, center.z),
                cube.half.multiply(lengths[0], lengths[1], lengths[2]), axes[0], axes[1], axes[2]);
    }

    record LockSurface(Vec3 joint, List<HydraContact.Box> boxes) {
        // Aim just inside the closest skin to the joint, so rotating the camera
        // does not slide the marker down the limb. Try exposed skin around cover.
        Vec3 focus(Player player, double range) {
            var eye = player.getEyePosition();
            double limit = range * range + 1e-6;
            var preferred = boxes.stream().map(box -> box.nearest(joint).lerp(box.center, .06))
                    .sorted(Comparator.comparingDouble(joint::distanceToSqr)).toList();
            for (var point : preferred)
                if (eye.distanceToSqr(point) <= limit && CombatTargeting.clearRay(player, eye, point)) return point;
            Vec3 best = null;
            double nearest = Double.POSITIVE_INFINITY;
            for (var box : boxes) {
                var p = box.nearest(eye);
                if (eye.distanceToSqr(p) > limit) continue;
                if (joint.distanceToSqr(p) < nearest && CombatTargeting.clearRay(player, eye, p)) {
                    best = p;
                    nearest = joint.distanceToSqr(p);
                }
                for (var point : box.points) {
                    double distance = joint.distanceToSqr(point);
                    if (distance < nearest && eye.distanceToSqr(point) <= limit && CombatTargeting.clearRay(player, eye, point)) {
                        best = point;
                        nearest = distance;
                    }
                }
            }
            return best;
        }
    }

    static LockSurface lockSurface(EncounterMob mob, GoliathLockPart part, float partial) {
        var table = GoliathMotion.table();
        var transforms = table.transforms(GoliathMotion.pose(mob, partial));
        Vec3 origin = mob.skinOrigin(partial);
        float yaw = mob.skinYaw(partial);
        var base = new Matrix4f().scale(GoliathPresentation.SCALE).rotateY((float)Math.toRadians(180 - yaw));
        int index = Arrays.asList(table.bones).indexOf(part.joint);
        var pivot = table.pivots[index];
        var at = new Matrix4f(base).mul(transforms[index])
                .transformPosition(new Vector3f(pivot[0], pivot[1], pivot[2]).div(16));
        var boxes = new ArrayList<HydraContact.Box>();
        for (var cube : CUBES)
            if (part.available(mob) && GoliathLockPart.forSkin(cube.bone) == part)
                boxes.add(worldBox(cube, transforms, base, origin));
        return new LockSurface(origin.add(at.x, at.y + .01, at.z), List.copyOf(boxes));
    }

    /** Only the striking hand and claws are needed while sweeping the planted-arm attack. */
    public static List<HydraContact.Box> spike(EncounterMob mob,float partial){
        var transforms=GoliathMotion.table().transforms(GoliathMotion.pose(mob,partial));
        var base=new Matrix4f().scale(GoliathPresentation.SCALE).rotateY((float)Math.toRadians(180-mob.skinYaw(partial)));
        var origin=mob.skinOrigin(partial);var boxes=new ArrayList<HydraContact.Box>();
        for(var cube:CUBES)if(cube.bone.equals("left_hand")||cube.bone.startsWith("left_finger_"))
            boxes.add(worldBox(cube,transforms,base,origin));
        return boxes;
    }

    /** Stable order is shared by both loaders and both sides of the spawn packet. */
    private static final List<String> REGION_BONES = buildRegionBones();
    public static List<String> regionBones(){return REGION_BONES;}
    private static List<String> buildRegionBones(){var result=new ArrayList<>(List.of("weak_0","weak_1"));CUBES.stream().map(Cube::bone).filter(n->!n.startsWith("weak_")).distinct().forEach(result::add);return List.copyOf(result);}
    public static List<HydraContact.Region> regions(float[][] pose,Vec3 origin,float yaw,int broken){
        return regions(pose,origin,yaw,broken,1F);
    }
    public static List<HydraContact.Region> regions(float[][] pose,Vec3 origin,float yaw,int broken,float scale){
        var transforms=GoliathMotion.table().transforms(pose);var base=new Matrix4f().scale(scale).rotateY((float)Math.toRadians(180-yaw));
        var byBone=new LinkedHashMap<String,List<HydraContact.Box>>();for(String bone:regionBones())byBone.put(bone,new ArrayList<>());
        for(var cube:CUBES){
            if(GoliathLockPart.severed(cube.bone,broken))continue;
            byBone.get(cube.bone).add(worldBox(cube, transforms, base, origin));
        }
        var result=new ArrayList<HydraContact.Region>();
        for(var entry:byBone.entrySet()){var boxes=List.copyOf(entry.getValue());AABB bounds=new AABB(origin,origin);if(!boxes.isEmpty()){bounds=boxes.get(0).bounds;for(var box:boxes)bounds=bounds.minmax(box.bounds);}result.add(new HydraContact.Region(entry.getKey(),boxes,bounds));}
        return List.copyOf(result);
    }
}
