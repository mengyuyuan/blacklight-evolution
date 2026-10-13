package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.client.model.BladeShape;
import com.codex.blacklight.combat.LocalWeaponMotion;
import com.codex.blacklight.combat.HammerScales;
import com.codex.blacklight.power.PowerForm;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonPoseTest {
    private record Form(PowerForm form,boolean armor,boolean shield,String attack) {}
    private record Vertex(String bone,Vector3f point,boolean grip,boolean distal) {}
    private static final List<Form> FORMS=List.of(
            new Form(PowerForm.CLAWS,false,false,"claw_auto1"),
            new Form(PowerForm.HAMMERFISTS,false,false,"hammer_jab"),
            new Form(PowerForm.WHIPFIST,false,false,"whip_lash"),
            new Form(PowerForm.MUSCLE_MASS,false,false,"muscle_jab"),
            new Form(PowerForm.BLADE,false,false,"blade_cut"),
            new Form(PowerForm.NONE,true,false,"utility_armor_transform"),
            new Form(PowerForm.BLADE,true,false,"blade_cut"));
    private static final Map<Form,List<Vertex>> GEOMETRY=new HashMap<>();
    private static final Matrix4f PROJECTION=projection(1280F/900);

    private static Matrix4f projection(float aspect) {
        return new Matrix4f().perspective((float)Math.toRadians(70),aspect,.05F,100);
    }
    // Independent fixture from 1.21.1 ItemInHandRenderer.renderPlayerArm plus
    // PlayerRenderer.renderHand's prepared vanilla ModelPart. Production invokes
    // the native method; it never duplicates these camera transforms.
    private static Matrix4f nativeFrame(boolean left,float swing,float equip,boolean slim) {
        float side=left?-1:1,root=(float)Math.sqrt(swing),pi=(float)Math.PI;
        return new Matrix4f().translation(side*(-.3F*(float)Math.sin(root*pi)+.64000005F),
                .4F*(float)Math.sin(root*pi*2)-.6F-equip*.6F,-.4F*(float)Math.sin(swing*pi)-.71999997F)
                .rotateY((float)Math.toRadians(side*45))
                .rotateY((float)Math.toRadians(side*(float)Math.sin(root*pi)*70))
                .rotateZ((float)Math.toRadians(side*(float)Math.sin(swing*swing*pi)*-20))
                .translate(side*-1,3.6F,3.5F).rotateZ((float)Math.toRadians(side*120))
                .rotateX((float)Math.toRadians(200)).rotateY((float)Math.toRadians(side*-135)).translate(side*5.6F,0,0)
                .translate(-side*5F/16F,2F/16F,0).rotateZ(side*.1F)
                .translate(-side*(slim?.5F:1)/16F,-2F/16F,0);
    }
    private static boolean poweredSide(Form form,boolean left) {
        return left?form.armor||form.shield||form.form==PowerForm.CLAWS||form.form==PowerForm.HAMMERFISTS||form.form==PowerForm.MUSCLE_MASS:form.form!=PowerForm.NONE||form.armor;
    }
    private static FirstPersonPose.Result convert(Form form,Matrix4f[] source,Matrix4f frame,boolean left,float hammer) {
        return FirstPersonPose.convertArm(source,form.form,form.armor,form.shield,
                form.shield?NativeShieldGrip.sample("shield_run",0,1):new Matrix4f(),1,1,PROJECTION,frame,hammer,left);
    }
    @Test void nativeEmptyHandFramesDisplayAllPoweredFormsAndSkins() {
        var rig=BlacklightSkeleton.get();var source=rig.getPoseMatrices(new com.codex.blacklight.animation.BlacklightPose());
        for(var form:FORMS)for(boolean left:new boolean[]{false,true})if(poweredSide(form,left))for(boolean slim:new boolean[]{false,true}) {
            var frame=nativeFrame(left,0,0,slim);var view=convert(form,source,frame,left,1);
            projectGeometry(form,view,frame,left,geometry(form),true);
        }
    }
    @Test void bodyWorldPositionAndLookRotationCannotMoveNativeHands() {
        var rig=BlacklightSkeleton.get();
        for(var form:FORMS)for(boolean left:new boolean[]{false,true})if(poweredSide(form,left)) {
            var source=rig.getPoseMatrices(MotionLibrary.pose(form.attack,.25F,false));var frame=nativeFrame(left,.3F,0,false);
            var before=convert(form,source,frame,left,1);
            var root=new Matrix4f().translation(12,-7,30).rotateY(1.7F).rotateX(-1.1F).rotateZ(.35F);
            var moved=new Matrix4f[source.length];for(int i=0;i<source.length;i++)moved[i]=new Matrix4f(root).mul(source[i]);
            var after=convert(form,moved,frame,left,1);
            for(String bone:new String[]{"Arm_","Hand_","Tool_"}) {
                int id=rig.searchJointByName(bone+(left?"L":"R")).getId();
                assertTrue(before.matrices()[id].equals(after.matrices()[id],.0003F),"world body transform escaped native arm attachment: "+form+bone);
            }
            assertEquals(before.nearestDepth(),after.nearestDepth(),.0003,form.toString());
        }
    }
    @Test void authoredAttacksAndNativeSwingKeepActualMeshesAheadOfTheDepthPlane() {
        var rig=BlacklightSkeleton.get();
        for(var form:FORMS)for(boolean left:new boolean[]{false,true})if(poweredSide(form,left))for(float phase:new float[]{.1F,.3F,.6F,.9F}) {
            float seconds=MotionLibrary.duration(form.attack)*phase;
            var source=rig.getPoseMatrices(MotionLibrary.pose(form.attack,seconds,false));
            var frame=new Matrix4f().rotateX(.06F).rotateY(-.06F).mul(nativeFrame(left,phase,.15F,false));
            var view=convert(form,source,frame,left,1);
            projectGeometry(form,view,frame,left,geometry(form),false);
        }
    }
    @Test void mixedMorphBoundsProtectOutgoingFormsAndLiquidAtExtremeLookAngles() {
        var rig=BlacklightSkeleton.get();var source=rig.getPoseMatrices(new com.codex.blacklight.animation.BlacklightPose());
        var visible=List.of(PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST);
        var points=new ArrayList<Vertex>();
        for(var form:visible)points.addAll(geometry(new Form(form,false,false,"")));
        for(var target:List.of(PowerForm.BLADE,PowerForm.WHIPFIST))for(float look:new float[]{-60,0,60})for(float swing:new float[]{0,.3F,.7F}) {
            var frame=new Matrix4f().rotateX((float)Math.toRadians(look)).mul(nativeFrame(false,swing,0,false));
            var view=FirstPersonPose.convertArm(source,target,false,false,new Matrix4f(),1,1,PROJECTION,frame,1,false,visible,.045F);
            var frames=new HashMap<String,Matrix4f>();float near=Float.POSITIVE_INFINITY;
            for(var point:points)if(point.bone.endsWith("_R")) {
                var transform=frames.computeIfAbsent(point.bone,bone->new Matrix4f(frame).mul(view.matrices()[rig.searchJointByName(bone).getId()]));
                var cameraPoint=transform.transformPosition(new Vector3f(point.point));
                // Any bounded liquid displacement can point toward the camera.
                near=Math.min(near,-cameraPoint.z-.045F);
            }
            assertTrue(near>=.1498F,"outgoing liquid crosses depth plane: "+target+" look="+look+" swing="+swing+" depth="+near);
            for(var bone:rig.bones())if(bone.getName().endsWith("_L"))
                assertTrue(source[bone.getId()].equals(view.matrices()[bone.getId()],0),"right-arm depth protection moved the left anchor");
        }
    }
    @Test void singularStableBoundsKeepTheExistingNativeArmMatricesExactly() {
        var rig=BlacklightSkeleton.get();var source=rig.getPoseMatrices(new com.codex.blacklight.animation.BlacklightPose());
        for(var form:FORMS)for(boolean left:new boolean[]{false,true})if(poweredSide(form,left)) {
            var frame=nativeFrame(left,.35F,.2F,false);
            var old=convert(form,source,frame,left,1);
            var union=FirstPersonPose.convertArm(source,form.form,form.armor,form.shield,
                    form.shield?NativeShieldGrip.sample("shield_run",0,1):new Matrix4f(),1,1,PROJECTION,frame,1,left,List.of(form.form),0);
            for(int i=0;i<source.length;i++)assertTrue(old.matrices()[i].equals(union.matrices()[i],0),form+" stable native placement changed");
            assertEquals(old.nearestDepth(),union.nearestDepth(),0);
            assertEquals(old.maximumProjectedX(),union.maximumProjectedX(),0);
            assertEquals(old.maximumProjectedY(),union.maximumProjectedY(),0);
        }
    }
    private static void projectGeometry(Form form,FirstPersonPose.Result view,Matrix4f frame,boolean left,List<Vertex> vertices,boolean idle) {
        var rig=BlacklightSkeleton.get();float near=Float.POSITIVE_INFINITY,maxX=0,maxY=0;int count=0,visible=0;
        var grip=form.shield?NativeShieldGrip.sample("shield_run",0,1):new Matrix4f();
        for(var vertex:vertices)if(vertex.bone.endsWith(left?"_L":"_R")) {
            var matrix=new Matrix4f(frame).mul(view.matrices()[rig.searchJointByName(vertex.bone).getId()]);
            if(vertex.grip)matrix.mul(grip);
            var point=matrix.transformPosition(new Vector3f(vertex.point));var clip=PROJECTION.transform(new Vector4f(point,1));
            assertTrue(Float.isFinite(clip.w)&&clip.w>0,"mesh behind native camera: "+form);
            assertTrue(clip.z/clip.w>=-1.0001F&&clip.z/clip.w<=1.0001F,"mesh crosses native depth plane: "+form);
            near=Math.min(near,-point.z);count++;
            float x=clip.x/clip.w,y=clip.y/clip.w;if(Math.abs(x)<=1&&Math.abs(y)<=1)visible++;
            if(vertex.distal){maxX=Math.max(maxX,Math.abs(x));maxY=Math.max(maxY,Math.abs(y));}
        }
        assertTrue(count>0,"a powered arm must include geometry");assertTrue(near>=.1499F,"actual depth clearance: "+form+" "+near);
        if(idle)assertTrue(visible>0,"native idle mesh must be visible: "+form+" left="+left);
        assertEquals(near,view.nearestDepth(),.0003,form.toString());assertEquals(maxX,view.maximumProjectedX(),.0003,form.toString());assertEquals(maxY,view.maximumProjectedY(),.0003,form.toString());
    }

    private static List<Vertex> geometry(Form form) {
        return GEOMETRY.computeIfAbsent(form,ignored->{
            var points=new LinkedHashSet<Vertex>();
            if(form.form!=PowerForm.NONE) {
                var mesh=read(form.form==PowerForm.CLAWS?"claw_blender":"form_"+form.form.id());
                for(var entry:mesh.getAsJsonObject("joints").entrySet()) {
                    String bone=entry.getKey();
                    boolean tip=form.form==PowerForm.WHIPFIST&&bone.equals("Tip_R");
                    if(!tip&&!bone.startsWith("Arm_")&&!bone.startsWith("Hand_"))continue;
                    if(bone.endsWith("_L")&&(form.form==PowerForm.BLADE||form.form==PowerForm.WHIPFIST))continue;
                    for(var face:entry.getValue().getAsJsonArray())for(var value:face.getAsJsonObject().getAsJsonArray("p")) {
                        var p=vector(value.getAsJsonArray());
                        if(!mesh.has("approvedProportions")&&form.form==PowerForm.BLADE&&bone.equals("Hand_R")) {
                            p.x*=BladeShape.WIDTH;p.z*=BladeShape.WIDTH;p.y=BladeShape.length(p.y);
                        }
                        // Tip_R is rendered in the forearm's stack in BlacklightClawLayer.
                        points.add(new Vertex(tip?"Hand_R":bone,p,false,tip||bone.startsWith("Hand_")&&p.y>.30F));
                    }
                }
            }
            if(form.armor)for(var item:read("armor_block").getAsJsonArray("parts")) {
                var part=item.getAsJsonObject();String bone=part.get("joint").getAsString();
                if(bone.startsWith("Arm_")||bone.startsWith("Hand_"))
                    for(var point:part.getAsJsonArray("points"))points.add(new Vertex(bone,vector(point.getAsJsonArray()),false,bone.startsWith("Hand_")));
            }
            if(form.shield)for(var item:read("shield_block").getAsJsonArray("parts"))
                for(var point:item.getAsJsonObject().getAsJsonArray("points"))points.add(new Vertex("Tool_L",vector(point.getAsJsonArray()),true,true));
            return new ArrayList<>(points);
        });
    }
    private static Vector3f vector(JsonArray point) {
        return new Vector3f(point.get(0).getAsFloat(),point.get(1).getAsFloat(),point.get(2).getAsFloat());
    }
    private static JsonObject read(String name) {
        try(var stream=FirstPersonPoseTest.class.getResourceAsStream("/assets/blacklight/models/"+name+".json")) {
            assertNotNull(stream,"mesh resource "+name);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException exception){throw new IllegalStateException(exception);}
    }
}
