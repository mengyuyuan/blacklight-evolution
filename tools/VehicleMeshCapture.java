import com.codex.blacklight.client.model.*;
import com.codex.blacklight.military.vehicle.*;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;

/** Offline QA: records actual production ModelPart vertices, including the actual seated split-arm rig. */
public final class VehicleMeshCapture {
    record Scene(String name,String chassis,String faction,List<double[]> vertices){}
    private static VertexConsumer recorder(List<double[]> vertices,int material){
        return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11){
                int uv=args.length==11?4:7,n=args.length-3;
                vertices.add(new double[]{((Number)args[0]).doubleValue(),((Number)args[1]).doubleValue(),((Number)args[2]).doubleValue(),
                    ((Number)args[uv]).doubleValue(),((Number)args[uv+1]).doubleValue(),((Number)args[n]).doubleValue(),((Number)args[n+1]).doubleValue(),((Number)args[n+2]).doubleValue(),material});
            }
            if(method.getReturnType()==void.class)return null;if(method.getReturnType()==boolean.class)return false;if(method.getReturnType()==int.class)return 0;return proxy;
        });
    }
    private static void appearance(ModelPart root){
        for(String name:List.of("head","body","right_arm","left_arm","right_leg","left_leg")){
            var part=root.getChild(name);part.getChild("equipment").visible=true;
            for(String role:List.of("team_leader","officer","commander"))part.getChild("role_"+role).visible=false;
            if(name.endsWith("_arm"))for(String role:List.of("team_leader","officer","commander"))part.getChild("forearm").getChild("role_"+role).visible=false;
        }
        for(int rank=0;rank<8;rank++)root.getChild("body").getChild("rank_"+rank).visible=rank==0;
    }
    public static void main(String[] args)throws Exception{
        var scenes=new ArrayList<Scene>();var interiors=new ArrayList<Scene>();
        for(String faction:List.of("military","blackwatch"))for(String chassis:List.of("truck","apc","tank")){
            var model=new MilitaryVehicleModel(chassis);model.pose(chassis.equals("truck")?0:25,chassis.equals("truck")?0:8,0);
            var vertices=new ArrayList<double[]>();model.root().render(new PoseStack(),recorder(vertices,0),15728880,0);
            scenes.add(new Scene(chassis+"-"+faction,chassis,faction,vertices));
            if(chassis.equals("truck")){
                var paired=new ArrayList<>(vertices);var riders=new ArrayList<double[]>();var definition=VehicleCatalog.loadDefault().require("truck");
                for(var seat:definition.seats()){
                    var root=BlackwatchModel.layer(false,faction.equals("military")).bakeRoot();var rider=new BlackwatchModel(root,faction.equals("military"));
                    appearance(root);rider.applyVehicleSeatPose(seat.job()==VehicleDefinition.Job.DRIVER);
                    var point=model.anchorWorld(seat.anchorId(),Vec3.ZERO,0);var pose=new PoseStack();pose.translate(point.x,point.y,point.z);
                    pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
                    root.render(pose,recorder(riders,1),15728880,0);
                }
                paired.addAll(riders);
                scenes.add(new Scene("truck-crew-"+faction,chassis,faction,paired));
                // Diagnostic cutaway changes only this offline vehicle instance, never production assets.
                var body=model.root().getChild("body");
                for(String name:List.of("door_left","door_right","cab_roof","cab_rear_wall","cab_windscreen_frame",
                        "left_canvas_side","right_canvas_side","canopy_top","canopy_left_shoulder","canopy_right_shoulder"))body.getChild(name).visible=false;
                var interior=new ArrayList<double[]>();model.root().render(new PoseStack(),recorder(interior,0),15728880,0);interior.addAll(riders);
                interiors.add(new Scene("truck-crew-interior-"+faction,chassis,faction,interior));
            }
        }
        Files.writeString(Path.of(args[0]),new Gson().toJson(scenes),java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(Path.of(args[0]).resolveSibling("actual-truck-interior-meshes.json"),new Gson().toJson(interiors),java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Captured "+scenes.size()+" actual production scenes; no OpenGL screenshot claim.");
    }
}
