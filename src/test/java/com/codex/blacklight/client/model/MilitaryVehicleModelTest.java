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
