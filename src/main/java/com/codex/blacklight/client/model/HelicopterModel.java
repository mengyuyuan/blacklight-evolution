package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;

/** Positive-Y authored cabin with separate main/tail rotors and sliding doors. */
public final class HelicopterModel {
    private final ModelPart root;
    public HelicopterModel(){
        JsonObject data;
        try(var input=HelicopterModel.class.getResourceAsStream("/assets/blacklight/military/helicopter-model.json")){
            if(input==null)throw new IllegalStateException("Missing helicopter mesh");
            data=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        var mesh=new MeshDefinition();var bones=new HashMap<String,PartDefinition>();
        for(var entry:data.getAsJsonObject("bones").entrySet()){
            var v=entry.getValue().getAsJsonArray();bones.put(entry.getKey(),mesh.getRoot().addOrReplaceChild(entry.getKey(),CubeListBuilder.create(),PartPose.offset(v.get(0).getAsFloat()*16,v.get(1).getAsFloat()*16,v.get(2).getAsFloat()*16)));
        }
        for(var raw:data.getAsJsonArray("parts")){
            var p=raw.getAsJsonObject();var b=p.getAsJsonArray("box");var uv=p.getAsJsonArray("uv");var pose=PartPose.ZERO;
            if(p.has("rotation")){
                var r=p.getAsJsonObject("rotation");float angle=(float)Math.toRadians(r.get("degrees").getAsFloat());String axis=r.get("axis").getAsString();
                pose=PartPose.rotation(axis.equals("x")?angle:0,axis.equals("y")?angle:0,axis.equals("z")?angle:0);
                if(r.has("origin")){
                    var origin=r.getAsJsonArray("origin");float x=origin.get(0).getAsFloat()*16,y=origin.get(1).getAsFloat()*16,z=origin.get(2).getAsFloat()*16;
                    b.set(0,new JsonPrimitive(b.get(0).getAsFloat()-x));b.set(1,new JsonPrimitive(b.get(1).getAsFloat()-y));b.set(2,new JsonPrimitive(b.get(2).getAsFloat()-z));
                    pose=PartPose.offsetAndRotation(x,y,z,axis.equals("x")?angle:0,axis.equals("y")?angle:0,axis.equals("z")?angle:0);
                }
            }
            bones.get(p.get("parent").getAsString()).addOrReplaceChild(p.get("name").getAsString(),CubeListBuilder.create().texOffs(uv.get(0).getAsInt(),uv.get(1).getAsInt())
                    .addBox(b.get(0).getAsFloat(),b.get(1).getAsFloat(),b.get(2).getAsFloat(),b.get(3).getAsFloat(),b.get(4).getAsFloat(),b.get(5).getAsFloat()),pose);
        }
        root=LayerDefinition.create(mesh,data.get("textureWidth").getAsInt(),data.get("textureHeight").getAsInt()).bakeRoot();
    }
    public ModelPart pose(BlackwatchHelicopter entity,float partial){
        root.getAllParts().forEach(ModelPart::resetPose);
        float angle=entity.rotorAngle(partial);
        root.getChild("rotor").yRot=angle;root.getChild("tail_rotor").xRot=-angle*2.1F;
        if(entity.doorsOpen()){root.getChild("door_left").z-=31;root.getChild("door_right").z-=31;}
        var gun=root.getChild("gun");gun.yRot=(float)Math.toRadians(entity.gunYaw());gun.xRot=(float)Math.toRadians(entity.gunPitch());
        for(int side:new int[]{-1,1})for(int slot=0;slot<4;slot++){
            var missile=root.getChild("hellfire_"+side+"_"+slot);
            missile.visible=!entity.pilot().owned()||com.codex.blacklight.military.aircraft.HelicopterHellfireRack.loaded(side,slot,entity.controlData().getInt("Rockets"));
        }
        var hijack=entity.pilot().hijack();
        if(hijack.active())root.getChild(hijack.side()<0?"cockpit_left":"cockpit_right").yRot=hijack.side()*(float)Math.toRadians(75)*Math.min(1,(hijack.ticks()+partial)/18F);
        return root;
    }
    public ModelPart wreckPose(float age,long seed){
        root.getAllParts().forEach(ModelPart::resetPose);
        root.getAllParts().forEach(part->part.visible=true);
        root.getChild("body").yScale=.78F;
        VehicleWreckPose.fragment(root.getChild("rotor"),age,new net.minecraft.world.phys.Vec3(1.8,2.5,-.8),-3.65F,.10F);
        root.getChild("rotor").yRot=(float)(seed%31)+Math.min(age,32)*.13F;
        VehicleWreckPose.fragment(root.getChild("tail_rotor"),age,new net.minecraft.world.phys.Vec3(-2.1,2,1),-2.35F,.6F);
        for(int side:new int[]{-1,1}){
            VehicleWreckPose.fragment(root.getChild(side<0?"door_left":"door_right"),age,new net.minecraft.world.phys.Vec3(side*2.7,3,-.6),-.6F,side*1.25F);
            root.getChild(side<0?"cockpit_left":"cockpit_right").yRot=side*1.2F;
            root.getChild(side<0?"pod_left":"pod_right").zRot=side*.4F;
            VehicleWreckPose.fragment(root.getChild(side<0?"rapid_left":"rapid_right"),age,new net.minecraft.world.phys.Vec3(side*1.8,2.3,.5),1.4F,side*.8F);
            for(int slot=0;slot<4;slot++)VehicleWreckPose.fragment(root.getChild("hellfire_"+side+"_"+slot),age,new net.minecraft.world.phys.Vec3(side*(1.2+slot*.12),1.8,.8),1.4F,side*.6F);
        }
        return root;
    }
}
