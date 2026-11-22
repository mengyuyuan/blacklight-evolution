package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Receiving surfaces come from the existing authored geometry and the same pose as the renderer. */
public final class GreeneContact {
    private record Cube(String bone,int index,Vec3 center,Vec3 half,Matrix4f local){}
    public record Surface(String bone,HydraContact.Box box){}
    /** Articulated Slug leaves still share one of the three native health
     * children. Geometry follows each actual joint; damage uses its child ID. */
    private static String region(String bone){return bone.startsWith("weak_")?bone.substring(0,6):bone;}
    private static final List<Cube> CUBES=load();
    private static Vec3 vector(JsonObject o,String key){var a=o.getAsJsonArray(key);return a==null?Vec3.ZERO:new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    private static List<Cube> load(){
        try(var in=Objects.requireNonNull(GreeneContact.class.getResourceAsStream("/assets/blacklight/geo/elizabeth_greene_mc.geo.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var result=new ArrayList<Cube>();var names=Arrays.asList(GreeneMotion.table().bones);
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var bone=value.getAsJsonObject();String name=bone.get("name").getAsString();int index=names.indexOf(name);if(!bone.has("cubes"))continue;
                for(var c:bone.getAsJsonArray("cubes")){if(index<0)throw new IllegalStateException("Unmapped Greene bone "+name);var cube=c.getAsJsonObject();
                    var o=vector(cube,"origin");var s=vector(cube,"size");var p=vector(cube,"pivot").multiply(-1,1,1).scale(1/16d);var v=vector(cube,"rotation");
                    double inflate=cube.has("inflate")?cube.get("inflate").getAsDouble()/16:0;
                    var local=new Matrix4f().translate((float)p.x,(float)p.y,(float)p.z).rotateZYX((float)Math.toRadians(v.z),(float)Math.toRadians(-v.y),(float)Math.toRadians(-v.x)).translate((float)-p.x,(float)-p.y,(float)-p.z);
                    result.add(new Cube(name,index,o.add(s.scale(.5)).multiply(-1,1,1).scale(1/16d),s.scale(1/32d).add(inflate,inflate,inflate),local));
                }
            }return List.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Invalid Greene receiving mesh",e);}
    }
    public static List<String> regionBones(){var result=new ArrayList<>(List.of("weak_0","weak_1","weak_2"));CUBES.stream().map(Cube::bone).filter(n->!n.startsWith("weak_")).distinct().forEach(result::add);return List.copyOf(result);}
    public static List<Surface> surfaces(float[][] pose,Vec3 origin,float yaw,int broken){
        return surfaces(pose,origin,yaw,broken,null,-1);
    }
    private static List<Surface> surfaces(float[][] pose,Vec3 origin,float yaw,int broken,GreeneForm form,int strikingSlot){
        var transforms=GreeneMotion.table().transforms(pose);var base=new Matrix4f().rotateY((float)Math.toRadians(180-yaw));var result=new ArrayList<Surface>();
        for(var cube:CUBES){String region=region(cube.bone);if(form!=null&&(!region.startsWith("weak_") || region.charAt(5)-'0'!=strikingSlot))continue;if(region.startsWith("weak_")&&(broken&(1<<Integer.parseInt(region.substring(5))))!=0)continue;
            var matrix=new Matrix4f(base).mul(transforms[cube.index]).mul(cube.local);var center=matrix.transformPosition(cube.center.toVector3f());var axes=new Vec3[3];double[] scale=new double[3];
            for(int i=0;i<3;i++){var v=matrix.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0));scale[i]=v.length();v.normalize();axes[i]=new Vec3(v.x,v.y,v.z);}
            result.add(new Surface(region,HydraContact.affine(origin.add(center.x,center.y+.01,center.z),cube.half.multiply(scale[0],scale[1],scale[2]),axes[0],axes[1],axes[2])));
        }
        if(form!=null)form.addSurfaces(result,transforms,yaw,origin,broken);
        return result;
    }
    public static List<HydraContact.Region> regions(float[][] pose,Vec3 origin,float yaw,int broken){
        return regions(pose,origin,yaw,broken,null,-1);
    }
    public static List<HydraContact.Region> regions(float[][] pose,Vec3 origin,float yaw,int broken,GreeneForm form,int strikingSlot){
        var surfaces=surfaces(pose,origin,yaw,broken,form,strikingSlot);var result=new ArrayList<HydraContact.Region>();
        for(String bone:regionBones()){var boxes=surfaces.stream().filter(s->s.bone.equals(bone)).map(Surface::box).toList();
            AABB bounds=new AABB(origin,origin);if(!boxes.isEmpty()){bounds=boxes.get(0).bounds;for(var box:boxes)bounds=bounds.minmax(box.bounds);}
            result.add(new HydraContact.Region(bone,boxes,bounds));
        }return List.copyOf(result);
    }

    /** Aim into a visible patch of the requested Slug. A nearest corner can
     * graze past its skin, or sit behind the Mother's own plated torso. */
    static Vec3 focus(List<HydraContact.Region> skin,int slot,net.minecraft.world.entity.player.Player player,double range){
        var limb=skin.get(slot);
        var eye=player.getEyePosition();
        var boxes=new ArrayList<>(limb.boxes());
        boxes.sort(Comparator.comparingDouble(b->b.nearest(eye).distanceToSqr(eye)));
        Vec3 best=null;
        double limit=range*range+1e-6;
        for(var box:boxes){
            var near=box.nearest(eye);
            if(near.distanceToSqr(eye)>limit)break;
            // Use the closest reachable visible surface for both lock and reach.
            // Moving a little inside the patch also avoids a tangent-only pick.
            var hit=visiblePatch(skin,limb,player,eye,near.lerp(box.center,.12));
            if(hit==null)hit=visiblePatch(skin,limb,player,eye,box.center);
            if(hit!=null&&eye.distanceToSqr(hit)<=limit){
                best=hit;limit=eye.distanceToSqr(hit);continue;
            }
            for(var corner:box.points){
                hit=visiblePatch(skin,limb,player,eye,corner.lerp(box.center,.18));
                if(hit!=null&&eye.distanceToSqr(hit)<=limit){
                    best=hit;limit=eye.distanceToSqr(hit);
                }
            }
        }
        return best;
    }
    private static Vec3 visiblePatch(List<HydraContact.Region> skin,HydraContact.Region limb,
                                     net.minecraft.world.entity.player.Player player,Vec3 eye,Vec3 inside){
        var hit=HydraHurtContact.ray(skin,eye,inside,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE);
        if(hit==null||!hit.bone().equals(limb.bone())||!CombatTargeting.clearRay(player,eye,hit.point()))return null;
        // Inside the moving arm, its first contact is exactly the player's eye.
        // Keep a forward direction for the crosshair, reach ray and weapon arc.
        return hit.point().distanceToSqr(eye)<1e-10?inside:hit.point();
    }
    private GreeneContact(){}
}
