package com.codex.blacklight.client.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleRiderPoseTest {
    private static java.util.List<double[]> renderedVertices(net.minecraft.client.model.geom.ModelPart part,com.mojang.blaze3d.vertex.PoseStack pose) {
        var vertices=new java.util.ArrayList<double[]>();
        var recorder=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(com.mojang.blaze3d.vertex.VertexConsumer.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11)
                vertices.add(new double[]{((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue()});
            if(method.getReturnType()==void.class)return null;if(method.getReturnType()==int.class)return 0;if(method.getReturnType()==boolean.class)return false;return proxy;
        });
        part.render(pose,recorder,15728880,0);return vertices;
    }
    private static double[] renderedBounds(net.minecraft.client.model.geom.ModelPart part,com.mojang.blaze3d.vertex.PoseStack pose) {
        double[] bounds={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for(var vertex:renderedVertices(part,pose))for(int axis=0;axis<3;axis++){
            bounds[axis]=Math.min(bounds[axis],vertex[axis]);bounds[axis+3]=Math.max(bounds[axis+3],vertex[axis]);
        }
        return bounds;
    }
    private static double[] vehicleBounds(MilitaryVehicleModel vehicle,String... path) {
        var pose=new com.mojang.blaze3d.vertex.PoseStack();var part=vehicle.root();
        for(String child:path){part.translateAndRotate(pose);part=part.getChild(child);}
        return renderedBounds(part,pose);
    }
    private static void seat(BlackwatchModel model,boolean driver) throws Exception {
        java.lang.reflect.Method method;
        try{method=BlackwatchModel.class.getMethod("applyVehicleSeatPose",boolean.class);}
        catch(NoSuchMethodException missing){fail("Actual split-arm soldier model must support a seated vehicle pose instead of its standing gun rig");return;}
        method.invoke(model,driver);
    }
    @Test void realTruckDriverBendsBothLegsAndUsesEmptyHandsWithoutDeletingGear() throws Exception {
        var root=BlackwatchModel.layer(false,true).bakeRoot();var model=new BlackwatchModel(root,true);
        model.applyReferencePose(BlackwatchRigPose.solve(0,0,false,0,1));
        root.getChild("body").getChild("equipment").visible=true;
        seat(model,true);
        assertFalse(root.getChild("body").getChild("carry").visible,"vehicle operator must not display an infantry gun through the steering position");
        assertTrue(root.getChild("right_leg").xRot < -1.2 && root.getChild("left_leg").xRot < -1.2,"both actual leg bones bend forward into the seat");
        assertTrue(root.getChild("right_arm").xRot < -.7 && root.getChild("left_arm").xRot < -.7,"both actual split arms reach the steering controls");
        assertTrue(root.getChild("body").getChild("equipment").visible,"seating does not strip the uniform or rank geometry");
    }
    @Test void realSeatedPassengerHasDifferentRestingArmsAndFiniteActualTransforms() throws Exception {
        var root=BlackwatchModel.layer(true,false).bakeRoot();var model=new BlackwatchModel(root,false);seat(model,false);
        assertFalse(root.getChild("body").getChild("carry").visible);
        assertTrue(root.getChild("right_arm").xRot > -.7 && root.getChild("left_arm").xRot > -.7,"passenger uses a resting arm pose rather than driving controls");
        root.getAllParts().forEach(part->{assertTrue(Float.isFinite(part.xRot));assertTrue(Float.isFinite(part.yRot));assertTrue(Float.isFinite(part.zRot));});
    }
    @Test void actualDriverHeadAndCameraFitBelowTheActualCabRoof() throws Exception {
        var vehicle=new MilitaryVehicleModel("truck");var seat=vehicle.anchorWorld("seat_driver",net.minecraft.world.phys.Vec3.ZERO,0);
        var roof=vehicle.root().getChild("body").getChild("cab_roof");
        var root=BlackwatchModel.layer(false,true).bakeRoot();var model=new BlackwatchModel(root,true);seat(model,true);
        for(String role:java.util.List.of("team_leader","officer","commander"))root.getChild("head").getChild("role_"+role).visible=false;
        var roofPass=new java.util.concurrent.atomic.AtomicBoolean(true);
        var maximum=new java.util.concurrent.atomic.AtomicReference<Double>(Double.POSITIVE_INFINITY);
        var recorder=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(com.mojang.blaze3d.vertex.VertexConsumer.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11)maximum.set(roofPass.get()?Math.min(maximum.get(),((Number)args[1]).doubleValue()):Math.max(maximum.get(),((Number)args[1]).doubleValue()));
            if(method.getReturnType()==void.class)return null;if(method.getReturnType()==int.class)return 0;if(method.getReturnType()==boolean.class)return false;return proxy;
        });
        roof.render(new com.mojang.blaze3d.vertex.PoseStack(),recorder,15728880,0);double roofHeight=maximum.get();maximum.set(Double.NEGATIVE_INFINITY);roofPass.set(false);
        var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.translate(seat.x,seat.y,seat.z);pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        root.getChild("head").render(pose,recorder,15728880,0);
        assertTrue(maximum.get()<roofHeight-.01,"actual helmet must fit inside the production cab, rather than protruding through its roof");
        assertTrue(seat.y+1.62<roofHeight,"actual passenger eye height must also remain below the physical cab roof");
    }
    @Test void everyActualTruckRiderBodyPartClearsTheCabAndCargoWalls() throws Exception {
        var vehicle=new MilitaryVehicleModel("truck");var failures=new java.util.ArrayList<String>();
        double cabLeft=vehicleBounds(vehicle,"body","door_left","left_cab_door")[3];
        double cabRight=vehicleBounds(vehicle,"body","door_right","right_cab_door")[0];
        double cargoLeft=vehicleBounds(vehicle,"body","left_canvas_side")[3];
        double cargoRight=vehicleBounds(vehicle,"body","right_canvas_side")[0];
        double cabFloor=vehicleBounds(vehicle,"body","cab_floor")[4],cabRoof=vehicleBounds(vehicle,"body","cab_roof")[1];
        double cargoFloor=vehicleBounds(vehicle,"body","cargo_floor")[4],cargoRoof=vehicleBounds(vehicle,"body","canopy_top")[1];
        double cabBack=vehicleBounds(vehicle,"body","cab_rear_wall")[5],cargoBack=vehicleBounds(vehicle,"body","door_rear","rear_tailgate")[5];
        double cargoFront=vehicleBounds(vehicle,"body","left_canvas_side")[5];
        var cabFront=new java.util.ArrayList<double[]>();
        for(String part:java.util.List.of("cab_lower_nose","cab_front_step_1","cab_front_step_2","cab_windscreen_frame"))cabFront.add(vehicleBounds(vehicle,"body",part));
        for(boolean military:new boolean[]{true,false})for(boolean heavy:new boolean[]{false,true})
            for(String appearance:java.util.List.of("none","team_leader","officer","commander"))
            for(var location:com.codex.blacklight.military.vehicle.VehicleCatalog.loadDefault().require("truck").seats()){
                var root=BlackwatchModel.layer(heavy,military).bakeRoot();var model=new BlackwatchModel(root,military);
                seat(model,location.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER);
                for(String name:java.util.List.of("head","body","right_arm","left_arm","right_leg","left_leg")){
                    var part=root.getChild(name);part.getChild("equipment").visible=true;
                    for(String role:java.util.List.of("team_leader","officer","commander")){
                        part.getChild("role_"+role).visible=role.equals(appearance);
                        if(name.endsWith("_arm"))part.getChild("forearm").getChild("role_"+role).visible=role.equals(appearance);
                    }
                }
                boolean command=appearance.equals("officer")||appearance.equals("commander");
                var headGear=root.getChild("head").getChild("equipment");
                if(military&&command)headGear.visible=false;
                if(!military)for(String hood:java.util.List.of("gear_hood_crown","gear_hood_rim","gear_hood_rear","gear_hood_right","gear_hood_left"))headGear.getChild(hood).visible=!command;
                var bodyGear=root.getChild("body").getChild("equipment");
                for(String pack:java.util.List.of("gear_patrol_pack","gear_radio","gear_aerial","gear_supply_pack","gear_supply_lid"))if(bodyGear.hasChild(pack))bodyGear.getChild(pack).visible=!command;
                root.getAllParts().forEach(part->{assertEquals(1,part.xScale);assertEquals(1,part.yScale);assertEquals(1,part.zScale);});
                boolean cargo=location.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.PASSENGER;
                double left=cargo?cargoLeft:cabLeft,right=cargo?cargoRight:cabRight;
                var point=vehicle.anchorWorld(location.anchorId(),net.minecraft.world.phys.Vec3.ZERO,0);
                var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.translate(point.x,point.y,point.z);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
                for(String name:java.util.List.of("head","body","right_arm","left_arm","right_leg","left_leg")){
                    var bounds=renderedBounds(root.getChild(name),pose);
                    if(bounds[0]<left+.01 || bounds[3]>right-.01)failures.add(String.format(java.util.Locale.ROOT,
                        "%s heavy=%s role=%s seat=%s part=%s rendered x=[%.6f, %.6f], wall interior=[%.6f, %.6f]",
                        military?"military":"blackwatch",heavy,appearance,location.id(),name,bounds[0],bounds[3],left,right));
                    double floor=cargo?cargoFloor:cabFloor,roof=cargo?cargoRoof:cabRoof,back=cargo?cargoBack:cabBack;
                    boolean vertical=bounds[1]<floor+.005 || bounds[4]>roof-.005;
                    boolean longitudinal=bounds[2]<back+.005 || cargo&&bounds[5]>cargoFront-.005;
                    if(!cargo)for(var vertex:renderedVertices(root.getChild(name),pose))for(var wall:cabFront)
                        if(vertex[1]>=wall[1] && vertex[1]<=wall[4] && vertex[2]>wall[2]-.005)longitudinal=true;
                    if(vertical || longitudinal)failures.add(String.format(java.util.Locale.ROOT,
                        "%s heavy=%s role=%s seat=%s part=%s rendered y=[%.6f, %.6f] z=[%.6f, %.6f], floor=%.6f roof=%.6f back=%.6f vertical=%s longitudinal=%s",
                        military?"military":"blackwatch",heavy,appearance,location.id(),name,bounds[1],bounds[4],bounds[2],bounds[5],floor,roof,back,vertical,longitudinal));
                }
            }
        assertTrue(failures.isEmpty(),String.join("\n",failures));
    }
}
