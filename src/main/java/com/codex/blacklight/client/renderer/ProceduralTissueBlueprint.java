package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.animation.ReceiverModelPose;
import com.codex.blacklight.infection.TissueMorphogenesis;
import com.codex.blacklight.mixin.client.AgeableModelAccessor;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import com.google.gson.*;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.*;
import java.util.*;

/** Reads real bind geometry. Registry/species names never select organ geometry. */
final class ProceduralTissueBlueprint {
    record Definition(JsonObject source,Map<String,ModelPart> parts) {}
    private record Secondary(TissueMorphogenesis.Bone bone,ModelPart part,int cubeSlot) {}
    private ProceduralTissueBlueprint(){}
    static Definition generate(EntityModel<?> model,int variant,boolean coat) {
        Map<String,ModelPart> parts=new TreeMap<>();Map<ModelPart,Matrix4f> bind=new IdentityHashMap<>();
        var root=ReceiverModelPose.root(model);
        if(root!=null)collect(root,"root",new Matrix4f(),parts,bind);
        else if(model instanceof HumanoidModel<?> h) {
            collect(h.body,"body",new Matrix4f(),parts,bind);collect(h.head,"head",new Matrix4f(),parts,bind);
            collect(h.rightArm,"right_arm",new Matrix4f(),parts,bind);collect(h.leftArm,"left_arm",new Matrix4f(),parts,bind);
            collect(h.rightLeg,"right_leg",new Matrix4f(),parts,bind);collect(h.leftLeg,"left_leg",new Matrix4f(),parts,bind);
        } else if(model instanceof AgeableModelAccessor ageable) {
            int i=0;for(var p:ageable.blacklight$heads())collect(p,"head_"+i++,new Matrix4f(),parts,bind);
            i=0;for(var p:ageable.blacklight$body())collect(p,"body_"+i++,new Matrix4f(),parts,bind);
        }
        var anatomy=ConsumeAnatomy.model(model);var bones=new ArrayList<TissueMorphogenesis.Bone>();var secondary=new ArrayList<Secondary>();
        for(var entry:parts.entrySet()) {
            String path=entry.getKey(),name=path.substring(path.lastIndexOf('/')+1).replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT);
            Set<String> words=new HashSet<>(Arrays.asList(name.split("[^a-z0-9]+")));
            if(!Collections.disjoint(words,Set.of("eye","eyes","ear","ears","horn","horns","saddle","rein","reins","stirrup","stirrups","bridle"))
                    ||!coat&&!Collections.disjoint(words,Set.of("hat","jacket","sleeve","pants","armor","armour","overlay")))continue;
            ModelPart part=entry.getValue();var cube=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes().stream()
                    .max(Comparator.comparingDouble(c->(c.maxX-c.minX)*(c.maxY-c.minY)*(c.maxZ-c.minZ))).orElse(null);
            if(cube==null)continue;
            var box=new TissueMorphogenesis.Box(new TissueMorphogenesis.V(cube.minX,cube.minY,cube.minZ),new TissueMorphogenesis.V(cube.maxX,cube.maxY,cube.maxZ));
            var inverse=new Matrix4f(bind.get(part)).invert();
            int role=TissueMorphogenesis.semanticRole(name);
            if(root==null&&model instanceof AgeableModelAccessor&&!(model instanceof HumanoidModel<?>))role=anatomy.getOrDefault(part,role);
            // A neck and a muzzle follow the head, but are not additional heads.
            if(name.contains("neck")||name.equals("head_parts"))role=0;
            if(name.contains("mouth")||name.contains("muzzle")||name.contains("nose")||name.contains("jaw")||name.contains("mane"))role=6;
            Vector3f centre=bind.get(part).transformPosition(new Vector3f((cube.minX+cube.maxX)*.5F,(cube.minY+cube.maxY)*.5F,(cube.minZ+cube.maxZ)*.5F));
            var bone=new TissueMorphogenesis.Bone(path,role,box,direction(inverse,0,-1,0),direction(inverse,0,0,-1),direction(inverse,1,0,0),value(centre));
            bones.add(bone);
            if(role==1) {
                var cubes=((ModelPartChildrenAccessor)(Object)part).blacklight$cubes();
                for(int i=0;i<cubes.size();i++) {
                    var extra=cubes.get(i);if(extra==cube)continue;
                    var bounds=new TissueMorphogenesis.Box(new TissueMorphogenesis.V(extra.minX,extra.minY,extra.minZ),new TissueMorphogenesis.V(extra.maxX,extra.maxY,extra.maxZ));
                    // A wolf muzzle can share its skull bone. Keep tiny native
                    // ears/horns, but cover substantial secondary head volumes
                    // without creating another mouth or another attacking organ.
                    if(!bounds.usable()||bounds.volume()<box.volume()*.12F)continue;
                    String alias=path+"#surface_"+i;
                    Vector3f extraCentre=bind.get(part).transformPosition(new Vector3f((extra.minX+extra.maxX)*.5F,(extra.minY+extra.maxY)*.5F,(extra.minZ+extra.maxZ)*.5F));
                    secondary.add(new Secondary(new TissueMorphogenesis.Bone(alias,6,bounds,bone.up(),bone.forward(),bone.right(),value(extraCentre)),part,i));
                }
            }
        }
        var plan=TissueMorphogenesis.generate(variant,bones,!coat);
        var source=new JsonObject();source.addProperty("format",1);source.addProperty("procedural",true);
        source.addProperty("organCount",plan.organs());var regions=new Gson().toJsonTree(plan.regions()).getAsJsonArray();int faces=plan.faces();
        for(var extra:secondary) {
            var addition=TissueMorphogenesis.generate(variant,List.of(extra.bone),false);
            if(regions.size()+addition.regions().size()>TissueMorphogenesis.MAX_BONES||faces+addition.faces()>TissueMorphogenesis.MAX_FACES)continue;
            for(var region:addition.regions()) {
                var value=new Gson().toJsonTree(region).getAsJsonObject();value.addProperty("cubeSlot",extra.cubeSlot);regions.add(value);
                parts.put(extra.bone.path(),extra.part);
            }
            faces+=addition.faces();
        }
        source.add("regions",regions);
        return new Definition(source,Map.copyOf(parts));
    }
    private static TissueMorphogenesis.V value(Vector3f v){return new TissueMorphogenesis.V(v.x,v.y,v.z);}
    private static TissueMorphogenesis.V direction(Matrix4f inverse,float x,float y,float z){return value(inverse.transformDirection(new Vector3f(x,y,z)).normalize());}
    private static void collect(ModelPart part,String path,Matrix4f parent,Map<String,ModelPart> parts,Map<ModelPart,Matrix4f> bind) {
        if(bind.containsKey(part)||parts.size()>=192)return;
        var rest=part.getInitialPose();var world=new Matrix4f(parent).translate(rest.x,rest.y,rest.z).rotateZYX(rest.zRot,rest.yRot,rest.xRot);
        parts.put(path,part);bind.put(part,world);
        new TreeMap<>(((ModelPartChildrenAccessor)(Object)part).blacklight$children()).forEach((name,child)->collect(child,path+"/"+name,world,parts,bind));
    }
}
