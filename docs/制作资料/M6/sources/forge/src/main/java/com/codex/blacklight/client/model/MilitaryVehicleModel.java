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
    private final JsonObject anchors;

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
    }
    private static Vec3 vector(JsonArray value) { return new Vec3(value.get(0).getAsDouble(),value.get(1).getAsDouble(),value.get(2).getAsDouble()); }
    private static void finite(double value) { if(!Double.isFinite(value) || Math.abs(value)>360000)throw new IllegalArgumentException("Invalid vehicle model pose"); }
    public ModelPart root() { return root; }
    public void pose(double turretYaw,double pitch,double wheelTravel) {
        finite(turretYaw);finite(pitch);finite(wheelTravel);
        root.getAllParts().forEach(ModelPart::resetPose);
        if(bones.containsKey("turret_yaw"))bones.get("turret_yaw").yRot=(float)Math.toRadians(turretYaw);
        if(bones.containsKey("gun_pitch"))bones.get("gun_pitch").xRot=(float)Math.toRadians(pitch);
        for(var entry:bones.entrySet())if(entry.getKey().contains("wheel"))entry.getValue().xRot=(float)(wheelTravel/.5);
    }
    public Vec3 anchorWorld(String name,Vec3 origin,double bodyYaw) {
        finite(bodyYaw);
        if(origin==null || !Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z) || !anchors.has(name))throw new IllegalArgumentException("Invalid vehicle model mount: "+name);
        var anchor=anchors.getAsJsonObject(name);String frame=anchor.get("frame").getAsString();
        var point=vector(anchor.getAsJsonArray("position")).subtract(pivots.get(frame));
        var chain=new ArrayDeque<String>();for(String current=frame;current!=null;current=parents.get(current))chain.addFirst(current);
        var stack=new PoseStack();stack.mulPose(Axis.YP.rotationDegrees((float)bodyYaw));
        for(String bone:chain)bones.get(bone).translateAndRotate(stack);
        var local=stack.last().pose().transformPosition(new Vector3f((float)point.x,(float)point.y,(float)point.z));
        return origin.add(local.x,local.y,local.z);
    }
}
