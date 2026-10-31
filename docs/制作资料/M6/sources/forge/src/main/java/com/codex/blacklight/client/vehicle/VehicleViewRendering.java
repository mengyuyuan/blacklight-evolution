package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.model.MilitaryVehicleModel;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Local first-person windows keep their authored frames while opening the opaque glass backing. */
public final class VehicleViewRendering {
    public interface Pass extends AutoCloseable { @Override void close(); }
    private final List<ModelPart> glass=new ArrayList<>();
    private ModelPart windowFrames;
    private final MilitaryVehicleModel model;
    private final boolean enclosed;
    public VehicleViewRendering(MilitaryVehicleModel model,String chassis) {
        this.model=model;enclosed=!chassis.equals("truck");
        try(var stream=VehicleViewRendering.class.getResourceAsStream("/assets/blacklight/military/vehicle-models.json")) {
            if(stream==null)throw new IllegalStateException("Missing authored vehicle windows");
            var asset=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("chassis").getAsJsonObject(chassis);
            var bones=new LinkedHashMap<String,ModelPart>();bones.put("root",model.root());
            for(var value:asset.getAsJsonArray("bones")) {
                var bone=value.getAsJsonObject();String name=bone.get("name").getAsString();
                if(!name.equals("root"))bones.put(name,bones.get(bone.get("parent").getAsString()).getChild(name));
            }
            JsonObject backing=null;var panes=new ArrayList<JsonObject>();
            for(var value:asset.getAsJsonArray("parts")) {
                var part=value.getAsJsonObject();
                if(part.get("material").getAsString().equals("glass"))glass.add(bones.get(part.get("parent").getAsString()).getChild(part.get("name").getAsString()));
                if(part.get("name").getAsString().equals("cab_windscreen_frame"))backing=part;
                if(part.get("name").getAsString().startsWith("windscreen_"))panes.add(part);
            }
            if(backing!=null) {
                glass.add(bones.get(backing.get("parent").getAsString()).getChild(backing.get("name").getAsString()));
                var box=backing.getAsJsonArray("box");float x=box.get(0).getAsFloat(),y=box.get(1).getAsFloat();
                List<float[]> rectangles=new ArrayList<>();rectangles.add(new float[]{x,y,x+box.get(3).getAsFloat(),y+box.get(4).getAsFloat()});
                for(var pane:panes) {
                    var p=pane.getAsJsonArray("box");float px=p.get(0).getAsFloat(),py=p.get(1).getAsFloat();var next=new ArrayList<float[]>();
                    for(var r:rectangles) {
                        float a=Math.max(r[0],px),b=Math.max(r[1],py),c=Math.min(r[2],px+p.get(3).getAsFloat()),d=Math.min(r[3],py+p.get(4).getAsFloat());
                        if(a>=c||b>=d){next.add(r);continue;}
                        if(r[0]<a)next.add(new float[]{r[0],r[1],a,r[3]});
                        if(c<r[2])next.add(new float[]{c,r[1],r[2],r[3]});
                        if(r[1]<b)next.add(new float[]{a,r[1],c,b});
                        if(d<r[3])next.add(new float[]{a,d,c,r[3]});
                    }
                    rectangles=next;
                }
                var mesh=new MeshDefinition();var uv=backing.getAsJsonArray("uv");int index=0;
                for(var r:rectangles)mesh.getRoot().addOrReplaceChild("frame_"+index++,CubeListBuilder.create().texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt())
                        .addBox(r[0],r[1],box.get(2).getAsFloat(),r[2]-r[0],r[3]-r[1],box.get(5).getAsFloat()),PartPose.ZERO);
                windowFrames=LayerDefinition.create(mesh,2048,2048).bakeRoot();
            }
        }catch(java.io.IOException failure){throw new IllegalStateException("Cannot read authored vehicle windows",failure);}
    }
    public void renderWindowFrames(PoseStack stack,VertexConsumer out,int light,int overlay) {
        if(windowFrames!=null)windowFrames.render(stack,out,light,overlay);
    }
    public void render(PoseStack stack,VertexConsumer out,int light,int overlay,boolean localFirstPerson,VehicleCamera.View view) {
        if(localFirstPerson&&enclosed&&view!=null)out=new VehicleOpticClipper(out,stack,view.position(),net.minecraft.world.phys.Vec3.directionFromRotation(view.pitch(),view.yaw()));
        try(var windows=open(localFirstPerson)) {
            model.root().render(stack,out,light,overlay);
            if(localFirstPerson)renderWindowFrames(stack,out,light,overlay);
        }
    }
    public Pass open(boolean localFirstPerson) {
        if(!localFirstPerson)return ()->{};
        var before=new boolean[glass.size()];
        for(int i=0;i<glass.size();i++){before[i]=glass.get(i).skipDraw;glass.get(i).skipDraw=true;}
        return ()->{for(int i=0;i<glass.size();i++)glass.get(i).skipDraw=before[i];};
    }
}
