package com.codex.blacklight.client;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.client.model.*;
import com.codex.blacklight.client.renderer.*;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** QA-only vertex sink: reuse the last displayed skin matrices and the normal weapon mesh emitter. */
final class RagdollVisualAuditActorFloor {
    private static ClawArmModel hammer;
    private static Object field(Object o,String name)throws ReflectiveOperationException{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void sample(JsonObject row,float partial)throws ReflectiveOperationException{
        var mc=Minecraft.getInstance();var f=StandalonePlayerRendering.class.getDeclaredField("RENDERERS");f.setAccessible(true);
        BlacklightPlayerModel model=null;
        for(var renderer:((Map<?,?>)f.get(null)).values()){
            var candidate=(BlacklightPlayerModel)((BlacklightPlayerRenderer)renderer).getModel();
            if(field(candidate,"renderedPlayer")==mc.player){model=candidate;break;}
        }
        if(model==null||model.matrices()==null)return;
        Map<Integer,Double> low=new TreeMap<>();int[] region={0},vertices={0};
        boolean[] weaponPass={false};double[] terrainDepth={0};int[] terrainVertices={0};
        var feet=mc.player.getPosition(partial);
        var facing=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(mc.player,partial)));
        Map<net.minecraft.core.BlockPos,List<net.minecraft.world.phys.AABB>> terrain=new HashMap<>();
        VertexConsumer sink=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class,ConsumeAnatomy.Consumer.class},(self,method,args)->{
            if(method.getName().equals("anatomy")&&args[0] instanceof Integer id)region[0]=id;
            if((method.getName().equals("addVertex")||method.getName().equals("vertex"))&&args!=null&&args.length>=3){
                org.joml.Vector3f point;
                if(args[0] instanceof org.joml.Matrix4f matrix)point=matrix.transformPosition(new org.joml.Vector3f(((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()));
                else if(args[0] instanceof PoseStack.Pose frame)point=frame.pose().transformPosition(new org.joml.Vector3f(((Number)args[1]).floatValue(),((Number)args[2]).floatValue(),((Number)args[3]).floatValue()));
                else if(args[0] instanceof Number&&args[1] instanceof Number&&args[2] instanceof Number)point=new org.joml.Vector3f(((Number)args[0]).floatValue(),((Number)args[1]).floatValue(),((Number)args[2]).floatValue());
                else return method.getReturnType()==void.class?null:self;
                low.merge(region[0],(double)point.y,Math::min);vertices[0]++;
                if(weaponPass[0]){
                    facing.transformPosition(point);var at=feet.add(point.x,point.y,point.z);terrainVertices[0]++;
                    var pos=net.minecraft.core.BlockPos.containing(at);
                    for(var box:terrain.computeIfAbsent(pos,p->mc.level.getBlockState(p).getCollisionShape(mc.level,p).toAabbs().stream().map(b->b.move(p)).toList())){
                        if(!box.contains(at))continue;
                        double depth=Math.min(Math.min(at.x-box.minX,box.maxX-at.x),Math.min(Math.min(at.y-box.minY,box.maxY-at.y),Math.min(at.z-box.minZ,box.maxZ-at.z)));
                        terrainDepth[0]=Math.max(terrainDepth[0],depth);
                    }
                }
            }
            return method.getReturnType()==void.class?null:self;
        });
        var stack=new PoseStack();stack.scale(.9375F,.9375F,.9375F);BlacklightBoneRendering.vanillaFrame(stack);
        model.renderToBuffer(stack,sink,15728880,0,1,1,1,1);
        var body=new JsonObject();low.forEach((id,y)->body.addProperty(String.valueOf(id),y));row.add("actorSkinLowest",body);
        low.clear();weaponPass[0]=true;
        var matrices=model.matrices();var rig=BlacklightSkeleton.get();var scales=WeaponScaleRenderer.sample(mc.player,partial);
        var morph=WeaponMorphTracker.sample(mc.player,partial);
        var surface=morph.parts().stream().filter(part->part.form()==com.codex.blacklight.power.PowerForm.HAMMERFISTS).map(WeaponMorphTracker.Part::surface).findFirst().orElse(null);
        if(surface==null)return;
        float materialTime=(float)(morph.materialTime()%50_000D);
        if(hammer==null)hammer=new ClawArmModel("form_hammerfists");
        for(int side=0;side<2;side++)for(String part:new String[]{"Arm_","Hand_"}){
            String name=part+(side==0?"R":"L");region[0]=side;
            stack=new PoseStack();stack.scale(.9375F,.9375F,.9375F);BlacklightBoneRendering.apply(stack,matrices[rig.searchJointByName(name).getId()]);
            hammer.renderJoint(name,stack,sink,15728880,0,surface.growth(),1,null,Float.POSITIVE_INFINITY,part.equals("Hand_")?scales[side]:null,surface,materialTime);
        }
        var fists=new JsonObject();low.forEach((id,y)->fists.addProperty(String.valueOf(id),y));row.add("actorHammerLowest",fists);row.addProperty("actorProbeVertices",vertices[0]);
        row.addProperty("actorHammerTerrainVertices",terrainVertices[0]);row.addProperty("actorHammerTerrainPenetration",terrainDepth[0]);
        var weapon=com.codex.blacklight.registry.ModAttachments.peekData(mc.player,com.codex.blacklight.registry.ModAttachments.WEAPON_VISUAL,com.codex.blacklight.combat.WeaponVisual.EMPTY);
        var sample=WeaponMotionClock.sample(weapon,mc.level.getGameTime()+partial);
        if(sample!=null){row.addProperty("actorClip",sample.key());row.addProperty("actorClipSeconds",sample.seconds());}
    }
}
