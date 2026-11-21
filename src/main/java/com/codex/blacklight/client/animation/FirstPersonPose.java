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
    private record ArmSurface(Vector3f local,float x,float y,double penalty) {
        double score(double targetX,double targetY){
            double dx=x-targetX,dy=y-targetY;
            return dx*dx+dy*dy+penalty;
        }
    }
    /** Two camera-facing points on the transformed hand's own mesh. The limb skeleton can be below the screen
     * while its gauntlet or organic forearm is still visible. Select those surfaces for the settled tissue. */
    public static Vector3f[] consumeSurface(PowerForm form,boolean plated,boolean left,Matrix4f[] poses,Matrix4f camera,Matrix4f projection,
                                            float width,float talons,float hammer){
        var surfaces=WeaponMorphTiming.arm(form,left)?biological(form):plated?armor():List.<Surface>of();
        String suffix=left?"_L":"_R";
        var rig=BlacklightSkeleton.get();
        var towardsWrist=poses[rig.searchJointByName("Hand"+suffix).getId()].getTranslation(new Vector3f())
                .sub(poses[rig.searchJointByName("Arm"+suffix).getId()].getTranslation(new Vector3f()));
        if(towardsWrist.lengthSquared()<1e-7F)return null;
        towardsWrist.normalize();
        var visible=new ArrayList<ArmSurface>();
        ArmSurface focus=null;
        double bestScore=Double.POSITIVE_INFINITY;
        for(var surface:surfaces){
            if(!surface.bone.endsWith(suffix))continue;
            var local=new Vector3f(surface.point);
            if(form==PowerForm.CLAWS){local.x*=width;local.z*=width;if(surface.bone.startsWith("Hand_")&&local.y>.30F)local.y=.30F+(local.y-.30F)*talons;}
            if(form==PowerForm.HAMMERFISTS&&surface.bone.startsWith("Hand_"))local.mul(hammer);
            poses[rig.searchJointByName(surface.bone).getId()].transformPosition(local);
            var eye=camera.transformPosition(local,new Vector3f());
            if(eye.z>-.10F)continue;
            var screen=projection.transformProject(eye,new Vector3f());
            if(!Float.isFinite(screen.x+screen.y)||Math.abs(screen.x)>1.20||screen.y< -1.50||screen.y>.28)continue;
            var candidate=new ArmSurface(local,screen.x*(left?-1:1),screen.y,.095*(-eye.z)+(surface.distal?.09:0));
            visible.add(candidate);
            double score=candidate.score(.62,-.61);
            if(Math.abs(screen.x)<=1.12&&screen.y>=-1.17&&score<bestScore){bestScore=score;focus=candidate;}
        }
        if(focus==null)return null;
        ArmSurface other=null;
        bestScore=Double.POSITIVE_INFINITY;
        for(var candidate:visible){
            // Choose a second mesh vertex with useful separation along the *posed* anatomical axis.
            // A screen-space ordering alone can run towards the fingers when a weapon turns over.
            double along=candidate.local.dot(towardsWrist)-focus.local.dot(towardsWrist);
            double distance=candidate.local.distance(focus.local);
            if(Math.abs(along)<.035||distance>.46)continue;
            double score=candidate.score(.75,-.85)+4*Math.pow(distance-.22,2)+1.7*Math.max(0,.15-Math.abs(along));
            if(score<bestScore){other=candidate;bestScore=score;}
        }
        if(other==null)return null;
        return focus.local.dot(towardsWrist)>=other.local.dot(towardsWrist)
                ? new Vector3f[]{focus.local,other.local} : new Vector3f[]{other.local,focus.local};
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
