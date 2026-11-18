package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The authored nozzle locators use the same head/torso poses as the rendered mesh. */
public final class SpitterMouth {
    public record Socket(Vec3 throat,Vec3 lip) {}
    private record Bone(String name,Vec3 pivot) {}
    private record Rig(List<Bone> chain,Vec3 throat,Vec3 lip) {}
    private static final Rig RIG=load();
    private static Vec3 vector(JsonArray a){return new Vec3(-a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble()).scale(1/16.0);}
    private static Rig load(){
        try(var in=Objects.requireNonNull(SpitterMouth.class.getResourceAsStream("/assets/blacklight/geo/blacklight_spitter_mc.geo.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var bones=new HashMap<String,JsonObject>();
            for(var entry:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){
                var bone=entry.getAsJsonObject();bones.put(bone.get("name").getAsString(),bone);
            }
            var head=Objects.requireNonNull(bones.get("head"));var locators=head.getAsJsonObject("locators");
            var chain=new ArrayList<Bone>();var seen=new HashSet<String>();String name="head";
            while(name!=null){
                if(!seen.add(name))throw new IllegalStateException("Cyclic spitter rig");
                var b=Objects.requireNonNull(bones.get(name));
                if(b.has("rotation"))throw new IllegalStateException("Rest rotation requires socket adaptation");
                chain.add(new Bone(name,vector(b.getAsJsonArray("pivot"))));name=b.has("parent")?b.get("parent").getAsString():null;
            }
            Collections.reverse(chain);
            return new Rig(List.copyOf(chain),vector(locators.getAsJsonArray("throat")),vector(locators.getAsJsonArray("mouth")));
        }catch(Exception e){throw new IllegalStateException("Invalid spitter mouth rig",e);}
    }
    public static Socket sample(MutantMove move,float age,Vec3 origin,float yaw){
        if(!move.species.equals("SPITTER"))throw new IllegalArgumentException("Spitter move required");
        var table=CreaturePoseTable.SPITTER;var pose=table.sample(move.clip,age);var names=Arrays.asList(table.bones);
        var m=new Matrix4f().rotateY((float)Math.toRadians(180-yaw));
        for(var bone:RIG.chain){int i=names.indexOf(bone.name);if(i<0)continue;var p=bone.pivot;var v=pose[i];
            m.translate((float)p.x+v[3]/16,(float)p.y+v[4]/16,(float)p.z+v[5]/16)
                .rotateZYX(v[2],v[1],v[0]).scale(v[6],v[7],v[8]).translate((float)-p.x,(float)-p.y,(float)-p.z);
        }
        return new Socket(origin.add(point(m,RIG.throat)).add(0,.01,0),origin.add(point(m,RIG.lip)).add(0,.01,0));
    }
    private static Vec3 point(Matrix4f m,Vec3 local){var p=m.transformPosition(local.toVector3f());return new Vec3(p.x,p.y,p.z);}
    private SpitterMouth(){}
}
