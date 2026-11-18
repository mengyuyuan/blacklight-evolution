package com.codex.blacklight.client.model;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.phys.Vec3;

/** Same authored ducted-rotor geometry, with a detached blade and sensor. */
public final class UavWreckModel {
    private final ModelPart root;
    public UavWreckModel(){
        JsonObject data;
        try(var input=UavWreckModel.class.getResourceAsStream("/assets/blacklight/military/recon-uav-wreck.json")){
            if(input==null)throw new IllegalStateException("Missing UAV wreck mesh");
            data=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var mesh=new MeshDefinition();var bones=new HashMap<String,PartDefinition>();
        for(var entry:data.getAsJsonObject("bones").entrySet()){
            var v=entry.getValue().getAsJsonArray();bones.put(entry.getKey(),mesh.getRoot().addOrReplaceChild(entry.getKey(),CubeListBuilder.create(),PartPose.offset(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat())));
        }
        int index=0;
        for(var raw:data.getAsJsonArray("parts")){
            var p=raw.getAsJsonObject();var b=p.getAsJsonArray("box");var uv=p.getAsJsonArray("uv");var pivot=p.getAsJsonArray("pivot");var r=p.getAsJsonArray("rotation");
            var pose=PartPose.offsetAndRotation(pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat(),(float)Math.toRadians(r.get(0).getAsFloat()),(float)Math.toRadians(r.get(1).getAsFloat()),(float)Math.toRadians(r.get(2).getAsFloat()));
            bones.get(p.get("parent").getAsString()).addOrReplaceChild("part_"+(index++),CubeListBuilder.create().texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt()).addBox(b.get(0).getAsFloat(),b.get(1).getAsFloat(),b.get(2).getAsFloat(),b.get(3).getAsFloat(),b.get(4).getAsFloat(),b.get(5).getAsFloat()),pose);
        }
        root=LayerDefinition.create(mesh,512,128).bakeRoot();
    }
    public ModelPart wreckPose(float age,long seed){
        root.getAllParts().forEach(ModelPart::resetPose);root.getChild("body").yScale=.82F;
        VehicleWreckPose.fragment(root.getChild("rotor"),age,new Vec3(1.2,2,-.7),-.5846876F,.3F);
        root.getChild("rotor").yRot=(float)(seed%31)+Math.min(age,30)*.16F;
        VehicleWreckPose.fragment(root.getChild("sensor"),age,new Vec3(-1,2.2,.65),-1.63125F,-.7F);
        return root;
    }
}
