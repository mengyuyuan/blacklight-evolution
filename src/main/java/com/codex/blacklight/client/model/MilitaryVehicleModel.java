package com.codex.blacklight.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.phys.Vec3;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.Vector3f;

/** Bakes the authored vehicle resource directly; all mounts share the server's coordinate system. */
public final class MilitaryVehicleModel {
    private final ModelPart root;
    private final Map<String,ModelPart> bones=new LinkedHashMap<>();
    private final Map<String,String> parents=new LinkedHashMap<>();
    private final Map<String,Vec3> pivots=new LinkedHashMap<>();
    private final Map<String,List<ModelPart>> skin=new LinkedHashMap<>();
    private List<com.codex.blacklight.military.vehicle.VehicleDeformation.Dent> dents=List.of();
    private final JsonObject anchors;
    private final List<ModelPart> primaryBarrel=new ArrayList<>(),secondaryBarrel=new ArrayList<>(),grounded=new ArrayList<>();
    private double primaryRecoil,secondaryRecoil;

    public MilitaryVehicleModel(String chassis) {
        JsonObject document;
        try(var input=MilitaryVehicleModel.class.getResourceAsStream("/assets/blacklight/military/vehicle-models.json")) {
            if(input==null)throw new IllegalArgumentException("Missing vehicle model asset");
            document=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException failure) { throw new IllegalArgumentException("Cannot read vehicle model asset",failure); }
        var entries=document.getAsJsonObject("chassis");
        if(document.get("schemaVersion").getAsInt()!=1 || !entries.has(chassis))throw new IllegalArgumentException("Unknown vehicle model: "+chassis);
        var asset=entries.getAsJsonObject(chassis);anchors=asset.getAsJsonObject("anchors");
        var mesh=new MeshDefinition();var definitions=new LinkedHashMap<String,PartDefinition>();
        definitions.put("root",mesh.getRoot());pivots.put("root",Vec3.ZERO);
        for(var raw:asset.getAsJsonArray("bones")) {
            var bone=raw.getAsJsonObject();String name=bone.get("name").getAsString();
            if(name.equals("root"))continue;
            String parent=bone.get("parent").getAsString();var offset=vector(bone.getAsJsonArray("pivot"));
            var owner=definitions.get(parent);
            if(owner==null || definitions.containsKey(name))throw new IllegalArgumentException("Invalid bone hierarchy: "+name);
            definitions.put(name,owner.addOrReplaceChild(name,CubeListBuilder.create(),PartPose.offset((float)offset.x,(float)offset.y,(float)offset.z)));
            parents.put(name,parent);pivots.put(name,pivots.get(parent).add(offset.scale(1/16.0)));
        }
        for(var raw:asset.getAsJsonArray("parts")) {
            var part=raw.getAsJsonObject();var box=part.getAsJsonArray("box");var uv=part.getAsJsonArray("uv");
            var owner=definitions.get(part.get("parent").getAsString());
            if(owner==null)throw new IllegalArgumentException("Unknown geometry owner");
            owner.addOrReplaceChild(part.get("name").getAsString(),CubeListBuilder.create().texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt())
                    .addBox(box.get(0).getAsFloat(),box.get(1).getAsFloat(),box.get(2).getAsFloat(),box.get(3).getAsFloat(),box.get(4).getAsFloat(),box.get(5).getAsFloat()),PartPose.ZERO);
        }
        root=LayerDefinition.create(mesh,document.get("textureWidth").getAsInt(),document.get("textureHeight").getAsInt()).bakeRoot();
        bones.put("root",root);
        for(var entry:parents.entrySet())bones.put(entry.getKey(),bones.get(entry.getValue()).getChild(entry.getKey()));
        for(var raw:asset.getAsJsonArray("parts")){
            var p=raw.getAsJsonObject();String name=p.get("name").getAsString();
            String parent=p.get("parent").getAsString();skin.computeIfAbsent(parent,key->new ArrayList<>()).add(bones.get(parent).getChild(name));
            if(!p.get("parent").getAsString().equals("gun_pitch"))continue;
            if(name.startsWith("main_barrel")||name.equals("main_muzzle"))primaryBarrel.add(bones.get("gun_pitch").getChild(name));
            if(name.equals("roof_gun_barrels")||name.equals("roof_gun_muzzle"))secondaryBarrel.add(bones.get("gun_pitch").getChild(name));
        }
        for(var e:parents.entrySet())if(e.getValue().equals("body")&&(e.getKey().startsWith("track_")||e.getKey().contains("wheel")))grounded.add(bones.get(e.getKey()));
    }
    private static Vec3 vector(JsonArray value) { return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble()); }
    private static void finite(double value) { if(!Double.isFinite(value) || Math.abs(value)>360000)throw new IllegalArgumentException("Invalid vehicle model pose"); }
    public ModelPart root() { return root; }
    public void deformation(List<com.codex.blacklight.military.vehicle.VehicleDeformation.Dent> value){dents=value;}
    public void render(PoseStack stack,com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay){
        if(dents.isEmpty()){root.render(stack,out,light,overlay);return;}
        renderBone("root",stack,out,light,overlay);
    }
    private void renderBone(String name,PoseStack stack,com.mojang.blaze3d.vertex.VertexConsumer out,int light,int overlay){
        var bone=bones.get(name);if(!bone.visible)return;
        stack.pushPose();bone.translateAndRotate(stack);
        var local=dents.stream().filter(d->d.bone().equals(name)).toList();
        var deformed=local.isEmpty()?out:new com.codex.blacklight.client.vehicle.VehicleDentMesh(out,stack,local);
        for(var part:skin.getOrDefault(name,List.of()))part.render(stack,deformed,light,overlay);
        for(var child:parents.entrySet())if(child.getValue().equals(name))renderBone(child.getKey(),stack,out,light,overlay);
        stack.popPose();
    }
    public void pose(double turretYaw,double pitch,double wheelTravel) {
        finite(turretYaw);finite(pitch);finite(wheelTravel);
        root.getAllParts().forEach(ModelPart::resetPose);
        primaryRecoil=secondaryRecoil=0;
        if(bones.containsKey("turret_yaw"))bones.get("turret_yaw").yRot=(float)Math.toRadians(turretYaw);
        if(bones.containsKey("gun_pitch"))bones.get("gun_pitch").xRot=(float)Math.toRadians(pitch);
        for(var entry:bones.entrySet())if(entry.getKey().contains("wheel"))entry.getValue().xRot=(float)(wheelTravel/.5);
    }
    public void feedback(com.codex.blacklight.military.vehicle.VehicleFeedbackMotion.Pose pose){
        primaryRecoil=pose.primaryRecoil();secondaryRecoil=pose.secondaryRecoil();
        for(var part:primaryBarrel)part.z-=(float)(primaryRecoil*16);
        for(var part:secondaryBarrel)part.z-=(float)(secondaryRecoil*16);
        var body=bones.get("body");if(body==null)return;
        body.xRot=(float)Math.toRadians(pose.pitch());body.zRot=(float)Math.toRadians(pose.roll());body.y=(float)(pose.heave()*16);
        // Suspension moves the sprung body; tracks and wheels keep their contact with the ground.
        var inverse=new org.joml.Quaternionf().rotationZYX(body.zRot,0,body.xRot).invert();
        for(var part:grounded){
            var at=inverse.transform(new Vector3f(part.x,part.y-body.y,part.z));part.setPos(at.x,at.y,at.z);
            var q=new org.joml.Quaternionf(inverse).mul(new org.joml.Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot));
            var m=new org.joml.Matrix3f().set(q);float y=(float)Math.asin(Math.clamp(-m.m02(),-1,1));
            part.setRotation((float)Math.atan2(m.m12(),m.m22()),y,(float)Math.atan2(m.m01(),m.m00()));
        }
    }
    public Vec3 anchorWorld(String name,Vec3 origin,double bodyYaw) {
        finite(bodyYaw);
        if(origin==null || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z) || !anchors.has(name))throw new IllegalArgumentException("Invalid vehicle model mount: "+name);
        var anchor=anchors.getAsJsonObject(name);String frame=anchor.get("frame").getAsString();
        var point=vector(anchor.getAsJsonArray("position")).subtract(pivots.get(frame));
        if(name.equals("muzzle_primary"))point=point.add(0,0,-primaryRecoil);
        if(name.equals("muzzle_secondary")&&!secondaryBarrel.isEmpty())point=point.add(0,0,-secondaryRecoil);
        var chain=new ArrayDeque<String>();for(String current=frame;current!=null;current=parents.get(current))chain.addFirst(current);
        var stack=new PoseStack();stack.mulPose(Axis.YP.rotationDegrees((float)bodyYaw));
        for(String bone:chain)bones.get(bone).translateAndRotate(stack);
        var local=stack.last().pose().transformPosition(new Vector3f((float)point.x,(float)point.y,(float)point.z));
        return origin.add(local.x,local.y,local.z);
    }
}
