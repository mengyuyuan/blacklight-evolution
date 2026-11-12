package com.codex.blacklight.client.model;

import com.codex.blacklight.military.vehicle.VehicleCatalog;
import com.codex.blacklight.military.vehicle.VehicleDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryVehicleModelTest {
    @Test void dentsBendInteriorFaceVerticesAndNeverLeakToTheNextVehicle(){
        var model=new MilitaryVehicleModel("tank");model.pose(0,0,0);
        var dent=new com.codex.blacklight.military.vehicle.VehicleDeformation.Dent("body",new Vec3(2,1.6,.4),new Vec3(-1,0,0),.9,.4);
        var counts=new int[3];var bent=new AtomicInteger();
        VertexConsumer collector=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex")&&args.length>=11){
                counts[0]++;double x=((Number)args[0]).doubleValue(),y=((Number)args[1]).doubleValue(),z=((Number)args[2]).doubleValue();
                if(x>1.5&&x<1.8&&y>1.45&&y<1.75&&z>.2&&z<.6) bent.incrementAndGet();
                for(int axis=0;axis<3;axis++)assertTrue(Double.isFinite(((Number)args[axis]).doubleValue()));
            }
            return method.getReturnType()==void.class?null:proxy;
        });
        model.render(new PoseStack(),collector,15728880,0);counts[1]=counts[0];assertEquals(0,bent.get());
        counts[0]=0;model.deformation(java.util.List.of(dent));model.render(new PoseStack(),collector,15728880,0);
        assertTrue(counts[0]>counts[1]);assertTrue(bent.get()>8,"dent needs interior surface vertices, not a translated whole cuboid");
        counts[0]=0;model.deformation(java.util.List.of());model.render(new PoseStack(),collector,15728880,0);assertEquals(counts[1],counts[0]);
    }
    @Test void actualRoofLeansWithIncomingForceAndAgainstFiringDirection(){
        var model=new MilitaryVehicleModel("tank");
        for(var push:java.util.List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
            model.pose(0,0,0);
            model.feedback(com.codex.blacklight.military.vehicle.VehicleFeedbackMotion.sample("",-1,-1,0,1,1,push));
            var stack=new PoseStack();model.root().getChild("body").translateAndRotate(stack);
            var roof=stack.last().pose().transformPosition(new org.joml.Vector3f(0,2,0));
            assertTrue(roof.x*push.x+roof.z*push.z>.02,"roof tips away from the impact source");
        }
        for(double yaw:new double[]{0,90,180,-90}){
            model.pose(yaw,0,0);
            model.feedback(com.codex.blacklight.military.vehicle.VehicleFeedbackMotion.sample("tank_he",1,-1,yaw,-1,0,Vec3.ZERO));
            var stack=new PoseStack();model.root().getChild("body").translateAndRotate(stack);
            var roof=stack.last().pose().transformPosition(new org.joml.Vector3f(0,2,0));
            assertTrue(roof.x*Math.sin(Math.toRadians(yaw))+roof.z*Math.cos(Math.toRadians(yaw))<-.01,"firing drives body against barrel direction");
        }
    }
    @Test void recoilMovesOnlyTheFiredBarrelAlongItsPitchedAxis(){
        var model=new MilitaryVehicleModel("tank");model.pose(65,15,0);
        var before=model.anchorWorld("muzzle_primary",Vec3.ZERO,0);var secondary=model.anchorWorld("muzzle_secondary",Vec3.ZERO,0);
        var axis=before.subtract(model.anchorWorld("breech_primary",Vec3.ZERO,0)).normalize();
        model.feedback(new com.codex.blacklight.military.vehicle.VehicleFeedbackMotion.Pose(0,0,0,.4,0));
        assertTrue(model.anchorWorld("muzzle_primary",Vec3.ZERO,0).distanceTo(before.subtract(axis.scale(.4)))<1e-5);
        assertTrue(model.anchorWorld("muzzle_secondary",Vec3.ZERO,0).distanceTo(secondary)<1e-5);
        model.pose(65,15,0);assertTrue(model.anchorWorld("muzzle_primary",Vec3.ZERO,0).distanceTo(before)<1e-5);
    }
    @Test void rockingSprungBodyKeepsTrackedAndWheeledGroundContactsFixed(){
        for(String id:java.util.List.of("tank","apc","truck")){
            var model=new MilitaryVehicleModel(id);model.pose(0,0,0);var body=model.root().getChild("body");
            var contact=body.getChild(id.equals("truck")?"left_wheel_1":"track_left");
            var before=new PoseStack();body.translateAndRotate(before);contact.translateAndRotate(before);var matrix=new org.joml.Matrix4f(before.last().pose());
            model.feedback(new com.codex.blacklight.military.vehicle.VehicleFeedbackMotion.Pose(3,-2,-.04,0,0));
            var after=new PoseStack();body.translateAndRotate(after);contact.translateAndRotate(after);
            for(var point:java.util.List.of(new org.joml.Vector3f(),new org.joml.Vector3f(1,1,1),new org.joml.Vector3f(-1,0,2))){
                assertTrue(matrix.transformPosition(new org.joml.Vector3f(point)).distance(after.last().pose().transformPosition(new org.joml.Vector3f(point)))<1e-5,id+" contact stays on ground");
            }
        }
    }
    @Test void actualBakedRenderingContainsAll377AuthoredCuboids() {
        for(var entry:java.util.Map.of("truck",78,"apc",138,"tank",161).entrySet()) {
            var model=new MilitaryVehicleModel(entry.getKey());
            var count=new AtomicInteger();
            var bounds=new double[]{Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            VertexConsumer collector=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
                if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11){
                    count.incrementAndGet();
                    for(int axis=0;axis<3;axis++){double value=((Number)args[axis]).doubleValue();bounds[axis]=Math.min(bounds[axis],value);bounds[axis+3]=Math.max(bounds[axis+3],value);}
                }
                if(method.getReturnType()==void.class)return null;
                if(method.getReturnType()==boolean.class)return false;
                if(method.getReturnType()==int.class)return 0;
                return proxy;
            });
            model.root().render(new PoseStack(),collector,15728880,0);
            assertEquals(entry.getValue()*24,count.get(),entry.getKey()+" actual rendered faces");
            // Authoring extrema, including the actual muzzle overhang; no catalogue-derived expected box.
            double[] expected=switch(entry.getKey()){
                case "truck"->new double[]{-1.45,0,-3.75,1.45,3.7,3.75};
                case "apc"->new double[]{-1.65,.02946429,-3.5,1.65,3.3,4.22413793};
                case "tank"->new double[]{-2,.03035714,-3.84615385,2,3.4,6.03076923};
                default->throw new AssertionError(entry.getKey());
            };
            assertArrayEquals(expected,bounds,2e-5,entry.getKey()+" actual rendered bounds");
        }
    }
    @Test void actualTankBonesMatchIndependentQuarterTurnAndOpticCoordinates() {
        var model=new MilitaryVehicleModel("tank");var origin=new Vec3(10,64,20);
        model.pose(90,0,0);
        var seat=model.anchorWorld("seat_driver",origin,90);
        assertEquals(12.03076923,seat.x,2e-5);assertEquals(64.06071429,seat.y,2e-5);assertEquals(20,seat.z,2e-5);
        var muzzle=model.anchorWorld("muzzle_primary",origin,0);
        assertEquals(16.03076923,muzzle.x,2e-5);assertEquals(66.55,muzzle.y,2e-5);assertEquals(20,muzzle.z,2e-5);
        model.pose(90,90,0);
        var optic=model.anchorWorld("camera_gunner",origin,0);
        assertEquals(9.78,optic.x,2e-5);assertEquals(67.35142857,optic.y,2e-5);assertEquals(19.44705882,optic.z,2e-5);
        model.pose(0,90,0);
        var lowered=model.anchorWorld("muzzle_primary",origin,0);
        assertEquals(10,lowered.x,2e-5);assertEquals(61.68846154,lowered.y,2e-5);assertEquals(21.16923077,lowered.z,2e-5);
    }
    @Test void allActualBoneMountsMatchServerAcrossBodyTurretAndPitch() {
        var origin=new Vec3(100,70,-120);
        for(String id:java.util.List.of("truck","apc","tank")) {
            var model=new MilitaryVehicleModel(id);var definition=VehicleCatalog.loadDefault().require(id);
            for(double body:new double[]{0,45,90,-170})for(double turret:new double[]{0,73,-120})for(double pitch:new double[]{-20,0,15}) {
                model.pose(turret,pitch,0);
                for(String name:definition.anchors().keySet()) {
                    var expected=definition.mountWorld(name,new VehicleDefinition.Vec3(origin.x,origin.y,origin.z),body,turret,pitch);
                    var actual=model.anchorWorld(name,origin,body);
                    assertEquals(expected.x(),actual.x,2e-5,id+" "+name+" x");
                    assertEquals(expected.y(),actual.y,2e-5,id+" "+name+" y");
                    assertEquals(expected.z(),actual.z,2e-5,id+" "+name+" z");
                }
            }
        }
    }
    @Test void invalidAssetsAndNonfinitePosesFailClosed() {
        assertThrows(IllegalArgumentException.class,()->new MilitaryVehicleModel("unknown"));
        var model=new MilitaryVehicleModel("tank");
        assertThrows(IllegalArgumentException.class,()->model.pose(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->model.anchorWorld("missing",Vec3.ZERO,0));
    }
    @Test void everyActualPrimaryAndSecondaryBarrelSharesTheAimedPitch() {
        for(String id:java.util.List.of("apc","tank")) {
            var model=new MilitaryVehicleModel(id);model.pose(0,10,0);
            for(String barrel:java.util.List.of("primary","secondary")) {
                var a=model.anchorWorld("breech_"+barrel,Vec3.ZERO,0);
                var b=model.anchorWorld("muzzle_"+barrel,Vec3.ZERO,0);
                var ray=b.subtract(a).normalize();
                assertTrue(ray.y < -.1,id+" "+barrel+" actual model muzzle must lower towards the aimed target");
            }
        }
    }
}
