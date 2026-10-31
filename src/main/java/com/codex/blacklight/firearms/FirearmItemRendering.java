package com.codex.blacklight.firearms;

import com.codex.blacklight.client.renderer.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.io.InputStreamReader;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** Uses the existing baked item/atlas, detaching only its authored magazine elements during reload. */
public final class FirearmItemRendering {
    private record Mesh(List<BakedQuad> body,List<BakedQuad> magazine,Vec3 muzzle,Vec3 magazineGrip,Vec3 boltGrip){}
    private static final Map<BakedModel,Mesh> CACHE=new WeakHashMap<>();
    private static Mesh mesh(Minecraft mc,BakedModel model,String weapon){return CACHE.computeIfAbsent(model,key->{
        var boxes=new ArrayList<AABB>();Vec3 muzzle=new Vec3(1,.5,.5),bolt=new Vec3(.55,.6,.5);double front=-100;
        var source=mc.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath("blacklight","models/item/"+weapon+".json"));
        if(source.isPresent())try(var reader=new InputStreamReader(source.get().open(),java.nio.charset.StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            if(json.has("elements"))for(var value:json.getAsJsonArray("elements")){
                var element=value.getAsJsonObject();String name=element.has("name")?element.get("name").getAsString():"";
                JsonArray f=element.getAsJsonArray("from"),t=element.getAsJsonArray("to");
                var box=new AABB(f.get(0).getAsDouble()/16,f.get(1).getAsDouble()/16,f.get(2).getAsDouble()/16,t.get(0).getAsDouble()/16,t.get(1).getAsDouble()/16,t.get(2).getAsDouble()/16);
                if(FirearmReloadMotion.magazinePart(name))boxes.add(box);
                if(name.contains("charging handle"))bolt=box.getCenter();
                if((name.contains("muzzle")||name.equals("barrel"))&&box.maxX>front){front=box.maxX;muzzle=new Vec3(box.maxX,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);}
            }
        }catch(java.io.IOException|RuntimeException ignored){boxes.clear();}
        var body=new ArrayList<BakedQuad>();var magazine=new ArrayList<BakedQuad>();
        for(Direction face:new Direction[]{null,Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST})for(var quad:model.getQuads(null,face,RandomSource.create(42))){
            boolean detachable=false;
            for(var box:boxes){boolean inside=true;var vertices=quad.getVertices();int stride=vertices.length/4;
                for(int i=0;i<4;i++){double x=Float.intBitsToFloat(vertices[i*stride]),y=Float.intBitsToFloat(vertices[i*stride+1]),z=Float.intBitsToFloat(vertices[i*stride+2]);
                    if(x<box.minX-1e-5||x>box.maxX+1e-5||y<box.minY-1e-5||y>box.maxY+1e-5||z<box.minZ-1e-5||z>box.maxZ+1e-5){inside=false;break;}}
                if(inside){detachable=true;break;}
            }
            (detachable?magazine:body).add(quad);
        }
        Vec3 grip=Vec3.ZERO;double volume=0;for(var b:boxes){double w=b.getXsize()*b.getYsize()*b.getZsize();grip=grip.add(b.getCenter().scale(w));volume+=w;}
        return new Mesh(List.copyOf(body),List.copyOf(magazine),muzzle,volume>0?grip.scale(1/volume):null,bolt);
    });}
    private static double smooth(double a,double b,double p){double t=Math.clamp((p-a)/(b-a),0,1);return t*t*(3-2*t);}
    /** Bind the support wrist to the real baked magazine and charging-handle coordinates. */
    public static FirearmHandPose.Hand reloadHand(Minecraft mc,ItemStack item,PoseStack weaponBasis,ItemDisplayContext context,boolean left,double progress){
        String weapon=((FirearmItem)item.getItem()).definition().id();var model=mc.getItemRenderer().getModel(item,mc.level,mc.player,0);var m=mesh(mc,model,weapon);
        if(m.magazineGrip==null)return FirearmHandPose.support(weapon,left,progress);
        var frame=FirearmReloadMotion.sample(progress);var basis=new PoseStack();basis.mulPose(weaponBasis.last().pose());model.getTransforms().getTransform(context).apply(left,basis);basis.translate(-.5,-.5,-.5);
        var center=m.magazineGrip.add(0,-frame.magazineDrop(),0);var charge=m.boltGrip;
        var local=center.lerp(charge,smooth(.73,.80,progress));
        var point=basis.last().pose().transformPosition(new org.joml.Vector3f((float)local.x,(float)local.y,(float)local.z));
        double fetch=smooth(.30,.41,progress)*(1-smooth(.47,.58,progress));
        point.y-=(float)(fetch*.12);point.z+=(float)(fetch*.06);
        point.z+=(float)(frame.bolt()*.065);
        var base=FirearmHandPose.support(weapon,left,0);var rotation=FirearmHandPose.support(weapon,left,progress);double w=frame.release();
        return new FirearmHandPose.Hand(base.left(),base.x()+(point.x-base.x())*w,base.y()+(point.y-base.y())*w,base.z()+(point.z-base.z())*w,rotation.pitch(),rotation.yaw(),rotation.roll());
    }
    public static void render(Minecraft mc,PoseStack pose,MultiBufferSource buffers,ItemStack item,ItemDisplayContext context,boolean left,int light,double reload,double shotAge){
        String weapon=((FirearmItem)item.getItem()).definition().id();var model=mc.getItemRenderer().getModel(item,mc.level,mc.player,0);var mesh=mesh(mc,model,weapon);
        pose.pushPose();model.getTransforms().getTransform(context).apply(left,pose);pose.translate(-.5,-.5,-.5);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS));
        for(var quad:mesh.body)out.putBulkData(pose.last(),quad,1,1,1,1,light,OverlayTexture.NO_OVERLAY);
        var frame=FirearmReloadMotion.sample(reload);
        if(frame.magazineVisible()){
            pose.pushPose();pose.translate(0,-frame.magazineDrop(),0);
            for(var quad:mesh.magazine)out.putBulkData(pose.last(),quad,1,1,1,1,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        if(shotAge>=0&&shotAge<.085){
            var flash=buffers.getBuffer(RenderType.entityTranslucent(ConsumedMobVisuals.WHITE));double size=.25*(1-shotAge/.085);var at=mesh.muzzle.add(.025,0,0);
            GunplayGeometry.rod(pose,flash,at,at.add(size,0,0),size*.25,0,0xffffc361,LightTexture.FULL_BRIGHT);
            GunplayGeometry.rod(pose,flash,at,at.add(size*.6,0,0),size*.11,0,0xfffff0c0,LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
    private FirearmItemRendering(){}
}
