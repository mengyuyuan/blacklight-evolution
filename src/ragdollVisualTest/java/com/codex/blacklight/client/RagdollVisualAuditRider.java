package com.codex.blacklight.client;

import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.client.model.BlacklightPlayerModel;
import com.codex.blacklight.client.model.ClawArmModel;
import com.codex.blacklight.client.tuning.ClawTransformSettings;
import com.codex.blacklight.power.PowerForm;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Proxy;
import com.codex.blacklight.client.renderer.*;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightMutant;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** QA only: actual displayed rider joints against tracked native Brute torso geometry. */
final class RagdollVisualAuditRider {
    private static final JsonArray BONES=load();
    private static JsonArray load(){
        try(var in=RagdollVisualAuditRider.class.getResourceAsStream("/assets/blacklight/geo/blacklight_brute_mc.geo.json")){
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private record Surface(Vec3 center,Vec3[] axes,double[] half){
        double gap(Vec3 point){
            var d=point.subtract(center);double out=0,inside=Double.POSITIVE_INFINITY;
            for(int i=0;i<3;i++){double q=Math.abs(d.dot(axes[i]))-half[i];out+=Math.max(0,q)*Math.max(0,q);inside=Math.min(inside,-q);}
            return out>0?Math.sqrt(out):-inside;
        }
    }
    private static JsonArray vector(Vec3 p){var a=new JsonArray();a.add(p.x);a.add(p.y);a.add(p.z);return a;}
    private static final Map<PowerForm,ClawArmModel> MESHES=new EnumMap<>(PowerForm.class);
    private static void mesh(JsonObject row,BlacklightPlayerModel actor,List<Surface> surfaces,float partial){
        var mc=Minecraft.getInstance();var feet=mc.player.getPosition(partial);
        var facing=new Matrix4f().rotateY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(mc.player,partial)));
        var stats=new TreeMap<String,double[]>();String[] region={"skin0"};
        VertexConsumer sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class,ConsumeAnatomy.Consumer.class},(self,method,args)->{
            if(method.getName().equals("anatomy")&&args[0] instanceof Integer id)region[0]="skin"+id;
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args!=null&&args.length>=3){
                Vector3f p;
                if(args[0] instanceof Matrix4f matrix)p=matrix.transformPosition(new Vector3f(((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()));
                else if(args[0] instanceof PoseStack.Pose frame)p=frame.pose().transformPosition(new Vector3f(((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()));
                else if(args[0] instanceof Number&&args[1] instanceof Number&&args[2] instanceof Number)p=new Vector3f(((Number)args[0]).floatValue(),((Number)args[1]).floatValue(),((Number)args[2]).floatValue());
                else return method.getReturnType()==void.class?null:self;
                facing.transformPosition(p);var world=feet.add(p.x,p.y,p.z);
                double gap=surfaces.stream().mapToDouble(s->s.gap(world)).min().orElse(Double.POSITIVE_INFINITY);
                var value=stats.computeIfAbsent(region[0],ignored->new double[]{0,Double.POSITIVE_INFINITY,0});
                value[0]++;value[1]=Math.min(value[1],gap);value[2]=Math.max(value[2],Math.max(0,-gap));
            }
            return method.getReturnType()==void.class?null:self;
        });
        var stack=new PoseStack();stack.scale(.9375F,.9375F,.9375F);BlacklightBoneRendering.vanillaFrame(stack);
        actor.renderToBuffer(stack,sink,15728880,0,1,1,1,1);
        var rig=BlacklightSkeleton.get();var matrices=actor.matrices();var morph=WeaponMorphTracker.sample(mc.player,partial);
        float time=(float)(morph.materialTime()%50_000D);
        float fit=1;
        if(com.codex.blacklight.client.model.MercerPlayerMesh.active(mc.player))
            fit=net.minecraft.util.Mth.lerp(net.minecraft.util.Mth.clamp(com.codex.blacklight.power.UtilityPowerSystem.visualWeight(mc.player,PowerForm.ARMOR,partial),0,1),.82F,1F);
        for(var part:morph.parts()){
            if(part.form()!=PowerForm.CLAWS&&part.form()!=PowerForm.MUSCLE_MASS)continue;
            var model=MESHES.computeIfAbsent(part.form(),f->new ClawArmModel(f==PowerForm.CLAWS?"claw_blender":"form_"+f.id()));
            boolean claws=part.form()==PowerForm.CLAWS;
            for(String side:List.of("R","L"))for(String prefix:List.of("Arm_","Hand_")){
                String joint=prefix+side;region[0]=part.form().id()+"_"+joint;
                var hand=side.equals("R")?ClawTransformSettings.HandSide.RIGHT:ClawTransformSettings.HandSide.LEFT;
                float width=claws?(float)ClawTransformSettings.limbWidth(hand):1;
                stack=new PoseStack();stack.scale(.9375F,.9375F,.9375F);BlacklightBoneRendering.apply(stack,matrices[rig.searchJointByName(joint).getId()]);
                boolean lower=prefix.equals("Hand_");stack.scale(width*fit,lower?fit:1,width*fit);
                Matrix4f wrist=null;
                if(claws&&lower){var tip=rig.searchJointByName("Tool_"+side);wrist=new Matrix4f(matrices[rig.searchJointByName(joint).getId()]).invert().mul(matrices[tip.getId()]).mul(new Matrix4f(tip.getLocalTransform()).invert());}
                model.renderJoint(joint,stack,sink,15728880,0,part.surface().growth(),claws&&lower?(float)ClawTransformSettings.talonLength(hand):1,wrist,Float.POSITIVE_INFINITY,null,part.surface(),time);
            }
        }
        var result=new JsonObject();stats.forEach((name,v)->{var item=new JsonObject();item.addProperty("vertices",v[0]);item.addProperty("minSignedGap",v[1]);item.addProperty("maxVertexPenetration",v[2]);result.add(name,item);});
        row.add("riderMeshTorso",result);
    }
    static void sample(JsonObject row,BlacklightMutant brute,float partial)throws ReflectiveOperationException{
        if(!ConsumeSequence.mountedStage(com.codex.blacklight.registry.ModAttachments.peekData(brute,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY).stage()))return;
        var mc=Minecraft.getInstance();var f=StandalonePlayerRendering.class.getDeclaredField("RENDERERS");f.setAccessible(true);
        BlacklightPlayerModel actor=null;
        for(var r:((Map<?,?>)f.get(null)).values()){
            var m=(BlacklightPlayerModel)((BlacklightPlayerRenderer)r).getModel();var field=m.getClass().getDeclaredField("renderedPlayer");field.setAccessible(true);
            if(field.get(m)==mc.player){actor=m;break;}
        }
        if(actor==null||actor.matrices()==null)return;
        var visual=com.codex.blacklight.registry.ModAttachments.peekData(brute,com.codex.blacklight.registry.ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        var seen=com.codex.blacklight.client.animation.PhaseEntryBlend.class.getDeclaredField("seen");seen.setAccessible(true);
        var entry=(long[])((Map<?,?>)seen.get(com.codex.blacklight.client.animation.PhaseEntryBlend.CONSUME_ACTOR)).get(mc.player);
        float entryWeight=entry!=null&&entry[0]==visual.started()*16+visual.stage()
                ?com.codex.blacklight.client.animation.PhaseEntryBlend.weight(entry[1],mc.level.getGameTime(),partial,4):0;
        row.addProperty("riderEntryWeight",entryWeight);
        if(!(mc.getEntityRenderDispatcher().getRenderer(brute) instanceof software.bernie.geckolib.renderer.GeoEntityRenderer<?> renderer))return;
        var processor=renderer.getGeoModel().getAnimationProcessor();boolean tracked=true;
        for(String key:List.of("torso","head")){var b=(software.bernie.geckolib.cache.object.GeoBone)processor.getBone(key);if(b==null)return;tracked&=b.isTrackingMatrices();b.setTrackingMatrices(true);}
        if(!tracked)return;
        var surfaces=new ArrayList<Surface>();var torso=new JsonArray();
        for(var value:BONES){var b=value.getAsJsonObject();String name=b.get("name").getAsString();if(!name.equals("torso"))continue;
            var bone=(software.bernie.geckolib.cache.object.GeoBone)processor.getBone(name);var matrix=new Matrix4f(bone.getWorldSpaceMatrix());matrix.m00(matrix.m00()-2).m11(matrix.m11()-2).m22(matrix.m22()-2);
            var pivot=b.getAsJsonArray("pivot");
            for(var element:b.getAsJsonArray("cubes")){
                var c=element.getAsJsonObject();var o=c.getAsJsonArray("origin");var size=c.getAsJsonArray("size");
                float[] center=new float[3];double[] half=new double[3];var axes=new Vec3[3];
                for(int i=0;i<3;i++){
                    center[i]=(o.get(i).getAsFloat()+size.get(i).getAsFloat()/2-pivot.get(i).getAsFloat())/16*(i==0?-1:1);
                    var axis=matrix.transformDirection(new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0));half[i]=size.get(i).getAsDouble()/32*axis.length();axis.normalize();axes[i]=new Vec3(axis.x,axis.y,axis.z);
                }
                var at=matrix.transformPosition(new Vector3f(center[0],center[1],center[2]));
                var world=new Vec3(at.x,at.y,at.z).subtract(brute.position()).add(brute.getPosition(partial));
                surfaces.add(new Surface(world,axes,half));var item=new JsonObject();item.add("center",vector(world));
                item.add("half",new Gson().toJsonTree(half));var directions=new JsonArray();for(var axis:axes)directions.add(vector(axis));item.add("axes",directions);torso.add(item);
            }
        }
        var rig=BlacklightSkeleton.get();var frame=new Matrix4f().rotateY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(mc.player,partial))).scale(.9375F);
        var joints=new JsonObject();var gaps=new JsonObject();
        for(String name:List.of("Root","Torso","Head","Arm_R","Arm_L","Hand_R","Hand_L","Tool_R","Tool_L","Thigh_R","Thigh_L","Leg_R","Leg_L","Knee_R","Knee_L")){
            var at=actor.matrices()[rig.searchJointByName(name).getId()].getTranslation(new Vector3f());frame.transformPosition(at);
            var world=mc.player.getPosition(partial).add(at.x,at.y,at.z);joints.add(name,vector(world));
            gaps.addProperty(name,surfaces.stream().mapToDouble(surface->surface.gap(world)).min().orElse(Double.NaN));
        }
        row.add("riderJoints",joints);row.add("riderJointTorsoGap",gaps);row.add("riderTorsoSurfaces",torso);
        mesh(row,actor,surfaces,partial);
    }
}
