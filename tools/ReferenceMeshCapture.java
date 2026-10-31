import com.codex.blacklight.client.model.BlackwatchModel;
import com.codex.blacklight.client.model.BlackwatchRigPose;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.MilitaryRole;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.block.model.ItemTransform;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** QA-only capture of the actual baked production model and its renderToBuffer vertices.
 * No hand-maintained duplicate mesh, Java geometry parser, GL context or entity registry. */
public final class ReferenceMeshCapture {
    record Scene(String name,double yaw,double pitch,boolean left,double reload,double time,double movement,double crouch,double shot,boolean gear,int role,String weapon){}
    record Mesh(String name,String faction,boolean gear,int role,String weapon,List<double[]> vertices,boolean heavy,String defaultRank){}
    private static MilitaryRole duty(Scene scene){return scene.role<0?MilitaryRole.TROOPER:MilitaryRole.values()[scene.role+1];}
    private static VertexConsumer recorder(List<double[]> vertices,int material){
        return (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args.length>=11){
                int uv=args.length==11?4:7,normal=args.length-3;
                vertices.add(new double[]{((Number)args[0]).doubleValue()*16,((Number)args[1]).doubleValue()*16,((Number)args[2]).doubleValue()*16,
                        ((Number)args[uv]).doubleValue(),((Number)args[uv+1]).doubleValue(),((Number)args[normal]).doubleValue(),((Number)args[normal+1]).doubleValue(),((Number)args[normal+2]).doubleValue(),material});
            }
            if(method.getReturnType()==void.class)return null;if(method.getReturnType()==boolean.class)return false;if(method.getReturnType()==int.class)return 0;return proxy;
        });
    }
    private static List<Scene> scenes(){return List.of(
        new Scene("idle",0,0,false,0,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("aim",.12,-.12,false,0,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("shot_peak",0,0,false,0,.2,0,0,1/12d,true,-1,"military_rifle"),
        new Scene("crouch",0,-.08,false,0,.2,0,1,Double.POSITIVE_INFINITY,true,-1,"military_carbine"),
        new Scene("reload_release",0,0,false,.25,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("reload_magazine",0,0,false,.5,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("reload_regrip",0,0,false,.85,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("run",0,0,false,0,.30,.85,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("left_main",.12,-.08,true,0,.2,0,0,Double.POSITIVE_INFINITY,true,-1,"military_rifle"),
        new Scene("uniform",0,0,false,0,0,0,0,Double.POSITIVE_INFINITY,false,-1,"military_rifle"),
        new Scene("officer",0,0,false,0,.2,0,0,Double.POSITIVE_INFINITY,true,1,"military_carbine"),
        new Scene("commander",0,0,false,0,.2,0,0,Double.POSITIVE_INFINITY,true,2,"military_carbine")
    );}
    private static void appearance(ModelPart root,Scene scene,boolean military){
        String[] roles={"team_leader","officer","commander"};
        for(String parent:new String[]{"head","body","right_arm","left_arm","right_leg","left_leg"}){
            var p=root.getChild(parent);p.getChild("equipment").visible=scene.gear;
            for(int i=0;i<roles.length;i++)p.getChild("role_"+roles[i]).visible=scene.gear&&scene.role==i;
            if(parent.endsWith("_arm")){
                var lower=p.getChild("forearm");lower.getChild("equipment").visible=scene.gear;
                for(int i=0;i<roles.length;i++)lower.getChild("role_"+roles[i]).visible=scene.gear&&scene.role==i;
            }
        }
        var headGear=root.getChild("head").getChild("equipment");if(military&&scene.role>=1)headGear.visible=false;
        for(String shell:new String[]{"gear_hood_crown","gear_hood_rim","gear_hood_rear","gear_hood_right","gear_hood_left"})
            if(headGear.hasChild(shell))headGear.getChild(shell).visible=scene.role<1;
        // Registered TROOPER (including raw heavy summons) defaults to PRIVATE; base slots may assign another rank.
        int rank=duty(scene).defaultRank().ordinal();
        var body=root.getChild("body");for(int i=0;i<8;i++)body.getChild("rank_"+i).visible=scene.gear&&i==rank;
        for(String pack:new String[]{"gear_patrol_pack","gear_radio","gear_aerial","gear_supply_pack","gear_supply_lid"})
            if(body.getChild("equipment").hasChild(pack))body.getChild("equipment").getChild(pack).visible=scene.role<1;
        var mount=body.getChild("carry").getChild("weapon_mount");
        for(String gun:com.codex.blacklight.client.model.BlackwatchReferenceProfile.load().weapons().keySet())mount.getChild(gun).visible=gun.equals(scene.weapon);
    }
    private static Mesh capture(Scene scene,boolean military){
        return capture(scene,military,false,false);
    }
    private static Mesh capture(Scene scene,boolean military,boolean heavy,boolean catalog){
        var root=BlackwatchModel.layer(heavy,military).bakeRoot();var model=new BlackwatchModel(root,military);
        appearance(root,scene,military);
        model.applyReferencePose(BlackwatchRigPose.animated(scene.yaw,scene.pitch,scene.left,scene.reload,scene.gear?1:0,
                scene.time,scene.movement,scene.crouch,scene.shot));
        var vertices=new ArrayList<double[]>();
        root.render(new PoseStack(),recorder(vertices,0),15728880,0);
        if(vertices.isEmpty()||vertices.size()%4!=0)throw new IllegalStateException("Incomplete actual model render capture: "+vertices.size());
        return new Mesh(scene.name,(catalog?"catalog_":"")+(military?"military":"blackwatch"),scene.gear,scene.role,scene.weapon,vertices,heavy,duty(scene).defaultRank().id());
    }
    private static Vector3f vector(com.google.gson.JsonArray a){return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    private static JsonObject item(String weapon)throws Exception{
        try(var input=ReferenceMeshCapture.class.getResourceAsStream("/assets/blacklight/models/item/"+weapon+".json")){
            if(input==null)throw new IllegalArgumentException("Missing real item JSON "+weapon);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static void item(List<double[]> vertices,PoseStack pose,String weapon,boolean left)throws Exception{
        var json=item(weapon);var display=json.getAsJsonObject("display").getAsJsonObject(left?"firstperson_lefthand":"firstperson_righthand");
        pose.pushPose();
        try{
            // Native ItemInHandRenderer.applyItemArmTransform followed by the production view's offset.
            int sign=left?-1:1;pose.translate(sign*.56,-.52,-.72);pose.translate(-sign*.13,.035,0);
            new ItemTransform(vector(display.getAsJsonArray("rotation")),vector(display.getAsJsonArray("translation")).div(16),vector(display.getAsJsonArray("scale"))).apply(left,pose);
            pose.translate(-.5,-.5,-.5);
            for(var raw:json.getAsJsonArray("elements")){
                var element=raw.getAsJsonObject();var min=vector(element.getAsJsonArray("from")).div(16);var max=vector(element.getAsJsonArray("to")).div(16);
                pose.pushPose();
                if(element.has("rotation")){
                    var r=element.getAsJsonObject("rotation");var origin=vector(r.getAsJsonArray("origin")).div(16);pose.translate(origin.x,origin.y,origin.z);
                    String axis=r.get("axis").getAsString();float angle=r.get("angle").getAsFloat();pose.mulPose((axis.equals("x")?Axis.XP:axis.equals("y")?Axis.YP:Axis.ZP).rotationDegrees(angle));
                    if(r.has("rescale")&&r.get("rescale").getAsBoolean()){
                        float scale=(float)(1/Math.cos(Math.toRadians(angle)));pose.scale(axis.equals("x")?1:scale,axis.equals("y")?1:scale,axis.equals("z")?1:scale);
                    }pose.translate(-origin.x,-origin.y,-origin.z);
                }
                float a=min.x,b=min.y,c=min.z,x=max.x,y=max.y,z=max.z;
                var corners=Map.of("north",new float[][]{{x,b,c},{a,b,c},{a,y,c},{x,y,c}},"south",new float[][]{{a,b,z},{x,b,z},{x,y,z},{a,y,z}},
                        "west",new float[][]{{a,b,c},{a,b,z},{a,y,z},{a,y,c}},"east",new float[][]{{x,b,z},{x,b,c},{x,y,c},{x,y,z}},
                        "up",new float[][]{{a,y,c},{a,y,z},{x,y,z},{x,y,c}},"down",new float[][]{{a,b,z},{a,b,c},{x,b,c},{x,b,z}});
                var normals=Map.of("north",new Vector3f(0,0,-1),"south",new Vector3f(0,0,1),"west",new Vector3f(-1,0,0),"east",new Vector3f(1,0,0),"up",new Vector3f(0,1,0),"down",new Vector3f(0,-1,0));
                for(var face:element.getAsJsonObject("faces").entrySet()){
                    var uv=face.getValue().getAsJsonObject().getAsJsonArray("uv");double u0=uv.get(0).getAsDouble()/16,v0=uv.get(1).getAsDouble()/16,u1=uv.get(2).getAsDouble()/16,v1=uv.get(3).getAsDouble()/16;
                    double[][] coords={{u1,v1},{u0,v1},{u0,v0},{u1,v0}};var n=pose.last().normal().transform(normals.get(face.getKey()),new Vector3f()).normalize();
                    for(int i=0;i<4;i++){
                        var p=corners.get(face.getKey())[i];var world=pose.last().pose().transformPosition(new Vector3f(p[0],p[1],p[2]));
                        vertices.add(new double[]{world.x*16,world.y*16,world.z*16,coords[i][0],coords[i][1],n.x,n.y,n.z,0});
                    }
                }pose.popPose();
            }
        }finally{pose.popPose();}
    }
    private static void skinArm(List<double[]> vertices,PoseStack pose,ModelPart player,FirearmHandPose.Hand hand,boolean slim){
        var arm=player.getChild(hand.left()?"left_arm":"right_arm");var sleeve=player.getChild(hand.left()?"left_sleeve":"right_sleeve");
        arm.resetPose();arm.setRotation(0,0,0);sleeve.copyFrom(arm);pose.pushPose();
        FirearmHandTransforms.apply(pose,hand,arm,slim);arm.render(pose,recorder(vertices,1),15728880,0);sleeve.render(pose,recorder(vertices,1),15728880,0);pose.popPose();
    }
    private static Mesh firstPerson(String name,String weapon,boolean left,boolean slim,double reload,double shot,boolean dual,boolean invisible)throws Exception{
        var vertices=new ArrayList<double[]>();var player=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
        var pose=new PoseStack();double envelope=Math.sin(Math.PI*reload),recoil=Double.isFinite(shot)?com.codex.blacklight.client.model.BlackwatchReferenceProfile.load().sample("shoot_standing","Two Hand Carry","rotation",shot)[0]/1.103638:0;
        FirearmHandTransforms.feedback(pose,new FirearmViewState.Frame((float)recoil,(float)envelope),left);
        if(!invisible){
            skinArm(vertices,pose,player,FirearmHandPose.primary(weapon,left,reload),slim);
            if(!dual)skinArm(vertices,pose,player,FirearmHandPose.support(weapon,left,reload),slim);
        }item(vertices,pose,weapon,left);
        if(dual){skinArm(vertices,pose,player,FirearmHandPose.primary(weapon,!left,0),slim);item(vertices,pose,weapon,!left);}
        return new Mesh(name,"firstperson",slim,invisible?0:2,weapon,vertices,false,"");
    }
    public static void main(String[] args)throws Exception{
        var meshes=new ArrayList<Mesh>();for(boolean military:new boolean[]{false,true})for(var scene:scenes())meshes.add(capture(scene,military));
        meshes.add(firstPerson("fp_right_rifle","military_rifle",false,false,0,Double.POSITIVE_INFINITY,false,false));
        meshes.add(firstPerson("fp_left_rifle","military_rifle",true,false,0,Double.POSITIVE_INFINITY,false,false));
        meshes.add(firstPerson("fp_shot_peak","military_rifle",false,false,0,1/12d,false,false));
        meshes.add(firstPerson("fp_reload","military_rifle",false,false,.5,Double.POSITIVE_INFINITY,false,false));
        meshes.add(firstPerson("fp_slim","military_rifle",false,true,0,Double.POSITIVE_INFINITY,false,false));
        meshes.add(firstPerson("fp_launcher","rocket_launcher",false,false,0,Double.POSITIVE_INFINITY,false,false));
        meshes.add(firstPerson("fp_dual","military_rifle",false,false,0,Double.POSITIVE_INFINITY,true,false));
        meshes.add(firstPerson("fp_invisible","military_rifle",false,false,0,Double.POSITIVE_INFINITY,false,true));
        for(boolean military:new boolean[]{false,true})for(String suffix:new String[]{"soldier","heavy","team_leader","officer","commander"})for(boolean gear:new boolean[]{true,false}){
            boolean heavy=suffix.equals("heavy");int role=suffix.equals("team_leader")?0:suffix.equals("officer")?1:suffix.equals("commander")?2:-1;
            String weapon=role>=1?"military_carbine":heavy?"military_machine_gun":"military_rifle";
            var scene=new Scene("catalog-"+(military?"military":"blackwatch")+"_"+suffix+"-"+(gear?"equipped":"uniform"),0,0,false,0,.2,0,0,Double.POSITIVE_INFINITY,gear,role,weapon);
            meshes.add(capture(scene,military,heavy,true));
        }
        var target=Path.of(args[0]);Files.createDirectories(target.getParent());Files.writeString(target,new Gson().toJson(meshes));
        System.out.println("Captured "+meshes.size()+" poses from actual production BlackwatchModel.render and reference-rig curves.");
    }
}
