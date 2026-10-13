package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.model.BladeShape;
import com.codex.blacklight.power.PowerForm;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Arm-local biological geometry attached after Minecraft's native hand transform. */
public final class FirstPersonPose {
    public record Result(Matrix4f[] matrices,float nearestDepth,float maximumProjectedX,float maximumProjectedY) { }
    private record Surface(String bone,Vector3f point,boolean distal) { }
    private static final Map<PowerForm,List<Surface>> BIO=new EnumMap<>(PowerForm.class);
    private static List<Surface> armor,shield;
    private FirstPersonPose() { }
    public static String idle(PowerForm form){return "basic_idle";}
    public static Result convertArm(Matrix4f[] source,PowerForm form,boolean plated,boolean shielded,Matrix4f shieldGrip,
                                   float width,float talons,Matrix4f projection,Matrix4f nativeArmFrame,float hammerScale,boolean left) {
        return convertArm(source,form,plated,shielded,shieldGrip,width,talons,projection,nativeArmFrame,hammerScale,left,List.of(form),0);
    }
    /** All still-visible forms share this native arm frame. The depth envelope
     * includes their outgoing geometry and bounded liquid motion, so choosing a
     * smaller target cannot expose an old fingertip to the near clipping plane. */
    public static Result convertArm(Matrix4f[] source,PowerForm form,boolean plated,boolean shielded,Matrix4f shieldGrip,
                                   float width,float talons,Matrix4f projection,Matrix4f nativeArmFrame,float hammerScale,boolean left,
                                   Collection<PowerForm> visibleBounds,float liquidMargin) {
        var rig=BlacklightSkeleton.get();String side=left?"L":"R";
        var neutral=rig.getPoseMatrices(new BlacklightPose());
        var neutralRoot=new Matrix4f(neutral[rig.rootJoint.getId()]).invert();
        var neutralArm=new Matrix4f(neutralRoot).mul(neutral[rig.searchJointByName("Arm_"+side).getId()]);
        // The original model frame is X-left/Y-down/Z-forward. Its product
        // with the anatomical arm bind keeps the same palm and thumb orientation.
        var basis=new Matrix4f().scaling(-1,-1,1).mul(new Matrix4f(neutralArm).setTranslation(0,0,0));
        var mapping=new Matrix4f(basis).mul(new Matrix4f(neutralArm).invert())
                .mul(new Matrix4f(source[rig.rootJoint.getId()]).invert());
        var result=new Matrix4f[source.length];for(int i=0;i<source.length;i++)result[i]=new Matrix4f(source[i]);
        for(var bone:rig.bones())if(bone.getName().endsWith("_"+side))result[bone.getId()]=new Matrix4f(mapping).mul(source[bone.getId()]);
        var vertices=new ArrayList<Vector3f>();var distal=new ArrayList<Boolean>();
        var bounds=EnumSet.of(form);if(visibleBounds!=null)for(var candidate:visibleBounds)if(candidate!=null)bounds.add(candidate);
        for(var boundedForm:bounds)if(WeaponMorphTiming.arm(boundedForm,left))
            for(var surface:biological(boundedForm))if(surface.bone.endsWith("_"+side)) {
                var p=new Vector3f(surface.point);
                if(boundedForm==PowerForm.CLAWS){p.x*=width;p.z*=width;if(surface.bone.startsWith("Hand_")&&p.y>.30F)p.y=.30F+(p.y-.30F)*talons;}
                var matrix=result[rig.searchJointByName(surface.bone).getId()];
                if(boundedForm==PowerForm.HAMMERFISTS&&surface.bone.startsWith("Hand_")) {
                    for(int corner=0;corner<8;corner++) {
                        var envelope=new Vector3f((corner&1)==0?p.x:p.x*hammerScale,(corner&2)==0?p.y:p.y*hammerScale,(corner&4)==0?p.z:p.z*hammerScale);
                        vertices.add(matrix.transformPosition(envelope));distal.add(surface.distal);
                    }
                }else{vertices.add(matrix.transformPosition(p));distal.add(surface.distal);}
            }
        if(plated)for(var surface:armor())if(surface.bone.endsWith("_"+side)){vertices.add(result[rig.searchJointByName(surface.bone).getId()].transformPosition(new Vector3f(surface.point)));distal.add(surface.distal);}
        if(left&&shielded){var grip=new Matrix4f(result[rig.searchJointByName("Tool_L").getId()]).mul(shieldGrip);for(var surface:shield()){vertices.add(grip.transformPosition(new Vector3f(surface.point)));distal.add(true);}}
        // Protect only the actual depth plane. Natural arm size and screen crops
        // remain those of Minecraft's native hand transform.
        float margin=Float.isFinite(liquidMargin)?Math.clamp(liquidMargin,0,.045F):0;
        float shift=0;for(var p:vertices){nativeArmFrame.transformPosition(p);shift=Math.max(shift,p.z+.15F+margin);}
        shift=Math.max(0,shift);
        var localShift=new Matrix4f(nativeArmFrame).invert().transformDirection(new Vector3f(0,0,-shift));
        for(var bone:rig.bones())if(bone.getName().endsWith("_"+side))result[bone.getId()]=new Matrix4f().translation(localShift).mul(result[bone.getId()]);
        float near=Float.POSITIVE_INFINITY,maxX=0,maxY=0;
        for(int i=0;i<vertices.size();i++){var p=vertices.get(i);p.z-=shift;near=Math.min(near,-p.z);if(distal.get(i)){maxX=Math.max(maxX,Math.abs(p.x*projection.m00()/-p.z));maxY=Math.max(maxY,Math.abs(p.y*projection.m11()/-p.z));}}
        return new Result(result,near,maxX,maxY);
    }
    private static JsonObject read(String name) {
        try(var in=FirstPersonPose.class.getResourceAsStream("/assets/blacklight/models/"+name+".json")) {
            if(in==null)throw new IllegalStateException("Missing viewmodel geometry: "+name);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read viewmodel geometry",e);}
    }
    private static Vector3f vector(JsonArray data){return new Vector3f(data.get(0).getAsFloat(),data.get(1).getAsFloat(),data.get(2).getAsFloat());}
    private static List<Surface> biological(PowerForm form) {
        return BIO.computeIfAbsent(form,ignored->{
            var mesh=read(form==PowerForm.CLAWS?"claw_blender":"form_"+form.id());var vertices=new LinkedHashSet<Surface>();
            for(var entry:mesh.getAsJsonObject("joints").entrySet()) {
                String bone=entry.getKey();if(bone.equals("Tip_R"))bone="Hand_R";if(!bone.startsWith("Arm_")&&!bone.startsWith("Hand_"))continue;
                for(var face:entry.getValue().getAsJsonArray())for(var vertex:face.getAsJsonObject().getAsJsonArray("p")) {
                    var p=vector(vertex.getAsJsonArray());if(!mesh.has("approvedProportions")&&form==PowerForm.BLADE&&bone.equals("Hand_R")){p.x*=BladeShape.WIDTH;p.z*=BladeShape.WIDTH;p.y=BladeShape.length(p.y);}
                    vertices.add(new Surface(bone,p,entry.getKey().equals("Tip_R")||bone.startsWith("Hand_")&&p.y>.30F));
                }
            }
            return List.copyOf(vertices);
        });
    }
    private static List<Surface> armor() {
        if(armor==null){var points=new ArrayList<Surface>();for(var item:read("armor_block").getAsJsonArray("parts")){var part=item.getAsJsonObject();String bone=part.get("joint").getAsString();if(bone.startsWith("Arm_")||bone.startsWith("Hand_"))for(var p:part.getAsJsonArray("points"))points.add(new Surface(bone,vector(p.getAsJsonArray()),bone.startsWith("Hand_")));}armor=List.copyOf(points);}
        return armor;
    }
    private static List<Surface> shield() {
        if(shield==null){var points=new ArrayList<Surface>();for(var item:read("shield_block").getAsJsonArray("parts"))for(var p:item.getAsJsonObject().getAsJsonArray("points"))points.add(new Surface("Tool_L",vector(p.getAsJsonArray()),true));shield=List.copyOf(points);}
        return shield;
    }
}
