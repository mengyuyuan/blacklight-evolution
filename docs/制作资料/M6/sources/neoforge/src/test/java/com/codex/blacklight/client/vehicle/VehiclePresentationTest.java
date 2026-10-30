package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.model.BlackwatchModel;
import com.codex.blacklight.client.model.MilitaryVehicleModel;
import com.codex.blacklight.military.vehicle.VehicleCatalog;
import com.codex.blacklight.mixin.client.PrototypeCameraAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehiclePresentationTest {
    private static final List<List<Vec3>> CAPTURES=new ArrayList<>();
    private static final com.google.gson.JsonArray CLEARANCES=new com.google.gson.JsonArray();
    private static void clearance(String model,String chassis,String seat,float yaw,float pitch,double top,double roof) {
        var row=new com.google.gson.JsonObject();row.addProperty("model",model);row.addProperty("chassis",chassis);row.addProperty("seat",seat);
        row.addProperty("headYaw",yaw);row.addProperty("headPitch",pitch);row.addProperty("top",top);row.addProperty("roof",roof);row.addProperty("clearance",roof-top);CLEARANCES.add(row);
    }
    static List<Vec3> vertices(ModelPart part,PoseStack stack) {
        var result=new ArrayList<Vec3>();
        var out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11)
                result.add(new Vec3(((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue()));
            if(method.getReturnType()==void.class)return null;
            if(method.getReturnType()==int.class)return 0;
            if(method.getReturnType()==boolean.class)return false;
            return proxy;
        });
        part.render(stack,out,15728880,0);assertFalse(result.isEmpty(),"must run real ModelPart vertices");CAPTURES.add(result);return result;
    }
    static PlayerModel<?> player(boolean slim) {
        return new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
    }
    static PoseStack playerFrame(Vec3 seat) {
        var stack=new PoseStack();stack.translate(seat.x,seat.y,seat.z);
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));stack.scale(-.9375F,-.9375F,.9375F);stack.translate(0,-1.501,0);
        return stack;
    }
    @Test void actualWideAndSlimPlayerLegsUseTheNpcSeatBaselineWithoutScalingOrHiding() {
        for(boolean slim:new boolean[]{false,true})for(boolean driver:new boolean[]{false,true}) {
            var model=player(slim);VehicleSeatPose.apply(model,driver,0,0);
            var npcRoot=BlackwatchModel.layer(false,true).bakeRoot();var npc=new BlackwatchModel(npcRoot,true);npc.applyVehicleSeatPose(driver);
            assertEquals(npc.rightLeg.xRot,model.rightLeg.xRot,1e-6,"Player must sit like the actual NPC, not stand inside the hull");
            assertEquals(npc.leftLeg.yRot,model.leftLeg.yRot,1e-6);
            for(var part:List.of(model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg,model.hat,model.jacket)) {
                assertTrue(part.visible,"seating must not hide Player");assertEquals(1,part.xScale);assertEquals(1,part.yScale);assertEquals(1,part.zScale);
            }
            var legs=vertices(model.rightLeg,playerFrame(Vec3.ZERO));
            double vertical=legs.stream().mapToDouble(v->v.y).max().orElseThrow()-legs.stream().mapToDouble(v->v.y).min().orElseThrow();
            assertTrue(vertical<.3,"real Player vertices must form forward seated legs, height="+vertical);
            assertEquals(model.rightArm.xRot,model.rightSleeve.xRot,1e-6,"custom skin sleeve must follow seated arm");
        }
    }
    @Test void actualPlayerHeadAndHatStayBelowEveryAuthoredRoofAtTheRealEntitySeats() {
        var failures=new ArrayList<String>();
        for(String chassis:List.of("truck","apc","tank"))for(var location:VehicleCatalog.loadDefault().require(chassis).seats())
        for(boolean slim:new boolean[]{false,true})for(float yaw:new float[]{-75,0,75})for(float pitch:new float[]{-45,0,45}) {
            var vehicle=new MilitaryVehicleModel(chassis);double roof=roofHeight(vehicle,chassis,location.id());
            var model=player(slim);VehicleSeatPose.apply(model,location.id().equals("driver"),yaw,pitch,VehicleSeatPose.drop(VehicleCatalog.loadDefault().require(chassis)));
            var seat=vehicle.anchorWorld(location.anchorId(),Vec3.ZERO,0);
            for(var part:List.of(model.head,model.hat)) {
                double top=vertices(part,playerFrame(seat)).stream().mapToDouble(v->v.y).max().orElseThrow();
                clearance("vanilla-"+(slim?"slim":"wide")+(part==model.hat?"-hat":"-head"),chassis,location.id(),yaw,pitch,top,roof);
                if(top>=roof-.01)failures.add(chassis+" "+location.id()+" slim="+slim+" top="+top+" actualRoof="+roof);
            }
        }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
    private static double roofHeight(MilitaryVehicleModel vehicle,String chassis,String seat) {
        String path=chassis.equals("truck")?(seat.startsWith("passenger")?"body/canopy_top":"body/cab_roof"):
                seat.equals("gunner")||seat.equals("commander")?"body/turret_yaw/turret_top":
                chassis.equals("apc")?(seat.equals("driver")?"body/glacis_step_3":"body/upper_hull"):"body/upper_deck";
        var stack=new PoseStack();var part=vehicle.root();
        for(String name:path.split("/")){part.translateAndRotate(stack);part=part.getChild(name);}
        var points=vertices(part,stack);
        return chassis.equals("truck")?points.stream().mapToDouble(v->v.y).min().orElseThrow():points.stream().mapToDouble(v->v.y).max().orElseThrow();
    }
    @Test void sharedPlayerModelRestoresItsPoseAfterTheMountedDrawIncludingSkinLayers() {
        var model=player(false);model.rightLeg.y=12;model.rightSleeve.xRot=.3F;model.crouching=true;model.attackTime=.8F;
        try(var saved=VehicleSeatPose.save(model)){VehicleSeatPose.apply(model,true,20,30,5);assertEquals(16,model.rightLeg.y);}
        assertEquals(12,model.rightLeg.y);assertEquals(.3F,model.rightSleeve.xRot);assertTrue(model.crouching);assertEquals(.8F,model.attackTime);
    }
    @Test void realCameraUsesTheAuthoredObservationMountInsteadOfStandingEyes() {
        for(String chassis:List.of("truck","apc","tank")) {
            var def=VehicleCatalog.loadDefault().require(chassis);var origin=new Vec3(17,70,-13);
            for(var seat:def.seats()) {
            var frame=VehicleCamera.view(def,seat.id(),origin,40,70,-10,-40,0,true,false);
            var camera=new TestCamera();VehicleCamera.place(camera,frame);
            assertEquals(frame.position(),camera.getPosition(),"real MC Camera receives the production placement");
            var mount=def.anchors().get("camera_"+seat.id());assertNotNull(mount,"must use a same-source physical observation mount for "+chassis+" "+seat.id());
            var expected=def.mountWorld("camera_"+seat.id(),new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(origin.x,origin.y,origin.z),40,70,-10);
            assertEquals(expected.x(),camera.getPosition().x,1e-6);assertEquals(expected.y(),camera.getPosition().y,1e-6);assertEquals(expected.z(),camera.getPosition().z,1e-6);
            assertEquals(!chassis.equals("truck"),frame.enclosed());
            assertTrue(frame.eye().distanceTo(frame.position())<2.5,"optic remains local to the real seated eye");
            }
        }
    }
    @Test void f5BothDirectionsShowALargerVehicleWithoutClippingActualMeshVertices() {
        for(String chassis:List.of("truck","apc","tank"))for(boolean reverse:new boolean[]{false,true}) {
            var def=VehicleCatalog.loadDefault().require(chassis);
            var frame=VehicleCamera.view(def,"driver",Vec3.ZERO,0,90,-25,0,0,false,reverse);
            var camera=new TestCamera();VehicleCamera.place(camera,frame);
            double distance=camera.getPosition().subtract(frame.pivot()).length();
            assertTrue(distance>Math.hypot(def.width(),def.length())/2,"camera stays outside the vehicle");
            assertTrue(distance<9,"the real GL vehicle was too small at the old >10m distance: "+chassis+" distance="+distance);
            assertTrue(camera.getPosition().y>def.height(),"F5 camera must clear the turret at horizontal input pitch");
            assertEquals(reverse?180:0,Math.abs(camera.getYRot()),1e-6);
            var forward=Vec3.directionFromRotation(camera.getXRot(),camera.getYRot());
            var right=forward.cross(new Vec3(0,1,0)).normalize();var up=right.cross(forward).normalize();
            for(float bodyYaw:new float[]{0,45,90}) {
                var model=new MilitaryVehicleModel(chassis);model.pose(90,-25,0);
                var stack=new PoseStack();stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyYaw));
                for(var point:vertices(model.root(),stack)) {
                    var delta=point.subtract(camera.getPosition());double depth=delta.dot(forward);
                    assertTrue(depth>.05,"vehicle must stay beyond camera near plane");
                    double vertical=delta.dot(up)/(depth*Math.tan(Math.toRadians(35)));
                    double horizontal=delta.dot(right)/(depth*Math.tan(Math.toRadians(35))*1.6);
                    assertTrue(Math.abs(horizontal)<.98&&Math.abs(vertical)<.98,"70 degree / 16:10 frustum crops "+chassis+" reverse="+reverse+" body="+bodyYaw+" projected="+horizontal+","+vertical);
                }
            }
        }
    }
    @Test void nearPlaneAndObservationPathRetractBeforeActualMinecraftWallShapes() {
        var wall=new net.minecraft.world.phys.AABB(-5,-5,1,5,5,2);
        var clipped=VehicleCamera.clear(Vec3.ZERO,new Vec3(0,0,3),(from,to)->wall.clip(from,to).orElse(null));
        assertTrue(clipped.z<.84,"camera near plane stays before world wall: "+clipped);
        var unchanged=VehicleCamera.clear(Vec3.ZERO,new Vec3(0,0,.5),(from,to)->wall.clip(from,to).orElse(null));
        assertEquals(.5,unchanged.z,1e-6,"clear local optical path is not moved arbitrarily");
    }
    @Test void actualRenderedVehicleFacesLeaveAUsableObservationFieldClear() {
        var failures=new ArrayList<String>();
        for(String chassis:List.of("truck","apc","tank"))for(var location:VehicleCatalog.loadDefault().require(chassis).seats())
        for(float[] angles:new float[][]{{0,0,0},{43,65,-10},{-57,-75,15}}) {
            String seat=location.id();
            var model=new MilitaryVehicleModel(chassis);var def=VehicleCatalog.loadDefault().require(chassis);
            model.pose(angles[1],angles[2],0);var stack=new PoseStack();stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angles[0]));
            var points=new ArrayList<Vec3>();var normals=new ArrayList<Vec3>();
            var out=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
                if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11) {
                    points.add(new Vec3(((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue()));
                    int end=args.length;normals.add(new Vec3(((Number)args[end-3]).doubleValue(),((Number)args[end-2]).doubleValue(),((Number)args[end-1]).doubleValue()));
                }
                if(method.getReturnType()==void.class)return null;if(method.getReturnType()==int.class)return 0;if(method.getReturnType()==boolean.class)return false;return proxy;
            });
            var interior=new VehicleViewRendering(model,chassis);
            float yaw=-angles[0]+(chassis.equals("truck")&&seat.startsWith("passenger")?180:location.id().equals("gunner")||location.id().equals("commander")?-angles[1]:0);
            var view=VehicleCamera.view(def,seat,Vec3.ZERO,angles[0],angles[1],angles[2],yaw,0,true,false);
            var localView=VehicleCamera.view(def,seat,Vec3.ZERO,0,angles[1],angles[2],yaw+angles[0],0,true,false);
            interior.render(stack,out,15728880,0,true,localView);
            for(float horizontal:new float[]{-12,0,12})for(float vertical:new float[]{-8,0}) {
            var direction=Vec3.directionFromRotation(view.pitch()+vertical,view.yaw()+horizontal);
            double nearest=Double.POSITIVE_INFINITY;
            for(int i=0;i<points.size();i+=4)if(normals.get(i).dot(direction)<-1e-5) {
                nearest=Math.min(nearest,ray(view.position(),direction,points.get(i),points.get(i+1),points.get(i+2)));
                nearest=Math.min(nearest,ray(view.position(),direction,points.get(i),points.get(i+2),points.get(i+3)));
            }
            if(nearest<=4)failures.add(chassis+" "+seat+" body/turret/pitch="+java.util.Arrays.toString(angles)+" ray="+horizontal+","+vertical+" camera="+view.position()+" ownMeshDistance="+nearest);
            }
        }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
    @Test void firstPersonOpticCutRestoresEveryF5FaceAndPreservesDistantOwnGeometry() {
        for(String chassis:List.of("truck","apc","tank")) {
            var model=new MilitaryVehicleModel(chassis);model.pose(37,-9,0);var interior=new VehicleViewRendering(model,chassis);
            var before=vertices(model.root(),new PoseStack());var local=new ArrayList<Vec3>();
            var recorder=recorder(local);
            var optic=model.anchorWorld("camera_driver",Vec3.ZERO,0);
            var view=VehicleCamera.view(VehicleCatalog.loadDefault().require(chassis),"driver",Vec3.ZERO,0,37,-9,0,0,true,false);
            interior.render(new PoseStack(),recorder,15728880,0,true,view);
            assertFalse(local.isEmpty(),"first person keeps the vehicle draw");
            assertTrue(local.stream().anyMatch(v->v.distanceTo(optic)>3),"first person retains distant own-vehicle faces");
            var after=new ArrayList<Vec3>();interior.render(new PoseStack(),recorder(after),15728880,0,false,view);
            assertEquals(before,after,"F5 after an optic pass restores every original own-model vertex");
            model.root().getAllParts().forEach(part->assertFalse(part.skipDraw,"no own-view visibility leaks to other draws"));
        }
    }
    private static VertexConsumer recorder(List<Vec3> result) {
        return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=3) {
                if(args[0] instanceof Number)result.add(new Vec3(((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue()));
                else if(args[0] instanceof org.joml.Matrix4f matrix&&args.length==4) {
                    var p=matrix.transformPosition(new org.joml.Vector3f(((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()));
                    result.add(new Vec3(p.x,p.y,p.z));
                }
            }
            if(method.getReturnType()==void.class)return null;if(method.getReturnType()==int.class)return 0;if(method.getReturnType()==boolean.class)return false;return proxy;
        });
    }
    @Test void actualBlacklightWideAndSlimSkinDrawsStaySeatedBelowEveryRoof() throws Exception {
        for(boolean slim:new boolean[]{false,true})for(String chassis:List.of("truck","apc","tank")) {
            var vehicle=new MilitaryVehicleModel(chassis);
            var nativeModel=new com.codex.blacklight.client.model.BlacklightPlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
            for(var seat:VehicleCatalog.loadDefault().require(chassis).seats())for(float yaw:new float[]{-75,0,75})for(float pitch:new float[]{-45,0,45}) {
                nativeModel.pose(VehicleSeatPose.skeleton(seat.id().equals("driver"),yaw,pitch,VehicleSeatPose.drop(VehicleCatalog.loadDefault().require(chassis))));
                var output=new ArrayList<Vec3>();var stack=playerFrame(vehicle.anchorWorld(seat.anchorId(),Vec3.ZERO,0));
                for(var method:nativeModel.getClass().getDeclaredMethods())if(method.getName().equals("renderToBuffer")) {
                    if(method.getParameterCount()==5)method.invoke(nativeModel,stack,recorder(output),15728880,0,-1);
                    else method.invoke(nativeModel,stack,recorder(output),15728880,0,1F,1F,1F,1F);
                }
                assertFalse(output.isEmpty(),"must execute native weighted skin VertexConsumer emissions");CAPTURES.add(output);
                double top=output.stream().mapToDouble(v->v.y).max().orElseThrow();
                double roof=roofHeight(vehicle,chassis,seat.id());clearance("BlacklightPlayerModel-"+(slim?"slim":"wide"),chassis,seat.id(),yaw,pitch,top,roof);
                assertTrue(top<roof-.01,chassis+" "+seat.id()+" native skin top="+top);
            }
        }
    }
    private static double ray(Vec3 origin,Vec3 direction,Vec3 a,Vec3 b,Vec3 c) {
        var e=b.subtract(a);var f=c.subtract(a);var p=direction.cross(f);double det=e.dot(p);
        if(Math.abs(det)<1e-8)return Double.POSITIVE_INFINITY;
        var t=origin.subtract(a);double u=t.dot(p)/det;if(u<0||u>1)return Double.POSITIVE_INFINITY;
        var q=t.cross(e);double v=direction.dot(q)/det;if(v<0||u+v>1)return Double.POSITIVE_INFINITY;
        double distance=f.dot(q)/det;return distance>.05?distance:Double.POSITIVE_INFINITY;
    }
    @Test void nativeAppearanceSkeletonSitsWithNoRootMotionOrCharacterRescaling() {
        var skeleton=com.codex.blacklight.animation.BlacklightSkeleton.get();
        var bind=skeleton.getPoseMatrices(new com.codex.blacklight.animation.BlacklightPose());
        for(boolean driver:new boolean[]{true,false}) {
            var pose=VehicleSeatPose.skeleton(driver,25,10);var frame=skeleton.getPoseMatrices(pose);
            for(var bone:skeleton.bones()) {
                var scale=pose.orElseEmpty(bone.getName()).scale();assertEquals(1,scale.x,1e-5);assertEquals(1,scale.y,1e-5);assertEquals(1,scale.z,1e-5);
            }
            for(String side:List.of("R","L")) {
                int id=skeleton.searchJointByName("Thigh_"+side).getId();
                var delta=new org.joml.Matrix4f(frame[id]).mul(new org.joml.Matrix4f(bind[id]).invert());
                var down=delta.transformDirection(new org.joml.Vector3f(0,-1,0));
                assertTrue(Math.abs(down.y)<1e-5&&Math.abs(down.z)>.99,"native ability/appearance thigh really sits");
            }
            assertEquals(bind[0].m31(),frame[0].m31(),1e-6,"truck seat retains its original fixed pelvis baseline");
        }
    }
    @Test void nativeThreeBlockRigHeadClearsEveryEnclosedSeatAtThePlayersPhysicalHeight() throws Exception {
        com.google.gson.JsonObject mesh;
        try(var input=getClass().getResourceAsStream("/assets/blacklight/models/mercer_v4.json")) {
            mesh=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var rig=new com.codex.blacklight.client.model.MercerRig(mesh.getAsJsonArray("bones"));
        assertEquals(1,VehicleSeatPose.appearanceScale(1.8F,2,1,false),1e-6,"ordinary appearance stays unchanged");
        assertEquals(1.6F,VehicleSeatPose.appearanceScale(1.8F,2,1.6F,false),1e-6,"dismounted cosmetic height is retained");
        var failures=new ArrayList<String>();
        for(String chassis:List.of("truck","apc","tank")) {
            var def=VehicleCatalog.loadDefault().require(chassis);var model=new MilitaryVehicleModel(chassis);
            for(var seat:def.seats())for(float yaw:new float[]{-75,0,75})for(float pitch:new float[]{-45,0,45}) {
                var skeleton=com.codex.blacklight.animation.BlacklightSkeleton.get().getPoseMatrices(VehicleSeatPose.skeleton(seat.id().equals("driver"),yaw,pitch,VehicleSeatPose.drop(def)));
                float scale=VehicleSeatPose.appearanceScale(1.8F,mesh.get("restHeight").getAsFloat(),3F/(.9375F*mesh.get("restHeight").getAsFloat()),true);
                var frame=rig.sample(skeleton,false,0,scale);double top=Double.NEGATIVE_INFINITY;
                for(var raw:mesh.getAsJsonArray("parts")) {
                    var part=raw.getAsJsonObject();if(part.get("region").getAsInt()!=1)continue;
                    for(var value:part.getAsJsonArray("vertices")) {
                        var v=value.getAsJsonObject();var p=v.getAsJsonArray("p");var w=v.getAsJsonArray("w");
                        var point=new org.joml.Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat());
                        var actual=frame.skin()[rig.index(w.get(0).getAsString())].transformPosition(new org.joml.Vector3f(point))
                                .lerp(frame.skin()[rig.index(w.get(1).getAsString())].transformPosition(new org.joml.Vector3f(point)),w.get(2).getAsFloat());
                        top=Math.max(top,def.anchors().get(seat.anchorId()).position().y()+actual.y*.9375F*scale);
                    }
                }
                double roof=roofHeight(model,chassis,seat.id());
                clearance("MercerRig-3m-cosmetic-at-1.8m-physical-height",chassis,seat.id(),yaw,pitch,top,roof);
                if(top>=roof-.01)failures.add(chassis+" "+seat.id()+" yaw="+yaw+" pitch="+pitch+" nativeHeadTop="+top+" roof="+roof);
            }
        }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
    @org.junit.jupiter.api.AfterAll static void captureActualVertices() throws Exception {
        String output=System.getProperty("blacklight.test.vehiclePlayerVertices");
        if(output!=null) {
            var document=new com.google.gson.JsonObject();document.addProperty("kind","actual-vanilla-and-BlacklightPlayerModel-VertexConsumer-emissions");document.addProperty("gameScreenshot",false);
            document.add("headClearanceCases",CLEARANCES);
            document.add("drawCalls",new com.google.gson.Gson().toJsonTree(CAPTURES));
            java.nio.file.Files.writeString(java.nio.file.Path.of(output),new com.google.gson.Gson().toJson(document));
        }
    }
    static final class TestCamera extends Camera implements PrototypeCameraAccess {
        public void blacklight$setPosition(Vec3 p){setPosition(p);}
        public void blacklight$setRotation(float yaw,float pitch,float roll){setRotation(yaw,pitch);}
        public void blacklight$setRotation(float yaw,float pitch){setRotation(yaw,pitch);}
        public float blacklight$getMaxZoom(float distance){throw new AssertionError("unexpected vanilla zoom path");}
        public double blacklight$getMaxZoom(double distance){throw new AssertionError("unexpected vanilla zoom path");}
        public void blacklight$move(float distance,float vertical,float horizontal){move(distance,vertical,horizontal);}
        public void blacklight$move(double distance,double vertical,double horizontal){move((float)distance,(float)vertical,(float)horizontal);}
    }
}
